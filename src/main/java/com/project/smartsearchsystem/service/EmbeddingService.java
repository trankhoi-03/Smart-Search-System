package com.project.smartsearchsystem.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {
    // Load the model (Sentence-BERT equivalent)
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    // Takes a text description and returns a vector (array of numbers)
    public float[] createEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // Convert text to Vector
        Embedding embedding = embeddingModel.embed(text).content();
        return embedding.vector();
    }
}
