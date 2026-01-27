package com.project.smartsearchsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenLibraryBookDto implements SearchableItem, ExternalBookSource {
    private String key;
    private String title;
    private String author;
    private String description;
    private String isbn;
    private String publicationYear;
    private String imageUrl;

    public OpenLibraryBookDto(String title, String author, String isbn, String imageUrl) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.imageUrl = imageUrl;
    }
}
