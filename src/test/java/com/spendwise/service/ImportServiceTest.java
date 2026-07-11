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
                new Category(2, "Groceries", TransactionType.EXPENSE, "#D97706"),
                new Category(3, "Transport", TransactionType.EXPENSE, "#0284C7"),
                new Category(4, "Uncategorized", TransactionType.EXPENSE, "#9CA3AF"),
                new Category(5, "Uncategorized Income", TransactionType.INCOME, "#9CA3AF"));
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
}
