package com.spendwise.controller;

import com.spendwise.model.Category;
import com.spendwise.model.Currency;
import com.spendwise.model.TransactionType;
import com.spendwise.service.ParsedExpense;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDate;

/** Mutable, JavaFX-property-backed row used only by the import review table. */
public class ImportRow {

    private final BooleanProperty include = new SimpleBooleanProperty();
    private final ObjectProperty<LocalDate> date = new SimpleObjectProperty<>();
    private final ObjectProperty<TransactionType> type = new SimpleObjectProperty<>();
    private final ObjectProperty<Category> category = new SimpleObjectProperty<>();
    private final StringProperty description = new SimpleStringProperty();
    private final DoubleProperty amount = new SimpleDoubleProperty();
    private final ObjectProperty<Currency> currency = new SimpleObjectProperty<>();
    private final StringProperty warning = new SimpleStringProperty();

    public ImportRow(ParsedExpense parsed) {
        include.set(parsed.amountFound());
        date.set(parsed.date());
        type.set(parsed.type());
        category.set(parsed.category());
        description.set(parsed.description());
        amount.set(parsed.amount());
        currency.set(parsed.currency());
        warning.set(parsed.warning() == null ? "" : parsed.warning());
    }

    public BooleanProperty includeProperty() {
        return include;
    }

    public ObjectProperty<LocalDate> dateProperty() {
        return date;
    }

    public ObjectProperty<TransactionType> typeProperty() {
        return type;
    }

    public ObjectProperty<Category> categoryProperty() {
        return category;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public DoubleProperty amountProperty() {
        return amount;
    }

    public ObjectProperty<Currency> currencyProperty() {
        return currency;
    }

    public StringProperty warningProperty() {
        return warning;
    }
}
