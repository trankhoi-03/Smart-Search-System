package com.project.smartsearchsystem.dto;

import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AmazonBookDto implements SearchableItem, ExternalBookSource {
    private String asin;
    private String title;
    private String author;
    private String description;
    private String isbn;
    private String publicationYear;
    private String imageUrl;

    @Transient
    private float[] embedding;

    private transient String source;

    public AmazonBookDto(String title, String author, String isbn, String imageUrl) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.imageUrl = imageUrl;
    }
}
