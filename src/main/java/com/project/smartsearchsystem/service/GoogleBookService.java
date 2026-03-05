package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.GoogleBookDto;

import java.util.List;

public interface GoogleBookService {
    public List<GoogleBookDto> searchGoogleBooks(String userInput);
}
