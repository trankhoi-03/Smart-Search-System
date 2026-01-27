package com.project.smartsearchsystem.repository;

import com.project.smartsearchsystem.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Integer> {

    Book findBookByTitle(String title);

    Optional<Book> findBookByTitleAndAuthor(String title, String author);

    @Query(value = """
        SELECT *, 
               ts_rank(
                setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
                setweight(to_tsvector('english', coalesce(description, '')), 'B'),
                plainto_tsquery('english', :query)
               ) AS rank
        FROM books 
        WHERE 
            (setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
             setweight(to_tsvector('english', coalesce(description, '')), 'B'))
            @@ plainto_tsquery('english', :query)
        ORDER BY rank DESC
    """, nativeQuery = true)
    List<Book> searchBooksByFullText(@Param("query") String query);

    List<Book> findBooksByAuthor(String author);

    List<Book> findBooksByTitleAndAuthor(String title, String author);

    @Query(value = """
        SELECT * FROM books 
        WHERE embedding IS NOT NULL 
        ORDER BY embedding <=> cast(:queryVector as vector) ASC 
        LIMIT 10
        """, nativeQuery = true)
    List<Book> findSimilarBooks(@Param("queryVector") float[] queryVector);



}
