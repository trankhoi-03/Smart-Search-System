package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.AmazonBookDto;
import com.project.smartsearchsystem.dto.BookSearchResponse;
import com.project.smartsearchsystem.dto.GoogleBookDto;
import com.project.smartsearchsystem.dto.OpenLibraryBookDto;
import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.utils.RecommendationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.project.smartsearchsystem.utils.RecommendationUtils.distinctByKey;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private final RecommendationUtils recommendationUtils;
    private final GoogleBookService googleBookService;
    private final OpenLibraryService openLibraryService;
    private final AmazonService amazonService;

    @Autowired
    public RecommendationServiceImpl(RecommendationUtils recommendationUtils, GoogleBookService googleBookService, OpenLibraryService openLibraryService, AmazonService amazonService) {
        this.recommendationUtils = recommendationUtils;
        this.googleBookService = googleBookService;
        this.openLibraryService = openLibraryService;
        this.amazonService = amazonService;
    }

    @Override
    public List<Book> getSimilarBooksFromExternal(String title, String author, String isbn) {
        if (title == null || title.isEmpty()) return List.of();

        // 1. Generate a "Related Search Query"
        // We want to search for the *Subject*, not the exact full title
        String searchQuery = recommendationUtils.generateRelatedQuery(title);
        System.out.println("Generating recommendations for query: " + searchQuery);

        CompletableFuture<List<GoogleBookDto>> googleTask = CompletableFuture.supplyAsync(() ->
            googleBookService.searchGoogleBooks(searchQuery)
        );

        CompletableFuture<List<OpenLibraryBookDto>> openLibTask = CompletableFuture.supplyAsync(() ->
            openLibraryService.searchOpenLibraryBooks(searchQuery)
        );

        CompletableFuture<List<AmazonBookDto>> amazonTask = CompletableFuture.supplyAsync(() ->
            amazonService.searchAmazonBooks(searchQuery)
        );


        // 3. Aggregate all results into one big list
        List<Book> candidates = new ArrayList<>();

        try {
            CompletableFuture.allOf(googleTask, openLibTask, amazonTask).join();

            recommendationUtils.addCandidates(candidates, googleTask.get());
            recommendationUtils.addCandidates(candidates, openLibTask.get());
            recommendationUtils.addCandidates(candidates, amazonTask.get());

            System.out.println("Found " + candidates.size() + " raw candidates.");
        } catch (Exception e) {
            System.err.println("Error fetching external recommendations: " + e.getMessage());
            return List.of();
        }

        return candidates.stream()
                .filter(b -> !recommendationUtils.isSameBook(b, title, isbn))
                .filter(distinctByKey(Book::getTitle))
                .limit(12)
                .collect(Collectors.toList());
    }
}
