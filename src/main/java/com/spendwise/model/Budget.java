package com.spendwise.model;

import com.spendwise.exception.ValidationException;

public class Budget {

    private final int id;
    private Category category;
    private double monthlyLimit;
    private double alertThresholdPct;

    public Budget(int id, Category category, double monthlyLimit, double alertThresholdPct) throws ValidationException {
        if (category == null) {
            throw new ValidationException("Category is required");
        }
        if (category.getType() != TransactionType.EXPENSE) {
            throw new ValidationException("Budgets can only be set on expense categories");
        }
        if (monthlyLimit <= 0) {
            throw new ValidationException("Monthly limit must be greater than zero");
        }
        if (alertThresholdPct <= 0 || alertThresholdPct > 1) {
            throw new ValidationException("Alert threshold must be between 0 and 1");
        }
        this.id = id;
        this.category = category;
        this.monthlyLimit = monthlyLimit;
        this.alertThresholdPct = alertThresholdPct;
    }

    public int getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) throws ValidationException {
        if (category == null || category.getType() != TransactionType.EXPENSE) {
            throw new ValidationException("Budgets can only be set on expense categories");
        }
        this.category = category;
    }

    public double getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(double monthlyLimit) throws ValidationException {
        if (monthlyLimit <= 0) {
            throw new ValidationException("Monthly limit must be greater than zero");
        }
        this.monthlyLimit = monthlyLimit;
    }

    public double getAlertThresholdPct() {
        return alertThresholdPct;
    }

    public void setAlertThresholdPct(double alertThresholdPct) throws ValidationException {
        if (alertThresholdPct <= 0 || alertThresholdPct > 1) {
            throw new ValidationException("Alert threshold must be between 0 and 1");
        }
        this.alertThresholdPct = alertThresholdPct;
    }

    public double usagePercent(double spentSoFar) {
        return monthlyLimit == 0 ? 0 : spentSoFar / monthlyLimit;
    }

    public double remaining(double spentSoFar) {
        return monthlyLimit - spentSoFar;
    }

    public boolean isNearLimit(double spentSoFar) {
        double usage = usagePercent(spentSoFar);
        return usage >= alertThresholdPct && usage < 1.0;
    }

    public boolean isOverBudget(double spentSoFar) {
        return usagePercent(spentSoFar) >= 1.0;
    }
}
