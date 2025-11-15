// order-management/src/main/java/com/thomas/order_management/service/ReviewProductTrendService.java
package com.thomas.order_management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.ProductTrendReportDTO;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewProductTrendService {

    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductTrendReportDTO analyzeProduct(Long productId, LocalDate start, LocalDate end) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return null;

        List<ProductReview> reviews = reviewRepository.findByProductIdAndCreatedAtBetween(
                productId,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );

        String text = reviews.isEmpty() ? "(keine Bewertungen verfügbar)" :
                reviews.stream().map(r -> "- " + r.getComment()).reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                Du bist ein Analyst für Kundenfeedback. Analysiere die folgenden Produktbewertungen für das Produkt "%s".
                1) Fasse positive Trends zusammen.
                2) Fasse negative Trends zusammen.
                3) Fasse neutrale Beobachtungen zusammen.
                4) Erstelle eine kurze Zusammenfassung für das Management.
                Gib ausschließlich folgendes JSON zurück:
                {
                  "summary": "",
                  "positive_trends": [],
                  "negative_trends": [],
                  "neutral_observations": []
                }
                Bewertungen:
                %s
                """.formatted(product.getName(), text);

        String response = chatClient.prompt().user(prompt).call().content();
        String clean = response.replaceAll("```json", "").replaceAll("```", "").trim();

        try {
            JsonNode root = objectMapper.readTree(clean);
            ProductTrendReportDTO dto = new ProductTrendReportDTO();
            dto.productId = product.getId();
            dto.productName = product.getName();
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
