package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses free-form pasted notes into transaction candidates. Handles a date appearing either alone
 * on its own line, or leading straight into that day's first entry on the same line
 * (e.g. "24th June - Transfer in = Ghs 500 (Senior Mike)"), with every subsequent line until the next
 * date belonging to that same day. Any line the parser can't confidently read is still surfaced
 * (flagged with a warning) rather than silently dropped or guessed wrong.
 */
public class ImportService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern CURRENCY_TOKEN = Pattern.compile("(?i)ghs|gh₵|ghc|usd|₵|\\$");
    private static final Pattern LEADING_SEPARATOR = Pattern.compile("^[\\s\\-:,]+");
    private static final Pattern TRIM_SEPARATORS = Pattern.compile("^[\\s\\-:,.=]+|[\\s\\-:,.=]+$");

    private static final Pattern DATE_DAY_FIRST =
            Pattern.compile("^(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]+)\\.?(?:,?\\s+(\\d{4}))?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_MONTH_FIRST =
            Pattern.compile("^([A-Za-z]+)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_ISO = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern DATE_NUMERIC = Pattern.compile("^(\\d{1,2})[/\\-](\\d{1,2})(?:[/\\-](\\d{2,4}))?\\b");

    private static final Map<String, Integer> MONTH_LOOKUP = buildMonthLookup();

    private static final List<String> INCOME_KEYWORDS = List.of(
            "salary", "wage", "wages", "income", "received", "payment received", "freelance", "gig pay",
            "cash in", "transfer in");

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("Groceries", List.of("grocery", "groceries", "supermarket", "market"));
        CATEGORY_KEYWORDS.put("Dining Out", List.of("restaurant", "lunch", "dinner", "breakfast", "jollof",
                "pizza", "kfc", "coffee", "cafe", "eatery", "chop bar", "waakye", "fufu", "food"));
        CATEGORY_KEYWORDS.put("Transport", List.of("uber", "bolt", "trotro", "tro-tro", "tnt", "taxi", "bus",
                "fuel", "petrol", "fare"));
        CATEGORY_KEYWORDS.put("Rent", List.of("rent"));
        CATEGORY_KEYWORDS.put("Utilities", List.of("ecg", "electricity", "water bill", "water for house", "water",
                "light bill", "utility", "internet", "data bundle", "airtime"));
        CATEGORY_KEYWORDS.put("Entertainment", List.of("netflix", "movie", "cinema", "spotify", "game"));
        CATEGORY_KEYWORDS.put("Health", List.of("hospital", "pharmacy", "clinic", "medicine", "drugs"));
        CATEGORY_KEYWORDS.put("Shopping", List.of("clothes", "shoes", "shopping", "mall", "amazon", "jersey",
                "shirt", "dress", "wear"));
        CATEGORY_KEYWORDS.put("Salary", List.of("salary", "wage", "wages"));
        CATEGORY_KEYWORDS.put("Freelance", List.of("freelance", "gig", "contract job"));
    }

    private record DateMatch(LocalDate date, String remainder) {
    }

    public List<ParsedExpense> parseNote(String rawText, List<Category> availableCategories) {
        List<ParsedExpense> results = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return results;
        }

        Category fallbackExpense = findByName(availableCategories, "Uncategorized").orElse(null);
        Category fallbackIncome = findByName(availableCategories, "Uncategorized Income").orElse(null);

        LocalDate currentDate = null;
        for (String rawLine : rawText.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }

            DateMatch dateMatch = extractLeadingDate(line);
            if (dateMatch != null) {
                currentDate = dateMatch.date();
                String remainder = LEADING_SEPARATOR.matcher(dateMatch.remainder().strip()).replaceFirst("").strip();
                if (remainder.isEmpty()) {
                    continue;
                }
                line = remainder;
            }

            results.add(parseExpenseLine(line, currentDate, availableCategories, fallbackExpense, fallbackIncome));
        }
        return results;
    }

    private ParsedExpense parseExpenseLine(String line, LocalDate currentDate, List<Category> availableCategories,
                                            Category fallbackExpense, Category fallbackIncome) {
        String warning = null;
        LocalDate date = currentDate;
        if (date == null) {
            date = LocalDate.now();
            warning = "No date found above this line — defaulted to today";
        }

        String note = null;
        Matcher parenMatcher = PARENTHETICAL.matcher(line);
        if (parenMatcher.find()) {
            note = parenMatcher.group(1).strip();
            line = parenMatcher.replaceAll("").strip();
        }

        double amount = 0;
        boolean amountFound = false;
        String description;

        int equalsIndex = line.indexOf('=');
        if (equalsIndex >= 0) {
            description = line.substring(0, equalsIndex).strip();
            String rest = line.substring(equalsIndex + 1);
            int secondEquals = rest.indexOf('=');
            String amountSegment = secondEquals >= 0 ? rest.substring(0, secondEquals) : rest;
            String extra = secondEquals >= 0 ? rest.substring(secondEquals + 1).strip() : null;

            Double parsedAmount = extractAmount(amountSegment);
            if (parsedAmount != null) {
                amount = parsedAmount;
                amountFound = true;
            } else {
                warning = appendWarning(warning, "Could not detect an amount — please edit");
            }
            if (extra != null && !extra.isBlank()) {
                warning = appendWarning(warning, "extra value in line not used as amount: " + extra);
            }
        } else {
            Matcher matcher = NUMBER_PATTERN.matcher(line);
            int lastStart = -1;
            int lastEnd = -1;
            String lastMatch = null;
            while (matcher.find()) {
                lastStart = matcher.start();
                lastEnd = matcher.end();
                lastMatch = matcher.group(1);
            }
            if (lastMatch != null) {
                amount = Double.parseDouble(lastMatch);
                amountFound = true;
                description = line.substring(0, lastStart) + line.substring(lastEnd);
            } else {
                description = line;
                warning = appendWarning(warning, "Could not detect an amount — please edit");
            }
        }

        description = TRIM_SEPARATORS.matcher(description).replaceAll("");
        if (description.isBlank()) {
            description = line;
        }
        if (note != null && !note.isBlank()) {
            description = description + " (" + note + ")";
        }

        String lowerDescription = description.toLowerCase(Locale.ROOT);
        boolean isIncome = INCOME_KEYWORDS.stream().anyMatch(lowerDescription::contains);
        TransactionType type = isIncome ? TransactionType.INCOME : TransactionType.EXPENSE;

        Category category = guessCategory(lowerDescription, type, availableCategories)
                .orElse(type == TransactionType.INCOME ? fallbackIncome : fallbackExpense);

        return new ParsedExpense(date, type, description, amount, category, amountFound, warning);
    }

    private Double extractAmount(String text) {
        String cleaned = CURRENCY_TOKEN.matcher(text).replaceAll("");
        cleaned = cleaned.replace(",", "");
        Matcher matcher = NUMBER_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private Optional<Category> guessCategory(String lowerDescription, TransactionType type,
                                              List<Category> availableCategories) {
        for (var entry : CATEGORY_KEYWORDS.entrySet()) {
            boolean matches = entry.getValue().stream().anyMatch(lowerDescription::contains);
            if (!matches) {
                continue;
            }
            var found = findByName(availableCategories, entry.getKey());
            if (found.isPresent() && found.get().getType() == type) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<Category> findByName(List<Category> categories, String name) {
        return categories.stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst();
    }

    private String appendWarning(String existing, String addition) {
        return existing == null ? addition : existing + "; " + addition;
    }

    private DateMatch extractLeadingDate(String line) {
        Matcher m = DATE_DAY_FIRST.matcher(line);
        if (m.find() && m.start() == 0) {
            Integer month = MONTH_LOOKUP.get(m.group(2).toLowerCase(Locale.ROOT));
            if (month != null) {
                int day = Integer.parseInt(m.group(1));
                Integer year = m.group(3) != null ? Integer.parseInt(m.group(3)) : null;
                return new DateMatch(buildDate(day, month, year), line.substring(m.end()));
            }
        }

        m = DATE_MONTH_FIRST.matcher(line);
        if (m.find() && m.start() == 0) {
            Integer month = MONTH_LOOKUP.get(m.group(1).toLowerCase(Locale.ROOT));
            if (month != null) {
                int day = Integer.parseInt(m.group(2));
                Integer year = m.group(3) != null ? Integer.parseInt(m.group(3)) : null;
                return new DateMatch(buildDate(day, month, year), line.substring(m.end()));
            }
        }

        m = DATE_ISO.matcher(line);
        if (m.find() && m.start() == 0) {
            LocalDate date = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            return new DateMatch(date, line.substring(m.end()));
        }

        m = DATE_NUMERIC.matcher(line);
        if (m.find() && m.start() == 0) {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            Integer year = m.group(3) != null ? Integer.parseInt(m.group(3)) : null;
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                return new DateMatch(buildDate(day, month, year), line.substring(m.end()));
            }
        }

        return null;
    }

    private LocalDate buildDate(int day, int month, Integer year) {
        int resolvedYear = year != null ? year : LocalDate.now().getYear();
        LocalDate candidate = LocalDate.of(resolvedYear, month, day);
        if (year == null && candidate.isAfter(LocalDate.now())) {
            candidate = candidate.minusYears(1);
        }
        return candidate;
    }

    private static Map<String, Integer> buildMonthLookup() {
        Map<String, Integer> map = new LinkedHashMap<>();
        String[] full = {"january", "february", "march", "april", "may", "june", "july", "august",
                "september", "october", "november", "december"};
        String[] shortNames = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < full.length; i++) {
            map.put(full[i], i + 1);
            map.put(shortNames[i], i + 1);
        }
        map.put("sept", 9);
        return map;
    }
}
