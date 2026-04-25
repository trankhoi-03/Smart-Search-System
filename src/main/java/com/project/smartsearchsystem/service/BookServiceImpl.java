package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.*;
import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.entity.SearchHistory;
import com.project.smartsearchsystem.repository.BookRepository;
import com.project.smartsearchsystem.repository.SearchHistoryRepository;
import com.project.smartsearchsystem.utils.BookUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
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
    private final SearchHistoryRepository historyRepository;
    private final BookRepository bookRepository;
    private final GoogleBookServiceImpl googleBookService;
    private final OpenLibraryServiceImpl openLibraryService;
    private final AmazonServiceImpl amazonService;
    private final EmbeddingService embeddingService;
    private final QueryService queryService;
    private final VisionService visionService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    public BookServiceImpl(BookUtils bookUtils, SearchHistoryRepository historyRepository, BookRepository bookRepository, GoogleBookServiceImpl googleBookService, OpenLibraryServiceImpl openLibraryService, AmazonServiceImpl amazonService, EmbeddingService embeddingService, QueryService queryService, VisionService visionService) {
        this.bookUtils = bookUtils;
        this.historyRepository = historyRepository;
        this.bookRepository = bookRepository;
        this.googleBookService = googleBookService;
        this.openLibraryService = openLibraryService;
        this.amazonService = amazonService;
        this.embeddingService = embeddingService;
        this.queryService = queryService;
        this.visionService = visionService;
    }

    @Override
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    @Override
    public List<Book> fullTextSearch(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return bookRepository.searchBooksByFullText(userInput.trim());
    }

    @Override
    public List<Book> searchLocalKeyword(String query) {
        return fullTextSearch(query);
    }

    @Override
    public List<Book> searchLocalSemantic(String query, float[] queryVector) {
        if (query == null || query.trim().isEmpty() || queryVector == null) {
            return List.of();
        }

        queryVector = normalizeVector(queryVector);

        // Build the vector string
        float[] finalUserQueryVector = queryVector;
        String vectorString = "[" +
                IntStream.range(0, queryVector.length)
                        .mapToObj(i -> String.format(Locale.US, "%.8f", finalUserQueryVector[i]))
                        .collect(Collectors.joining(",")) +
                "]";

        // 1. Run the vector query
        List<Object[]> raw = bookRepository.findSemanticMatches(vectorString);

        // 2. Extract IDs and distances (Filtering out weak AI matches early)
        List<Integer> validIds = new ArrayList<>();
        double threshold = 0.55;

        for (Object[] row : raw) {
            Double distance = (Double) row[row.length - 1];
            if (distance <= threshold) {
                Integer id = Math.toIntExact(((Number) row[0]).longValue());
                validIds.add(id);
            }
        }
        // 3. Fetch ALL books in ONE fast database call!
        if (validIds.isEmpty()) return List.of();
        return bookRepository.findAllById(validIds);
    }

    @Override
    public ExternalSearchResults searchGoogleAndOpenLib(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ExternalSearchResults(List.of(), List.of());
        }

        System.out.println(">>> Sending to APIs: '" + query + "'");
        CompletableFuture<List<GoogleBookDto>> google = CompletableFuture.supplyAsync(() -> {
                    return googleBookService.searchGoogleBooks(query);
                }).orTimeout(5, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    System.err.println("Google Search failed: " + ex.getMessage());
                    return Collections.emptyList();
                });

        CompletableFuture<List<OpenLibraryBookDto>> openLibrary = CompletableFuture.supplyAsync(() -> {
                    return openLibraryService.searchOpenLibraryBooks(query);
                })
                .orTimeout(15, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    System.err.println("Open Library failed: " + ex.getMessage());
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
    @Cacheable(value = "searchResults", key = "#query")
    public BookSearchResponse searchFastSources(String query) {
        String normalizedQuery = normalize(query);
        String apiQuery = queryService.enhanceQuery(query);

        // 1. Generate Vectors & Search
        float[] queryVector = embeddingService.createEmbedding(apiQuery);

        // 2. Semantic Search
        List<Book> localSemantic = searchLocalSemantic(apiQuery, queryVector);

        // 3. Keyword Search
        List<Book> localKeyword = searchLocalKeyword(normalizedQuery);

        Map<Integer, Book> uniqueBooksMap = new HashMap<>();
        for (Book book : localKeyword) uniqueBooksMap.put(book.getId(), book);
        for (Book book : localSemantic) uniqueBooksMap.put(book.getId(), book);

        // Re-Rank the unified list using Hybrid Scoring (Vector + Keyword Boost)
        List<Book> finalLocalList = reRankLocal(new ArrayList<>(uniqueBooksMap.values()), queryVector, query, apiQuery);

        boolean hasStrongLocalMatch = false;

        if (!finalLocalList.isEmpty()) {
            Book topBook = finalLocalList.getFirst();

            double vectorScore = topBook.getEmbedding() == null ? 0.0 : cosineSimilarity(queryVector, topBook.getEmbedding());
            double keywordScore = scoreBooks(apiQuery, query, topBook.getTitle(), topBook.getAuthor());

            double normalizedKeywordScore = Math.min(Math.max(keywordScore / 15000.0, 0.0), 1.0);

            double alpha = 0.70;
            double topScore = (alpha * normalizedKeywordScore) + ((1 - alpha) * vectorScore);

            System.out.println("Top Local Book Score: " + topScore + " (" + topBook.getTitle() + ") ");

            if (topScore >= 0.85) {
                hasStrongLocalMatch = true;
            }
        }
        if (hasStrongLocalMatch && finalLocalList.size() >= 10) {
            System.out.println("Local match perfect! Skipping external APIs");
            return new BookSearchResponse(finalLocalList);
        }

        System.out.println("Local database missing true match. Firing external APIs...");

        if (!hasStrongLocalMatch) {
            System.out.println("Clearing weak local results to prevent user confusion.");
            finalLocalList.clear();
        }

        CompletableFuture<ExternalSearchResults> externalFetchFuture = CompletableFuture.supplyAsync(() -> {
            return searchGoogleAndOpenLib(apiQuery);
        });

        CompletableFuture<ExternalSearchResults> reRankedExternalFuture = externalFetchFuture
                .thenApplyAsync(rawResults -> bookUtils.rankExternalResultsParallel(rawResults, queryVector, query, apiQuery));

        // Wait for the re-ranking to finish
        ExternalSearchResults finalExternalResults = reRankedExternalFuture.join();

        CompletableFuture.runAsync(() -> {
            List<ExternalBookSource> combinedSources = new ArrayList<>();
            if (finalExternalResults.google() != null) {
                combinedSources.addAll(finalExternalResults.google());
            }
            if (finalExternalResults.openLibrary() != null) {
                combinedSources.addAll(finalExternalResults.openLibrary());
            }
            saveExternalBookToDatabase(combinedSources);

            Cache searchCache = cacheManager.getCache("searchResults");
            if (searchCache != null) {
                searchCache.evict(query);
                System.out.println("Cache cleared for '" + query + "' because new books were saved locally");
            }
        });

        // 4. Return the Unified List
        return new BookSearchResponse(
                finalLocalList, // <--- The single, sorted, smart list
                finalExternalResults.google(),
                finalExternalResults.openLibrary()
        );
    }

    @Override
    public List<AmazonBookDto> searchAmazon(String query) {
        return amazonService.searchAmazonBooks(query);
    }

    @Override
    @Cacheable(value = "amazonResults", key = "#query")
    public List<AmazonBookDto> searchAmazonOnly(String query) {
        String apiQuery = queryService.enhanceQuery(query);
        List<AmazonBookDto> rawAmazon = searchAmazon(apiQuery);

        if (!rawAmazon.isEmpty()) {
            List<AmazonBookDto> candidates = rawAmazon.stream().limit(30).toList();
            bookUtils.populateEmbeddingsAsync(candidates).join();
            float[] queryVector = embeddingService.createEmbedding(apiQuery);
            List<AmazonBookDto> finalRankedAmazonList = reRankExternal(candidates, queryVector, query, apiQuery).stream()
                    .filter(distinctByKey(b -> b.getTitle().toLowerCase().trim()))
                    .limit(10)
                    .toList();

            CompletableFuture.runAsync(() -> {
                try {
                    saveExternalBookToDatabase(new ArrayList<>(finalRankedAmazonList));

                    Cache amazonCache = cacheManager.getCache("amazonResults");
                    if (amazonCache != null) {
                        amazonCache.evict(query);
                        System.out.println("Amazon cache cleared for '" + query + "'");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to save Amazon books to DB: " + e.getMessage());
                }
            });
            return finalRankedAmazonList;
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


        // 3. CHECK DUPLICATES (Logic Changed: Title + Author Only)
        Optional<Book> existingBook = Optional.empty();

        if (newBook.getAuthor() != null) {
            // Find by Title & Author (case-insensitive recommended)
            existingBook = bookRepository.findBookByTitleAndAuthor(
                    newBook.getTitle(),
                    newBook.getAuthor()
            );
        }

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
            // Ensure ISBN is saved as "N/A" if missing, not null
            if (newBook.getIsbn() == null) {
                newBook.setIsbn("N/A");
            }

            return bookRepository.save(newBook);
        }
    }

    @Override
    @Transactional
    public void saveExternalBookToDatabase(List<ExternalBookSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }

        System.out.println("Starting Data Hydration: Saving external results in DB");
        List<Book> booksToSave = new ArrayList<>();

        for (ExternalBookSource externalBook : sources) {
            String title = externalBook.getTitle();
            String author = externalBook.getAuthor();

            Optional<Book> existingBook = bookRepository.findBookByTitleAndAuthor(title, author);

            if (existingBook.isEmpty()) {
                Book newBook = convertToBook(externalBook);

                if (externalBook instanceof SearchableItem) {
                    newBook.setEmbedding(((SearchableItem) externalBook).getEmbedding());
                }
                booksToSave.add(newBook);
            }
        }
        if (!booksToSave.isEmpty()) {
            bookRepository.saveAll(booksToSave);
            System.out.println("Data Hydration Complete: Saved " + booksToSave.size() + " new books to database.");
        }
        else {
            System.out.println("Data Hydration Skipped: No new unique books found");
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

    @Override
    @Cacheable(value = "bookSummaries", key = "#title + '-' + #author")
    public String generateBookSummary(String title, String author, String imageUrl) {

        System.out.println("CACHE MISS: Asking Gemini to write a summary for " + title);
        String finalAuthor = author;

        boolean isAuthorMissing = (author == null || author.trim().isEmpty() || author.equals("N/A") || author.equals("Unknown Author"));

        if (isAuthorMissing && imageUrl != null && !imageUrl.isEmpty()) {
            try {
                System.out.println("Author is missing. Asking Gemini Vision to read the book cover");

                String extractedCoverText = visionService.extractTextFromImageUrl(imageUrl);

                finalAuthor = "an unknown author. However, the book cover contains this text: " + extractedCoverText;
            } catch (Exception e) {
                System.err.println("Vision extraction failed, falling back to basic prompt.");
                finalAuthor = "an unknown author";
            }
        }
        else if (isAuthorMissing) {
            finalAuthor = "an unknown author";
        }

        String prompt = String.format(
                "Act as an expert librarian. I am looking at the book '%s' by %s. " +
                        "Please provide a quick, engaging TL;DR summary of this book. " +
                        "Format your response exactly like this:\n\n" +
                        "**The TL;DR:**\n(Write a 2-sentence maximum summary here)\n\n" +
                        "**3 Key Concepts:**\n" +
                        "- (Concept 1)\n" +
                        "- (Concept 2)\n" +
                        "- (Concept 3)",
                title, finalAuthor
        );

        return queryService.generateText(prompt);
    }
}
