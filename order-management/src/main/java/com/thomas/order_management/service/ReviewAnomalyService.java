// order-management/src/main/java/com/thomas/order_management/service/ReviewAnomalyService.java
package com.thomas.order_management.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomas.order_management.dto.AnomalyReportDTO;
import com.thomas.order_management.dto.ProductAnomalyDTO;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.ProductReview;
import com.thomas.order_management.repository.ProductRepository;
import com.thomas.order_management.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewAnomalyService {

    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnomalyReportDTO detectAnomalies(LocalDate start, LocalDate end) {
        
        // 1. Alle Produkte und Reviews im Zeitraum laden
        List<Product> products = productRepository.findAll();
        List<ProductReview> reviews = reviewRepository.findByCreatedAtBetween(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        // 2. Reviews nach Produkt-ID gruppieren
        Map<Long, List<ProductReview>> reviewsByProduct = reviews.stream()
                .collect(Collectors.groupingBy(r -> r.getProduct().getId()));

        // 3. Eine Liste mit Produkt-Statistiken für die KI erstellen
        List<ProductStat> productStats = products.stream()
                .map(product -> {
                    List<ProductReview> productReviews = reviewsByProduct.getOrDefault(product.getId(), List.of());
                    double avgRating = productReviews.stream()
                            .mapToDouble(ProductReview::getRating)
                            .average().orElse(0.0);
                    
                    // Nur die negativsten Kommentare als Kontext mitschicken
                    String negativeSnippets = productReviews.stream()
                            .filter(r -> r.getRating() <= 2)
                            .map(ProductReview::getComment)
                            .limit(5) // Nur die ersten 5 als Beispiel
                            .collect(Collectors.joining("; "));

                    return new ProductStat(
                            product.getId(),
                            product.getName(),
                            avgRating,
                            productReviews.size(),
                            negativeSnippets
                    );
                })
                .filter(stat -> stat.reviewCount > 0) // Nur Produkte mit Reviews analysieren
                .toList();

        // 4. Daten für die KI als JSON-String vorbereiten
        String aiInputJson;
        try {
            aiInputJson = objectMapper.writeValueAsString(productStats);
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Serialisieren der Produkt-Statistiken", e);
        }

        // 5. Der neue, intelligentere Prompt
        String prompt = """
            Du bist ein Analyst für Produktqualität. Analysiere die folgende JSON-Liste von Produkt-Statistiken.
            Identifiziere die Top 3-5 "Sorgenkinder" (Anomalien).
            Eine Anomalie ist z.B.:
            - Eine signifikant niedrige Durchschnittsbewertung (z.B. < 2.5) bei vielen Reviews.
            - Eine hohe Anzahl an Reviews, aber eine Bewertung, die stark von der Norm (4-5) abweicht.
            - Negative Kommentare, die auf ernste Probleme hinweisen (z.B. "defekt", "kaputt", "gefährlich").

            Analysiere diese Daten:
            %s

            Gib als Antwort NUR ein JSON-Array zurück, das die `ProductAnomalyDTO`-Struktur verwendet.
            Fülle die Felder "productId", "productName", "avgRating" und "reviewCount" aus den Eingabedaten.
            Formuliere für "reason" einen kurzen Grund (z.B. "Stark negative Bewertungen und Keyword 'defekt'")
            und extrahiere 2-3 "negativeKeywords" aus den `negativeSnippets`.

            Beispiel-Antwort:
            [
              {
                "productId": 12,
                "productName": "ErgoPro Stuhl",
                "reason": "Sehr niedrige Bewertung (1.8) bei 25 Reviews.",
                "avgRating": 1.8,
                "reviewCount": 25,
                "negativeKeywords": ["gebrochen", "Anleitung fehlt"]
              }
            ]
            """.formatted(aiInputJson);

        String aiJsonOutput = chatClient.prompt().user(prompt).call().content();

        // 6. Antwort der KI parsen
        List<ProductAnomalyDTO> anomalies;
        try {
            aiJsonOutput = cleanJson(aiJsonOutput);
            anomalies = objectMapper.readValue(aiJsonOutput, new TypeReference<List<ProductAnomalyDTO>>() {});
        } catch (Exception e) {
            // Fallback, falls die KI kein valides JSON liefert
            anomalies = List.of(new ProductAnomalyDTO(0L, "AI-Parsing-Fehler", e.getMessage(), 0, 0, List.of()));
        }

        AnomalyReportDTO report = new AnomalyReportDTO();
        report.anomalies = anomalies;
        report.windowStart = start;
        report.windowEnd = end;
        return report;
    }

    // Interne Record-Klasse nur für die Statistik-Erstellung
    private record ProductStat(
            Long productId,
            String productName,
            double avgRating,
            long reviewCount,
            String negativeSnippets
    ) {}

    private static String cleanJson(String raw) {
        // Entfernt Markdown-Codeblöcke, falls die KI sie hinzufügt
        return raw.replaceAll("(?s)```json", "").replaceAll("(?s)```", "").trim();
    }
}