package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.*;
import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.entity.SearchHistory;
import com.project.smartsearchsystem.entity.User;
import com.project.smartsearchsystem.repository.BookRepository;
import com.project.smartsearchsystem.repository.SearchHistoryRepository;
import com.project.smartsearchsystem.repository.UserRepository;
import com.project.smartsearchsystem.utils.BookUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.project.smartsearchsystem.utils.BookUtils.*;
import static com.project.smartsearchsystem.utils.RecommendationUtils.distinctByKey;

@Service
public class BookServiceImpl implements BookService {

    private final BookUtils bookUtils;
    private final UserRepository userRepository;
    private final SearchHistoryRepository historyRepository;
    private final BookRepository bookRepository;
    private final GoogleBookServiceImpl googleBookService;
    private final OpenLibraryServiceImpl openLibraryService;
    private final AmazonServiceImpl amazonService;
    private final EmbeddingService embeddingService;
    private final QueryService queryService;

    @Autowired
    public BookServiceImpl(BookUtils bookUtils, UserRepository userRepository, SearchHistoryRepository historyRepository, BookRepository bookRepository, GoogleBookServiceImpl googleBookService, OpenLibraryServiceImpl openLibraryService, AmazonServiceImpl amazonService, EmbeddingService embeddingService, QueryService queryService) {
        this.bookUtils = bookUtils;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.bookRepository = bookRepository;
        this.googleBookService = googleBookService;
        this.openLibraryService = openLibraryService;
        this.amazonService = amazonService;
        this.embeddingService = embeddingService;
        this.queryService = queryService;
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
    public List<Book> searchLocalKeyword(String query) {
        return sortAndFilter(fullTextSearch(query), normalize(query));
    }

    @Override
    public List<Book> searchLocalSemantic(String query, float[] queryVector) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        if (queryVector == null) {
            return List.of();
        }

        queryVector = normalizeVector(queryVector);

        // 2. Build the vector string "[val1,val2,...]"
        float[] finalUserQueryVector = queryVector;
        String vectorString = "[" +
                IntStream.range(0, queryVector.length)
                        .mapToObj(i -> String.format(Locale.US, "%.8f", finalUserQueryVector[i]))
                        .collect(Collectors.joining(",")) +
                "]";

        // Debug: log the string
        System.out.println("Full query vector string: " + vectorString);
        System.out.println("Vector length: " + queryVector.length);

        // 3. Run the vector query (pass vectorString, NOT plain query!)
        List<Object[]> raw = bookRepository.findSemanticMatches(vectorString);

        // 4. Log raw results (
        System.out.println("=== RAW TOP BEFORE FILTERING ===");
        for (Object[] row : raw) {
            Integer id = Math.toIntExact(((Number) row[0]).longValue());
            Book book = bookRepository.findById(id).orElse(null);
            if (book != null) {
                Double distance = (Double) row[row.length - 1];
                System.out.println(" - " + book.getTitle() + " → distance = " + distance);
            }
        }

        // 5. Normalize query for keyword boost
        String normalizedQuery = normalize(query.toLowerCase());

        // 6. Apply semantic filter/sort
        List<Book> filtered = bookUtils.sortAndFilterSemantic(raw, normalizedQuery);

        System.out.println("=== AFTER sortAndFilterSemantic() ===");
        for (Book b : filtered) {
            System.out.println(" - Title: " + b.getTitle() + " | Author: " + b.getAuthor());
        }

        return filtered;
    }

    @Override
    public ExternalSearchResults searchGoogleAndOpenLib(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ExternalSearchResults(List.of(), List.of());
        }


