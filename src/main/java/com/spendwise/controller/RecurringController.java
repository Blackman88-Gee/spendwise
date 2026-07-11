package com.spendwise.controller;

import com.spendwise.dao.CategoryRepository;
import com.spendwise.dao.RecurringRuleRepository;
import com.spendwise.dao.SqliteCategoryRepository;
import com.spendwise.dao.SqliteRecurringRuleRepository;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.RecurringRule;
import com.spendwise.model.TransactionType;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class RecurringController {

    @FXML private TableView<RecurringRule> table;
    @FXML private TableColumn<RecurringRule, String> typeColumn;
    @FXML private TableColumn<RecurringRule, String> categoryColumn;
    @FXML private TableColumn<RecurringRule, String> descriptionColumn;
    @FXML private TableColumn<RecurringRule, String> amountColumn;
    @FXML private TableColumn<RecurringRule, String> frequencyColumn;
    @FXML private TableColumn<RecurringRule, String> nextDueColumn;
    @FXML private TableColumn<RecurringRule, Boolean> activeColumn;
    @FXML private TableColumn<RecurringRule, Void> actionsColumn;

    private final CategoryRepository categoryRepository = new SqliteCategoryRepository();
    private final RecurringRuleRepository ruleRepository = new SqliteRecurringRuleRepository(categoryRepository);

    @FXML
    public void initialize() {
        setupColumns();
        refresh();
    }

    private void setupColumns() {
        typeColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getType() == TransactionType.INCOME ? "Income" : "Expense"));
        categoryColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().getName()));
        descriptionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));
        amountColumn.setCellValueFactory(data -> new SimpleStringProperty(String.format("$%.2f", data.getValue().getAmount())));
        frequencyColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFrequency().name()));
        nextDueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNextDueDate().toString()));

        activeColumn.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                RecurringRule rule = getTableView().getItems().get(getIndex());
                checkBox.setSelected(rule.isActive());
                checkBox.setOnAction(e -> {
                    rule.setActive(checkBox.isSelected());
                    ruleRepository.save(rule);
                });
                setGraphic(checkBox);
            }
        });
        activeColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleBooleanProperty(data.getValue().isActive()));

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");
            private final HBox box = new HBox(6, editButton, deleteButton);

            {
                editButton.setOnAction(e -> onEditRule(getTableView().getItems().get(getIndex())));
                deleteButton.setOnAction(e -> onDeleteRule(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void refresh() {
        List<RecurringRule> rules = ruleRepository.findAll();
        table.setItems(FXCollections.observableArrayList(rules));
    }

    @FXML
    public void onAddRule() {
        openRuleDialog(null);
    }

    private void onEditRule(RecurringRule rule) {
        openRuleDialog(rule);
    }

    private void onDeleteRule(RecurringRule rule) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the recurring rule \"" + rule.getDescription() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Delete Recurring Rule");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                ruleRepository.delete(rule.getId());
                refresh();
            }
        });
    }

    private void openRuleDialog(RecurringRule existing) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/spendwise/fxml/recurring_form.fxml"));
            Parent formRoot = loader.load();
            RecurringFormController formController = loader.getController();
            formController.setAvailableCategories(categoryRepository.findAll());
            if (existing != null) {
                formController.loadForEdit(existing);
            }

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(existing == null ? "Add Recurring Rule" : "Edit Recurring Rule");
            dialog.getDialogPane().setContent(formRoot);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            AtomicReference<RecurringRule> builtRule = new AtomicReference<>();
            Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                try {
                    builtRule.set(formController.buildRule());
                } catch (ValidationException ex) {
                    formController.showError(ex.getMessage());
                    event.consume();
                }
            });

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK && builtRule.get() != null) {
                ruleRepository.save(builtRule.get());
                refresh();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not open recurring rule form", e);
        }
    }
}
