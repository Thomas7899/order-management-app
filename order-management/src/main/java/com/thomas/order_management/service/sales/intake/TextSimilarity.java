package com.thomas.order_management.service.sales.intake;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Unscharfer Vergleich von Bezeichnungen, wie sie in Bestellungen stehen, mit den Stammdaten:
 * Schreibweisen (Umlaute, Bindestriche, Tippfehler) und Wortteile ("Sauger" in "Akkusauger") werden toleriert.
 */
final class TextSimilarity {

    private static final Set<String> STOPWORDS = Set.of("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen",
            "und", "mit", "fur", "von", "vom", "zum", "zur", "im", "in", "aus", "auf", "je", "bitte", "noch", "neu",
            "stuck", "stk", "st", "x", "set", "teilig", "the", "of");

    private TextSimilarity() {
    }

    static String normalize(String text) {
        if (text == null) return "";
        String s = text.toLowerCase(Locale.GERMANY).replace("ß", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[^a-z0-9]+", " ").trim();
    }

    static List<String> tokens(String text) {
        String n = normalize(text);
        if (n.isEmpty()) return List.of();
        return Arrays.stream(n.split(" "))
                .filter(t -> t.length() > 1 || Character.isDigit(t.charAt(0)))
                .filter(t -> !STOPWORDS.contains(t))
                .distinct()
                .toList();
    }

    /**
     * Ähnlichkeit 0..1 einer Bezeichnung aus der Bestellung mit einer Stammdaten-Bezeichnung.
     * Gewichtet vor allem, wie vollständig die Wörter der Bestellung im Stammsatz vorkommen.
     */
    static double score(String requested, String master) {
        List<String> a = tokens(requested);
        List<String> b = tokens(master);
        if (a.isEmpty() || b.isEmpty()) return 0;
        double containment = coverage(a, b);
        double coverage = coverage(b, a);
        return 0.65 * containment + 0.35 * coverage;
    }

    /** Anteil der Wörter aus {@code from}, die in {@code in} (unscharf) vorkommen. */
    static double coverage(List<String> from, List<String> in) {
        double sum = 0;
        for (String t : from) {
            double best = 0;
            for (String u : in) {
                best = Math.max(best, tokenMatch(t, u));
                if (best == 1) break;
            }
            sum += best;
        }
        return sum / from.size();
    }

    static double tokenMatch(String a, String b) {
        if (a.equals(b)) return 1;
        int shorter = Math.min(a.length(), b.length());
        if (shorter >= 4 && (a.startsWith(b) || b.startsWith(a))) return 0.9;
        if (shorter >= 5 && (a.contains(b) || b.contains(a))) return 0.8;
        if (shorter >= 5) {
            int distance = levenshtein(a, b, 2);
            if (distance == 1) return 0.85;
            if (distance == 2 && shorter >= 9) return 0.75;
        }
        return 0;
    }

    /** Levenshtein-Distanz mit Abbruch, sobald {@code limit} überschritten ist. */
    static int levenshtein(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) return limit + 1;
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int rowMin = curr[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, curr[j]);
            }
            if (rowMin > limit) return limit + 1;
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
