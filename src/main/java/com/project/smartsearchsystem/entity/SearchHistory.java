package com.project.smartsearchsystem.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class SearchHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer userId;
    private String query;
    private LocalDateTime searchTime;

    private Boolean foundLocally;

    public SearchHistory(Integer userId, String query) {
        this.userId = userId;
        this.query = query;
        this.searchTime = LocalDateTime.now();
    }

    public SearchHistory(Integer userId, String query, Boolean foundLocally) {
        this.userId = userId;
        this.query = query;
        this.foundLocally = foundLocally;
    }
}
