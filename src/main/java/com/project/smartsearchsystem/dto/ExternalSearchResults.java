package com.project.smartsearchsystem.dto;

import com.project.smartsearchsystem.entity.Book;

import java.util.List;

public record ExternalSearchResults(
        List<GoogleBookDto> google,
        List<OpenLibraryBookDto> openLibrary,
        List<AmazonBookDto> amazon) { }
