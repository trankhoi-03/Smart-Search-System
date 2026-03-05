package com.project.smartsearchsystem.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
public class QueryService {
    private final RestTemplate restTemplate = new RestTemplate();

    // Put your Gemini API key in your application.properties or application.yml
    // e.g., gemini.api.key=AIzaSyYourKeyHere...
    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    public String enhanceQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            return "";
        }

        // Very short queries → fast path (no LLM cost)
        if (query.split("\\s+").length <= 2) {
            System.out.println("⚡ Very short query. Using regex cleaner.");
            return cleanQueryForAPI(query);
        }

        String promptText = """
        You are an expert search query optimizer for a book search engine.
        
        Analyze the user query and return ONLY the best possible search string:
        
        - If the user is looking for a SPECIFIC book (mentions ordinal like 1st/2nd/3rd, part, volume, book number, sequel, prequel, installment, final book, or any series reference), return the OFFICIAL full English title only.
        - If the user is looking for a general topic, genre, or category (e.g. "books about java", "python programming", "best sci-fi novels 2025"), return a clean, concise keyword phrase optimized for APIs (remove filler words like "books about", "find me", "show me", "recommend", etc.).
        
        Rules:
        - Output ONLY the final search string. Nothing else.
        - No quotes, no markdown, no explanations, no extra words.
        - Use proper capitalization for titles.
        
        User query: """ + query;

        try {
            // Build request
            JSONObject part = new JSONObject().put("text", promptText);
            JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
            JSONObject requestBody = new JSONObject().put("contents", new JSONArray().put(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    GEMINI_URL + geminiApiKey,
                    requestEntity,
                    String.class
            );

            if (response.getBody() != null) {
                JSONObject jsonResponse = new JSONObject(response.getBody());
                JSONArray candidates = jsonResponse.optJSONArray("candidates");

                if (candidates != null && !candidates.isEmpty()) {
                    JSONObject contentObj = candidates.getJSONObject(0).optJSONObject("content");
                    if (contentObj != null) {
                        JSONArray parts = contentObj.optJSONArray("parts");
                        if (parts != null && !parts.isEmpty()) {
                            String enhanced = parts.getJSONObject(0).optString("text", query).trim();

                            // Final safety cleaning (removes any accidental markdown/quotes)
                            enhanced = enhanced
                                    .replaceAll("[\"'`*]", "")
                                    .replaceAll("\\s+", " ")
                                    .trim();

                            if (enhanced.length() < 3) {
                                System.out.println("⚠️ LLM returned empty/useless output → fallback");
                                return cleanQueryForAPI(query);
                            }

                            System.out.println("🤖 LLM Smart Rewrite: '" + rawQuery + "' → '" + enhanced + "'");
                            return enhanced;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ LLM enhancement failed: " + e.getMessage());
            // e.printStackTrace(); // uncomment only when debugging
        }

        // Ultimate fallback
        System.out.println("Falling back to regex cleaner.");
        return cleanQueryForAPI(query);
    }

    public String callGemini(String prompt) {
        try {
            JSONObject part = new JSONObject().put("text", prompt);
            JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
            JSONObject requestBody = new JSONObject().put("contents", new JSONArray().put(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    GEMINI_URL + geminiApiKey,
                    entity,
                    String.class
            );

            if (response.getBody() != null) {
                JSONObject json = new JSONObject(response.getBody());
                JSONArray candidates = json.optJSONArray("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    return candidates.getJSONObject(0)
                            .optJSONObject("content")
                            .optJSONArray("parts")
                            .getJSONObject(0)
                            .optString("text", "Sorry, I couldn't generate a reply.");
                }
            }
        } catch (Exception e) {
            System.err.println("Gemini call failed: "  + e.getMessage());
        }
        return "Sorry, I'm having a brain freeze right now. Can you try rephrasing";
    }

    public String cleanQueryForAPI(String query) {
        if (query == null) return "";

        String cleaned = query.toLowerCase()
                // 1. Remove intent/action words
                .replaceAll("\\b(book|books|about|find|looking|for|search|show|me)\\b", "")
                // 2. Remove relational/ordinal words
                .replaceAll("\\b(first|second|third|last|sequel|prequel|part|volume|edition|series)\\b", "")
                // 3. 🔴 NEW: Remove filler "stop words"
                .replaceAll("\\b(written by|author|like|similar to|of|the|a|an|in|on|with)\\b", "")
                .trim()
                .replaceAll("\\s+", " ");

        if (cleaned.length() < 2) return query;

        return cleaned;
    }
}
