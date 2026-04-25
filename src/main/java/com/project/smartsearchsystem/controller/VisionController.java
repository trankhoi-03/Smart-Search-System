package com.project.smartsearchsystem.controller;

import com.project.smartsearchsystem.service.VisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/vision")
@CrossOrigin(origins = "http://localhost:5173")
public class VisionController {
    @Autowired
    private VisionService visionService;

    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic" // iPhones often use HEIC
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @PostMapping("/analyze")
    public ResponseEntity<String> analyzeImage(@RequestParam("image") MultipartFile image) {

        // 1. Validation: Is the file completely empty?
        if (image.isEmpty()) {
            return ResponseEntity.badRequest().body("No image provided.");
        }

        // 2. Validation: Is it actually an image?
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest()
                    .body("Invalid file type. Please upload a valid image (JPEG, PNG, or WEBP).");
        }

        // 3. Validation: Is the file too large?
        if (image.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest()
                    .body("File is too large. Maximum size allowed is 5MB.");
        }

        try {
            // If it passes all security checks, send it to the service!
            String extractedText = visionService.extractTextFromImage(image);
            return ResponseEntity.ok(extractedText);

        } catch (Exception e) {
            System.err.println("Vision API Error: " + e.getMessage());
            return ResponseEntity.status(500).body("Error analyzing image.");
        }
    }
}
