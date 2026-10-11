package com.thomas.order_management.service.reviews;

import com.thomas.order_management.model.ReviewAspect;
import com.thomas.order_management.model.ReviewEmotion;
import com.thomas.order_management.model.ReviewInsight;
import com.thomas.order_management.model.ReviewSentiment;
import com.thomas.order_management.model.ReviewTheme;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regelbasierte Einordnung über Stichwörter. Wird genutzt, solange keine KI verfügbar ist,
 * und als Rückfall, wenn ein KI-Aufruf scheitert. Bewusst einfach und nachvollziehbar.
 */
@Component
public class RuleBasedReviewClassifier {

    private static final Map<ReviewTheme, Pattern> THEMES = new EnumMap<>(ReviewTheme.class);

    static {
        THEMES.put(ReviewTheme.QUALITY, words("qualität", "verarbeit", "material", "hochwertig", "wertig", "billig",
                "minderwertig", "stabil", "instabil", "wackel", "nähte?", "naht", "oberfläche", "solide", "robust",
                "massiv", "beschichtung", "kratzer", "schlampig", "knarz", "kanten"));
        THEMES.put(ReviewTheme.FUNCTION, words("funktion", "funktioniert", "(?<!preis-)leistung", "akku", "laufzeit",
                "display", "bildschirm", "klang", "sound", "lautstärke", "laut\\b", "saug", "heiz", "kühl", "leucht", "licht",
                "helligkeit", "dunkel", "verbindung", "bluetooth", "wlan", "app\\b", "software", "update", "firmware",
                "sensor", "motor", "schneid", "temperatur", "messung", "genauigkeit", "programm", "druck", "reichweite",
                "sitz", "bequem", "polster", "stauraum", "schublade", "höhenverstellung", "verklemm", "papierstau",
                "zweck", "training", "widerstand", "rutsch", "in der hand", "ausreichend", "ergebnis", "rasen"));
        THEMES.put(ReviewTheme.DURABILITY, words("kaputt", "defekt", "gebrochen", "ausgefallen", "ausfall", "verschleiß",
                "rost", "abgebrochen", "riss\\b", "rissig", "gerissen", "undicht", "flacker", "hält", "haltbar", "langlebig",
                "nach \\w+ (tagen|wochen|monaten)", "nach kurzer zeit", "seit (über )?(einem|zwei|drei|\\w+) (jahr|monaten|wochen)",
                "seit monaten", "nach dem auspacken", "überlebt", "übersteh", "aufgegangen", "löst sich", "dauereinsatz",
                "auf dauer", "wie neu"));
        THEMES.put(ReviewTheme.USABILITY, words("montage", "aufbau", "aufgebaut", "anleitung", "bedien", "einricht",
                "handhab", "intuitiv", "kompliziert", "umständlich", "schrauben", "reinig", "einstell", "zusammenbau",
                "installation", "selbsterklärend"));
        THEMES.put(ReviewTheme.DESIGN, words("design", "optik", "optisch", "aussehen", "sieht(?!( es)? aus wie neu)",
                "farbe", "schick", "elegant", "edel", "hingucker", "stylisch", "sportlich"));
        THEMES.put(ReviewTheme.PRICE, words("preis", "geld", "teuer", "günstig", "kosten", "überteuert", "preiswert",
                "seinen preis", "jeden cent", "angebot", "schnäppchen"));
        THEMES.put(ReviewTheme.DELIVERY, words("liefer", "versand", "paket", "verpack", "zusteller", "angekommen",
                "geliefert", "transport", "spedition", "karton", "zustellung", "sendung"));
        THEMES.put(ReviewTheme.SERVICE, words("support", "kundendienst", "kundenservice", "service", "hotline",
                "reklamation", "rücksend", "retoure", "umtausch", "garantie", "erstattung", "rückmeldung"));
    }

