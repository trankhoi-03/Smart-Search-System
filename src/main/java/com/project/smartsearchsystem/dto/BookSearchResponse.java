package com.project.smartsearchsystem.dto;

import com.project.smartsearchsystem.entity.Book;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BookSearchResponse {
    private final boolean single;
    private final Object singleResult;

    private List<Book> localResults;
    private List<GoogleBookDto> googleBookResults;
    private List<OpenLibraryBookDto> openLibraryBookResults;
    private List<AmazonBookDto> amazonBookResults;


    public BookSearchResponse(List<Book> localHits) {
        this.single = true;
        this.singleResult = localHits;
    }

    public BookSearchResponse(List<Book> localResults,
                              List<GoogleBookDto> googleBookResults,
                              List<OpenLibraryBookDto> openLibraryBookResults,
                              List<AmazonBookDto> amazonBookResults) {
        this.single = false;
        this.singleResult = localResults;
        this.googleBookResults = googleBookResults;
        this.openLibraryBookResults = openLibraryBookResults;
        this.amazonBookResults = amazonBookResults;
    }
}
