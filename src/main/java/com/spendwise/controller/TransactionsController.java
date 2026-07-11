package com.spendwise.controller;

import com.spendwise.dao.CategoryRepository;
import com.spendwise.dao.SqliteBudgetRepository;
import com.spendwise.dao.SqliteCategoryRepository;
import com.spendwise.dao.SqliteTransactionRepository;
import com.spendwise.dao.TransactionRepository;
import com.spendwise.exception.BudgetExceededException;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;
import com.spendwise.service.BudgetService;
import com.spendwise.service.ExportService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class TransactionsController {

    @FXML private ComboBox<Category> filterCategoryCombo;
    @FXML private DatePicker filterStartDate;
    @FXML private DatePicker filterEndDate;
    @FXML private TextField searchField;
    @FXML private TableView<Transaction> table;
    @FXML private TableColumn<Transaction, String> dateColumn;
    @FXML private TableColumn<Transaction, String> typeColumn;
    @FXML private TableColumn<Transaction, String> categoryColumn;
    @FXML private TableColumn<Transaction, String> descriptionColumn;
    @FXML private TableColumn<Transaction, String> amountColumn;
    @FXML private TableColumn<Transaction, Void> actionsColumn;
    @FXML private Label summaryLabel;

    private final CategoryRepository categoryRepository = new SqliteCategoryRepository();
    private final TransactionRepository transactionRepository = new SqliteTransactionRepository(categoryRepository);
    private final BudgetService budgetService =
            new BudgetService(new SqliteBudgetRepository(categoryRepository), transactionRepository);
    private final ExportService exportService = new ExportService();

    private List<Transaction> lastFiltered = List.of();

    @FXML
    public void initialize() {
        setupColumns();
        setupFilterCategoryCombo();
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshTable());
        filterCategoryCombo.valueProperty().addListener((obs, oldVal, newVal) -> refreshTable());
        filterStartDate.valueProperty().addListener((obs, oldVal, newVal) -> refreshTable());
        filterEndDate.valueProperty().addListener((obs, oldVal, newVal) -> refreshTable());
        refreshTable();
    }

    private void setupFilterCategoryCombo() {
        List<Category> categories = categoryRepository.findAll();
        filterCategoryCombo.setItems(FXCollections.observableArrayList(categories));
        filterCategoryCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Category category) {
                return category == null ? "All Categories" : category.getName();
            }

            @Override
            public Category fromString(String string) {
                return null;
            }
        });
    }

    private void setupColumns() {
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDate().toString()));
        typeColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getType() == TransactionType.INCOME ? "Income" : "Expense"));
        categoryColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().getName()));
        descriptionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));
        amountColumn.setCellValueFactory(data -> new SimpleStringProperty(
                String.format("%s$%.2f", data.getValue().getType() == TransactionType.EXPENSE ? "-" : "+",
                        data.getValue().getAmount())));

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");
            private final HBox box = new HBox(6, editButton, deleteButton);

            {
                editButton.setOnAction(e -> onEditTransaction(getTableView().getItems().get(getIndex())));
                deleteButton.setOnAction(e -> onDeleteTransaction(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void refreshTable() {
        List<Transaction> all = transactionRepository.findAll();
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        Category categoryFilter = filterCategoryCombo.getValue();
        LocalDate start = filterStartDate.getValue();
        LocalDate end = filterEndDate.getValue();

        List<Transaction> filtered = all.stream()
                .filter(t -> categoryFilter == null || t.getCategory().equals(categoryFilter))
                .filter(t -> start == null || !t.getDate().isBefore(start))
                .filter(t -> end == null || !t.getDate().isAfter(end))
                .filter(t -> search.isBlank() || t.getDescription().toLowerCase().contains(search))
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .toList();

        table.setItems(FXCollections.observableArrayList(filtered));
        updateSummary(filtered);
        lastFiltered = filtered;
    }

    private void updateSummary(List<Transaction> transactions) {
        double net = transactions.stream().mapToDouble(Transaction::signedAmount).sum();
        summaryLabel.setText(String.format("%d transactions — net %s$%.2f",
                transactions.size(), net < 0 ? "-" : "+", Math.abs(net)));
    }

    @FXML
    public void onResetFilters() {
        filterCategoryCombo.setValue(null);
        filterStartDate.setValue(null);
        filterEndDate.setValue(null);
        searchField.clear();
        refreshTable();
    }

    @FXML
    public void onAddTransaction() {
        openTransactionDialog(null);
    }

    @FXML
    public void onImportFromNotes() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/spendwise/fxml/import_dialog.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Import Expenses from Notes");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(table.getScene().getWindow());
            Scene scene = new Scene(root, 900, 620);
            scene.getStylesheets().add(getClass().getResource("/com/spendwise/css/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();

            refreshTable();
        } catch (IOException e) {
            throw new IllegalStateException("Could not open import dialog", e);
        }
    }

    @FXML
    public void onExportCsv() {
        if (lastFiltered.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "There are no transactions to export for the current filters.");
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Transactions to CSV");
        fileChooser.setInitialFileName("spendwise-transactions-" + LocalDate.now() + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        Window window = table.getScene().getWindow();
        java.io.File file = fileChooser.showSaveDialog(window);
        if (file == null) {
            return;
        }

        try {
            exportService.exportTransactionsToCsv(lastFiltered, file.toPath());
            Alert done = new Alert(Alert.AlertType.INFORMATION, "Exported " + lastFiltered.size() + " transactions to " + file.getName());
            done.setHeaderText(null);
            done.showAndWait();
        } catch (IOException e) {
            Alert error = new Alert(Alert.AlertType.ERROR, "Failed to export CSV: " + e.getMessage());
            error.setHeaderText("Export Failed");
            error.showAndWait();
        }
    }

    private void onEditTransaction(Transaction transaction) {
        openTransactionDialog(transaction);
    }

    private void onDeleteTransaction(Transaction transaction) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + transaction.getDescription() + "\"? This cannot be undone.", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Delete Transaction");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                transactionRepository.delete(transaction.getId());
                refreshTable();
            }
        });
    }

    private void openTransactionDialog(Transaction existing) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/spendwise/fxml/transaction_form.fxml"));
            Parent formRoot = loader.load();
            TransactionFormController formController = loader.getController();
            formController.setAvailableCategories(categoryRepository.findAll());
            if (existing != null) {
                formController.loadForEdit(existing);
            }

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(existing == null ? "Add Transaction" : "Edit Transaction");
            dialog.getDialogPane().setContent(formRoot);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            AtomicReference<Transaction> builtTransaction = new AtomicReference<>();
            Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                try {
                    Transaction candidate = formController.buildTransaction();
                    if (candidate instanceof Expense expense) {
                        try {
                            budgetService.validateAgainstBudget(expense);
                        } catch (BudgetExceededException budgetEx) {
                            Alert warn = new Alert(Alert.AlertType.CONFIRMATION, budgetEx.getMessage() + "\n\nSave anyway?",
                                    ButtonType.YES, ButtonType.NO);
                            warn.setHeaderText("Budget Alert");
                            Optional<ButtonType> choice = warn.showAndWait();
                            if (choice.isEmpty() || choice.get() != ButtonType.YES) {
                                event.consume();
                                return;
                            }
                        }
                    }
                    builtTransaction.set(candidate);
                } catch (ValidationException ex) {
                    formController.showError(ex.getMessage());
                    event.consume();
                }
            });

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK && builtTransaction.get() != null) {
                transactionRepository.save(builtTransaction.get());
                refreshTable();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not open transaction form", e);
        }
    }
}