    private static final Pattern POSITIVE = words("gut", "super", "toll", "top\\b", "prima", "perfekt", "klasse",
            "hervorragend", "ausgezeichnet", "zufrieden", "begeistert", "empfehl", "schnell", "pünktlich", "einwandfrei",
            "problemlos", "hochwertig", "solide", "stabil", "robust", "schön", "schick", "elegant", "edel", "günstig",
            "preiswert", "fair", "freundlich", "hilfsbereit", "kompetent", "einfach", "intuitiv", "liebe", "gerne",
            "überzeugt", "angenehm", "leise", "präzise", "zuverlässig", "unkompliziert", "genial", "traumhaft",
            "langlebig", "lohnt", "kulant", "flott", "stark", "hell", "massiv", "locker", "beeindruck", "flüssig",
            "klar", "kräftig", "kinderleicht", "selbsterklärend", "genau\\b", "bequem", "hingucker", "modern",
            "sportlich", "stimmt", "schnäppchen", "schafft", "überlebt", "wie neu", "beschriftet", "fein\\b",
            "(minuten|stunde) erledigt", "größer als angegeben", "besser als (erwartet|gedacht|angegeben)",
            "viel stauraum", "findet seinen platz", "tadellos", "wertig", "dauereinsatz", "kraftvoll",
            // Diese Wörter sind nur verneint auffällig ("nicht erreichbar", "keine Rückmeldung")
            "hält", "reicht", "funktioniert", "passt", "erreichbar", "rückmeldung", "lösung");
    private static final Pattern NEGATIVE = words("schlecht", "mangelhaft", "kaputt", "defekt", "gebrochen", "enttäusch",
            "ärger", "frust", "schade", "leider", "billig", "minderwertig", "wackel", "instabil", "teuer", "überteuert",
            "langsam", "verspät", "beschädigt", "verkratzt", "kompliziert", "umständlich", "unverständlich", "fehlt",
            "fehlten", "problem(?!los)", "mangel", "unbrauchbar", "schrott", "katastroph", "unverschämt", "unfreundlich",
            "ausgefallen", "ausfall", "flacker", "abgebrochen", "schwach", "dünn", "riecht", "undicht", "rost", "nervig",
            "nervt", "stört", "laut\\b", "zu klein", "zu groß", "verbogen", "bricht", "dauert", "gedauert", "ignoriert",
            "zurückgeschickt", "leer\\b", "gelbstich", "farbstich", "stürzt", "unfertig", "rutscht", "klemm", "verklemm",
            "schwergängig", "laufen schwer", "läuft schwer", "kratzer", "schlampig", "riss\\b", "rissig", "gerissen",
            "aufgegangen", "löst sich", "weicht", "ungenau", "schwank", "verbrannt", "ungleichmäßig", "verliert",
            "unmöglich", "dunkler als", "blasser als", "greller als", "anders aus", "dunkel\\b",
            "als auf (den|dem) (fotos?|bildern?|produktbildern?)", "lässt .{0,25}nach", "mehr erwartet", "mehr erhofft",
            "ohne benachrichtigung", "nicht informiert", "verschoben", "durchnässt", "eingedrückt", "warteschleife",
            "gibt es nicht", "tut .{0,20}weh", "fehlkauf", "nichts besonderes", "knarz", "abgestellt", "standardantwort");
    private static final Pattern NEGATION = Pattern.compile("(nicht|kein|keine|keinen|keiner|nie|ohne|kaum|nichts)");
    /** Verben, bei denen die Verneinung im Deutschen meist folgt ("reicht nicht", "funktioniert nicht mehr"). */
    private static final Pattern NEGATED_AFTER = Pattern.compile("(hält|reicht|funktioniert|passt|klappt|lohnt|überzeugt|hilft|erreichbar)");

    private static final Pattern ANGER = words("unverschämt", "frechheit", "sauer", "wütend", "ärgerlich", "skandal", "geld zurück");
    private static final Pattern FRUSTRATION = words("frust", "nervig", "nervt", "zeitverschwendung", "stundenlang", "ständig", "schon wieder", "warteschleife");
    private static final Pattern DISAPPOINTMENT = words("enttäusch", "schade", "mehr erwartet", "mehr erhofft", "fehlkauf");
    private static final Pattern SURPRISE = words("überrasch", "unerwartet", "nicht gedacht", "nicht erwartet", "übertroffen");
    private static final Pattern JOY = words("begeistert", "liebe", "glücklich", "genial", "traumhaft", "das beste", "highlight", "hingucker");

