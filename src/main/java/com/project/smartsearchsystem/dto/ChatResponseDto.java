package com.project.smartsearchsystem.dto;

import com.project.smartsearchsystem.entity.Book;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {
    private String reply;
    private List<Book> books;
}
