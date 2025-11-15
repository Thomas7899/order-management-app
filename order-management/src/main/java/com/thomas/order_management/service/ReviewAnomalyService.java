// order-management/src/main/java/com/thomas/order_management/service/ReviewAnomalyService.java
package com.thomas.order_management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.AnomalyReportDTO;
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
public class ReviewAnomalyService {

    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnomalyReportDTO detectAnomalies(LocalDate start, LocalDate end) {
        List<Product> products = productRepository.findAll();
        List<String> aiInput = new ArrayList<>();

        for (Product p : products) {
            List<ProductReview> reviews = reviewRepository.findByProductIdAndCreatedAtBetween(
                    p.getId(),
                    start.atStartOfDay(),
                    end.plusDays(1).atStartOfDay()
            );
            double rating = reviews.stream().mapToDouble(ProductReview::getRating).average().orElse(0);
            long count = reviews.size();
            aiInput.add(p.getName() + " | Rating: " + rating + " | Count: " + count);
        }

        String prompt = """
                Analysiere die folgenden Produktdaten.
                Finde Produkte, die auffällig negativ sind oder einen extremen Trend aufweisen.
                Gib ausschließlich folgendes JSON zurück:
                {
                  "anomalies": []
                }
                Produktdaten:
                %s
                """.formatted(String.join("\n", aiInput));

        String res = chatClient.prompt().user(prompt).call().content();
        String clean = res.replaceAll("```json", "").replaceAll("```", "").trim();

        try {
            JsonNode root = objectMapper.readTree(clean);
            AnomalyReportDTO dto = new AnomalyReportDTO();
            dto.anomalies = toList(root.get("anomalies"));
            dto.windowStart = start;
            dto.windowEnd = end;
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
