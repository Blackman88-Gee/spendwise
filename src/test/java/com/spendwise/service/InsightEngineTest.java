package com.spendwise.service;

import com.spendwise.dao.InMemoryTransactionRepository;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Insight;
import com.spendwise.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightEngineTest {

    private InMemoryTransactionRepository transactionRepository;
    private InsightEngine insightEngine;
    private Category salary;
    private Category dining;
    private LocalDate today;

    @BeforeEach
    void setUp() throws ValidationException {
        transactionRepository = new InMemoryTransactionRepository();
        insightEngine = new InsightEngine(transactionRepository);
        salary = new Category(1, "Salary", TransactionType.INCOME, "#2E7D32");
        dining = new Category(2, "Dining Out", TransactionType.EXPENSE, "#D84315");
        today = LocalDate.now();
    }

    @Test
    void flagsSpendingMoreThanIncome() throws ValidationException {
        transactionRepository.save(new Income(0, 500.0, "Pay", salary, today, null));
        transactionRepository.save(new Expense(0, 800.0, "Big spend", dining, today, null));

        List<Insight> insights = insightEngine.generateInsights(YearMonth.from(today));

        assertTrue(insights.stream().anyMatch(i -> i.getSeverity() == Insight.Severity.CRITICAL));
    }

    @Test
    void flagsHighDiscretionaryRatio() throws ValidationException {
        transactionRepository.save(new Income(0, 1000.0, "Pay", salary, today, null));
        transactionRepository.save(new Expense(0, 400.0, "Nights out", dining, today, null));

        List<Insight> insights = insightEngine.generateInsights(YearMonth.from(today));

        assertTrue(insights.stream().anyMatch(i -> i.getMessage().contains("dining, entertainment")));
    }

    @Test
    void returnsInfoInsightWhenNothingFlagged() throws ValidationException {
        transactionRepository.save(new Income(0, 1000.0, "Pay", salary, today, null));
        transactionRepository.save(new Expense(0, 20.0, "Small coffee", dining, today, null));

        List<Insight> insights = insightEngine.generateInsights(YearMonth.from(today));

        assertTrue(insights.stream().anyMatch(i -> i.getSeverity() == Insight.Severity.INFO));
    }
}
