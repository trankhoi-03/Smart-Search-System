package com.project.smartsearchsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ImageResult {
    private String title;
    private String imageUrl;
    private String thumbnailUrl;
    private String sourceUrl;
}
