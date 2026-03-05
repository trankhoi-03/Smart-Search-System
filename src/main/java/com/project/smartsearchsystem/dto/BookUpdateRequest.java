package com.project.smartsearchsystem.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookUpdateRequest implements ExternalBookSource {
    private String title;
    private String author;
    private String description;
    private String isbn;
    private String publicationYear;
    private String imageUrl;
    private String source;
}
