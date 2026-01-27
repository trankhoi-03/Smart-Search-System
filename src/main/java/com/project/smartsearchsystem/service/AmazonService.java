package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.AmazonBookDto;

import java.util.List;

public interface AmazonService {
    public List<AmazonBookDto> searchAmazonBooks(String userInput);

    public String findCoverUrlById(String id);

    public AmazonBookDto fetchBookDetails(String asin);
}
