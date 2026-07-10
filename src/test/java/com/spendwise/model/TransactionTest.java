package com.spendwise.model;

import com.spendwise.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionTest {

    private Category expenseCategory() throws ValidationException {
        return new Category(1, "Groceries", TransactionType.EXPENSE, "#EF6C00");
    }

    private Category incomeCategory() throws ValidationException {
        return new Category(2, "Salary", TransactionType.INCOME, "#2E7D32");
    }

    @Test
    void expenseSignedAmountIsNegative() throws ValidationException {
        Expense expense = new Expense(1, 50.0, "Weekly shop", expenseCategory(), LocalDate.now(), null);
        assertEquals(-50.0, expense.signedAmount());
    }

    @Test
    void incomeSignedAmountIsPositive() throws ValidationException {
        Income income = new Income(1, 2000.0, "Monthly pay", incomeCategory(), LocalDate.now(), null);
        assertEquals(2000.0, income.signedAmount());
    }

    @Test
    void negativeAmountIsRejected() {
        assertThrows(ValidationException.class,
                () -> new Expense(1, -10.0, "Invalid", expenseCategory(), LocalDate.now(), null));
    }

    @Test
    void zeroAmountIsRejected() {
        assertThrows(ValidationException.class,
                () -> new Expense(1, 0.0, "Invalid", expenseCategory(), LocalDate.now(), null));
    }

    @Test
    void blankDescriptionIsRejected() {
        assertThrows(ValidationException.class,
                () -> new Expense(1, 10.0, "   ", expenseCategory(), LocalDate.now(), null));
    }

    @Test
    void futureDateIsRejected() {
        assertThrows(ValidationException.class,
                () -> new Expense(1, 10.0, "Future purchase", expenseCategory(), LocalDate.now().plusDays(1), null));
    }

    @Test
    void expenseRejectsIncomeCategory() throws ValidationException {
        Category income = incomeCategory();
        assertThrows(ValidationException.class,
                () -> new Expense(1, 10.0, "Wrong category", income, LocalDate.now(), null));
    }
}
