package com.project.smartsearchsystem.service;


import com.project.smartsearchsystem.dto.GoogleBookDto;
import com.project.smartsearchsystem.utils.BookUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class GoogleBookServiceImpl implements GoogleBookService {
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GOOGLE_BOOKS_API = "https://www.googleapis.com/books/v1/volumes?q=";

    @Override
    public List<GoogleBookDto> searchGoogleBooks(String query) {
        List<GoogleBookDto> results = new ArrayList<>();

        try {
            String cleaned = query.replaceAll("[^a-zA-Z0-9\\s]", "");
            String modifiedQuery = URLEncoder.encode(cleaned, StandardCharsets.UTF_8);
            String requestUrl = GOOGLE_BOOKS_API + modifiedQuery.replace(" ", "+") + "&maxResults=10";

            System.out.println("🔗 [GOOGLE BOOKS] REQUEST URL: " + requestUrl);

            ResponseEntity<String> response = restTemplate.getForEntity(requestUrl, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }

            JSONObject json = new JSONObject(response.getBody());
            int totalItems = json.optInt("totalItems", -1);
            JSONArray items = json.optJSONArray("items");
            System.out.println("📊 [GOOGLE BOOKS] totalItems=" + totalItems + " | items.length=" + (items != null ? items.length() : 0));

            if (items == null || items.isEmpty()) {
                return List.of();
            }

            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    JSONObject volumeInfo = item.optJSONObject("volumeInfo");
                    if (volumeInfo == null) continue;

                    String title = volumeInfo.optString("title", "N/A");

                    JSONArray authorsArray = volumeInfo.optJSONArray("authors");
                    String author = BookUtils.extractAuthors(authorsArray);

                    // Description - ALL safe now (no more .getString())
                    String description = volumeInfo.optString("description", "");
                    if (description.isEmpty() && item.has("searchInfo")) {
                        JSONObject searchInfo = item.optJSONObject("searchInfo");
                        if (searchInfo != null) {
                            description = searchInfo.optString("textSnippet", "");
                        }
                    }
                    if (!description.isEmpty()) {
                        description = description.replaceAll("<[^>]*>", "");
                    } else {
                        description = "No description available.";
                    }

                    // ISBN
                    String isbn = "Unknown";
                    JSONArray isbnArray = volumeInfo.optJSONArray("industryIdentifiers");
                    if (isbnArray != null && !isbnArray.isEmpty()) {
                        for (int isbnIndex = 0; isbnIndex < isbnArray.length(); isbnIndex++) {
                            JSONObject isbnItem = isbnArray.getJSONObject(isbnIndex);
                            if ("ISBN_10".equals(isbnItem.optString("type"))) {
                                isbn = isbnItem.optString("identifier", "N/A");
                                break;
                            }
                        }
                    }

                    // Image
                    String imageUrl = null;
                    JSONObject imageLinks = volumeInfo.optJSONObject("imageLinks");
                    if (imageLinks != null) {
                        imageUrl = imageLinks.optString("thumbnail", null);
                    }

                    GoogleBookDto dto = new GoogleBookDto(title, author, isbn, imageUrl, description);

                    results.add(dto);

                    System.out.println("🟢 [GB] ADDED: " + title);

                } catch (Exception e) {
                    System.err.println("⚠️ [GB] Skipped bad item #" + i + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
                }
            }
            return results;

        } catch (Exception e) {
            System.err.println("❌ Google Books search failed completely: " + e.getMessage());
            e.printStackTrace();
            return results;        // ← return partial results instead of empty
        }
    }
}
