package com.spendwise.model;

import com.spendwise.exception.ValidationException;

import java.time.LocalDate;

public class Expense extends Transaction {

    public Expense(int id, double amount, Currency currency, String description, Category category,
                   LocalDate date, Integer recurringRuleId) throws ValidationException {
        super(id, amount, currency, description, category, date, recurringRuleId);
        if (category.getType() != TransactionType.EXPENSE) {
            throw new ValidationException("Category '" + category.getName() + "' is not an expense category");
        }
    }

    public Expense(int id, double amount, String description, Category category,
                   LocalDate date, Integer recurringRuleId) throws ValidationException {
        this(id, amount, Currency.GHS, description, category, date, recurringRuleId);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.EXPENSE;
    }

    @Override
    public double signedAmount() {
        return -getAmount();
    }
}
