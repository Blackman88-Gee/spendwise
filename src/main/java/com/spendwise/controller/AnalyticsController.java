package com.spendwise.controller;

import com.spendwise.dao.CategoryRepository;
import com.spendwise.dao.SqliteCategoryRepository;
import com.spendwise.dao.SqliteTransactionRepository;
import com.spendwise.dao.TransactionRepository;
import com.spendwise.model.Insight;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;
import com.spendwise.service.InsightEngine;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsController {

    @FXML private PieChart categoryPieChart;
    @FXML private BarChart<String, Number> trendBarChart;
    @FXML private CategoryAxis trendXAxis;
    @FXML private VBox insightsContainer;

    private final CategoryRepository categoryRepository = new SqliteCategoryRepository();
    private final TransactionRepository transactionRepository = new SqliteTransactionRepository(categoryRepository);
    private final InsightEngine insightEngine = new InsightEngine(transactionRepository);

    @FXML
    public void initialize() {
        populatePieChart();
        populateTrendChart();
        populateInsights();
    }

    private void populatePieChart() {
        YearMonth thisMonth = YearMonth.now();
        List<Transaction> monthTransactions = transactionRepository.findByDateRange(
                thisMonth.atDay(1), thisMonth.atEndOfMonth());

        Map<String, Double> totals = new LinkedHashMap<>();
        for (Transaction t : monthTransactions) {
            if (t.getType() != TransactionType.EXPENSE) {
                continue;
            }
            totals.merge(t.getCategory().getName(), t.getAmount(), Double::sum);
        }

        if (totals.isEmpty()) {
            categoryPieChart.setData(FXCollections.observableArrayList());
            return;
        }

        var data = totals.entrySet().stream()
                .map(e -> new PieChart.Data(e.getKey(), e.getValue()))
                .toList();
        categoryPieChart.setData(FXCollections.observableArrayList(data));
    }

    private void populateTrendChart() {
        trendBarChart.getData().clear();
        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Income");
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expense");

        YearMonth current = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            List<Transaction> monthTransactions = transactionRepository.findByDateRange(
                    month.atDay(1), month.atEndOfMonth());
            double income = monthTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.INCOME)
                    .mapToDouble(Transaction::getAmount).sum();
            double expense = monthTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE)
                    .mapToDouble(Transaction::getAmount).sum();

            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + month.getYear();
            incomeSeries.getData().add(new XYChart.Data<>(label, income));
            expenseSeries.getData().add(new XYChart.Data<>(label, expense));
        }

        trendBarChart.getData().addAll(incomeSeries, expenseSeries);
    }

    private void populateInsights() {
        insightsContainer.getChildren().clear();
        Label header = new Label("Spending Insights");
        header.getStyleClass().add("card-section-title");
        insightsContainer.getChildren().add(header);

        List<Insight> insights = insightEngine.generateInsights(YearMonth.now());
        for (Insight insight : insights) {
            Label label = new Label(insight.getMessage());
            label.setWrapText(true);
            label.getStyleClass().add(switch (insight.getSeverity()) {
                case CRITICAL -> "insight-critical";
                case WARNING -> "insight-warning";
                case INFO -> "insight-info";
            });
            insightsContainer.getChildren().add(label);
        }
    }
}
