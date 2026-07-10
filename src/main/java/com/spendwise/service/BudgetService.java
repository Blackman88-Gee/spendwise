package com.spendwise.service;

import com.spendwise.dao.BudgetRepository;
import com.spendwise.dao.TransactionRepository;
import com.spendwise.exception.BudgetExceededException;
import com.spendwise.model.Budget;
import com.spendwise.model.Expense;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(BudgetRepository budgetRepository, TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
    }

    public record BudgetStatus(Budget budget, double spent) {
        public double usagePercent() {
            return budget.usagePercent(spent);
        }

        public double remaining() {
            return budget.remaining(spent);
        }

        public boolean isNearLimit() {
            return budget.isNearLimit(spent);
        }

        public boolean isOverBudget() {
            return budget.isOverBudget(spent);
        }
    }

    public List<BudgetStatus> getBudgetStatuses(YearMonth month) {
        List<Transaction> monthTransactions = transactionRepository.findByDateRange(month.atDay(1), month.atEndOfMonth());
        List<BudgetStatus> statuses = new ArrayList<>();
        for (Budget budget : budgetRepository.findAll()) {
            double spent = spentInCategory(monthTransactions, budget.getCategory().getId());
            statuses.add(new BudgetStatus(budget, spent));
        }
        return statuses;
    }

    /** Called before persisting a new expense so the UI can warn the user before they blow their budget. */
    public void validateAgainstBudget(Expense expense) throws BudgetExceededException {
        Optional<Budget> maybeBudget = budgetRepository.findByCategoryId(expense.getCategory().getId());
        if (maybeBudget.isEmpty()) {
            return;
        }
        Budget budget = maybeBudget.get();
        YearMonth month = YearMonth.from(expense.getDate());
        List<Transaction> monthTransactions = transactionRepository.findByDateRange(month.atDay(1), month.atEndOfMonth());
        double alreadySpent = spentInCategory(monthTransactions, budget.getCategory().getId());
        double projected = alreadySpent + expense.getAmount();

        if (projected > budget.getMonthlyLimit()) {
            double overspend = projected - budget.getMonthlyLimit();
            throw new BudgetExceededException(String.format(
                    "This would put your '%s' spending at $%.2f, which is $%.2f over your $%.2f monthly budget.",
                    expense.getCategory().getName(), projected, overspend, budget.getMonthlyLimit()), overspend);
        }
    }

    private double spentInCategory(List<Transaction> transactions, int categoryId) {
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getCategory().getId() == categoryId)
                .mapToDouble(Transaction::getAmount)
                .sum();
    }
}
