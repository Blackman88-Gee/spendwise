package com.spendwise.model;

import com.spendwise.exception.ValidationException;

import java.time.LocalDate;

public class RecurringRule {

    private final int id;
    private TransactionType type;
    private double amount;
    private Currency currency;
    private String description;
    private Category category;
    private Frequency frequency;
    private LocalDate nextDueDate;
    private boolean active;

    public RecurringRule(int id, TransactionType type, double amount, Currency currency, String description,
                          Category category, Frequency frequency, LocalDate nextDueDate, boolean active)
            throws ValidationException {
        if (type == null) {
            throw new ValidationException("Type is required");
        }
        if (amount <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (description == null || description.isBlank()) {
            throw new ValidationException("Description is required");
        }
        if (category == null || category.getType() != type) {
            throw new ValidationException("Category must match the recurring rule's type");
        }
        if (frequency == null) {
            throw new ValidationException("Frequency is required");
        }
        if (nextDueDate == null) {
            throw new ValidationException("Next due date is required");
        }
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.currency = currency == null ? Currency.GHS : currency;
        this.description = description.trim();
        this.category = category;
        this.frequency = frequency;
        this.nextDueDate = nextDueDate;
        this.active = active;
    }

    public RecurringRule(int id, TransactionType type, double amount, String description, Category category,
                          Frequency frequency, LocalDate nextDueDate, boolean active) throws ValidationException {
        this(id, type, amount, Currency.GHS, description, category, frequency, nextDueDate, active);
    }

    public LocalDate computeNextDueDate() {
        return switch (frequency) {
            case DAILY -> nextDueDate.plusDays(1);
            case WEEKLY -> nextDueDate.plusWeeks(1);
            case MONTHLY -> nextDueDate.plusMonths(1);
        };
    }

    public int getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
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

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency == null ? Currency.GHS : currency;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(LocalDate nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
