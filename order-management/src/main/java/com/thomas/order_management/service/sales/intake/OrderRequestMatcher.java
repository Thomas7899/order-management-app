package com.thomas.order_management.service.sales.intake;

import com.thomas.order_management.dto.sales.OrderRequestDtos.*;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.Product;

import java.util.*;

/**
 * Ordnet ausgelesene Angaben den Stammdaten zu. Die Zuordnung ist berechnet und nachvollziehbar:
 * Nummern und E-Mail-Adressen zählen am meisten, Namen und Bezeichnungen werden unscharf verglichen.
 * Ein Vorschlag der KI aus dem Materialkatalog fließt als zusätzliches Indiz ein, ersetzt den Vergleich aber nicht.
 */
final class OrderRequestMatcher {

    static final double HIGH = 0.9;
    static final double MEDIUM = 0.6;
    static final double MIN = 0.4;
    /** Liegen zwei Kandidaten näher beieinander, gilt die Zuordnung als mehrdeutig. */
    private static final double AMBIGUITY_GAP = 0.05;
    private static final int ALTERNATIVES = 3;

    private OrderRequestMatcher() {
    }

    static Confidence confidence(double score) {
        return score >= HIGH ? Confidence.HIGH : score >= MEDIUM ? Confidence.MEDIUM : score >= MIN ? Confidence.LOW : Confidence.NONE;
    }

    // ------------------------------------------------------------------
    // Auftraggeber
    // ------------------------------------------------------------------

    static CustomerProposal matchCustomer(Party party, List<Customer> customers) {
        if (party == null) return new CustomerProposal(null, null, List.of());
        String number = digits(party.customerNumber());
        String email = lower(party.email());
        String emailLocal = email == null || !email.contains("@") ? null : email.substring(0, email.indexOf('@'));
        List<String> nameTokens = TextSimilarity.tokens(party.name());
        String city = TextSimilarity.normalize(party.city());

        List<CustomerCandidate> candidates = new ArrayList<>();
        for (Customer c : customers) {
            double score = 0;
            String reason = null;
            if (number != null && number.equals(c.getCustomerNumber())) {
                score = 1.0;
                reason = "Debitorennummer " + number;
            } else if (email != null && email.equalsIgnoreCase(c.getEmail())) {
                score = 0.98;
                reason = "E-Mail-Adresse bekannt";
            } else {
                List<String> customerTokens = TextSimilarity.tokens(c.getFirstName() + " " + c.getLastName());
                double name = nameTokens.isEmpty() ? 0 : TextSimilarity.coverage(customerTokens, nameTokens);
                boolean sameLocalPart = emailLocal != null && c.getEmail() != null
                        && c.getEmail().toLowerCase(Locale.ROOT).startsWith(emailLocal + "@");
                if (name > 0) {
                    score = 0.85 * name;
                    reason = name >= 0.99 ? "Name stimmt überein" : "Name ähnlich";
                }
                if (sameLocalPart) {
                    score = Math.max(score, 0.7) + (name > 0 ? 0.05 : 0);
                    reason = reason == null ? "E-Mail-Name passt, Domain abweichend" : reason + ", E-Mail-Name passt";
                }
                if (score > 0 && !city.isEmpty() && city.equals(TextSimilarity.normalize(c.getCity()))) {
                    score += 0.1;
                    reason += ", Ort " + c.getCity();
                }
                score = Math.min(score, 0.97);
            }
            if (score >= MIN) {
                candidates.add(new CustomerCandidate(c.getId(), c.getCustomerNumber(), c.getFirstName() + " " + c.getLastName(),
                        c.getEmail(), c.getCity(), round(score), confidence(score), reason));
            }
        }
        candidates.sort(Comparator.comparingDouble(CustomerCandidate::score).reversed());
        if (candidates.isEmpty()) return new CustomerProposal(party, null, List.of());
        // Schwache Treffer nur als Alternative anbieten, nicht vorschlagen
        if (candidates.get(0).score() < MEDIUM) {
            return new CustomerProposal(party, null, candidates.subList(0, Math.min(candidates.size(), ALTERNATIVES)));
        }

        CustomerCandidate top = candidates.get(0);
        if (candidates.size() > 1 && top.score() < 0.98 && candidates.get(1).score() >= top.score() - AMBIGUITY_GAP) {
            top = new CustomerCandidate(top.customerId(), top.customerNumber(), top.name(), top.email(), top.city(), top.score(),
                    Confidence.MEDIUM, top.reason() + " – mehrdeutig, auch " + candidates.get(1).name());
        }
        return new CustomerProposal(party, top, candidates.subList(1, Math.min(candidates.size(), ALTERNATIVES + 1)));
    }

