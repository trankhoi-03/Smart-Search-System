package com.project.smartsearchsystem.utils;

import com.project.smartsearchsystem.entity.Book;
import com.project.smartsearchsystem.repository.BookRepository;
import com.project.smartsearchsystem.service.BookImportService;
import com.project.smartsearchsystem.service.EmbeddingService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class EmbeddingMigrationRunner implements ApplicationRunner {

    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;
    private final BookImportService bookImportService;

    // YOUR PERFECT CONFIGURATION
    private static final boolean FORCE_RE_MIGRATION = true;
    private static final boolean RUN_CSV_IMPORT = false;
    private static final int BATCH_SIZE = 300;

    public EmbeddingMigrationRunner(BookRepository bookRepository,
                                    EmbeddingService embeddingService,
                                    BookImportService bookImportService) {
        this.bookRepository = bookRepository;
        this.embeddingService = embeddingService;
        this.bookImportService = bookImportService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("Embedding migration runner started...");

        if (RUN_CSV_IMPORT) {
            System.out.println("Starting CSV Data Import phase...");
            bookImportService.importBooksFromCsv("BooksDataset.csv");
        } else {
            System.out.println("Skipping CSV Import phase...");
        }

        boolean shouldRun = FORCE_RE_MIGRATION || args.containsOption("re-migrate");
        if (!shouldRun) return;

        System.out.println("Fetching exactly ONE batch of up to " + BATCH_SIZE + " books...");

        // Fetch only the first 100 books that need embeddings, sorted by ID
        Pageable pageRequest = PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"));
        List<Book> batchToMigrate = bookRepository.findByEmbeddingIsNull(pageRequest).getContent();

        if (batchToMigrate.isEmpty()) {
            System.out.println("🎉 All books have embeddings! Migration is completely finished.");
            return;
        }

        System.out.println("Generating embeddings for " + batchToMigrate.size() + " books...");
        int processedThisBatch = 0;
        int totalFailed = 0;

        for (Book book : batchToMigrate) {
            String textToVectorized = buildTextToEmbed(book);

            if (textToVectorized.isEmpty()) continue;

            try {
                float[] vector = embeddingService.createEmbedding(textToVectorized);
                vector = normalizeVector(vector);

                book.setEmbedding(vector);
                processedThisBatch++;

                if (processedThisBatch % 10 == 0) {
                    System.out.println("  -> Batch progress: " + processedThisBatch + " / " + batchToMigrate.size());
                }

                // API PROTECTION: Pause for half a second.
                Thread.sleep(500);

            } catch (Exception e) {
                totalFailed++;
                System.err.println("Failed for book ID " + book.getId() + ": " + e.getMessage());
            }
        }

        // Save the single batch
        bookRepository.saveAll(batchToMigrate);
        System.out.println("Saved 1 batch of " + processedThisBatch + " books.");
        System.out.println("Embedding process stopped. The application is now fully ready!");
    }

    private String buildTextToEmbed(Book book) {
        String title = book.getTitle() != null ? book.getTitle().trim() : "";
        String author = book.getAuthor() != null ? book.getAuthor().trim() : "";
        String desc = book.getDescription() != null ? book.getDescription().trim() : "";

        if (desc.contains("No description available") || desc.isEmpty()) {
            desc = "";
        }

        StringBuilder sb = new StringBuilder();
        if (!title.isEmpty()) {
            sb.append(title);
        }
        if (!author.isEmpty()) {
            sb.append(" by ").append(author);
        }
        if (!desc.isEmpty()) {
            sb.append(" ").append(desc);
        }

        return sb.toString().trim();
    }

    private float[] normalizeVector(float[] vec) {
        // Assuming your existing normalization logic is here or in EmbeddingService
        return vec;
    }
}
