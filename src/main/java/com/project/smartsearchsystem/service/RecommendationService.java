package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.entity.Book;

import java.util.List;

public interface RecommendationService {
    List<Book> getSimilarBooksFromExternal(String title, String author, String isbn);
}
