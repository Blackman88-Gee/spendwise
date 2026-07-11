package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses free-form pasted notes into transaction candidates. The expected shape is a date line
 * ("July 10", "7/10", "2026-07-10", ...) followed by one expense per line ("Uber - 15.00"), but any
 * line lacking a detectable date/amount is still surfaced (flagged) rather than silently dropped, so
 * the review screen can fix it instead of losing it.
 */
public class ImportService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern TRIM_SEPARATORS = Pattern.compile("^[\\s\\-:,.$₵]+|[\\s\\-:,.$₵]+$");

    private static final List<DateTimeFormatter> DATE_FORMATTERS_WITH_YEAR = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));

    private static final List<DateTimeFormatter> DATE_FORMATTERS_NO_YEAR = List.of(
            DateTimeFormatter.ofPattern("M/d"),
            DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH));

    private static final List<String> INCOME_KEYWORDS =
            List.of("salary", "wage", "wages", "income", "received", "payment received", "freelance", "gig pay");

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("Groceries", List.of("grocery", "groceries", "supermarket", "market"));
        CATEGORY_KEYWORDS.put("Dining Out", List.of("restaurant", "lunch", "dinner", "breakfast", "jollof",
                "pizza", "kfc", "coffee", "cafe", "eatery", "chop bar", "waakye", "fufu"));
        CATEGORY_KEYWORDS.put("Transport", List.of("uber", "bolt", "trotro", "taxi", "bus", "fuel", "petrol", "fare"));
        CATEGORY_KEYWORDS.put("Rent", List.of("rent"));
        CATEGORY_KEYWORDS.put("Utilities", List.of("ecg", "electricity", "water bill", "light bill", "utility",
                "internet", "data bundle", "airtime"));
        CATEGORY_KEYWORDS.put("Entertainment", List.of("netflix", "movie", "cinema", "spotify", "game"));
        CATEGORY_KEYWORDS.put("Health", List.of("hospital", "pharmacy", "clinic", "medicine", "drugs"));
        CATEGORY_KEYWORDS.put("Shopping", List.of("clothes", "shoes", "shopping", "mall", "amazon"));
        CATEGORY_KEYWORDS.put("Salary", List.of("salary", "wage", "wages"));
        CATEGORY_KEYWORDS.put("Freelance", List.of("freelance", "gig", "contract job"));
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

            LocalDate parsedDate = tryParseDate(line);
            if (parsedDate != null) {
                currentDate = parsedDate;
                continue;
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
            warning = "No date header found above this line — defaulted to today";
        }

        double amount = 0;
        boolean amountFound = false;
        String description = line;

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
            description = TRIM_SEPARATORS.matcher(description).replaceAll("");
            if (description.isBlank()) {
                description = line;
            }
        } else {
            warning = appendWarning(warning, "Could not detect an amount — please edit");
        }

        String lowerDescription = description.toLowerCase(Locale.ROOT);
        boolean isIncome = INCOME_KEYWORDS.stream().anyMatch(lowerDescription::contains);
        TransactionType type = isIncome ? TransactionType.INCOME : TransactionType.EXPENSE;

        Category category = guessCategory(lowerDescription, type, availableCategories)
                .orElse(type == TransactionType.INCOME ? fallbackIncome : fallbackExpense);

        return new ParsedExpense(date, type, description.isBlank() ? line : description, amount, category,
                amountFound, warning);
    }

    private java.util.Optional<Category> guessCategory(String lowerDescription, TransactionType type,
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
        return java.util.Optional.empty();
    }

    private java.util.Optional<Category> findByName(List<Category> categories, String name) {
        return categories.stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst();
    }

    private String appendWarning(String existing, String addition) {
        return existing == null ? addition : existing + "; " + addition;
    }

    private LocalDate tryParseDate(String line) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS_WITH_YEAR) {
            try {
                return LocalDate.parse(line, formatter);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS_NO_YEAR) {
            try {
                var monthDay = java.time.MonthDay.parse(line, formatter);
                LocalDate candidate = monthDay.atYear(LocalDate.now().getYear());
                if (candidate.isAfter(LocalDate.now())) {
                    candidate = candidate.minusYears(1);
                }
                return candidate;
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        return null;
    }
}
