package com.thomas.order_management.service.sales.intake;

import com.thomas.order_management.dto.sales.OrderRequestDtos.Extraction;
import com.thomas.order_management.dto.sales.OrderRequestDtos.Line;
import com.thomas.order_management.dto.sales.OrderRequestDtos.Party;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest Bestellungen ohne KI mit festen Mustern aus: Mengenangaben mit Einheit, Materialnummern, Kunden- und
 * Bestellnummer, Liefertermin (Datum, Kalenderwoche, Wochentag), Absender und Grußzeile.
 * Formlose Texte erkennt das nur teilweise; die Oberfläche weist darauf hin.
 */
final class RuleBasedOrderExtractor {

    private static final String UNIT = "(stück|stk\\.?|st\\.?|x|×|pcs|karton|kartons|paletten?|packungen?|pkg\\.?)";
    private static final Pattern QTY_FIRST = Pattern.compile("(?i)^(\\d{1,4})\\s*" + UNIT + "(?=\\s|$)\\s*(.+)$");
    private static final Pattern QTY_LAST = Pattern.compile("(?i)^(.+?)[\\s:,–-]+(\\d{1,4})\\s*" + UNIT + "(?=\\s|$|[.,;])");
    /** Tabellenzeile: Pos, Materialnummer, Bezeichnung, Menge, Einheit (z. B. aus einem PDF) */
    private static final Pattern TABLE_ROW = Pattern.compile("(?i)^(?:\\d{1,3}\\s+)?(\\d{6})\\s+(.+?)\\s+(\\d{1,4})\\s*(st|stk\\.?|stück|karton|pcs)?\\s*(?:[\\d.,]+\\s*(?:€|eur)?\\s*)*$");
    private static final Pattern MATERIAL_NUMBER = Pattern.compile("\\b(2\\d{5})\\b");
    private static final Pattern PRICE = Pattern.compile("(?i)(?:à|a|zu|je)?\\s*\\d+[.,]\\d{2}\\s*(?:€|eur)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern FROM_HEADER = Pattern.compile("(?im)^(?:von|from|absender)\\s*:\\s*\"?([^<\"\\n]+?)\"?\\s*<?([\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+)?>?\\s*$");
    private static final Pattern CUSTOMER_NUMBER = Pattern.compile("(?i)(?:kunden|debitoren)[- ]?(?:nr\\.?|nummer)\\s*[:.]?\\s*(\\d{6})");
    private static final Pattern REFERENCE = Pattern.compile("(?i)(?:bestell(?:ung)?s?[- ]?(?:nr\\.?|nummer)|unsere\\s+(?:referenz|zeichen)|po[- ]?(?:nr\\.?|nummer|number))\\s*[:#]?\\s*(?:(?:ist|lautet)\\s+)?([A-Z0-9][A-Z0-9\\-/.]{2,34})");
    private static final Pattern DATE = Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})?\\b");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern CALENDAR_WEEK = Pattern.compile("(?i)\\bKW\\s*(\\d{1,2})(?:\\s*/\\s*(\\d{4}))?");
    private static final Pattern DELIVERY_HINT = Pattern.compile("(?i)liefer|bis\\b|spätestens|termin|benötig|brauch|bräucht");
    private static final Pattern CLOSING = Pattern.compile("(?i)^.{0,30}\\b(?:grü(?:ß|ss)en?|gruß|vg|mfg)[,!.]?$");
    private static final Pattern ZIP_CITY = Pattern.compile("\\b\\d{4,5}\\s+([A-ZÄÖÜ][\\p{L}\\-]+(?:\\s+[A-ZÄÖÜ(][\\p{L}.()\\-]+)?)");
    private static final Pattern COMPANY = Pattern.compile("\\b(GmbH|AG|KG|OHG|UG|e\\.K\\.|GbR)\\b");
    private static final Pattern NOTE = Pattern.compile("(?i)anliefer|avis|vorher\\s+anruf|telefonisch\\s+ankünd|rampe|hinterhof|lieferhinweis|teillieferung");
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.of("montag", DayOfWeek.MONDAY, "dienstag", DayOfWeek.TUESDAY,
            "mittwoch", DayOfWeek.WEDNESDAY, "donnerstag", DayOfWeek.THURSDAY, "freitag", DayOfWeek.FRIDAY, "samstag", DayOfWeek.SATURDAY);
    private static final String OWN_COMPANY = "om handels";
    private static final String OWN_ZIP = "80339";

    private RuleBasedOrderExtractor() {
    }

    static Extraction extract(String text, String sender, LocalDate today) {
        List<String> lines = text.lines().map(String::strip).toList();
        return new Extraction(party(text, sender, lines), reference(text), deliveryDate(lines, today), null, notes(lines), items(lines));
    }

    // ------------------------------------------------------------------

    private static List<Line> items(List<String> lines) {
        List<Line> items = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.replaceFirst("^[-*•·]\\s*", "").replaceFirst("^\\d{1,2}[.)]\\s+", "");
            if (line.isBlank() || CUSTOMER_NUMBER.matcher(line).find() || REFERENCE.matcher(line).find()) continue;

            Matcher table = TABLE_ROW.matcher(line);
            if (table.matches() && line.matches(".*\\b2\\d{5}\\b.*")) {
                items.add(new Line(table.group(1), clean(table.group(2)), Integer.valueOf(table.group(3)),
                        unit(table.group(4)), null));
                continue;
            }
            String withoutPrice = PRICE.matcher(line).replaceAll(" ").strip();
            Matcher first = QTY_FIRST.matcher(withoutPrice);
            Matcher last = QTY_LAST.matcher(withoutPrice);
            String description;
            int quantity;
            String unit;
            if (first.find()) {
                quantity = Integer.parseInt(first.group(1));
                unit = unit(first.group(2));
                description = first.group(3);
            } else if (last.find()) {
                description = last.group(1);
                quantity = Integer.parseInt(last.group(2));
                unit = unit(last.group(3));
            } else {
                continue;
            }
            Matcher number = MATERIAL_NUMBER.matcher(description);
            String articleNumber = number.find() ? number.group(1) : null;
            description = clean(MATERIAL_NUMBER.matcher(description).replaceAll(" "));
            if (description.length() < 3 && articleNumber == null) continue;
            items.add(new Line(articleNumber, description, quantity, unit, null));
        }
        return items;
    }

    private static String unit(String raw) {
        if (raw == null) return "Stück";
        String u = raw.toLowerCase(Locale.GERMANY).replace(".", "");
        return switch (u) {
            case "karton", "kartons" -> "Karton";
            case "palette", "paletten" -> "Palette";
            case "packung", "packungen", "pkg" -> "Packung";
            default -> "Stück";
        };
    }

    private static String clean(String description) {
        return description.replaceAll("(?i)^(?:je|à|von|vom|des|der|die|das)\\s+", "")
                .replaceAll("(?i)\\b(?:bitte|noch|zusätzlich)\\b", " ")
                .replaceAll("[\\s,:;–-]+$", "")
                .replaceAll("\\s{2,}", " ")
                .strip();
    }

    private static Party party(String text, String sender, List<String> lines) {
        String name = null;
        String email = null;
        Matcher from = FROM_HEADER.matcher(sender != null && !sender.isBlank() ? "Von: " + sender : text);
        if (from.find()) {
            name = from.group(1).strip();
            email = from.group(2);
            if (name.contains("@")) name = null;
        }
        if (email == null) {
            Matcher m = EMAIL.matcher(sender != null ? sender + " " + text : text);
            while (m.find()) {
                if (!m.group().toLowerCase(Locale.ROOT).contains("om-handel")) {
                    email = m.group();
                    break;
                }
            }
        }
        // Grußzeile: der Name steht in der Regel in der nächsten nicht leeren Zeile
        for (int i = 0; i < lines.size() - 1; i++) {
            if (CLOSING.matcher(lines.get(i)).matches()) {
                for (int j = i + 1; j < lines.size(); j++) {
                    if (!lines.get(j).isBlank()) {
                        if (lines.get(j).matches("[\\p{L}.\\- ]{3,60}")) name = lines.get(j);
                        break;
                    }
                }
            }
        }
        String company = null;
        String city = null;
        for (String line : lines) {
            if (company == null && COMPANY.matcher(line).find() && !line.toLowerCase(Locale.GERMANY).contains(OWN_COMPANY)
                    && line.length() < 80) {
                company = line.replaceFirst("(?i)^(?:firma|kunde|besteller)\\s*:\\s*", "");
            }
            Matcher zip = ZIP_CITY.matcher(line);
            if (zip.find() && !line.toLowerCase(Locale.GERMANY).contains(OWN_COMPANY) && !line.contains(OWN_ZIP)) {
                city = zip.group(1);
            }
        }
        Matcher number = CUSTOMER_NUMBER.matcher(text);
        return new Party(name, company, email, number.find() ? number.group(1) : null, city);
    }

    private static String reference(String text) {
        Matcher m = REFERENCE.matcher(text);
        while (m.find()) {
            if (m.group(1).matches(".*\\d.*")) return m.group(1).replaceAll("[.]+$", "");
        }
        return null;
    }

    private static String deliveryDate(List<String> lines, LocalDate today) {
        for (String line : lines) {
            if (!DELIVERY_HINT.matcher(line).find()) continue;
            Matcher iso = ISO_DATE.matcher(line);
            if (iso.find()) return iso.group();
            Matcher date = DATE.matcher(line);
            if (date.find()) {
                int day = Integer.parseInt(date.group(1));
                int month = Integer.parseInt(date.group(2));
                if (day < 1 || day > 31 || month < 1 || month > 12) continue;
                int year = date.group(3) == null ? today.getYear()
                        : date.group(3).length() == 2 ? 2000 + Integer.parseInt(date.group(3)) : Integer.parseInt(date.group(3));
                try {
                    LocalDate d = LocalDate.of(year, month, day);
                    if (date.group(3) == null && d.isBefore(today)) d = d.plusYears(1);
                    return d.toString();
                } catch (RuntimeException ignored) {
                    continue;
                }
            }
            Matcher week = CALENDAR_WEEK.matcher(line);
            if (week.find()) {
                int year = week.group(2) != null ? Integer.parseInt(week.group(2)) : today.getYear();
                LocalDate monday = LocalDate.of(year, 6, 1)
                        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, Integer.parseInt(week.group(1)))
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                if (week.group(2) == null && monday.isBefore(today)) monday = monday.plusYears(1)
                        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, Integer.parseInt(week.group(1)))
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return monday.toString();
            }
            String lower = line.toLowerCase(Locale.GERMANY);
            for (var day : WEEKDAYS.entrySet()) {
                if (lower.matches(".*\\b" + day.getKey() + "\\b.*")) {
                    LocalDate d = today.with(TemporalAdjusters.next(day.getValue()));
                    if (lower.contains("nächste woche") || lower.contains("kommende woche")) {
                        d = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(day.getValue()));
                    }
                    return d.toString();
                }
            }
        }
        return null;
    }

    private static String notes(List<String> lines) {
        List<String> notes = lines.stream().filter(l -> NOTE.matcher(l).find() && l.length() < 200).toList();
        return notes.isEmpty() ? null : String.join(" ", notes);
    }
}
