package com.spendwise.controller;

import com.spendwise.exception.ValidationException;
import com.spendwise.model.Budget;
import com.spendwise.model.Category;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.List;

public class BudgetFormController {

    @FXML private ComboBox<Category> categoryCombo;
    @FXML private TextField limitField;
    @FXML private TextField thresholdField;
    @FXML private Label errorLabel;

    private int editingId = 0;

    @FXML
    public void initialize() {
        categoryCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Category category) {
                return category == null ? "" : category.getName();
            }

            @Override
            public Category fromString(String string) {
                return null;
            }
        });
    }

    public void setAvailableCategories(List<Category> categories) {
        categoryCombo.setItems(FXCollections.observableArrayList(categories));
        if (!categories.isEmpty()) {
            categoryCombo.setValue(categories.get(0));
        }
    }

    public void loadForEdit(Budget budget) {
        editingId = budget.getId();
        categoryCombo.setItems(FXCollections.observableArrayList(budget.getCategory()));
        categoryCombo.setValue(budget.getCategory());
        categoryCombo.setDisable(true);
        limitField.setText(String.valueOf(budget.getMonthlyLimit()));
        thresholdField.setText(String.valueOf((int) Math.round(budget.getAlertThresholdPct() * 100)));
    }

    public Budget buildBudget() throws ValidationException {
        errorLabel.setText("");
        Category category = categoryCombo.getValue();
        if (category == null) {
            throw new ValidationException("Please choose a category");
        }

        double limit;
        try {
            limit = Double.parseDouble(limitField.getText().trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Monthly limit must be a valid number");
        }

        double thresholdPct;
        try {
            thresholdPct = Double.parseDouble(thresholdField.getText().trim()) / 100.0;
        } catch (NumberFormatException e) {
            throw new ValidationException("Alert threshold must be a valid number");
        }

        return new Budget(editingId, category, limit, thresholdPct);
    }

    public void showError(String message) {
        errorLabel.setText(message);
    }
}
