package com.project.smartsearchsystem.dto;

import com.project.smartsearchsystem.entity.Book;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BookSearchResponse {

    private List<Book> localResults;
    private List<GoogleBookDto> googleBookResults;
    private List<OpenLibraryBookDto> openLibraryBookResults;

    public BookSearchResponse(List<Book> localResults) {
        this.localResults = localResults;
    }

    public BookSearchResponse(List<Book> localResults,
                              List<GoogleBookDto> googleBookResults,
                              List<OpenLibraryBookDto> openLibraryBookResults) {
        this.localResults = localResults;
        this.googleBookResults = googleBookResults;
        this.openLibraryBookResults = openLibraryBookResults;
    }

}
