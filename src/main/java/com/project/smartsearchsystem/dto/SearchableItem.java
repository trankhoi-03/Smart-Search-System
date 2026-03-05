package com.project.smartsearchsystem.dto;

public interface SearchableItem {
    String getTitle();
    String getAuthor();
    String getDescription();
    float[] getEmbedding();

    void setEmbedding(float[] vector);
}
