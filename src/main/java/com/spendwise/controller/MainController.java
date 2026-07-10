package com.spendwise.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.List;

public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Button navDashboard;
    @FXML private Button navTransactions;
    @FXML private Button navBudgets;
    @FXML private Button navRecurring;
    @FXML private Button navAnalytics;

    @FXML
    public void initialize() {
        showDashboard();
    }

    @FXML
    public void showDashboard() {
        load("dashboard.fxml", navDashboard);
    }

    @FXML
    public void showTransactions() {
        load("transactions.fxml", navTransactions);
    }

    @FXML
    public void showBudgets() {
        load("budgets.fxml", navBudgets);
    }

    @FXML
    public void showRecurring() {
        load("recurring.fxml", navRecurring);
    }

    @FXML
    public void showAnalytics() {
        load("analytics.fxml", navAnalytics);
    }

    private void load(String fxmlFile, Button activeButton) {
        try {
            Node node = FXMLLoader.load(getClass().getResource("/com/spendwise/fxml/" + fxmlFile));
            contentArea.getChildren().setAll(node);
            highlight(activeButton);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load view: " + fxmlFile, e);
        }
    }

    private void highlight(Button activeButton) {
        List<Button> navButtons = List.of(navDashboard, navTransactions, navBudgets, navRecurring, navAnalytics);
        for (Button button : navButtons) {
            button.getStyleClass().remove("nav-button-active");
        }
        activeButton.getStyleClass().add("nav-button-active");
    }
}
