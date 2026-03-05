package com.project.smartsearchsystem.utils;

import com.project.smartsearchsystem.dto.ExternalBookSource;
import com.project.smartsearchsystem.entity.Book;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@Component
public class RecommendationUtils {
    public String generateRelatedQuery(String title) {
        String cleaned = title.toLowerCase()
                .replaceAll(":", "")
                .replaceAll("edition", "")
                .replaceAll("guide", "")
                .replaceAll("introduction to", "")
                .replaceAll("principles of", "")
                .replaceAll("handbook", "")
                .trim();

        String[] words = cleaned.split(" ");
        if (words.length > 4) {
            return String.join(" ", Arrays.copyOfRange(words, 0, 4));
        }
        return cleaned;
    }

    public boolean isSameBook(Book candidate, String currentTitle, String currentIsbn) {
        if (currentIsbn != null && !currentIsbn.equals("N/A") &&
            candidate.getIsbn() != null && candidate.getIsbn().equals(currentIsbn)) {
            return true;
        }

        if (candidate.getTitle().equalsIgnoreCase(currentTitle)) {
            return true;
        }
        return false;
    }

    public void addCandidates(List<Book> destination, List<? extends ExternalBookSource> sources) {
        if (sources != null && !sources.isEmpty()) {
            sources.stream()
                    .map(BookUtils::convertToBook)
                    .forEach(destination::add);
        }
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }


}