    public ReviewClassification classify(String comment, int rating) {
        String text = comment == null ? "" : comment.toLowerCase(Locale.GERMAN);
        double ratingScore = switch (rating) {
            case 5 -> 0.9;
            case 4 -> 0.5;
            case 3 -> 0.0;
            case 2 -> -0.5;
            default -> -0.9;
        };

        int positive = 0;
        int negative = 0;
        Set<String> seen = new LinkedHashSet<>();
        List<ReviewAspect> aspects = new ArrayList<>();

        // Teilsätze getrennt betrachten: "Design schön, aber Akku schwach" sind zwei Aspekte
        for (String clause : text.split("[.!?;]+|,?\\s+aber\\s+|,?\\s+jedoch\\s+|,?\\s+allerdings\\s+")) {
            if (clause.isBlank()) continue;
            int polarity = polarity(clause);
            if (polarity > 0) positive++;
            if (polarity < 0) negative++;
            ReviewSentiment clauseSentiment = polarity > 0 ? ReviewSentiment.POSITIVE
                    : polarity < 0 ? ReviewSentiment.NEGATIVE
                    : rating >= 4 ? ReviewSentiment.POSITIVE : rating <= 2 ? ReviewSentiment.NEGATIVE : ReviewSentiment.NEUTRAL;
            for (Map.Entry<ReviewTheme, Pattern> theme : THEMES.entrySet()) {
                if (theme.getValue().matcher(clause).find() && seen.add(theme.getKey() + ":" + clauseSentiment)) {
                    aspects.add(new ReviewAspect(theme.getKey(), clauseSentiment));
                }
            }
        }

        double textScore = positive + negative == 0 ? 0 : (double) (positive - negative) / (positive + negative);
        double score = Math.max(-1, Math.min(1, 0.7 * ratingScore + 0.3 * textScore));
        ReviewSentiment sentiment = score >= 0.25 ? ReviewSentiment.POSITIVE
                : score <= -0.25 ? ReviewSentiment.NEGATIVE : ReviewSentiment.NEUTRAL;

        return new ReviewClassification(sentiment, Math.round(score * 100) / 100.0, emotion(text, sentiment),
                aspects, ReviewInsight.Source.RULES);
    }

    /** Positive minus negative Signalwörter; ein "nicht/kein" in den zwei Wörtern davor dreht die Richtung. */
    private static int polarity(String clause) {
        return Integer.signum(count(POSITIVE, clause, 1) + count(NEGATIVE, clause, -1));
    }

    private static int count(Pattern pattern, String clause, int sign) {
        int sum = 0;
        Matcher matcher = pattern.matcher(clause);
        while (matcher.find()) {
            boolean negated = negatedBefore(clause, matcher.start())
                    || (NEGATED_AFTER.matcher(matcher.group()).lookingAt() && negatedAfter(clause, matcher.end()));
            sum += negated ? -sign : sign;
        }
        return sum;
    }

    /** Verneinung in den drei Wörtern davor; sie wirkt nicht über "und", "oder" oder ein Komma hinweg. */
    private static boolean negatedBefore(String clause, int position) {
        String[] before = clause.substring(0, position).trim().split("\\s+");
        for (int i = before.length - 1; i >= Math.max(0, before.length - 3); i--) {
            String token = before[i];
            if (token.endsWith(",")) return false;
            if (NEGATION.matcher(token).matches()) return true;
            if (token.equals("und") || token.equals("oder") || token.equals("sondern")) return false;
        }
        return false;
    }

    private static boolean negatedAfter(String clause, int position) {
        String[] after = clause.substring(position).trim().split("[\\s,]+");
        for (int i = 0; i < Math.min(2, after.length); i++) {
            if (NEGATION.matcher(after[i]).matches()) return true;
        }
        return false;
    }

    private static ReviewEmotion emotion(String text, ReviewSentiment sentiment) {
        if (sentiment == ReviewSentiment.NEGATIVE) {
            if (ANGER.matcher(text).find()) return ReviewEmotion.ANGER;
            if (FRUSTRATION.matcher(text).find()) return ReviewEmotion.FRUSTRATION;
            return ReviewEmotion.DISAPPOINTMENT;
        }
        if (sentiment == ReviewSentiment.POSITIVE) {
            if (SURPRISE.matcher(text).find()) return ReviewEmotion.SURPRISE;
            if (JOY.matcher(text).find()) return ReviewEmotion.JOY;
            return ReviewEmotion.SATISFACTION;
        }
        if (DISAPPOINTMENT.matcher(text).find()) return ReviewEmotion.DISAPPOINTMENT;
        return ReviewEmotion.NEUTRAL;
    }

    /** Wortanfänge: "liefer" trifft "Lieferung" und "geliefert" nicht doppelt, "app" aber nicht "klappt". */
    private static Pattern words(String... stems) {
        return Pattern.compile("(?<![\\p{L}])(" + String.join("|", stems) + ")", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
    }
}
