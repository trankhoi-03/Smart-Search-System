package com.project.smartsearchsystem.controller;

import com.project.smartsearchsystem.dto.*;
import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.security.JwtTokenProvider;
import com.project.smartsearchsystem.service.BookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/books")
@CrossOrigin(origins = "*")
public class BookController {

    private final JwtTokenProvider jwtTokenProvider;
    private final BookService localBookService;

    public BookController(JwtTokenProvider jwtTokenProvider, BookService localBookService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.localBookService = localBookService;
    }

    @GetMapping
    public List<Book> getAllBooks() {
        return localBookService.getAllBooks();
    }

    @GetMapping("/search")
    public BookSearchResponse search(@RequestParam String query, @RequestHeader("Authorization") String token) {
        return localBookService.searchFastSources(query);
    }

    @GetMapping("/search/amazon")
    public ResponseEntity<List<AmazonBookDto>> amazonSearch(@RequestParam String query, @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(localBookService.searchAmazonOnly(query));
    }

    @PostMapping("chat/recommend")
    public ResponseEntity<ChatResponseDto> aiRecommend(
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String token) {
        Integer userId = jwtTokenProvider.getIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String message = request.get("message");
        ChatResponseDto response = localBookService.getAIRecommendation(message, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/most-search")
    public ResponseEntity<List<String>> getMostSearchBooks(@RequestHeader("Authorization") String token) {
        Integer userId = jwtTokenProvider.getIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<String> mostSearched = localBookService.getMostSearchedBooks(userId, 5);
        return ResponseEntity.ok(mostSearched);
    }


    @PostMapping("/embeddings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> generateEmbeddings() {
        localBookService.generateEmbeddingsForAllBooks();
        return ResponseEntity.ok("Generated embeddings successfully");
    }

    @PutMapping("/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> insertBook(@RequestBody BookUpdateRequest request) {
        try {
            Book savedBook = localBookService.insertBook(request);
            return ResponseEntity.ok(savedBook.getTitle());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


}
