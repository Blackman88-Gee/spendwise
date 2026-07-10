package com.spendwise.service;

import com.spendwise.dao.InMemoryBudgetRepository;
import com.spendwise.dao.InMemoryTransactionRepository;
import com.spendwise.exception.BudgetExceededException;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Budget;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BudgetServiceTest {

    private InMemoryTransactionRepository transactionRepository;
    private InMemoryBudgetRepository budgetRepository;
    private BudgetService budgetService;
    private Category dining;

    @BeforeEach
    void setUp() throws ValidationException {
        transactionRepository = new InMemoryTransactionRepository();
        budgetRepository = new InMemoryBudgetRepository();
        budgetService = new BudgetService(budgetRepository, transactionRepository);
        dining = new Category(1, "Dining Out", TransactionType.EXPENSE, "#D84315");
        budgetRepository.save(new Budget(1, dining, 100.0, 0.8));
    }

    @Test
    void allowsSpendingWithinBudget() {
        Expense expense = expenseOf(50.0);
        assertDoesNotThrow(() -> budgetService.validateAgainstBudget(expense));
    }

    @Test
    void throwsWhenExpenseWouldExceedBudget() throws ValidationException {
        transactionRepository.save(new Expense(1, 80.0, "Dinner", dining, LocalDate.now(), null));
        Expense secondExpense = expenseOf(30.0);

        BudgetExceededException ex = assertThrows(BudgetExceededException.class,
                () -> budgetService.validateAgainstBudget(secondExpense));
        assertEqualsDelta(10.0, ex.getOverspendAmount());
    }

    private void assertEqualsDelta(double expected, double actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, 0.0001);
    }

    private Expense expenseOf(double amount) {
        try {
            return new Expense(0, amount, "Test expense", dining, LocalDate.now(), null);
        } catch (ValidationException e) {
            throw new IllegalStateException(e);
        }
    }
}
