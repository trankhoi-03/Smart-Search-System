package com.project.smartsearchsystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoogleBookDto implements SearchableItem, ExternalBookSource {
    private String id;
    private String title;
    private String author;
    private String description;
    private String isbn;
    private String publicationYear;
    private String imageUrl;


    public GoogleBookDto(String title, String author, String isbn, String imageUrl) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.imageUrl = imageUrl;
    }
}
