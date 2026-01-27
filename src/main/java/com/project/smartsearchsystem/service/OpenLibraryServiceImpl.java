package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.OpenLibraryBookDto;
import com.project.smartsearchsystem.utils.InfoUtils;
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
public class OpenLibraryServiceImpl implements OpenLibraryService {
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String OPEN_LIBRARY_API = "https://openlibrary.org/search.json?title=";
    private static final String OPEN_LIBRARY_BASE = "https://openlibrary.org";

    @Override
    public List<OpenLibraryBookDto> searchOpenLibraryBooks(String query) {
        List<OpenLibraryBookDto> results = new ArrayList<>();
        try {
            String modifiedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String requestUrl = OPEN_LIBRARY_API + modifiedQuery + "&limit=10&offset=10";

            ResponseEntity<String> response = restTemplate.getForEntity(requestUrl, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            JSONObject json = new JSONObject(response.getBody());
            JSONArray docs = json.optJSONArray("docs");
            if (docs == null) {
                return List.of();
            }
            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.getJSONObject(i);

                // Get the title
                String title = doc.optString("title", "N/A");

                // Get the author
                JSONArray authorsArray = doc.optJSONArray("author_name");
                String author = InfoUtils.extractAuthors(authorsArray);

                // Get the description of the book
                String description = "No description available";
                if (doc.has("first_sentence")) {
                    JSONArray sentences = doc.optJSONArray("first_sentence");
                    if (sentences != null && !sentences.isEmpty()) {
                        description = sentences.getString(0);
                    }
                }

                // Get ISBN
                String isbn = "N/A";
                JSONArray isbnList = doc.optJSONArray("isbn");
                if (isbnList != null && !isbnList.isEmpty()) {
                    isbn = isbnList.getString(0);
                }

                // Get publication year
                Integer publicationYear = doc.has("first_publish_year") ? doc.optInt("first_publish_year") : null;


                // Get image of the book
                String imageUrl = "/placeholder.jpg";
                if (doc.has("cover_i")) {
                    int coverId = doc.optInt("cover_i");
                    imageUrl = "https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg";
                }
                else if (!isbn.equals("N/A")) {
                    imageUrl = "https://covers.openlibrary.org/b/isbn/" + isbn + "-L.jpg";
                }
                results.add(new OpenLibraryBookDto(title, author, isbn, imageUrl));
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public String findCoverUrlById(String id) {
        if (id == null) return null;

        if (id.toUpperCase().startsWith("OL")) {
            return "https://covers.openlibrary.org/b/olid/" + id + "-L.jpg";
        }
       return "https://covers.openlibrary.org/b/isbn/" + id + "-L.jpg";
    }

    @Override
    public OpenLibraryBookDto fetchBookDetails(String olKey) {
        try {
            String url = OPEN_LIBRARY_BASE + olKey + ".json";

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return null;

            JSONObject json = new JSONObject(response.getBody());

            // Get the title of the book
            String title = json.optString("title", "N/A");

            // Get the author(s) of the book
            String author = InfoUtils.extractAuthors(json.optJSONArray("author_name"));

            // Get the description of the book
            String description = "No description available.";
            // OL description can be a String OR an Object { "type": "text", "value": "..." }
            Object descObj = json.opt("description");
            if (descObj instanceof String) {
                description = (String) descObj;
            } else if (descObj instanceof JSONObject) {
                description = ((JSONObject) descObj).optString("value");
            }

            // Get the isbn
            String isbn = "N/A";
            JSONArray isbnList = json.optJSONArray("isbn");
            if (isbnList != null && !isbnList.isEmpty()) {
                isbn = isbnList.getString(0);
            }

            // Get publication year
            String created = json.optJSONObject("created") != null ?
                    json.getJSONObject("created").optString("value") : "";
            String publicationYear = InfoUtils.extractYear(created);

            // Get the image
            String imageUrl = "/placeholder.jpg";
            JSONArray covers = json.optJSONArray("covers");
            if (covers != null && !covers.isEmpty()) {
                imageUrl = "https://covers.openlibrary.org/b/id/" + covers.getInt(0) + "-L.jpg";
            }

            return new OpenLibraryBookDto(olKey, title, author, description, isbn, publicationYear, imageUrl);
        } catch (Exception e) {
            return null;
        }
    }
}
