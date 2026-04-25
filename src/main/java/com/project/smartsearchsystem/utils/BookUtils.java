package com.project.smartsearchsystem.utils;

import com.project.smartsearchsystem.dto.*;
import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.service.EmbeddingService;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class BookUtils {

    private final EmbeddingService embeddingService;

    @Autowired
    public BookUtils(EmbeddingService embeddingService) {
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

    public static String sanitizeForOpenLibrary(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }

        String lowerQuery = query.toLowerCase().trim();

        String sanitized = lowerQuery
                .replace("c++", "c plus plus")
                .replace("c#", "c sharp")
                .replace(".net", "dotnet");

        boolean isAmbiguousLanguage = sanitized.contains("c sharp") ||
                                      sanitized.contains("c plus plus") ||
                                      lowerQuery.equals("c");

        if (isAmbiguousLanguage && !sanitized.contains("programming")) {
            System.out.println("Ambiguous tech term detected. Injecting context author");
            sanitized = sanitized + "programming";
        }

        return sanitized;
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
        if (userInput == null) return "";
        return userInput.toLowerCase()
                // 1. Replace hyphens, underscores, and slashes with a space
                .replaceAll("[-_:/]", " ")
                // 2. Remove all other non-alphanumeric characters (keeps only letters, numbers, and spaces)
                .replaceAll("[^a-z0-9 ]", "")
                // 3. Replace multiple accidental spaces with a single space
                .replaceAll("\\s+", " ")
                // 4. Trim leading/trailing whitespace
                .trim();
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

    public static int scoreBooks(String rewrittenQuery, String originalQuery, String title, String author) {
        if (rewrittenQuery == null || rewrittenQuery.trim().isEmpty() || title == null) return -5000;

        // 'q' is the clean AI query (used for matching the title)
        String q = normalize(rewrittenQuery);
        String t = normalize(title);

        // 'rawQ' is the original messy query (used ONLY for finding the author)
        String rawQ = normalize(originalQuery);
        String a = normalize(author != null ? author : "");

        // THE AUTHOR EXTRACTION & BONUS
        int authorBonus = 0;
        if (!a.isEmpty()) {
            String[] authorWords = a.split("\\s+");

            // The Domain Blacklist! Prevent generic publisher words from stealing the query.
            List<String> authorBlacklist = Arrays.asList("programming", "language", "languages", "academy", "press", "publications",
                                                        "development", "software", "guide", "tutorial");

            for (String aw : authorWords) {
                // Check if it's > 2 chars, in the query, AND NOT in the blacklist!
                if (aw.length() > 2 && rawQ.contains(aw) && !authorBlacklist.contains(aw)) {
                    authorBonus += 5000;
                    q = q.replace(aw, "").replaceAll("\\s+", " ").trim();
                }
            }
        }

        if (q.isEmpty()) return authorBonus > 0 ? authorBonus : -5000;

        // TIER 1: Absolute Exact Match
        if (t.equals(q)) return 15000 + authorBonus;

        // TIER 2: Title Starts With Exact Query
        if (t.startsWith(q + " ") || t.startsWith(q + ":")) return 13000 + authorBonus;

        // TIER 2.5: Core Starts With (Stop word Immunity)
        String stopWordRegex = "\\b(for|the|a|an|in|of|and|to|with|on)\\b";
        String qCore = q.replaceAll(stopWordRegex, "").replaceAll("\\s+", " ").trim();
        String tCore = t.replaceAll(stopWordRegex, "").replaceAll("\\s+", " ").trim();

        if (!qCore.isEmpty() && (tCore.equals(qCore) || tCore.startsWith(qCore + " "))) {
            return 12500 + authorBonus;
        }

        // TIER 3: Reverse Containment (The "Author Name" Fix)
        if (q.startsWith(t + " ") || q.startsWith(t + ":") || q.contains(" " + t + " ")) {

            // THE VERSION PROTECTOR: Check the "extra" stuff the user typed
            String extra = q.replace(t, "").trim();

            // If the extra stuff contains a standalone number, it's a version requirement!
            // DO NOT give it the massive author bonus. Let it fall down to Tier 4.
            if (!extra.matches(".*\\b\\d+\\b.*")) {
                return ((t.length() <= 8 && q.length() > 20) ? 9000 : 11000) + authorBonus;
            } else {
                System.out.println("Tier 3 Protector: Caught version number '" + extra + "' for title '" + t + "'. Dropping to Tier 4.");
            }
        }

        // Prepare Word Arrays for Tiers 4 and 5
        String[] qWords = q.split("\\s+");
        List<String> titleWords = Arrays.asList(t.split("\\s+"));

        // TIER 4: Scattered Keyword Match
        boolean hasAllWords = true;
        for (String word : qWords) {
            boolean isVitalNumber = word.matches(".*\\d+.*");

            if (word.length() > 2 || isVitalNumber) {
                if (isVitalNumber) {
                    if (!t.matches(".*\\b" + word + "\\b")) {
                        hasAllWords = false;
                        break;
                    }
                }
                else if (!t.contains(word)) {
                    hasAllWords = false;
                    break;
                }
            }
        }

        if (hasAllWords && qWords.length > 1) {
            int fluff = Math.abs(t.length() - q.length());
            if (fluff <= 10) return 10000 + authorBonus;
            if (fluff <= 30) return 8000 + authorBonus;
            return 5000 + authorBonus;
        }

        //  TIER 5: Prefix / Substring Match
        if (t.contains(q)) {
            int prefixPenalty = (t.indexOf(q) > 5) ? 2000 : 0;
            int extraLength = t.length() - q.length();

            if (extraLength <= 10) return 5000 - prefixPenalty + authorBonus;
            if (extraLength <= 40) return 3000 - prefixPenalty + authorBonus;
            return 1500 - prefixPenalty + authorBonus;
        }

        // ELIMINATION CHECK: Fails immediately if the Primary Subject is missing
        String primarySubject = qWords[0];
        if (!titleWords.contains(primarySubject) && !t.contains(primarySubject)) {
            // If they got the author right but the title completely wrong, just return the author bonus
            return -5000 + (authorBonus > 0 ? (authorBonus / 2) : 0);
        }

        // TIER 6: Partial Word Matching & Ratio Check
        List<String> stopWordsList = Arrays.asList("a", "an", "the", "and", "or", "of", "in", "on", "with", "to", "for", "book");
        int meaningfulQueryWords = 0;
        int meaningfulMatches = 0;
        int totalMatchCount = 0;

        for (String word : qWords) {
            boolean titleHasWord = titleWords.contains(word) || t.contains(word);

            if (titleHasWord) totalMatchCount++;

            if (!stopWordsList.contains(word)) {
                meaningfulQueryWords++;
                if (titleHasWord) meaningfulMatches++;
            }
        }

        // Heavy penalty for highly specific queries missing core nouns
        if (meaningfulQueryWords >= 3 && ((double) meaningfulMatches / meaningfulQueryWords) < 0.75) {
            return -2000 + authorBonus;
        }

        // Final Fallback Scores
        return ((totalMatchCount > 1) ? 2000 + (totalMatchCount * 10) : 1000) + authorBonus;
    }

    public static List<Book> reRankLocal(List<Book> finalList, float[] queryVector, String originalQuery, String apiQuery) {
        String lowerQuery = apiQuery.toLowerCase().trim();
        double alpha = 0.70; // 70% Keyword, 30% Semantic

        List<ScoredExternal<Book>> scoredBooks = finalList.stream()
                .map(book -> {
                    String lowerTitle = book.getTitle().toLowerCase();

                    // 1. Lexical Traps (Return -1.0 to guarantee elimination)
                    if (lowerQuery.contains("c++") && !lowerTitle.contains("c++") && !lowerTitle.contains("cpp")) {
                        return new ScoredExternal<>(book, -1.0);
                    }
                    if (lowerQuery.contains("c#") && !lowerTitle.contains("c#") && !lowerTitle.contains("c sharp")) {
                        return new ScoredExternal<>(book, -1.0);
                    }

                    // The Strict "C" Trap
                    if (lowerQuery.equals("c programming") || lowerQuery.equals("c")) {
                        boolean hasIsolatedC = lowerTitle.matches(".*\\bc\\b.*");
                        if (!hasIsolatedC || lowerTitle.contains("c++") || lowerTitle.contains("c#") || lowerTitle.contains("objective-c")) {
                            System.out.println("Lexical Filter: Dropping '" + book.getTitle() + "' (Not purely C)");
                            return new ScoredExternal<>(book, -1.0);
                        }
                    }

                    // The "Go" Trap
                    if (lowerQuery.contains("go programming") || lowerQuery.equals("go")) {
                        boolean hasGo = lowerTitle.matches(".*\\bgo\\b.*") || lowerTitle.contains("golang");
                        if (!hasGo) {
                            System.out.println("Lexical Filter: Dropping '" + book.getTitle() + "' (Missing Go)");
                            return new ScoredExternal<>(book, -1.0);
                        }
                    }

                    // The .NET Trap
                    if (lowerQuery.contains(".net") || lowerQuery.equals("net")) {
                        boolean isActualDotNet = lowerTitle.contains(".net")
                                || lowerTitle.contains("net framework")
                                || lowerTitle.contains("asp.net")
                                || lowerTitle.contains("vb.net")
                                || lowerTitle.contains("c#");

                        if (!isActualDotNet) {
                            System.out.println("Lexical Filter: Dropping '" + book.getTitle() + "' (Not a .NET book)");
                            return new ScoredExternal<>(book, -1.0);
                        }
                    }

                    // 2. Base Scores
                    float[] vec = book.getEmbedding();
                    // Prevent negative cosine similarity from breaking the math
                    double vectorScore = (vec == null) ? 0.0 : Math.max(0.0, cosineSimilarity(queryVector, vec));

                    double rawKeywordScore = scoreBooks(apiQuery, originalQuery, book.getTitle(), book.getAuthor());

                    // 3. Normalize and Weight (Cap between 0.0 and 1.0)
                    double normalizedKeyword = Math.min(Math.max(rawKeywordScore / 15000.0, 0.0), 1.0);
                    double finalScore = (alpha * normalizedKeyword) + ((1.0 - alpha) * vectorScore);

                    System.out.printf("Title: %-45s | Vector: %.4f | Keyword Norm: %.4f | Final Hybrid: %.4f%n",
                            book.getTitle(), vectorScore, normalizedKeyword, finalScore);

                    return new ScoredExternal<>(book, finalScore);
                })
                .sorted((b1, b2) -> Double.compare(b2.similarity(), b1.similarity()))
                .toList();

        // 4. Filter and Return
        return scoredBooks.stream()
                // Drop anything below 0.05 (Filters out traps and completely irrelevant AI hallucinations)
                .filter(sb -> sb.similarity() >= 0.05)
                .limit(20)
                .map(ScoredExternal::item)
                .collect(Collectors.toCollection(ArrayList::new));
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

    public static <T extends SearchableItem> List<T> reRankExternal(List<T> items, float[] queryVector, String originalQuery, String apiQuery) {

        System.out.println("\n=== STARTING HYBRID RE-RANKING ===");
        System.out.println("Target API Query: '" + apiQuery + "'");
        System.out.println("Total candidates received from API: " + items.size());
        System.out.println("------------------------------------------------");

        double alpha = 0.70; // Consistent 70/30 split

        List<ScoredExternal<T>> scoredList = items.stream()
                .filter(item -> item.getEmbedding() != null)
                .map(item -> {
                    // 1. Calculate Base Scores
                    double semanticScore = Math.max(0.0, cosineSimilarity(queryVector, item.getEmbedding()));
                    String title = item.getTitle() != null ? item.getTitle() : "UNKNOWN TITLE";
                    String author = item.getAuthor() != null ? item.getAuthor() : "N/A";

                    double rawKeywordScore = scoreBooks(apiQuery, originalQuery, title, author);

                    // 2. Normalize and Weight
                    double normalizedKeyword = Math.min(Math.max(rawKeywordScore / 15000.0, 0.0), 1.0);
                    double combinedScore = (alpha * normalizedKeyword) + ((1.0 - alpha) * semanticScore);

                    // 3. PRINT THE MATH FOR EACH BOOK
                    System.out.printf("Candidate: '%s'%n", title);
                    System.out.printf("  -> Semantic: %.4f | Keyword Norm: %.4f | Combined: %.4f%n",
                            semanticScore, normalizedKeyword, combinedScore);

                    return new ScoredExternal<>(item, combinedScore);
                })
                .sorted(Comparator.comparingDouble(s -> -s.similarity())) // Sort High to Low
                .toList();

        System.out.println("------------------------------------------------");
        System.out.println("TOP 10 FINAL RANKINGS");

        // 4. Print the final sorted winners
        List<T> finalResults = scoredList.stream()
                // THE SHIELD EXECUTOR: Drop anything that is mathematically weak
                .filter(scored -> scored.similarity() >= 0.05)
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
    public ExternalSearchResults rankExternalResultsParallel(ExternalSearchResults rawResults, float[] queryVector, String originalQuery, String apiQuery) {
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
        List<GoogleBookDto> rankedGoogle = reRankExternal(googleCandidates, normalizedVector, originalQuery, apiQuery).stream().limit(10).toList();
        List<OpenLibraryBookDto> rankedOpenLib = reRankExternal(openLibCandidates, normalizedVector, originalQuery, apiQuery).stream().limit(10).toList();

        return new ExternalSearchResults(rankedGoogle, rankedOpenLib);
    }
}
