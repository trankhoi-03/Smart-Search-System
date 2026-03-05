package com.project.smartsearchsystem.dto;

public interface ExternalBookSource {
    String getTitle();
    String getAuthor();
    String getDescription();
    String getIsbn();
    String getPublicationYear();
    String getImageUrl();
    String getSource();
    void setSource(String sourceName);
}
