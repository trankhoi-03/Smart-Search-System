package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.*;
import com.project.smartsearchsystem.entity.Book;

import java.util.List;

public interface BookService {
    List<Book> getAllBooks();

    List<Book> fullTextSearch(String userInput);

    List<Book> searchLocalKeyword(String query);

    List<Book> searchLocalSemantic(String query, float[] queryVector);

    ExternalSearchResults searchGoogleAndOpenLib(String query);

    BookSearchResponse searchFastSources(String query);

    List<AmazonBookDto> searchAmazon(String query);

    List<AmazonBookDto> searchAmazonOnly(String query);

    void generateEmbeddingsForAllBooks();

    Book insertBook(ExternalBookSource source);

    ChatResponseDto getAIRecommendation(String userMessage, Integer userId);

    public List<String> getMostSearchedBooks(Integer userId, Integer limit);

}