        System.out.println(">>> Sending to APIs: '" + query + "'");
        // Helper function to create a safe, timed-out future
        // This defines: "Run this task, but if it takes > 5s, give me an empty list."
        CompletableFuture<List<GoogleBookDto>> google = CompletableFuture.supplyAsync(() -> {
                    return googleBookService.searchGoogleBooks(query);
                }).orTimeout(5, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    System.err.println("❌ Google Search failed: " + ex.getMessage());
                    return Collections.emptyList();
                });

        CompletableFuture<List<OpenLibraryBookDto>> openLibrary = CompletableFuture.supplyAsync(() -> {
                    return openLibraryService.searchOpenLibraryBooks(query);
                })
                // ⬆️ INCREASE TIMEOUT: Large JSON payloads take Java longer to parse than browsers
                .orTimeout(15, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    // 🛑 THE FIX: Stop swallowing the exception, so you can see why it fails
                    System.err.println("❌ Open Library failed: " + ex.getMessage());
                    ex.printStackTrace();
                    return Collections.emptyList();
                });

        CompletableFuture.allOf(google, openLibrary).join();

        try {
            return new ExternalSearchResults(google.get(), openLibrary.get());
        } catch (Exception e) {
            throw new RuntimeException("External search failed", e);
        }
    }

    @Override
    public BookSearchResponse searchFastSources(String query) {
        // 1. Capture User
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String currentUsername = auth.getName();

            CompletableFuture.runAsync(() -> {
                try {
                    User user = userRepository.findByUsername(currentUsername).orElse(null);

                    if (user != null) {
                        SearchHistory history = new SearchHistory(user.getId(), query);
                        historyRepository.save(history);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to save search history: " + e.getMessage());
                }
            });
        }

        String normalizedQuery = normalize(query);
        String apiQuery = queryService.enhanceQuery(query);


        List<Book> localKeyword = searchLocalKeyword(normalizedQuery);

        // Check for Exact Match
        List<Book> exactMatches = localKeyword.stream()
                .filter(b -> b.getTitle().equalsIgnoreCase(query))
                .toList();

        if (!exactMatches.isEmpty()) {
            System.out.println("⚡ Exact match found! Skipping external search.");
            return new BookSearchResponse(exactMatches);
        }

        // 1. Start External Search IMMEDIATELY
        // Reduce Amazon timeout inside this method to 5 or 6 seconds maximum.
        CompletableFuture<ExternalSearchResults> externalFetchFuture = CompletableFuture.supplyAsync(() -> {
            // A. Clean Query
            return searchGoogleAndOpenLib(apiQuery);
        });

        // 2. Start Query Embedding in Parallel
        CompletableFuture<float[]> queryVectorFuture = CompletableFuture.supplyAsync(() ->
                embeddingService.createEmbedding(apiQuery)
        );


        // 4. Local Semantic (Depends on Query Vector)
        CompletableFuture<List<Book>> localSemanticFuture = queryVectorFuture.thenApplyAsync(vector ->
                searchLocalSemantic(normalizedQuery, vector)
        );

        // 5. External Re-ranking (The Complex Chain)
        // We need BOTH the Search Results AND the Query Vector to proceed.
        CompletableFuture<ExternalSearchResults> reRankedExternalFuture = externalFetchFuture
                .thenCombineAsync(queryVectorFuture, (rawResults, queryVector) -> bookUtils.rankExternalResultsParallel(rawResults, queryVector, apiQuery));

        // 6. Join All
        // We only wait for the SLOWEST task here.
        ExternalSearchResults keywordResults = externalFetchFuture.join();
        ExternalSearchResults semanticResults = reRankedExternalFuture.join();
        List<Book> localSemantic = localSemanticFuture.join();

        return new BookSearchResponse(
                localKeyword,
                localSemantic,
                keywordResults.google(),
                keywordResults.openLibrary(),
                semanticResults.google(),
                semanticResults.openLibrary()
        );
    }

    @Override
    public List<AmazonBookDto> searchAmazon(String query) {
        String normalizedQuery = normalize(query);
        return amazonService.searchAmazonBooks(query);
    }

    @Override
    public List<AmazonBookDto> searchAmazonOnly(String query) {
        String apiQuery = queryService.enhanceQuery(query);
        List<AmazonBookDto> rawAmazon = searchAmazon(apiQuery);

        if (!rawAmazon.isEmpty()) {
            List<AmazonBookDto> candidates = rawAmazon.stream().limit(30).toList();
            bookUtils.populateEmbeddingsAsync(candidates).join();
            float[] queryVector = embeddingService.createEmbedding(apiQuery);
            return reRankExternal(candidates, queryVector, apiQuery).stream()
                    .filter(distinctByKey(b -> b.getTitle().toLowerCase().trim()))
                    .limit(10)
                    .toList();
        }

        return rawAmazon;
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

        boolean hasDescription = textToVectorized != null && !textToVectorized.trim().isEmpty() && !textToVectorized.contains("No description available for this book.");

        // If description is missing, use Title + Author
        if (!hasDescription) {
            System.out.println("No description found. Using title + author for embedding");
            textToVectorized = newBook.getTitle() + " by " + newBook.getAuthor();
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
    public ChatResponseDto getAIRecommendation(String userMessage, Integer userId) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new ChatResponseDto("Hi! What kind of book are you looking for today?", List.of());
        }

        try {
            List<SearchHistory> recentHistory = historyRepository.findTop5UserByIdOrderBySearchTimeDesc(userId);

            String enhancedQuery = queryService.enhanceQuery(userMessage);
            float[] queryVector = embeddingService.createEmbedding(enhancedQuery);

            List<Book> localsBook = searchLocalSemantic(enhancedQuery, queryVector);

            ExternalSearchResults external = searchGoogleAndOpenLib(enhancedQuery);

            List<Book> externalBooks = Stream.concat(
                    external.google().stream().map(BookUtils::convertToBook),
                    external.openLibrary().stream().map(BookUtils::convertToBook)
            ).limit(6).toList();


            List<Book> contextBooks = Stream.concat(localsBook.stream(), externalBooks.stream())
                    .distinct()
                    .limit(6)
                    .toList();

            String systemPrompt = """
            You are a warm, knowledgeable, and slightly witty book recommendation assistant.
            Speak like a friendly librarian who loves books.
            Use the books below as your ONLY source of recommendations.
            Never invent books that are not in the list.
            Keep your reply under 130 words.
            Be conversational and end with a question to continue the chat.
            """;

            String context = contextBooks.stream()
                    .map(b -> String.format("- \"%s\" by %s (%s)",
                            b.getTitle(),
                            b.getAuthor() != null ? b.getAuthor() : "Unknown",
                            b.getDescription() != null ? b.getDescription().substring(0, Math.min(120, b.getDescription().length())) + "..." : ""))
                    .collect(Collectors.joining("\n"));

            String fullPrompt = systemPrompt + "\n\nUser message: " + userMessage +
                    "\n\nAvailable books:\n" + context +
                    "\n\nRecent user searches: " + recentHistory.stream()
                    .map(SearchHistory::getQuery)
                    .collect(Collectors.joining(", "));

            String aiReply = queryService.callGemini(fullPrompt);

            return new ChatResponseDto(aiReply, contextBooks);
        } catch (Exception e) {
            System.err.println("AI Chat error: " + e.getMessage());
            return new ChatResponseDto("Sorry, I'm having trouble thinking right now. Try searching normally for now!", List.of());
        }
    }

    @Override
    public List<String> getMostSearchedBooks(Integer userId, Integer limit) {
        List<Object[]> results = historyRepository.findMostSearchedQueriesByUser(userId);
        return results.stream()
                .limit(limit)
                .map(result -> (String) result[0])
                .collect(Collectors.toList());
    }
}
