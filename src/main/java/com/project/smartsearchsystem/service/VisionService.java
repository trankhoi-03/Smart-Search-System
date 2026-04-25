package com.project.smartsearchsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class VisionService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractTextFromImage(MultipartFile image) throws Exception {
        byte[] imageBytes = image.getBytes();
        String mimeType = image.getContentType();

        return processImageWithGemini(imageBytes, mimeType);
    }

    public String extractTextFromImageUrl(String imageUrl) throws Exception {
        byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);

        if (imageBytes == null) {
            throw new Exception("Failed to download image from URL: " + imageUrl);
        }

        String mimeType = "image/jpeg";
        String lowerUrl = imageUrl.toLowerCase();
        if (lowerUrl.contains(".png")) mimeType = "image/png";
        else if (lowerUrl.contains(".webp")) mimeType = "image/webp";

        return processImageWithGemini(imageBytes, mimeType);
    }

    private String processImageWithGemini(byte[] imageBytes, String mimeType) throws Exception {
        // 1. Convert to Base64
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        if (mimeType == null) mimeType = "image/jpeg";

        // 2. Prepare URL & Headers
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 3. Construct the JSON body (Exactly as you wrote it!)
        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Image);

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("inline_data", inlineData);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", "You are the visual extraction engine for a smart library search system. " +
                "Look at this image. If it is a book cover, extract the title of the book and the author's name if visible. " +
                "If it is code or a concept, extract the main technical topic. " +
                "Return ONLY the raw extracted text (e.g., 'Clean Code by Robert C. Martin' or 'Java Programming'). " +
                "Do not use markdown, formatting, or conversational filler.");

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(textPart, imagePart));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        // 4. Send the request
        String jsonResponse = restTemplate.postForObject(url, request, String.class);

        // 5. Parse the response securely
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        JsonNode candidates = rootNode.path("candidates");

        if (candidates.isArray() && !candidates.isEmpty()) {
            return candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText()
                    .trim();
        }

        throw new Exception("AI could not extract text from the provided image.");
    }
}

