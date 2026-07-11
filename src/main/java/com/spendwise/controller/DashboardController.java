package com.spendwise.controller;

import com.spendwise.dao.CategoryRepository;
import com.spendwise.dao.SqliteCategoryRepository;
import com.spendwise.dao.SqliteTransactionRepository;
import com.spendwise.dao.TransactionRepository;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

public class DashboardController {

    @FXML private Label balanceLabel;
    @FXML private Label monthIncomeLabel;
    @FXML private Label monthExpenseLabel;
    @FXML private ListView<String> recentList;

    private final CategoryRepository categoryRepository = new SqliteCategoryRepository();
    private final TransactionRepository transactionRepository = new SqliteTransactionRepository(categoryRepository);

    @FXML
    public void initialize() {
        List<Transaction> all = transactionRepository.findAll();

        double balance = all.stream().mapToDouble(Transaction::signedAmount).sum();
        balanceLabel.setText(String.format("$%.2f", balance));

        YearMonth thisMonth = YearMonth.now();
        List<Transaction> monthTransactions = transactionRepository.findByDateRange(
                thisMonth.atDay(1), thisMonth.atEndOfMonth());
        double monthIncome = monthTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .mapToDouble(Transaction::getAmount).sum();
        double monthExpense = monthTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .mapToDouble(Transaction::getAmount).sum();
        monthIncomeLabel.setText(String.format("$%.2f", monthIncome));
        monthExpenseLabel.setText(String.format("$%.2f", monthExpense));

        List<String> recent = all.stream()
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .limit(8)
                .map(t -> String.format("%s   %-20s %s$%.2f (%s)",
                        t.getDate(), t.getDescription(), t.getType() == TransactionType.EXPENSE ? "-" : "+",
                        t.getAmount(), t.getCategory().getName()))
                .toList();
        List<String> items = recent.isEmpty()
                ? List.of("No transactions yet — add one from the Transactions tab.")
                : recent;
        recentList.setItems(FXCollections.observableArrayList(items));
        recentList.setPrefHeight(items.size() * 32 + 2);
    }
}
