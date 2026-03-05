package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.OpenLibraryBookDto;
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
import java.util.stream.Collectors;

@Service
@Transactional
public class OpenLibraryServiceImpl implements OpenLibraryService {
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String OPEN_LIBRARY_API = "https://openlibrary.org/search.json?title=";

    @Override
    public List<OpenLibraryBookDto> searchOpenLibraryBooks(String query) {
        List<OpenLibraryBookDto> results = new ArrayList<>();
        List<String> workKeys = new ArrayList<>();

        try {
            String modifiedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String requestUrl = OPEN_LIBRARY_API + modifiedQuery + "&limit=20&fields=*";

            System.out.println("🔗 [OPEN LIBRARY] REQUEST URL: " + requestUrl);

            ResponseEntity<String> response = restTemplate.getForEntity(requestUrl, String.class);
            System.out.println("📡 [OPEN LIBRARY] HTTP status: " + response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }

            JSONObject json = new JSONObject(response.getBody());
            int numFound = json.optInt("num_found", -1);
            JSONArray docs = json.optJSONArray("docs");
            System.out.println("📊 [OPEN LIBRARY] num_found=" + numFound + " | docs.length=" + (docs != null ? docs.length() : 0));

            if (docs == null || docs.isEmpty()) {
                return List.of();
            }

            for (int i = 0; i < docs.length(); i++) {
                try {
                    JSONObject doc = docs.getJSONObject(i);

                    String title = doc.optString("title", "N/A");

                    JSONArray authorsArray = doc.optJSONArray("author_name");
                    String author = BookUtils.extractAuthors(authorsArray);

                    // Description (fixed subject/subjects mismatch + safe access)
                    String description = "";
                    if (doc.has("first_sentence")) {
                        JSONArray sentences = doc.optJSONArray("first_sentence");
                        if (sentences != null && !sentences.isEmpty()) {
                            description = sentences.optString(0, "");
                        }
                    }
                    if (description.isEmpty() && doc.has("subjects")) {   // ← fixed: "subjects" not "subject"
                        JSONArray subjects = doc.optJSONArray("subjects");
                        if (subjects != null && !subjects.isEmpty()) {
                            List<String> topicList = new ArrayList<>();
                            for (int j = 0; j < Math.min(subjects.length(), 5); j++) {
                                topicList.add(subjects.optString(j, ""));
                            }
                            description = "Topics: " + String.join(", ", topicList);
                        }
                    }
                    if (description.isEmpty()) {
                        description = "No description available.";
                    }

                    // ISBN
                    String isbn = "N/A";
                    JSONArray isbnList = doc.optJSONArray("isbn");
                    if (isbnList != null && !isbnList.isEmpty()) {
                        isbn = isbnList.optString(0, "N/A");
                    }

                    Integer publicationYear = doc.has("first_publish_year") ? doc.optInt("first_publish_year") : null;

                    // Image
                    String imageUrl = "/placeholder.jpg";
                    if (doc.has("cover_i")) {
                        int coverId = doc.optInt("cover_i");
                        imageUrl = "https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg";
                    } else if (!isbn.equals("N/A")) {
                        imageUrl = "https://covers.openlibrary.org/b/isbn/" + isbn + "-L.jpg";
                    }

                    String key = doc.optString("key", "");
                    if (!key.isEmpty()) {
                        workKeys.add(key.replace("/works/", ""));
                    }

                    OpenLibraryBookDto dto = new OpenLibraryBookDto(title, author, isbn, imageUrl, description);
                    dto.setKey(key);                    // moved here (safer)

                    results.add(dto);

                    System.out.println("🟢 [OL] ADDED: " + title
                            + " | key=" + key
                            + " | cover_i=" + doc.optInt("cover_i"));

                } catch (Exception e) {
                    System.err.println("⚠️ [OL] Skipped bad item #" + i + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
                }
            }
            // ============================================================

            if (!workKeys.isEmpty()) {
                enrichWithDescriptions(results, workKeys);
            }

            // Your existing debug + deduplication
            System.out.println("================ OPEN LIBRARY DEBUG ================");
            System.out.println("Query: " + query);
            System.out.println("Books Found (Raw): " + results.size());
            for (int i = 0; i < results.size(); i++) {
                OpenLibraryBookDto b = results.get(i);
                System.out.printf("[%d] %s | Author: %s | Editions: %d%n",
                        i, b.getTitle(), b.getAuthor(), b.getEditionCount());
            }
            System.out.println("====================================================");

            // Deduplicate (keep the highest edition count)
            return new ArrayList<>(results.stream()
                    .collect(Collectors.toMap(
                            OpenLibraryBookDto::getTitle,
                            dto -> dto,
                            (existing, replacement) -> existing.getEditionCount() > replacement.getEditionCount() ? existing : replacement
                    ))
                    .values().stream().toList());

        } catch (Exception e) {
            System.err.println("❌ Open Library search failed completely: " + e.getMessage());
            e.printStackTrace();
            return results;        // ← return partial results instead of empty
        }
    }


    private void enrichWithDescriptions(List<OpenLibraryBookDto> books, List<String> keys) {
        // Limit to 50 keys to prevent URL overflow
        List<String> batchKeys = keys.stream().limit(50).map(k -> "OLID:" + k).collect(Collectors.toList());
        String ids = String.join(",", batchKeys);

        String url = "https://openlibrary.org/api/books?bibkeys=" + ids + "&jscmd=details&format=json";

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getBody() != null) {
                JSONObject json = new JSONObject(response.getBody());

                for (OpenLibraryBookDto book : books) {
                    // Key format in response: "OLID:OL12345W"
                    String lookupKey = "OLID:" + book.getKey().replace("/works/", "");

                    if (json.has(lookupKey)) {
                        JSONObject details = json.getJSONObject(lookupKey);
                        if (details.has("details")) {
                            JSONObject internalDetails = details.getJSONObject("details");

                            // Extract Description (It can be a String or an Object)
                            String desc = "";
                            if (internalDetails.has("description")) {
                                Object descObj = internalDetails.get("description");
                                if (descObj instanceof String) {
                                    desc = (String) descObj;
                                } else if (descObj instanceof JSONObject) {
                                    desc = ((JSONObject) descObj).optString("value");
                                }
                            }

                            // Fallback to subjects if description is still missing
                            if (desc.isEmpty() && internalDetails.has("subjects")) {
                                // ... extract subjects ...
                                desc = "Topics: ";
                            }

                            if (!desc.isEmpty()) {
                                book.setDescription(desc);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to batch fetch OpenLibrary descriptions: " + e.getMessage());
            // Do nothing, just leave descriptions empty
        }
    }
}