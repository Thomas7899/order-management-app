package com.thomas.order_management.service.reviews;

import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.model.ReviewInsight;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewInsightRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Speichert je Bewertung eine Einordnung. Neue Bewertungen werden sofort eingeordnet (mit KI, sonst
 * regelbasiert). Der Bestand wird zuerst regelbasiert und danach im Hintergrund per KI eingeordnet,
 * damit Auswertungen nie auf das Sprachmodell warten müssen.
 */
@Slf4j
@Service
public class ReviewInsightService {

    /** Fortschritt der Hintergrund-Einordnung per KI. */
    public record JobStatus(boolean running, int processed, int total, int failedBatches,
                            LocalDateTime startedAt, LocalDateTime finishedAt, String lastError) {
    }

    private static final int MAX_FAILED_FIRST_BATCHES = 3;
    private static final int PARALLEL_BATCHES = 4;

    private final ProductReviewRepository reviewRepository;
    private final ReviewInsightRepository insightRepository;
    private final RuleBasedReviewClassifier rules;
    private final LlmReviewClassifier llm;
    private final TransactionTemplate tx;
    private final int batchSize;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger failedBatches = new AtomicInteger();
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile String lastError;

    public ReviewInsightService(ProductReviewRepository reviewRepository, ReviewInsightRepository insightRepository,
                                RuleBasedReviewClassifier rules, LlmReviewClassifier llm,
                                PlatformTransactionManager transactionManager,
                                @Value("${app.ai.classification-batch-size:20}") int batchSize) {
        this.reviewRepository = reviewRepository;
        this.insightRepository = insightRepository;
        this.rules = rules;
        this.llm = llm;
        this.tx = new TransactionTemplate(transactionManager);
        this.batchSize = Math.max(1, batchSize);
    }

    /** Neue Bewertung sofort einordnen. */
    public void classify(ProductReview review) {
        Map<Long, ReviewClassification> result = llm.isAvailable() ? llm.classify(List.of(review)) : Map.of();
        ReviewClassification classification = result.getOrDefault(review.getId(),
                rules.classify(review.getComment(), review.getRating()));
        tx.executeWithoutResult(status -> save(review.getId(), classification));
    }

    /** Ordnet alle Bewertungen ohne Einordnung regelbasiert ein (schnell, ohne KI). */
    public int classifyMissingWithRules() {
        List<Long> ids = insightRepository.findReviewIdsWithoutInsight();
        for (int from = 0; from < ids.size(); from += 200) {
            List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + 200));
            tx.executeWithoutResult(status -> reviewRepository.findAllById(chunk)
                    .forEach(r -> save(r.getId(), rules.classify(r.getComment(), r.getRating()))));
        }
        if (!ids.isEmpty()) log.info("{} Bewertungen regelbasiert eingeordnet", ids.size());
        return ids.size();
    }

    /**
     * Startet die Einordnung per KI im Hintergrund für alle Bewertungen ohne KI-Einordnung.
     *
     * @return false, wenn keine KI verfügbar ist oder bereits ein Lauf aktiv ist
     */
    public boolean startLlmClassification() {
        if (!llm.isAvailable() || !running.compareAndSet(false, true)) {
            return false;
        }
        List<Long> ids = insightRepository.findReviewIdsWithoutInsightOrSource(ReviewInsight.Source.RULES);
        processed.set(0);
        total.set(ids.size());
        failedBatches.set(0);
        lastError = null;
        startedAt = LocalDateTime.now();
        finishedAt = null;
        Thread.ofVirtual().name("review-classification").start(() -> runLlmClassification(ids));
        return true;
    }

    private void runLlmClassification(List<Long> ids) {
        AtomicInteger succeeded = new AtomicInteger();
        AtomicBoolean aborted = new AtomicBoolean(false);
        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_BATCHES, Thread.ofVirtual().factory())) {
            for (int from = 0; from < ids.size(); from += batchSize) {
                List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + batchSize));
                executor.submit(() -> {
                    if (aborted.get()) return;
                    try {
                        List<ProductReview> reviews = reviewRepository.findWithProductByIdIn(chunk);
                        Map<Long, ReviewClassification> result = llm.classify(reviews);
                        if (result.isEmpty()) {
                            failedBatches.incrementAndGet();
                            lastError = "KI hat für einen Block keine verwertbare Antwort geliefert";
                            // Scheitern die ersten Blöcke alle, liegt es am Dienst (Schlüssel, Kontingent): abbrechen
                            if (succeeded.get() == 0 && failedBatches.get() >= MAX_FAILED_FIRST_BATCHES && aborted.compareAndSet(false, true)) {
                                lastError = "Abgebrochen: die ersten " + failedBatches.get() + " Blöcke sind fehlgeschlagen";
                                log.warn("KI-Einordnung der Bewertungen abgebrochen: {}", lastError);
                            }
                        } else {
                            succeeded.incrementAndGet();
                            tx.executeWithoutResult(status -> result.forEach(this::save));
                        }
                    } catch (RuntimeException ex) {
                        failedBatches.incrementAndGet();
                        lastError = ex.getMessage();
                        log.warn("KI-Einordnung eines Blocks fehlgeschlagen", ex);
                    } finally {
                        processed.addAndGet(chunk.size());
                    }
                });
            }
        } finally {
            finishedAt = LocalDateTime.now();
            running.set(false);
            log.info("KI-Einordnung beendet: {} Bewertungen, {} Blöcke erfolgreich, {} ohne Ergebnis",
                    ids.size(), succeeded.get(), failedBatches.get());
        }
    }

    public JobStatus jobStatus() {
        return new JobStatus(running.get(), processed.get(), total.get(), failedBatches.get(), startedAt, finishedAt, lastError);
    }

    public boolean isAiAvailable() {
        return llm.isAvailable();
    }

    private void save(Long reviewId, ReviewClassification c) {
        ReviewInsight insight = insightRepository.findById(reviewId).orElseGet(() -> {
            ReviewInsight created = new ReviewInsight();
            created.setReview(reviewRepository.getReferenceById(reviewId));
            return created;
        });
        insight.setSentiment(c.sentiment());
        insight.setScore(c.score());
        insight.setEmotion(c.emotion());
        insight.setSource(c.source());
        insight.setAnalyzedAt(LocalDateTime.now());
        insight.getAspects().clear();
        insight.getAspects().addAll(c.aspects());
        insightRepository.save(insight);
    }
}
