package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.*;
import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.project.smartsearchsystem.utils.InfoUtils.*;

@Service
public class BookServiceImpl implements BookService{

    private final BookRepository bookRepository;
    private final GoogleBookServiceImpl googleBookService;
    private final OpenLibraryServiceImpl openLibraryService;
    private final AmazonServiceImpl amazonService;
    private final EmbeddingService embeddingService;

    @Autowired
    public BookServiceImpl(BookRepository bookRepository, GoogleBookServiceImpl googleBookService, OpenLibraryServiceImpl openLibraryService, AmazonServiceImpl amazonService, EmbeddingService embeddingService) {
        this.bookRepository = bookRepository;
        this.googleBookService = googleBookService;
        this.openLibraryService = openLibraryService;
        this.amazonService = amazonService;
        this.embeddingService = embeddingService;
    }

    @Override
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    @Override
    public List<Book> fullTextSearch(String userInput) {
        String normalizedUserInput = normalize(userInput);

        // Check for exact match in the database
        Book exactMatch = bookRepository.findBookByTitle(normalizedUserInput);
        if (exactMatch != null) {
            // Double-check the match with normalized comparison
            String storedTitle = exactMatch.getTitle() != null ? normalize(exactMatch.getTitle()) : "";
            if (storedTitle.equals(normalizedUserInput)) {
                return Collections.singletonList(exactMatch);  // Return only the exact match
            }
        }

        // If no exact match, perform full-text search
        String query = userInput.trim().replaceAll("\\s+", "&");
        return bookRepository.searchBooksByFullText(query);
    }

    @Override
    public List<Book> searchLocal(String query) {
        return sortAndFilter(fullTextSearch(query), normalize(query));
    }

    @Override
    @Cacheable(value = "external_books", key = "#query")
    public ExternalSearchResults searchExternal(String query) {
        String normalizedQuery = normalize(query);

        // Helper function to create a safe, timed-out future
        // This defines: "Run this task, but if it takes > 5s, give me an empty list."
        CompletableFuture<List<GoogleBookDto>> google = CompletableFuture.supplyAsync(() -> {
                    return sortAndFilter(googleBookService.searchGoogleBooks(query), normalizedQuery);
                }).orTimeout(5, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    System.err.println("Google Search timed out or failed: " + ex.getMessage());
                    return Collections.emptyList(); // Return empty if slow/failed
                });

        CompletableFuture<List<OpenLibraryBookDto>> openLibrary = CompletableFuture.supplyAsync(() -> {
                    return sortAndFilter(openLibraryService.searchOpenLibraryBooks(query), normalizedQuery);
                }).orTimeout(5, TimeUnit.SECONDS)
                .exceptionally(ex -> Collections.emptyList());

        CompletableFuture<List<AmazonBookDto>> amazon = CompletableFuture.supplyAsync(() -> {
                    return sortAndFilter(amazonService.searchAmazonBooks(query), normalizedQuery);
                }).orTimeout(10, TimeUnit.SECONDS)
                .exceptionally(ex -> Collections.emptyList());

        // This will now take MAX 5 seconds (plus tiny overhead), never 11s.
        CompletableFuture.allOf(google, openLibrary, amazon).join();

