// order-management/src/main/java/com/thomas/order_management/service/ReviewTrendAnalysisService.java
package com.thomas.order_management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.model.ReviewTrendReport;
import com.thomas.order_management.repository.ProductReviewRepository;
import com.thomas.order_management.repository.ReviewTrendReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewTrendAnalysisService {

    private final ProductReviewRepository productReviewRepository;
    private final ReviewTrendReportRepository trendReportRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ReviewTrendReport analyze(LocalDate windowStart, LocalDate windowEnd) {
        List<ProductReview> reviews = fetchReviews(windowStart, windowEnd);

        String joined = reviews.stream()
                .map(r -> "- " + r.getComment())
                .collect(Collectors.joining("\n"));

        String prompt = """
    Du bist ein Analyst für Kundenfeedback. Analysiere die folgenden Produktbewertungen.
    
    1) Extrahiere wiederkehrende POSITIVe Trends (kurze, präzise Stichworte).
    2) Extrahiere wiederkehrende NEGATIVe Trends (kurze, präzise Stichworte).
    3) Extrahiere wiederkehrende NEUTRALE Beobachtungen (kurze, präzise Stichworte).
    4) Erstelle eine kurze, prägnante Zusammenfassung in einem Absatz für das Management.
    
    Gib die Antwort als STRICT JSON zurück mit den Schlüsseln:
    {
      "summary": "...",
      "positive_trends": ["..."],
      "negative_trends": ["..."],
      "neutral_observations": ["..."]
    }
    
    Wichtig: Antworte ausschließlich auf DEUTSCH.
    
    Bewertungen:
    %s
    """.formatted(joined.isBlank() ? "(keine Bewertungen verfügbar)" : joined);

        String aiJson = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

        aiJson = cleanJson(aiJson);

        List<String> positives = new ArrayList<>();
        List<String> negatives = new ArrayList<>();
        List<String> neutrals = new ArrayList<>();
        String summary = "";

        try {
            JsonNode root = objectMapper.readTree(aiJson);
            summary = text(root, "summary");
            positives = toList(root.get("positive_trends"));
            negatives = toList(root.get("negative_trends"));
            neutrals = toList(root.get("neutral_observations"));
        } catch (Exception e) {
            summary = "AI-Analyse (JSON-Parsing fehlgeschlagen): " + aiJson;
        }

        ReviewTrendReport report = ReviewTrendReport.builder()
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .generatedAt(Instant.now())
                .summary(summary)
                .positiveTrends(positives)
                .negativeTrends(negatives)
                .neutralObservations(neutrals)
                .build();

        return trendReportRepository.save(report);
    }

    public List<ReviewTrendReport> listAll() {
        return trendReportRepository.findAll();
    }

    private List<ProductReview> fetchReviews(LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            return productReviewRepository.findByCreatedAtBetween(
                    start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        }
        return productReviewRepository.findAll();
    }

    private static String cleanJson(String raw) {
        return raw.replaceAll("(?s)```json", "")
                .replaceAll("(?s)```", "")
                .trim();
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return (n != null && !n.isNull()) ? n.asText() : "";
    }

    private static List<String> toList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> list.add(n.asText()));
        }
        return list;
    }
}
