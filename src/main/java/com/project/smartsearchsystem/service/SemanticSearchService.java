package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SemanticSearchService {

    private final EmbeddingService embeddingService;
    private final BookRepository bookRepository;

    public SemanticSearchService(EmbeddingService embeddingService, BookRepository bookRepository) {
        this.embeddingService = embeddingService;
        this.bookRepository = bookRepository;
    }

    public List<Book> search(String userQuery) {
        // 1. Validation
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return List.of();
        }

        // 2. Convert User Query -> Vector
        float[] queryVector = embeddingService.createEmbedding(userQuery);

        // 3. Run Cosine Similarity Search
        return bookRepository.findSimilarBooks(queryVector);
    }
}