        try {
            return new ExternalSearchResults(google.get(), openLibrary.get(), amazon.get());
        } catch (Exception e) {
            throw new RuntimeException("External search failed", e);
        }
    }

    @Override
    public void generateEmbeddingsForAllBooks() {
        List<Book> allBooks = bookRepository.findAll();

        for (Book book : allBooks) {
            if (book.getDescription() != null && book.getEmbedding() == null) {
                // 1. Calculate vector
                float[] vector = embeddingService.createEmbedding(book.getDescription());

                // 2. Save back to DB
                book.setEmbedding(vector);
                bookRepository.save(book);

                System.out.println("Vectorized book: " + book.getTitle());
            }
        }
    }

    @Override
    @Transactional
    public Book insertBook(ExternalBookSource source) {
        // 1. Validation
        if (source == null) {
            throw new IllegalArgumentException("Book data cannot be null");
        }

        // 2. Convert DTO -> Book
        Book newBook = convertToBook(source);

        // Validate Title
        if (newBook.getTitle() == null || newBook.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Book title is required");
        }

        String textToVectorized = newBook.getDescription();

        // If description is missing, use Title + Author
        if (textToVectorized == null || textToVectorized.trim().isEmpty() || textToVectorized.contains("No description available for this book.")) {
            System.out.println("No description found. Using title + author for embedding");
            textToVectorized = newBook.getTitle() + "by " + newBook.getAuthor();
        }

        if (!textToVectorized.isEmpty()) {
            try {
                float[] vector = embeddingService.createEmbedding(textToVectorized);
                newBook.setEmbedding(vector);
            } catch (Exception e) {
                System.err.println("Failed to generate embedding: " + e.getMessage());
            }
        }


        // 3. CHECK DUPLICATES (Logic Changed: Title + Author Only) 🔍
        // We strictly ignore ISBN for lookup now, as requested.
        Optional<Book> existingBook = Optional.empty();

        if (newBook.getAuthor() != null) {
            // Find by Title & Author (case-insensitive recommended)
            existingBook = bookRepository.findBookByTitleAndAuthor(
                    newBook.getTitle(),
                    newBook.getAuthor()
            );
        }
        // Fallback: If author is missing, maybe check just title?
        // (Optional, but risky if multiple books have same title.
        //  For now, let's assume Author is usually present).

        // 4. SAVE OR UPDATE
        if (existingBook.isPresent()) {
            Book bookToUpdate = existingBook.get();
            System.out.println("Duplicate found: Updating ID " + bookToUpdate.getId());

            // A. Smart Merge: Only update if we have better data

            // Update Description if ours is empty
            if (hasNoText(bookToUpdate.getDescription()) && !hasNoText(newBook.getDescription())) {
                bookToUpdate.setDescription(newBook.getDescription());
            }

            // Update Image if ours is missing/placeholder
            if (hasNoImage(bookToUpdate.getImage()) && !hasNoImage(newBook.getImage())) {
                bookToUpdate.setImage(newBook.getImage());
            }

            // Update ISBN if ours is "N/A" but the new one is valid
            if (isInvalid(bookToUpdate.getIsbn()) && !isInvalid(newBook.getIsbn())) {
                bookToUpdate.setIsbn(newBook.getIsbn());
            }

            if (newBook.getEmbedding() != null) {
                bookToUpdate.setEmbedding(newBook.getEmbedding());
            }

            return bookRepository.save(bookToUpdate);
        } else {
            // 5. INSERT NEW BOOK (The "Python" case)
            System.out.println("No duplicate found. Creating new book: " + newBook.getTitle());

            if (newBook.getDescription() == null) {
                newBook.setDescription("No description available.");
            }
            // Ensure ISBN is saved as "N/A" if missing, not null (if your DB requires it)
            if (newBook.getIsbn() == null) {
                newBook.setIsbn("N/A");
            }

            return bookRepository.save(newBook);
        }
    }

    @Override
    public int scoreBooks(String userInput, String title) {
        int score = 0;
        if (userInput == null || userInput.trim().isEmpty()) return score;

        String q = normalize(userInput);
        String[] queryWords = q.split("\\s+");

        if (title != null) {
            String t = normalize(title);

            if (t.equals(q)) {
                score += 100; // exact title match
            }
            else if (t.startsWith(q + " ")) {
                score += 80; // title starts with query
            }
            else if (t.contains(q)) {
                score += 60; // title contains query
            }

            for (String word : queryWords) {
                if (t.contains(word)) score += 10;
                if (t.startsWith(word)) score += 5;
            }
        }
        return score;
    }

    @Override
    public void logSearch(String userInput, String userToken) {

    }

    @Override
    public List<String> getMostSearchedBooks(int limit) {
        return List.of();
    }

    @Override
    public Book convertToBook(ExternalBookSource source) {
        Book book = new Book();
        book.setTitle(source.getTitle());
        book.setAuthor(source.getAuthor());
        book.setDescription(source.getDescription());
        book.setIsbn(source.getIsbn());
        book.setPublicationYear(source.getPublicationYear());
        book.setImage(source.getImageUrl());
        return book;
    }


    @Override
    public String normalize(String userInput) {
        return userInput.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "");
    }

    @Override
    public <T extends SearchableItem> List<T> sortAndFilter(List<T> items, String normalizedQuery) {
        List<ScoredItem<T>> scoredList = new ArrayList<>();

        for (T item : items) {
            String title = item.getTitle() != null ? item.getTitle() : "";
            String normalizedTitle = normalize(title);

            int score = scoreBooks(normalizedQuery, normalizedTitle);

            if (score > 0 || normalizedTitle.contains(normalizedQuery)) {
                scoredList.add(new ScoredItem<>(item, score));
            }
        }

        scoredList.sort((a, b) -> {
            String titleA = normalize(a.getItem().getTitle());
            String titleB = normalize(b.getItem().getTitle());

            boolean aStart = titleA.startsWith(normalizedQuery);
            boolean bStart = titleB.startsWith(normalizedQuery);

            if (aStart && !bStart) return -1;
            if (!aStart && bStart) return 1;
            return Integer.compare(b.getScore(), a.getScore());
        });

        return scoredList.stream().map(ScoredItem::getItem).collect(Collectors.toList());
    }
}
