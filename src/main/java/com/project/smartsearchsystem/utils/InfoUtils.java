package com.project.smartsearchsystem.utils;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class InfoUtils {
    // Regex to find 4 consecutive digits (likely a year)
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");

    public static String extractYear(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }

        // Case 1: Google Books often returns "YYYY-MM-DD" or just "YYYY"
        // We can simply take the first 4 characters if they are digits.
        if (dateString.length() >= 4 && dateString.substring(0, 4).matches("\\d{4}")) {
            return dateString.substring(0, 4);
        }

        // Case 2: Amazon/Other usually text like "August 4, 2023"
        // We use Regex to hunt for a year pattern (19xx or 20xx)
        Matcher matcher = YEAR_PATTERN.matcher(dateString);
        if (matcher.find()) {
            return matcher.group();
        }

        return null; // Could not find a year
    }

    public static String extractAuthors(JSONArray authorsArray) {
        String author = "Unknown";
        List<String> authorsList = new ArrayList<String>();

        for (int i = 0; i < authorsArray.length(); i++) {
            authorsList.add(authorsArray.optString(i));
        }
        author = String.join(" ", authorsList);
        return author;
    }


    public static boolean hasNoText(String text) {
        return text == null || text.trim().isEmpty() || text.equals("No description available.");
    }

    public static boolean hasNoImage(String url) {
        return url == null || url.trim().isEmpty() || url.contains("placeholder");
    }

    public static boolean isInvalid(String isbn) {
        return isbn == null || isbn.equals("N/A") || isbn.isEmpty();
    }


}