    // ------------------------------------------------------------------
    // Positionen
    // ------------------------------------------------------------------

    static List<ItemProposal> matchItems(List<Line> lines, List<Product> products) {
        if (lines == null) return List.of();
        Map<String, Product> byNumber = new HashMap<>();
        products.forEach(p -> byNumber.put(p.getMaterialNumber(), p));

        List<ItemProposal> result = new ArrayList<>();
        int lineNumber = 0;
        for (Line line : lines) {
            lineNumber += 10;
            result.add(matchLine(lineNumber, line, products, byNumber));
        }
        return result;
    }

    private static ItemProposal matchLine(int lineNumber, Line line, List<Product> products, Map<String, Product> byNumber) {
        Map<Long, MaterialCandidate> best = new LinkedHashMap<>();
        Product byArticle = byNumber.get(digits(line.articleNumber()));
        if (byArticle != null) {
            offer(best, byArticle, 1.0, "Materialnummer " + byArticle.getMaterialNumber());
        }
        Product byAi = byNumber.get(digits(line.catalogMaterialNumber()));
        for (Product p : products) {
            double fuzzy = line.description() == null ? 0 : TextSimilarity.score(line.description(), p.getName());
            if (p == byAi) {
                boolean confirmed = fuzzy >= 0.5;
                offer(best, p, confirmed ? Math.max(fuzzy, 0.93) : Math.max(fuzzy, 0.8),
                        confirmed ? "KI-Zuordnung, Bezeichnung passt" : "KI-Zuordnung, Bezeichnung weicht ab");
            } else if (fuzzy >= MIN) {
                offer(best, p, fuzzy, fuzzy >= 0.999 ? "Bezeichnung identisch"
                        : "Bezeichnung ähnlich (" + Math.round(fuzzy * 100) + " %)");
            }
        }
        List<MaterialCandidate> candidates = new ArrayList<>(best.values());
        candidates.sort(Comparator.comparingDouble(MaterialCandidate::score).reversed());
        if (candidates.isEmpty()) return new ItemProposal(lineNumber, line, null, List.of());
        if (candidates.get(0).score() < MEDIUM) {
            return new ItemProposal(lineNumber, line, null, candidates.subList(0, Math.min(candidates.size(), ALTERNATIVES)));
        }

        MaterialCandidate top = candidates.get(0);
        if (candidates.size() > 1 && top.score() < 1.0 && candidates.get(1).score() >= top.score() - AMBIGUITY_GAP) {
            top = new MaterialCandidate(top.productId(), top.materialNumber(), top.name(), top.unitOfMeasure(), top.price(),
                    top.score(), top.confidence() == Confidence.HIGH ? Confidence.MEDIUM : top.confidence(),
                    top.reason() + " – mehrdeutig, auch " + candidates.get(1).name());
        }
        return new ItemProposal(lineNumber, line, top, candidates.subList(1, Math.min(candidates.size(), ALTERNATIVES + 1)));
    }

    private static void offer(Map<Long, MaterialCandidate> best, Product p, double score, String reason) {
        MaterialCandidate current = best.get(p.getId());
        if (current == null || current.score() < score) {
            best.put(p.getId(), new MaterialCandidate(p.getId(), p.getMaterialNumber(), p.getName(), p.getUnitOfMeasure(),
                    p.getPrice(), round(score), confidence(score), reason));
        }
    }

    private static String digits(String value) {
        if (value == null) return null;
        String d = value.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static String lower(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static double round(double score) {
        return Math.round(score * 100) / 100.0;
    }
}
