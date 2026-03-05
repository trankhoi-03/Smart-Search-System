package com.project.smartsearchsystem.dto;

import com.project.smartsearchsystem.entity.Book;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BookSearchResponse {

    private boolean single;
    private Object singleResult;

    // Local results (both keyword and semantic)
    private List<Book> localKeywordResults;
    private List<Book> localSemanticResults;

    // External - Keyword search
    private List<GoogleBookDto> googleKeywordResults;
    private List<OpenLibraryBookDto> openLibraryKeywordResults;
    private List<AmazonBookDto> amazonKeywordResults;

    // External - Semantic re-ranked
    private List<GoogleBookDto> googleSemanticResults;
    private List<OpenLibraryBookDto> openLibrarySemanticResults;
    private List<AmazonBookDto> amazonSemanticResults;

    // Existing constructors (keep them untouched!)
    public BookSearchResponse(List<Book> localHits) {
        this.single = true;
        this.singleResult = localHits;
    }

    public BookSearchResponse(List<Book> localResults,
                              List<GoogleBookDto> googleBookResults,
                              List<OpenLibraryBookDto> openLibraryBookResults,
                              List<AmazonBookDto> amazonBookResults) {
        this.single = false;
        this.localKeywordResults = localResults;  // assuming this was keyword-only before
        this.googleKeywordResults = googleBookResults;
        this.openLibraryKeywordResults = openLibraryBookResults;
        this.amazonKeywordResults = amazonBookResults;
    }

    // NEW constructor - full hybrid
    public BookSearchResponse(
            List<Book> localKeywordResults,
            List<Book> localSemanticResults,
            List<GoogleBookDto> googleKeywordResults,
            List<OpenLibraryBookDto> openLibraryKeywordResults,
            List<GoogleBookDto> googleSemanticResults,
            List<OpenLibraryBookDto> openLibrarySemanticResults) {

        this.single = false;
        this.localKeywordResults = localKeywordResults;
        this.localSemanticResults = localSemanticResults;
        this.googleKeywordResults = googleKeywordResults;
        this.openLibraryKeywordResults = openLibraryKeywordResults;
        this.googleSemanticResults = googleSemanticResults;
        this.openLibrarySemanticResults = openLibrarySemanticResults;
    }
}
