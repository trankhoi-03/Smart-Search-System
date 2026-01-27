package com.project.smartsearchsystem.service;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.project.smartsearchsystem.dto.GoogleBookDto;
import com.project.smartsearchsystem.utils.InfoUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class GoogleBookServiceImpl implements GoogleBookService {
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GOOGLE_BOOKS_API = "https://www.googleapis.com/books/v1/volumes?q=";
    private static final String GOOGLE_VOLUME_URL = "https://www.googleapis.com/books/v1/volumes/";


    @Override
    public List<GoogleBookDto> searchGoogleBooks(String query) {
        List<GoogleBookDto> results = new ArrayList<>();
        try {
            String modifiedQuery = "intitle:\"" + query + "\"";
            String requestUrl = GOOGLE_BOOKS_API + modifiedQuery.replace(" ", "+") + "&maxResults=10";

            ResponseEntity<String> response = restTemplate.getForEntity(requestUrl, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }

            JSONObject json = new JSONObject(response.getBody());
            JSONArray items = json.optJSONArray("items");
            if (items == null) {
                return List.of();
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                JSONObject volumeInfo = item.optJSONObject("volumeInfo");

                if (volumeInfo == null) continue;

                // Get the title of the book
                String title = volumeInfo.optString("title", "N/A");

                // Get the author(s) of the book
                JSONArray authorsArray = volumeInfo.optJSONArray("authors");
                String author = InfoUtils.extractAuthors(authorsArray);

                // Get ISBN
                JSONArray isbnArray = volumeInfo.optJSONArray("industryIdentifiers");
                String isbn = "Unknown";

                if (isbnArray != null && !isbnArray.isEmpty()) {
                    for (int isbnIndex = 0; isbnIndex < isbnArray.length(); isbnIndex++) {
                        JSONObject isbnItem = isbnArray.getJSONObject(isbnIndex);
                        if ("ISBN_10".equals(isbnItem.optString("type"))) {
                            isbn = isbnItem.optString("identifier", "N/A");
                            break;
                        }
                    }
                }


                // Get image of the book
                String imageUrl = null;
                JSONObject imagelinks = volumeInfo.optJSONObject("imageLinks");
                if (imagelinks != null) {
                    imageUrl = imagelinks.optString("thumbnail", null);
                }
                results.add(new GoogleBookDto(title, author, isbn, imageUrl));
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public String findCoverUrlById(String volumeId) {
        try {
            String json = Unirest
                    .get(GOOGLE_VOLUME_URL + volumeId)
                    .asString()
                    .getBody();

            JSONObject root = new JSONObject(json);
            JSONObject info = root.optJSONObject("volumeInfo");

            if (info != null) {
                JSONObject img = info.optJSONObject("imageLinks");
                if (img != null) {
                    String url = img.optString("extraLarge", null);
                    if (url == null) url = img.optString("large", null);
                    if (url == null) url = img.optString("medium", null);
                    if (url == null) url = img.optString("thumbnail", null);
                    if (url != null) {
                        return url.replaceFirst("^http:", "https:");
                    }
                }
            }
        } catch (Exception e) {
            return "Could not find cover url";
        }
        return null;
    }

    @Override
    public GoogleBookDto fetchBookDetails(String googleId) {
        try {
            String url = GOOGLE_VOLUME_URL + googleId;
            String jsonStr = Unirest.get(url).asString().getBody();

            JSONObject root = new JSONObject(jsonStr);
            JSONObject volumeInfo = root.optJSONObject("volumeInfo");
            if (volumeInfo == null) return null;

            // Get the title of the book
            String title = volumeInfo.optString("title", "N/A");

            // Get the author(s) of the book
            String author = InfoUtils.extractAuthors(volumeInfo.optJSONArray("authors"));

            // Get the description of the book
            String description = volumeInfo.optString("description", "No description available");

            // Get ISBN
            JSONArray isbnArray = volumeInfo.optJSONArray("industryIdentifiers");
            String isbn = "Unknown";

            if (isbnArray != null && !isbnArray.isEmpty()) {
                for (int isbnIndex = 0; isbnIndex < isbnArray.length(); isbnIndex++) {
                    JSONObject isbnItem = isbnArray.getJSONObject(isbnIndex);
                    if ("ISBN_10".equals(isbnItem.optString("type"))) {
                        isbn = isbnItem.optString("identifier", "N/A");
                        break;
                    }
                }
            }
            else {
                throw new Error("Could not find ISBN");
            }

            // Get publication year
            String publicationYear = InfoUtils.extractYear(volumeInfo.optString("publishedDate", "N/A"));

            // Get high-res image
            String imageUrl = "/placeholder.jpg";
            JSONObject image = volumeInfo.optJSONObject("imageLinks");
            if (image != null) {
                // Try to get the biggest image available
                imageUrl = image.optString("extraLarge",
                        image.optString("large",
                                image.optString("medium",
                                        image.optString("thumbnail", "/placeholder.jpg"))));
            }
            return new GoogleBookDto(googleId, title, author, description, isbn, publicationYear, imageUrl);

        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }
}
