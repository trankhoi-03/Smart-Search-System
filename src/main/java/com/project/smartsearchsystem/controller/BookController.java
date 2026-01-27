package com.project.smartsearchsystem.controller;

import com.project.smartsearchsystem.dto.*;
import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/books")
@CrossOrigin(origins = "*")
public class BookController {


    private final BookService localBookService;

    public BookController(BookService localBookService) {
        this.localBookService = localBookService;
    }


    @GetMapping
    public List<Book> getAllBooks() {
        return localBookService.getAllBooks();
    }

    @GetMapping("/search")
    public BookSearchResponse search(@RequestParam String query, @RequestHeader("Authorization") String token) {

        // 1. 🚀 KICK OFF EXTERNAL SEARCH IMMEDIATELY (Async)
        // We start this now so, it runs in the background while we check the local DB.
        CompletableFuture<ExternalSearchResults> externalFuture = CompletableFuture.supplyAsync(() ->
                localBookService.searchExternal(query)
        );

        // 2. 🏠 SEARCH LOCAL DATABASE
        List<Book> local = localBookService.searchLocal(query);

        // 3. 🔍 CHECK FOR EXACT MATCH
        List<Book> exactMatches = local.stream()
                .filter(b -> b.getTitle() != null && b.getTitle().equalsIgnoreCase(query))
                .toList();

        if (!exactMatches.isEmpty()) {
            return new BookSearchResponse(exactMatches);
        }

        // 4. ⏳ JOIN EXTERNAL RESULTS
        // If we reach here, we need the external data.
        // Since we started it at Step 1, it might already be done!
        ExternalSearchResults externalResults;
        try {
            externalResults = externalFuture.join(); // Wait for it to finish
        } catch (Exception e) {
            // Fallback if external search crashes completely
            externalResults = new ExternalSearchResults(List.of(), List.of(), List.of());
        }

        // 5. COMBINE AND RETURN
        List<GoogleBookDto> googleDto = externalResults.google().stream().toList();
        List<OpenLibraryBookDto> openLibraryDto = externalResults.openLibrary().stream().toList();
        List<AmazonBookDto> amazonDto = externalResults.amazon().stream().toList();

        return new BookSearchResponse(local, googleDto, openLibraryDto, amazonDto);
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
