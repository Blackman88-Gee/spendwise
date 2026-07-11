package com.spendwise.controller;

import com.spendwise.dao.CategoryRepository;
import com.spendwise.dao.SqliteBudgetRepository;
import com.spendwise.dao.SqliteCategoryRepository;
import com.spendwise.dao.SqliteTransactionRepository;
import com.spendwise.dao.BudgetRepository;
import com.spendwise.dao.TransactionRepository;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Budget;
import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;
import com.spendwise.service.BudgetService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class BudgetsController {

    @FXML private VBox budgetCardsContainer;

    private final CategoryRepository categoryRepository = new SqliteCategoryRepository();
    private final TransactionRepository transactionRepository = new SqliteTransactionRepository(categoryRepository);
    private final BudgetRepository budgetRepository = new SqliteBudgetRepository(categoryRepository);
    private final BudgetService budgetService = new BudgetService(budgetRepository, transactionRepository);

    @FXML
    public void initialize() {
        refresh();
    }

    private void refresh() {
        budgetCardsContainer.getChildren().clear();
        List<BudgetService.BudgetStatus> statuses = budgetService.getBudgetStatuses(YearMonth.now());
        if (statuses.isEmpty()) {
            budgetCardsContainer.getChildren().add(new Label("No budgets set yet — click \"+ Set Budget\" to add one."));
            return;
        }
        for (BudgetService.BudgetStatus status : statuses) {
            budgetCardsContainer.getChildren().add(buildCard(status));
        }
    }

    private VBox buildCard(BudgetService.BudgetStatus status) {
        Budget budget = status.budget();

        Label title = new Label(budget.getCategory().getName());
        title.getStyleClass().add("card-section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button editButton = new Button("Edit");
        editButton.setOnAction(e -> openBudgetDialog(budget));
        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> onDeleteBudget(budget));

        HBox header = new HBox(8, title, spacer, editButton, deleteButton);
        header.setAlignment(Pos.CENTER_LEFT);

        ProgressBar progressBar = new ProgressBar(Math.min(status.usagePercent(), 1.0));
        progressBar.setMaxWidth(Double.MAX_VALUE);
        String barColor = status.isOverBudget() ? "#C62828" : status.isNearLimit() ? "#EF6C00" : "#2E7D32";
        progressBar.setStyle("-fx-accent: " + barColor + ";");

        Label detail = new Label(String.format("$%.2f of $%.2f spent (%.0f%%)%s",
                status.spent(), budget.getMonthlyLimit(), status.usagePercent() * 100,
                status.isOverBudget() ? "  —  OVER BUDGET" : status.isNearLimit() ? "  —  approaching limit" : ""));
        detail.getStyleClass().add("placeholder-text");

        VBox card = new VBox(8, header, progressBar, detail);
        card.getStyleClass().add("card");
        return card;
    }

    private void onDeleteBudget(Budget budget) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove the budget for \"" + budget.getCategory().getName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Delete Budget");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                budgetRepository.delete(budget.getId());
                refresh();
            }
        });
    }

    @FXML
    public void onAddBudget() {
        List<Category> budgetedCategories = budgetRepository.findAll().stream()
                .map(Budget::getCategory).toList();
        List<Category> available = categoryRepository.findAll().stream()
                .filter(c -> c.getType() == TransactionType.EXPENSE)
                .filter(c -> !budgetedCategories.contains(c))
                .toList();

        if (available.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "All expense categories already have a budget.");
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }
        openBudgetDialog(null, available);
    }

    private void openBudgetDialog(Budget existing) {
        openBudgetDialog(existing, null);
    }

    private void openBudgetDialog(Budget existing, List<Category> available) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/spendwise/fxml/budget_form.fxml"));
            Parent formRoot = loader.load();
            BudgetFormController formController = loader.getController();

            if (existing != null) {
                formController.loadForEdit(existing);
            } else {
                formController.setAvailableCategories(available);
            }

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(existing == null ? "Set Budget" : "Edit Budget");
            dialog.getDialogPane().setContent(formRoot);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            AtomicReference<Budget> builtBudget = new AtomicReference<>();
            Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                try {
                    builtBudget.set(formController.buildBudget());
                } catch (ValidationException ex) {
                    formController.showError(ex.getMessage());
                    event.consume();
                }
            });

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK && builtBudget.get() != null) {
                budgetRepository.save(builtBudget.get());
                refresh();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not open budget form", e);
        }
    }
}
