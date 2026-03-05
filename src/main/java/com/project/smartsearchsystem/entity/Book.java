package com.project.smartsearchsystem.entity;

import com.project.smartsearchsystem.dto.SearchableItem;
import com.project.smartsearchsystem.utils.VectorType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "books")
@Getter
@Setter
public class Book implements SearchableItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Getter
    @Column(name = "title", columnDefinition = "text", nullable = false)
    private String title;

    @Getter
    @Column(name = "author", columnDefinition = "text", nullable = false)
    private String author;

    @Column(name = "description", columnDefinition = "text", nullable = false)
    private String description;

    @Column(name = "isbn", length = 20)
    private String isbn;

    @Column(name = "publish_year")
    private String publicationYear;

    @Column(name = "image_url", columnDefinition = "text")
    private String image;

    // The Vector Embedding
    // Store the "meaning" of text_description as numbers
    @Type(VectorType.class)
    @Column(name = "embedding", columnDefinition = "vector(384)")
    private float[] embedding;

    private transient String source;


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
