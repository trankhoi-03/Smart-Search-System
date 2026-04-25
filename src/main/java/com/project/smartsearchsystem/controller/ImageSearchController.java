package com.project.smartsearchsystem.controller;

import com.project.smartsearchsystem.dto.ImageResult;
import com.project.smartsearchsystem.service.ImageSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "http://localhost:5173") // Allow your React app to talk to it
public class ImageSearchController {

    @Autowired
    private ImageSearchService imageSearchService;

    @GetMapping("/search")
    public ResponseEntity<List<ImageResult>> searchImages(@RequestParam String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<ImageResult> results = imageSearchService.searchImages(query);
        return ResponseEntity.ok(results);
    }
}