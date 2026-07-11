package com.spendwise.controller;

import com.spendwise.dao.CategoryRepository;
import com.spendwise.dao.SqliteBudgetRepository;
import com.spendwise.dao.SqliteCategoryRepository;
import com.spendwise.dao.SqliteTransactionRepository;
import com.spendwise.dao.TransactionRepository;
import com.spendwise.exception.BudgetExceededException;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Category;
import com.spendwise.model.Currency;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;
import com.spendwise.service.BudgetService;
import com.spendwise.service.ImportService;
import com.spendwise.service.ParsedExpense;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ImportController {

    @FXML private TextArea noteInput;
    @FXML private Label summaryLabel;
    @FXML private TableView<ImportRow> reviewTable;
    @FXML private TableColumn<ImportRow, Boolean> includeColumn;
    @FXML private TableColumn<ImportRow, LocalDate> dateColumn;
    @FXML private TableColumn<ImportRow, TransactionType> typeColumn;
    @FXML private TableColumn<ImportRow, Category> categoryColumn;
    @FXML private TableColumn<ImportRow, String> descriptionColumn;
    @FXML private TableColumn<ImportRow, Double> amountColumn;
    @FXML private TableColumn<ImportRow, Currency> currencyColumn;
    @FXML private TableColumn<ImportRow, String> warningColumn;

    private final CategoryRepository categoryRepository = new SqliteCategoryRepository();
    private final TransactionRepository transactionRepository = new SqliteTransactionRepository(categoryRepository);
    private final BudgetService budgetService =
            new BudgetService(new SqliteBudgetRepository(categoryRepository), transactionRepository);
    private final ImportService importService = new ImportService();

    private List<Category> availableCategories = List.of();
    private boolean imported = false;

    @FXML
    public void initialize() {
        availableCategories = categoryRepository.findAll();
        setupColumns();
    }

    public boolean wasImported() {
        return imported;
    }

    private void setupColumns() {
        includeColumn.setCellValueFactory(data -> data.getValue().includeProperty());
        includeColumn.setCellFactory(CheckBoxTableCell.forTableColumn(includeColumn));

        dateColumn.setCellValueFactory(data -> data.getValue().dateProperty());
        dateColumn.setCellFactory(col -> new TableCell<ImportRow, LocalDate>() {
            private final DatePicker picker = new DatePicker();

            {
                picker.setPrefWidth(115);
                picker.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null && getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        getTableView().getItems().get(getIndex()).dateProperty().set(newVal);
                    }
                });
            }

            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    picker.setValue(item);
                    setGraphic(picker);
                }
            }
        });

        typeColumn.setCellValueFactory(data -> data.getValue().typeProperty());

        categoryColumn.setCellValueFactory(data -> data.getValue().categoryProperty());
        categoryColumn.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<>() {
            @Override
            public String toString(Category category) {
                return category == null ? "" : category.getName();
            }

            @Override
            public Category fromString(String string) {
                return availableCategories.stream().filter(c -> c.getName().equals(string)).findFirst().orElse(null);
            }
        }, FXCollections.observableArrayList(availableCategories)));
        categoryColumn.setOnEditCommit(event -> {
            ImportRow row = event.getRowValue();
            Category newCategory = event.getNewValue();
            row.categoryProperty().set(newCategory);
            row.typeProperty().set(newCategory.getType());
        });

        descriptionColumn.setCellValueFactory(data -> data.getValue().descriptionProperty());
        descriptionColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        descriptionColumn.setOnEditCommit(event -> event.getRowValue().descriptionProperty().set(event.getNewValue()));

        amountColumn.setCellValueFactory(data -> data.getValue().amountProperty().asObject());
        amountColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        amountColumn.setOnEditCommit(event -> event.getRowValue().amountProperty().set(event.getNewValue()));

        currencyColumn.setCellValueFactory(data -> data.getValue().currencyProperty());
        currencyColumn.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<>() {
            @Override
            public String toString(Currency currency) {
                return currency == null ? "" : currency.name();
            }

            @Override
            public Currency fromString(String string) {
                return Currency.valueOf(string);
            }
        }, FXCollections.observableArrayList(Currency.GHS, Currency.USD)));
        currencyColumn.setOnEditCommit(event -> event.getRowValue().currencyProperty().set(event.getNewValue()));

        warningColumn.setCellValueFactory(data -> data.getValue().warningProperty());
        warningColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    getStyleClass().remove("form-warning");
                } else {
                    setText(item);
                    if (!getStyleClass().contains("form-warning")) {
                        getStyleClass().add("form-warning");
                    }
                }
            }
        });
    }

    @FXML
    public void onParse() {
        List<ParsedExpense> parsed = importService.parseNote(noteInput.getText(), availableCategories);
        List<ImportRow> rows = parsed.stream().map(ImportRow::new).toList();
        reviewTable.setItems(FXCollections.observableArrayList(rows));

        long needsAttention = parsed.stream().filter(p -> p.warning() != null).count();
        summaryLabel.setText(parsed.isEmpty()
                ? "No lines recognized — check the format and try again."
                : String.format("%d row(s) parsed, %d need attention.", parsed.size(), needsAttention));
    }

    @FXML
    public void onCancel() {
        closeWindow();
    }

    @FXML
    public void onImport() {
        List<ImportRow> selected = reviewTable.getItems().stream()
                .filter(row -> row.includeProperty().get())
                .toList();

        int savedCount = 0;
        List<String> errors = new ArrayList<>();
        List<String> budgetNotes = new ArrayList<>();

        for (ImportRow row : selected) {
            try {
                Transaction transaction = row.typeProperty().get() == TransactionType.INCOME
                        ? new Income(0, row.amountProperty().get(), row.currencyProperty().get(), row.descriptionProperty().get(),
                                row.categoryProperty().get(), row.dateProperty().get(), null)
                        : new Expense(0, row.amountProperty().get(), row.currencyProperty().get(), row.descriptionProperty().get(),
                                row.categoryProperty().get(), row.dateProperty().get(), null);

                if (transaction instanceof Expense expense) {
                    try {
                        budgetService.validateAgainstBudget(expense);
                    } catch (BudgetExceededException budgetEx) {
                        budgetNotes.add(budgetEx.getMessage());
                    }
                }

                transactionRepository.save(transaction);
                savedCount++;
            } catch (ValidationException e) {
                errors.add(row.descriptionProperty().get() + ": " + e.getMessage());
            }
        }

        imported = savedCount > 0;

        StringBuilder message = new StringBuilder(String.format("Imported %d of %d selected row(s).", savedCount, selected.size()));
        if (!errors.isEmpty()) {
            message.append("\n\nSkipped due to validation errors:\n").append(String.join("\n", errors));
        }
        if (!budgetNotes.isEmpty()) {
            message.append("\n\nBudget notes:\n").append(String.join("\n", budgetNotes));
        }

        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, message.toString());
        alert.setHeaderText("Import Complete");
        alert.showAndWait();

        if (savedCount > 0) {
            closeWindow();
        }
    }

    private void closeWindow() {
        Stage stage = (Stage) reviewTable.getScene().getWindow();
        stage.close();
    }
}
