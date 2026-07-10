package com.spendwise.model;

import com.spendwise.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetTest {

    private Category expenseCategory() throws ValidationException {
        return new Category(1, "Dining Out", TransactionType.EXPENSE, "#D84315");
    }

    @Test
    void usagePercentComputesCorrectly() throws ValidationException {
        Budget budget = new Budget(1, expenseCategory(), 200.0, 0.8);
        assertEquals(0.5, budget.usagePercent(100.0), 0.0001);
    }

    @Test
    void isNearLimitTrueBetweenThresholdAndFull() throws ValidationException {
        Budget budget = new Budget(1, expenseCategory(), 200.0, 0.8);
        assertTrue(budget.isNearLimit(170.0));
        assertFalse(budget.isNearLimit(100.0));
        assertFalse(budget.isNearLimit(200.0));
    }

    @Test
    void isOverBudgetTrueAtOrAboveLimit() throws ValidationException {
        Budget budget = new Budget(1, expenseCategory(), 200.0, 0.8);
        assertTrue(budget.isOverBudget(200.0));
        assertTrue(budget.isOverBudget(250.0));
        assertFalse(budget.isOverBudget(199.0));
    }

    @Test
    void budgetRejectsIncomeCategory() throws ValidationException {
        Category income = new Category(2, "Salary", TransactionType.INCOME, "#2E7D32");
        assertThrows(ValidationException.class, () -> new Budget(1, income, 200.0, 0.8));
    }

    @Test
    void budgetRejectsInvalidThreshold() throws ValidationException {
        Category expense = expenseCategory();
        assertThrows(ValidationException.class, () -> new Budget(1, expense, 200.0, 1.5));
    }
}
