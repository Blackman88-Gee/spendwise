package com.spendwise.controller;

import com.spendwise.dao.CategoryRepository;
import com.spendwise.dao.SqliteCategoryRepository;
import com.spendwise.dao.SqliteTransactionRepository;
import com.spendwise.dao.TransactionRepository;
import com.spendwise.model.Currency;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML private FlowPane summaryCardsContainer;
    @FXML private ListView<String> recentList;

    private final CategoryRepository categoryRepository = new SqliteCategoryRepository();
    private final TransactionRepository transactionRepository = new SqliteTransactionRepository(categoryRepository);

    @FXML
    public void initialize() {
        List<Transaction> all = transactionRepository.findAll();

        Currency primary = dominantCurrency(all);
        Currency secondary = primary == Currency.GHS ? Currency.USD : Currency.GHS;

        summaryCardsContainer.getChildren().clear();
        summaryCardsContainer.getChildren().add(balanceCard("Total Balance (" + primary + ")", all, primary, true));
        if (all.stream().anyMatch(t -> t.getCurrency() == secondary)) {
            summaryCardsContainer.getChildren().add(balanceCard("Total Balance (" + secondary + ")", all, secondary, false));
        }

        YearMonth thisMonth = YearMonth.now();
        List<Transaction> monthTransactions = transactionRepository.findByDateRange(
                thisMonth.atDay(1), thisMonth.atEndOfMonth());

        summaryCardsContainer.getChildren().add(monthCard("This Month — Income (" + primary + ")",
                monthTransactions, primary, TransactionType.INCOME, "card-value-income"));
        summaryCardsContainer.getChildren().add(monthCard("This Month — Expenses (" + primary + ")",
                monthTransactions, primary, TransactionType.EXPENSE, "card-value-expense"));

        if (monthTransactions.stream().anyMatch(t -> t.getCurrency() == secondary)) {
            summaryCardsContainer.getChildren().add(monthCard("This Month — Income (" + secondary + ")",
                    monthTransactions, secondary, TransactionType.INCOME, "card-value-income"));
            summaryCardsContainer.getChildren().add(monthCard("This Month — Expenses (" + secondary + ")",
                    monthTransactions, secondary, TransactionType.EXPENSE, "card-value-expense"));
        }

        List<String> recent = all.stream()
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .limit(8)
                .map(t -> String.format("%s   %-20s %s%s (%s)",
                        t.getDate(), t.getDescription(), t.getType() == TransactionType.EXPENSE ? "-" : "+",
                        t.getCurrency().format(t.getAmount()), t.getCategory().getName()))
                .toList();
        List<String> items = recent.isEmpty()
                ? List.of("No transactions yet — add one from the Transactions tab.")
                : recent;
        recentList.setItems(FXCollections.observableArrayList(items));
        recentList.setPrefHeight(items.size() * 32 + 2);
    }

    /** The currency with the most transaction activity is treated as primary, so the dashboard emphasizes whichever one actually matters day-to-day. */
    private Currency dominantCurrency(List<Transaction> all) {
        Map<Currency, Long> counts = all.stream()
                .collect(Collectors.groupingBy(Transaction::getCurrency, Collectors.counting()));
        return counts.getOrDefault(Currency.USD, 0L) > counts.getOrDefault(Currency.GHS, 0L)
                ? Currency.USD : Currency.GHS;
    }

    private VBox balanceCard(String label, List<Transaction> transactions, Currency currency, boolean primary) {
        double balance = transactions.stream()
                .filter(t -> t.getCurrency() == currency)
                .mapToDouble(Transaction::signedAmount)
                .sum();
        Label valueLabel = new Label(currency.format(balance));
        valueLabel.getStyleClass().add(primary ? "card-value" : "card-value-secondary");
        return card(label, valueLabel);
    }

    private VBox monthCard(String label, List<Transaction> monthTransactions, Currency currency,
                           TransactionType type, String valueStyleClass) {
        double total = monthTransactions.stream()
                .filter(t -> t.getCurrency() == currency)
                .filter(t -> t.getType() == type)
                .mapToDouble(Transaction::getAmount)
                .sum();
        Label valueLabel = new Label(currency.format(total));
        valueLabel.getStyleClass().add(valueStyleClass);
        return card(label, valueLabel);
    }

    private VBox card(String label, Label valueLabel) {
        Label titleLabel = new Label(label);
        titleLabel.getStyleClass().add("card-label");
        VBox box = new VBox(6, titleLabel, valueLabel);
        box.getStyleClass().add("card");
        box.setPrefWidth(220);
        return box;
    }
}
