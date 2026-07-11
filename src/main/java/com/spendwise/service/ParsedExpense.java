package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.Currency;
import com.spendwise.model.TransactionType;

import java.time.LocalDate;

public record ParsedExpense(LocalDate date, TransactionType type, String description, double amount,
                             Currency currency, Category category, boolean amountFound, String warning) {
}
