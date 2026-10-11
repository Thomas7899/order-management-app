package com.thomas.order_management.service;

import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ReviewEmbeddingRepository;
import com.thomas.order_management.repository.ReviewEmbeddingRepository.SimilarReview;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embeddings der Bewertungen für die semantische Suche (pgvector, Kosinus-Ähnlichkeit).
 */
@Slf4j
@Service
public class ReviewEmbeddingService {

    private static final int BATCH_SIZE = 100;

    private final ReviewEmbeddingRepository embeddingRepository;
    private final OpenAiEmbeddingModel embeddingModel;

    public ReviewEmbeddingService(ReviewEmbeddingRepository embeddingRepository, OpenAiEmbeddingModel embeddingModel) {
        this.embeddingRepository = embeddingRepository;
        this.embeddingModel = embeddingModel;
    }

    /** Erzeugt Embeddings blockweise (ein API-Aufruf je 100 Bewertungen). Gibt die Anzahl der erfolgreichen zurück. */
    public int embed(List<ProductReview> reviews) {
        int done = 0;
        for (int from = 0; from < reviews.size(); from += BATCH_SIZE) {
            List<ProductReview> batch = reviews.subList(from, Math.min(reviews.size(), from + BATCH_SIZE));
            List<String> texts = batch.stream().map(ReviewEmbeddingService::embeddingText).toList();
            try {
                List<float[]> vectors = embeddingModel.embed(texts);
                for (int i = 0; i < batch.size(); i++) {
                    embeddingRepository.upsertEmbedding(batch.get(i).getId(), texts.get(i), normalize(vectors.get(i)));
                }
                done += batch.size();
            } catch (RuntimeException ex) {
                log.warn("Embeddings für {} Bewertungen fehlgeschlagen: {}", batch.size(), ex.getMessage());
            }
        }
        return done;
    }

    /** Kandidaten der Vektorsuche, absteigend nach Ähnlichkeit. */
    public List<SimilarReview> findSimilar(String query, int candidates) {
        return embeddingRepository.findSimilar(normalize(embeddingModel.embed(query)), candidates);
    }

    /**
     * Text, der eingebettet wird: nur der Kommentar. Ein gemeinsamer Vorspann (Material, Warengruppe) macht
     * alle Vektoren einander ähnlicher und verwischt den Abstand zwischen passenden und unpassenden Treffern.
     */
    static String embeddingText(ProductReview review) {
        return review.getComment() == null ? "" : review.getComment();
    }

    private static List<Double> normalize(float[] values) {
        double sum = 0;
        for (float v : values) sum += v * v;
        double norm = sum == 0 ? 1 : Math.sqrt(sum);
        List<Double> result = new ArrayList<>(values.length);
        for (float v : values) result.add(v / norm);
        return result;
    }
}
