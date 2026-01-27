package com.project.smartsearchsystem.entity;

import com.project.smartsearchsystem.dto.SearchableItem;
import com.project.smartsearchsystem.utils.VectorConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "books")
@Getter
@Setter
public class Book implements SearchableItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Getter
    @Column(name = "title", nullable = false)
    private String title;

    @Getter
    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "description", columnDefinition = "text", nullable = false, length = 2500)
    private String description;

    @Column(name = "isbn", nullable = false, length = 20)
    private String isbn;

    @Column(name = "publish_year", nullable = false)
    private String publicationYear;

    @Column(name = "image_url", nullable = false)
    private String image;

    // The Vector Embedding
    // Store the "meaning" of text_description as numbers
    @Column(name = "embedding", columnDefinition = "vector")
    @Convert(converter = VectorConverter.class)
    private float[] embedding;


    public Book() {
    }

    public Book(Integer id, String title, String author, String description, String isbn, String publicationYear, String image, float[] embedding) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.description = description;
        this.isbn = isbn;
        this.publicationYear = publicationYear;
        this.image = image;
        this.embedding = embedding;
    }

    @Override
    public String toString() {
        return "Book{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", description='" + description + '\'' +
                ", isbn='" + isbn + '\'' +
                ", publishedDate=" + publicationYear +
                ", image='" + image + '\'' +
                '}';
    }
}
