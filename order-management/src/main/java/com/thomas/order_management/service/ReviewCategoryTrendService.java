// order-management/src/main/java/com/thomas/order_management/service/ReviewCategoryTrendService.java
package com.thomas.order_management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.CategoryTrendReportDTO;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewCategoryTrendService {

    private final ProductReviewRepository reviewRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CategoryTrendReportDTO analyzeCategory(String category, LocalDate start, LocalDate end) {
        List<ProductReview> reviews = reviewRepository.findByProductCategoryAndCreatedAtBetween(
                category,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );

        String text = reviews.isEmpty() ? "(keine Bewertungen)" :
                reviews.stream().map(r -> "- " + r.getComment()).reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                Analysiere alle Bewertungen der Kategorie "%s".
                Gib ausschließlich folgendes JSON zurück:
                {
                  "summary": "",
                  "positive_trends": [],
                  "negative_trends": [],
                  "neutral_observations": []
                }
                Bewertungen:
                %s
                """.formatted(category, text);

        String response = chatClient.prompt().user(prompt).call().content();
        String clean = response.replaceAll("```json", "").replaceAll("```", "").trim();

        try {
            JsonNode root = objectMapper.readTree(clean);
            CategoryTrendReportDTO dto = new CategoryTrendReportDTO();
            dto.category = category;
            dto.summary = root.get("summary").asText();
            dto.positiveTrends = toList(root.get("positive_trends"));
            dto.negativeTrends = toList(root.get("negative_trends"));
            dto.neutralObservations = toList(root.get("neutral_observations"));
            dto.windowStart = start;
            dto.windowEnd = end;
            dto.avgRating = reviews.stream().mapToDouble(ProductReview::getRating).average().orElse(0);
            dto.reviewCount = reviews.size();
            return dto;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> toList(JsonNode n) {
        List<String> l = new ArrayList<>();
        if (n != null && n.isArray()) n.forEach(x -> l.add(x.asText()));
        return l;
    }
}
