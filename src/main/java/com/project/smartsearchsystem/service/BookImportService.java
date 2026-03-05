package com.project.smartsearchsystem.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class BookImportService {

    private final BookRepository bookRepository;

    public BookImportService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional
    public void importBooksFromCsv(String classpathFilePath) throws IOException {
        int batchSize = 1000;
        List<Book> batch = new ArrayList<>(batchSize);
        int totalImported = 0;
        int failedRows = 0;
        int currentLineNumber = 1; // Track line numbers for your error report

        System.out.println("Starting import from: " + classpathFilePath);

        RFC4180Parser rfc4180Parser = new RFC4180ParserBuilder().build();

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(classpathFilePath))))
                .withCSVParser(rfc4180Parser)
                .build()) {

            String[] line;
            boolean isHeader = true;

            // 3. Restructure the loop to catch errors line-by-line
            while (true) {
                try {
                    line = reader.readNext();
                    currentLineNumber++;

                    // If we reach the end of the file, break the loop
                    if (line == null) {
                        break;
                    }

                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }

                    // Extract data (Adding safety checks for column bounds)
                    String title = line.length > 0 && line[0] != null ? line[0].trim() : "";
                    String author = line.length > 1 && line[1] != null ? line[1].trim() : "";
                    String description = line.length > 2 && line[2] != null ? line[2].trim() : "";

                    if (title.isEmpty() && author.isEmpty()) continue;

                    Book newBook = new Book();
                    newBook.setTitle(title);
                    newBook.setAuthor(author);
                    newBook.setDescription(description);

                    batch.add(newBook);

                    if (batch.size() >= batchSize) {
                        bookRepository.saveAll(batch);
                        totalImported += batch.size();
                        batch.clear();
                    }

                } catch (CsvValidationException e) {
                    // 4. Track the exact line that caused the error and CONTINUE
                    failedRows++;
                    System.err.println("Skipping malformed data near line " + currentLineNumber + ": " + e.getMessage());
                }
            }

            // Save any remaining books
            if (!batch.isEmpty()) {
                bookRepository.saveAll(batch);
                totalImported += batch.size();
            }

            System.out.println("Import phase complete!");
            System.out.println("Total books saved to database: " + totalImported);
            System.out.println("Total malformed rows skipped: " + failedRows);

        } catch (Exception e) {
            System.err.println("A fatal error occurred during CSV setup: " + e.getMessage());
        }
    }
}
