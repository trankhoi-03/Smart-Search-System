package com.project.smartsearchsystem.controller;

import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.entity.SearchHistory;
import com.project.smartsearchsystem.repository.SearchHistoryRepository;
import com.project.smartsearchsystem.security.JwtTokenProvider;
import com.project.smartsearchsystem.service.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;


    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }


    @GetMapping("/external/similar")
    public ResponseEntity<List<Book>> getSimilarExternalBooks(@RequestParam String title,
                                                              @RequestParam(required = false) String author,
                                                              @RequestParam(required = false) String isbn) {
        return ResponseEntity.ok(recommendationService.getSimilarBooksFromExternal(title, author, isbn));
    }
}
