package com.spendwise.service;

import com.spendwise.exception.ValidationException;
import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportServiceTest {

    private ImportService importService;
    private List<Category> categories;

    @BeforeEach
    void setUp() throws ValidationException {
        importService = new ImportService();
        categories = List.of(
                new Category(1, "Salary", TransactionType.INCOME, "#0F766E"),
                new Category(2, "Freelance", TransactionType.INCOME, "#4F46E5"),
                new Category(3, "Groceries", TransactionType.EXPENSE, "#D97706"),
                new Category(4, "Dining Out", TransactionType.EXPENSE, "#DB2777"),
                new Category(5, "Transport", TransactionType.EXPENSE, "#0284C7"),
                new Category(6, "Rent", TransactionType.EXPENSE, "#9333EA"),
                new Category(7, "Utilities", TransactionType.EXPENSE, "#0891B2"),
                new Category(8, "Entertainment", TransactionType.EXPENSE, "#E11D48"),
                new Category(9, "Health", TransactionType.EXPENSE, "#65A30D"),
                new Category(10, "Shopping", TransactionType.EXPENSE, "#78716C"),
                new Category(11, "Uncategorized", TransactionType.EXPENSE, "#9CA3AF"),
                new Category(12, "Uncategorized Income", TransactionType.INCOME, "#9CA3AF"));
    }

    @Test
    void parsesDateHeaderFollowedByExpenseLines() {
        String note = """
                July 10
                Uber - 15.00
                Groceries - 45.20
                """;

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.date().getMonth() == Month.JULY && r.date().getDayOfMonth() == 10));
        assertEquals("Transport", rows.get(0).category().getName());
        assertEquals(15.00, rows.get(0).amount(), 0.001);
        assertEquals("Groceries", rows.get(1).category().getName());
        assertEquals(45.20, rows.get(1).amount(), 0.001);
    }

    @Test
    void flagsLineWithNoDetectableAmount() {
        String note = """
                July 10
                Uber to work
                """;

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals(1, rows.size());
        assertFalse(rows.get(0).amountFound());
        assertTrue(rows.get(0).warning().contains("amount"));
    }

    @Test
    void detectsIncomeFromKeywords() {
        String note = """
                July 10
                Salary received - 1500
                """;

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals(1, rows.size());
        assertEquals(TransactionType.INCOME, rows.get(0).type());
        assertEquals("Salary", rows.get(0).category().getName());
    }

    @Test
    void defaultsToTodayWhenNoDateHeaderPrecedesLine() {
        String note = "Uber - 15.00";

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals(1, rows.size());
        assertEquals(LocalDate.now(), rows.get(0).date());
        assertTrue(rows.get(0).warning().contains("date"));
    }

    @Test
    void unknownCategoryFallsBackToUncategorized() {
        String note = """
                July 10
                Random unclassifiable thing - 9.99
                """;

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals("Uncategorized", rows.get(0).category().getName());
    }

    @Test
    void parsesOrdinalDateSharingALineWithItsFirstEntry() {
        String note = """
                24th June - Transfer in = Ghs 500 ( Senior Mike)
                TNT to Accra = 10
                """;

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals(2, rows.size());
        assertEquals(Month.JUNE, rows.get(0).date().getMonth());
        assertEquals(24, rows.get(0).date().getDayOfMonth());
        assertEquals(TransactionType.INCOME, rows.get(0).type());
        assertEquals(500.0, rows.get(0).amount(), 0.001);
        assertTrue(rows.get(0).description().contains("Senior Mike"));

        assertEquals(rows.get(0).date(), rows.get(1).date());
        assertEquals("Transport", rows.get(1).category().getName());
        assertEquals(10.0, rows.get(1).amount(), 0.001);
    }

    @Test
    void ignoresParentheticalNumbersWhenExtractingAmount() {
        String note = """
                25th June - Cash in = Ghs 1,200 ( $100 taken from Petty cash USD)
                """;

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals(1, rows.size());
        assertEquals(1200.0, rows.get(0).amount(), 0.001);
    }

    @Test
    void ignoresRunningTotalAfterSecondEqualsSign() {
        String note = """
                25th June - Cash in = Ghs 50 = Ghs 550
                """;

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals(1, rows.size());
        assertEquals(50.0, rows.get(0).amount(), 0.001);
        assertTrue(rows.get(0).warning() != null && rows.get(0).warning().contains("550"));
    }

    @Test
    void dateOnlyLineWithNoRemainderUpdatesDateWithoutCreatingARow() {
        String note = """
                26th June
                Water for house = 150
                """;

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals(1, rows.size());
        assertEquals(26, rows.get(0).date().getDayOfMonth());
        assertEquals(Month.JUNE, rows.get(0).date().getMonth());
        assertEquals("Utilities", rows.get(0).category().getName());
    }

    @Test
    void recognizesPersonalShorthandKeywordForTransport() {
        String note = """
                24th June
                TNT= 15
                """;

        List<ParsedExpense> rows = importService.parseNote(note, categories);

        assertEquals("Transport", rows.get(0).category().getName());
        assertEquals(15.0, rows.get(0).amount(), 0.001);
    }
}
