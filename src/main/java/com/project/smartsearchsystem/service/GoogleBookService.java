package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.GoogleBookDto;

import java.util.List;

public interface GoogleBookService {
    public List<GoogleBookDto> searchGoogleBooks(String userInput);

    public String findCoverUrlById(String volumeId);

    public GoogleBookDto fetchBookDetails(String googleId);
}
