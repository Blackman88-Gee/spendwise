package com.spendwise.service;

import com.spendwise.dao.TransactionRepository;
import com.spendwise.model.Insight;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates plain-language "spending harm" feedback rather than raw numbers —
 * the differentiator that separates SpendWise from a plain ledger.
 */
public class InsightEngine {

    private static final Set<String> DISCRETIONARY_CATEGORIES = Set.of("Dining Out", "Entertainment", "Shopping");
    private static final double DISCRETIONARY_ALERT_THRESHOLD = 0.30;
    private static final double CATEGORY_INCREASE_ALERT_THRESHOLD = 0.20;
    private static final int HABITUAL_TRANSACTION_COUNT = 4;
    private static final double PROJECTED_ANNUAL_ALERT_FLOOR = 500.0;

    private final TransactionRepository transactionRepository;

    public InsightEngine(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<Insight> generateInsights(YearMonth month) {
        List<Transaction> current = transactionRepository.findByDateRange(month.atDay(1), month.atEndOfMonth());
        YearMonth previousMonth = month.minusMonths(1);
        List<Transaction> previous = transactionRepository.findByDateRange(
                previousMonth.atDay(1), previousMonth.atEndOfMonth());

        double totalIncome = sum(current, TransactionType.INCOME);
        double totalExpense = sum(current, TransactionType.EXPENSE);

        List<Insight> insights = new ArrayList<>();
        addOverspendInsight(insights, totalIncome, totalExpense);
        addDiscretionaryInsight(insights, current, totalIncome);
        addCategoryTrendInsights(insights, current, previous);
        addProjectedAnnualCostInsights(insights, current);

        if (insights.isEmpty()) {
            insights.add(new Insight(Insight.Severity.INFO, "No spending red flags this month — keep it up."));
        }
        return insights;
    }

    private double sum(List<Transaction> transactions, TransactionType type) {
        return transactions.stream().filter(t -> t.getType() == type).mapToDouble(Transaction::getAmount).sum();
    }

    private void addOverspendInsight(List<Insight> insights, double income, double expense) {
        if (income > 0 && expense > income) {
            double deficit = expense - income;
            insights.add(new Insight(Insight.Severity.CRITICAL, String.format(
                    "You spent $%.2f more than you earned this month. Keeping this up erodes your savings by roughly $%.2f every month.",
                    deficit, deficit)));
        }
    }

    private void addDiscretionaryInsight(List<Insight> insights, List<Transaction> current, double income) {
        if (income <= 0) {
            return;
        }
        double discretionary = current.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> DISCRETIONARY_CATEGORIES.contains(t.getCategory().getName()))
                .mapToDouble(Transaction::getAmount)
                .sum();
        double ratio = discretionary / income;
        if (ratio >= DISCRETIONARY_ALERT_THRESHOLD) {
            insights.add(new Insight(Insight.Severity.WARNING, String.format(
                    "%.0f%% of your income this month went to dining, entertainment, and shopping — that's $%.2f that could have gone to savings.",
                    ratio * 100, discretionary)));
        }
    }

    private void addCategoryTrendInsights(List<Insight> insights, List<Transaction> current, List<Transaction> previous) {
        Map<String, Double> currentByCategory = totalsByCategory(current);
        Map<String, Double> previousByCategory = totalsByCategory(previous);

        for (var entry : currentByCategory.entrySet()) {
            double prevAmount = previousByCategory.getOrDefault(entry.getKey(), 0.0);
            if (prevAmount <= 0) {
                continue;
            }
            double increasePct = (entry.getValue() - prevAmount) / prevAmount;
            if (increasePct >= CATEGORY_INCREASE_ALERT_THRESHOLD) {
                insights.add(new Insight(Insight.Severity.WARNING, String.format(
                        "Your spending on %s is up %.0f%% from last month ($%.2f vs $%.2f).",
                        entry.getKey(), increasePct * 100, entry.getValue(), prevAmount)));
            }
        }
    }

    private Map<String, Double> totalsByCategory(List<Transaction> transactions) {
        Map<String, Double> totals = new HashMap<>();
        for (Transaction t : transactions) {
            if (t.getType() != TransactionType.EXPENSE) {
                continue;
            }
            totals.merge(t.getCategory().getName(), t.getAmount(), Double::sum);
        }
        return totals;
    }

    private void addProjectedAnnualCostInsights(List<Insight> insights, List<Transaction> current) {
        Map<String, List<Transaction>> byCategory = new HashMap<>();
        for (Transaction t : current) {
            if (t.getType() != TransactionType.EXPENSE) {
                continue;
            }
            byCategory.computeIfAbsent(t.getCategory().getName(), k -> new ArrayList<>()).add(t);
        }

        for (var entry : byCategory.entrySet()) {
            List<Transaction> txs = entry.getValue();
            if (txs.size() < HABITUAL_TRANSACTION_COUNT) {
                continue;
            }
            double monthTotal = txs.stream().mapToDouble(Transaction::getAmount).sum();
            double projectedAnnual = monthTotal * 12;
            if (projectedAnnual >= PROJECTED_ANNUAL_ALERT_FLOOR) {
                insights.add(new Insight(Insight.Severity.INFO, String.format(
                        "You made %d %s purchases this month totaling $%.2f. At that pace, that's about $%.2f a year.",
                        txs.size(), entry.getKey(), monthTotal, projectedAnnual)));
            }
        }
    }
}
