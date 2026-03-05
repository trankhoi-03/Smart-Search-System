package com.project.smartsearchsystem.utils;

import com.project.smartsearchsystem.dto.*;
import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.repository.BookRepository;
import com.project.smartsearchsystem.service.EmbeddingService;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class BookUtils {

    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;

    @Autowired
    public BookUtils(BookRepository bookRepository, EmbeddingService embeddingService) {
        this.bookRepository = bookRepository;
        this.embeddingService = embeddingService;
    }

    public static String extractAuthors(JSONArray authorsArray) {
        String author = "Unknown";
        List<String> authorsList = new ArrayList<String>();

        for (int i = 0; i < authorsArray.length(); i++) {
            authorsList.add(authorsArray.optString(i));
        }
        author = String.join(", ", authorsList);
        return author;
    }


    public static boolean hasNoText(String text) {
        return text == null || text.trim().isEmpty() || text.equals("No description available.");
    }

    public static boolean hasNoImage(String url) {
        return url == null || url.trim().isEmpty() || url.contains("placeholder");
    }

    public static boolean isInvalid(String isbn) {
        return isbn == null || isbn.equals("N/A") || isbn.isEmpty();
    }

    public static Book convertToBook(ExternalBookSource source) {
        Book book = new Book();
        book.setTitle(source.getTitle());
        book.setAuthor(source.getAuthor());
        book.setDescription(source.getDescription());
        book.setIsbn(source.getIsbn());
        book.setPublicationYear(source.getPublicationYear());
        book.setImage(source.getImageUrl());
        book.setSource(source.getSource());
        return book;
    }

    public static String normalize(String userInput) {
        return userInput.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "");
    }

    public static float[] normalizeVector(float[] vector) {
        if (vector == null || vector.length == 0) return vector;
        double sumSq = 0.0;
        for (float v : vector) sumSq += (double) v * v;
        float norm = (float) Math.sqrt(sumSq);
        if (norm == 0 || Math.abs(norm - 1.0) < 1e-6) return vector;  // already normalized or zero
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }

    public static int scoreBooks(String userInput, String title) {
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

    public static  <T extends SearchableItem> List<T> sortAndFilter(List<T> items, String normalizedQuery) {
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
            return Double.compare(b.getScore(), a.getScore());
        });

        return scoredList.stream().map(ScoredItem::getItem).collect(Collectors.toList());
    }

    public List<Book> sortAndFilterSemantic(List<Object[]> rawItems, String normalizedQuery) {
        List<ScoredItem<Book>> scored = new ArrayList<>();

        for (Object[] row : rawItems) {
            Integer id = Math.toIntExact(((Number) row[0]).longValue());
            Book book = bookRepository.findById(id).orElse(null);
            if (book == null) continue;

            Double distance = (Double) row[row.length - 1];  // last column = distance
            Double threshold = 0.55;
            // Hard threshold: drop weak semantic matches
            if (distance > threshold) continue;

            // Optional: keyword boost (light, no hard drop)
            String normTitle = normalize(book.getTitle());
            int keywordScore = scoreBooks(normalizedQuery, normTitle);
            double combinedScore = distance - (keywordScore * 0.05);  // adjust weight

            scored.add(new ScoredItem<>(book, combinedScore));
        }

        // Sort: lower combined = better
        scored.sort(Comparator.comparingDouble(ScoredItem::getScore));

        return scored.stream()
                .map(ScoredItem::getItem)
                .collect(Collectors.toList());
    }

    public float[] embedExternalBooks(SearchableItem item) {
        String text = (item.getTitle() != null ? item.getTitle() : "") +
                (item.getAuthor() != null ? " by " + item.getAuthor() : "") +
                (item.getDescription() != null ? " " + item.getDescription() : "");

        if (text.trim().isEmpty()) return null;

        text = normalize(text.toLowerCase());
        float[] vector = embeddingService.createEmbedding(text);
        return normalizeVector(vector);
    }

    // Run embedding generation for a list of items in PARALLEL
    public <T extends SearchableItem> CompletableFuture<List<T>> populateEmbeddingsAsync(List<T> items) {
        List<CompletableFuture<Void>> futures = items.stream()
                .map(item -> CompletableFuture.runAsync(() -> {
                    // This runs in a separate thread for each book
                    float[] vector = embedExternalBooks(item);
                    item.setEmbedding(vector);
                }))
                .toList();

        // Wait for all embeddings to finish, then return the original list
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> items);
    }

    public static double cosineSimilarity(float[] queryVector, float[] bookVector) {
        if (queryVector.length != bookVector.length || queryVector.length == 0) return 0.0;
        double dot = 0.0, normQ = 0.0, normB = 0.0;
        for (int i = 0; i < queryVector.length; i++) {
            dot += queryVector[i] * bookVector[i];
            normQ += queryVector[i] * queryVector[i];
            normB += bookVector[i] * bookVector[i];
        }
        normQ = Math.sqrt(normQ);
        normB = Math.sqrt(normB);
        return (normQ == 0 || normB == 0) ? 0.0 : dot / (normQ * normB);
    }

    private record ScoredExternal<T>(T item, double similarity) {}

    public static <T extends SearchableItem> List<T> reRankExternal(List<T> items, float[] queryVector, String apiQuery) {

        System.out.println("\n=== STARTING HYBRID RE-RANKING ===");
        System.out.println("Target API Query: '" + apiQuery + "'");
        System.out.println("Total candidates received from API: " + items.size());
        System.out.println("------------------------------------------------");

        List<ScoredExternal<T>> scoredList = items.stream()
                .filter(item -> item.getEmbedding() != null)
                .map(item -> {
                    // 1. Calculate Scores
                    double semanticScore = cosineSimilarity(queryVector, item.getEmbedding());
                    String title = item.getTitle() != null ? item.getTitle() : "UNKNOWN TITLE";
                    int keywordScore = scoreBooks(apiQuery, title);
                    double combinedScore = semanticScore + (keywordScore / 500.0);

                    // 🔍 2. PRINT THE MATH FOR EACH BOOK
                    // Using %.4f to format the double values to 4 decimal places for readability
                    System.out.printf("Candidate: '%s'%n", title);
                    System.out.printf("  -> Semantic: %.4f | Keyword: %d | Combined: %.4f%n",
                            semanticScore, keywordScore, combinedScore);

                    return new ScoredExternal<>(item, combinedScore);
                })
                .sorted(Comparator.comparingDouble(s -> -s.similarity())) // Sort High to Low
                .toList();

        System.out.println("------------------------------------------------");
        System.out.println("🏆 TOP 10 FINAL RANKINGS 🏆");

        // 3. Print the final sorted winners using .peek() before collecting
        List<T> finalResults = scoredList.stream()
                .limit(10)
                .peek(scored -> {
                    System.out.printf("WINNER: '%s' (Score: %.4f)%n",
                            scored.item().getTitle(),
                            scored.similarity());
                })
                .map(ScoredExternal::item)
                .collect(Collectors.toList());

        System.out.println("=== END HYBRID RE-RANKING ===\n");

        return finalResults;
    }

    // Helper method: Takes existing results and re-ranks them
    public ExternalSearchResults rankExternalResultsParallel(ExternalSearchResults rawResults, float[] queryVector, String apiQuery) {
        if (rawResults == null) return new ExternalSearchResults(List.of(), List.of());

        // Normalize query vector once
        float[] normalizedVector = normalizeVector(queryVector);

        // 1. Select Candidates (Top 10-15)
        List<GoogleBookDto> googleCandidates = rawResults.google().stream().limit(30).toList();
        List<OpenLibraryBookDto> openLibCandidates = rawResults.openLibrary().stream().limit(30).toList();

        // DEBUG
        System.out.println("DEBUG: Google Books returned " + googleCandidates.size() + " raw items.");
        System.out.println("DEBUG: Open Library returned " + openLibCandidates.size() + " raw items.");

        if (!googleCandidates.isEmpty()) {
            System.out.println("DEBUG: Top Raw Item: " + googleCandidates.getFirst().getTitle());
            float[] topItemVector = embedExternalBooks(googleCandidates.getFirst());
            double score = cosineSimilarity(normalizedVector, topItemVector);
            System.out.println("DEBUG: Top Item Semantic Score: " + score);
        }

        if (!openLibCandidates.isEmpty()) {
            System.out.println("DEBUG: Top Open Library Item: " + openLibCandidates.getFirst().getTitle());
            float[] topItemVector = embedExternalBooks(openLibCandidates.getFirst());
            double score = cosineSimilarity(normalizedVector, topItemVector);
            System.out.println("DEBUG: Top Item Semantic Score: " + score);
        }


        // 2. Generate Embeddings in Parallel (Fire all at once)
        CompletableFuture<List<GoogleBookDto>> googleFuture = populateEmbeddingsAsync(googleCandidates);
        CompletableFuture<List<OpenLibraryBookDto>> openLibFuture = populateEmbeddingsAsync(openLibCandidates);


        // 3. Wait for all embeddings
        CompletableFuture.allOf(googleFuture, openLibFuture).join();

        // 4. Re-rank (Fast CPU calculation)
        // Now that items have embeddings, we can sort them
        List<GoogleBookDto> rankedGoogle = reRankExternal(googleCandidates, normalizedVector, apiQuery).stream().limit(10).toList();
        List<OpenLibraryBookDto> rankedOpenLib = reRankExternal(openLibCandidates, normalizedVector, apiQuery).stream().limit(10).toList();

        return new ExternalSearchResults(rankedGoogle, rankedOpenLib);
    }
}
