package com.project.smartsearchsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.smartsearchsystem.dto.ImageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImageSearchService {

    @Value("${serper.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ImageResult> searchImages(String query) {
        List<ImageResult> imageResults = new ArrayList<>();

        try {
            String url = "https://google.serper.dev/images";

            // 1. Set up the Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", apiKey);

            // 2. Set up the Request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("q", query);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            // 3. Make the POST request
            String jsonResponse = restTemplate.postForObject(url, request, String.class);

            // 4. Parse the Serper.dev JSON
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode imagesNode = rootNode.path("images");

            if (imagesNode.isArray()) {
                int count = 0;
                for (JsonNode item : imagesNode) {
                    if (count >= 15) break;

                    String title = item.path("title").asText();
                    String imageUrl = item.path("imageUrl").asText();
                    String sourceUrl = item.path("link").asText();

                    String thumbnailUrl = item.has("thumbnailUrl") ? item.path("thumbnailUrl").asText() : imageUrl;

                    imageResults.add(new ImageResult(title, imageUrl, thumbnailUrl, sourceUrl));
                    count++;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching images from Serper API: " + e.getMessage());
        }
        return imageResults;
    }
}
