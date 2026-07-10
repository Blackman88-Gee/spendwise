package com.spendwise.model;

import com.spendwise.exception.ValidationException;

import java.time.LocalDate;

public abstract class Transaction {

    private final int id;
    private double amount;
    private String description;
    private Category category;
    private LocalDate date;
    private final Integer recurringRuleId;

    protected Transaction(int id, double amount, String description, Category category,
                          LocalDate date, Integer recurringRuleId) throws ValidationException {
        validate(amount, description, category, date);
        this.id = id;
        this.amount = amount;
        this.description = description.trim();
        this.category = category;
        this.date = date;
        this.recurringRuleId = recurringRuleId;
    }

    private static void validate(double amount, String description, Category category, LocalDate date)
            throws ValidationException {
        if (amount <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (description == null || description.isBlank()) {
            throw new ValidationException("Description is required");
        }
        if (category == null) {
            throw new ValidationException("Category is required");
        }
        if (date == null) {
            throw new ValidationException("Date is required");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ValidationException("Date cannot be in the future");
        }
    }

    public abstract TransactionType getType();

    /** Positive for income, negative for expense — the polymorphic hook used by balance and chart calculations. */
    public abstract double signedAmount();

    public int getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) throws ValidationException {
        if (amount <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) throws ValidationException {
        if (description == null || description.isBlank()) {
            throw new ValidationException("Description is required");
        }
        this.description = description.trim();
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) throws ValidationException {
        if (category == null) {
            throw new ValidationException("Category is required");
        }
        this.category = category;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) throws ValidationException {
        if (date == null) {
            throw new ValidationException("Date is required");
        }
        this.date = date;
    }

    public Integer getRecurringRuleId() {
        return recurringRuleId;
    }
}
