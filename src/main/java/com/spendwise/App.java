package com.spendwise;

import com.spendwise.dao.DatabaseManager;
import com.spendwise.dao.SqliteCategoryRepository;
import com.spendwise.dao.SqliteRecurringRuleRepository;
import com.spendwise.dao.SqliteTransactionRepository;
import com.spendwise.model.Transaction;
import com.spendwise.service.RecurringTransactionService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.List;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Something went wrong: " + throwable.getMessage() + "\n\nYou can keep using SpendWise, but please save your work.");
            alert.setHeaderText("Unexpected Error");
            alert.showAndWait();
        });

        try {
            DatabaseManager.initSchema();
            generateDueRecurringTransactions();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/spendwise/fxml/main.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(getClass().getResource("/com/spendwise/css/styles.css").toExternalForm());

            stage.setTitle("SpendWise — Expense Tracking & Budgeting");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "SpendWise could not start: " + e.getMessage());
            alert.setHeaderText("Startup Failed");
            alert.showAndWait();
            throw new IllegalStateException("Failed to start SpendWise", e);
        }
    }

    private void generateDueRecurringTransactions() {
        var categoryRepository = new SqliteCategoryRepository();
        var transactionRepository = new SqliteTransactionRepository(categoryRepository);
        var ruleRepository = new SqliteRecurringRuleRepository(categoryRepository);
        var service = new RecurringTransactionService(ruleRepository, transactionRepository);

        List<Transaction> generated = service.generateDueTransactions();
        if (!generated.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, generated.size()
                    + " recurring transaction(s) were added automatically: "
                    + generated.stream().map(Transaction::getDescription).distinct().reduce((a, b) -> a + ", " + b).orElse(""));
            alert.setHeaderText("Recurring Transactions Applied");
            alert.showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
