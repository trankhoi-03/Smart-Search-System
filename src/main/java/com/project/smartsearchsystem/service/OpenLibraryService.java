package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.OpenLibraryBookDto;

import java.util.List;

public interface OpenLibraryService {
    public List<OpenLibraryBookDto> searchOpenLibraryBooks(String query);
}
