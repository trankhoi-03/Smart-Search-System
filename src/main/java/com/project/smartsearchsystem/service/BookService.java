package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.*;
import com.project.smartsearchsystem.entity.Book;

import java.util.List;

public interface BookService {
    public List<Book> getAllBooks();

    public List<Book> fullTextSearch(String userInput);

    public List<Book> searchLocal(String query);

    public ExternalSearchResults searchExternal(String query);

    public void generateEmbeddingsForAllBooks();

    public Book insertBook(ExternalBookSource source);

    public int scoreBooks(String userInput, String title);

    public void logSearch(String userInput, String userToken);

    public List<String> getMostSearchedBooks(int limit);

    public Book convertToBook(ExternalBookSource source);

    public String normalize(String userInput);

    public <T extends SearchableItem> List<T> sortAndFilter(List<T> items, String normalizedQuery);
}