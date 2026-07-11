package com.spendwise.controller;

import com.spendwise.exception.ValidationException;
import com.spendwise.model.Category;
import com.spendwise.model.Currency;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;

public class TransactionFormController {

    @FXML private ComboBox<TransactionType> typeCombo;
    @FXML private ComboBox<Category> categoryCombo;
    @FXML private TextField amountField;
    @FXML private ComboBox<Currency> currencyCombo;
    @FXML private TextField descriptionField;
    @FXML private DatePicker dateField;
    @FXML private Label errorLabel;

    private List<Category> allCategories = List.of();
    private int editingId = 0;

    @FXML
    public void initialize() {
        typeCombo.setItems(FXCollections.observableArrayList(TransactionType.INCOME, TransactionType.EXPENSE));
        typeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TransactionType type) {
                return type == null ? "" : (type == TransactionType.INCOME ? "Income" : "Expense");
            }

            @Override
            public TransactionType fromString(String string) {
                return TransactionType.valueOf(string.toUpperCase());
            }
        });
        typeCombo.setValue(TransactionType.EXPENSE);
        typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> refreshCategoryOptions());

        currencyCombo.setItems(FXCollections.observableArrayList(Currency.GHS, Currency.USD));
        currencyCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Currency currency) {
                return currency == null ? "" : currency.name() + " (" + currency.getSymbol() + ")";
            }

            @Override
            public Currency fromString(String string) {
                return Currency.valueOf(string.split(" ")[0]);
            }
        });
        currencyCombo.setValue(Currency.GHS);

        dateField.setValue(LocalDate.now());
        dateField.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });
    }

    public void setAvailableCategories(List<Category> categories) {
        this.allCategories = categories;
        refreshCategoryOptions();
    }

    private void refreshCategoryOptions() {
        TransactionType type = typeCombo.getValue();
        Category previouslySelected = categoryCombo.getValue();
        categoryCombo.setItems(FXCollections.observableArrayList(
                allCategories.stream().filter(c -> c.getType() == type).toList()));
        if (previouslySelected != null && previouslySelected.getType() == type) {
            categoryCombo.setValue(previouslySelected);
        }
    }

    public void loadForEdit(Transaction transaction) {
        editingId = transaction.getId();
        typeCombo.setValue(transaction.getType());
        refreshCategoryOptions();
        categoryCombo.setValue(transaction.getCategory());
        amountField.setText(String.valueOf(transaction.getAmount()));
        currencyCombo.setValue(transaction.getCurrency());
        descriptionField.setText(transaction.getDescription());
        dateField.setValue(transaction.getDate());
    }

    public Transaction buildTransaction() throws ValidationException {
        errorLabel.setText("");
        TransactionType type = typeCombo.getValue();
        Category category = categoryCombo.getValue();
        if (category == null) {
            throw new ValidationException("Please choose a category");
        }

        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Amount must be a valid number");
        }

        String description = descriptionField.getText();
        LocalDate date = dateField.getValue();
        Currency currency = currencyCombo.getValue();

        return type == TransactionType.INCOME
                ? new Income(editingId, amount, currency, description, category, date, null)
                : new Expense(editingId, amount, currency, description, category, date, null);
    }

    public void showError(String message) {
        errorLabel.setText(message);
    }
}
