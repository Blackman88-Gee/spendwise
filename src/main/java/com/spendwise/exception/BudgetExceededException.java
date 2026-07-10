package com.spendwise.exception;

public class BudgetExceededException extends Exception {

    private final double overspendAmount;

    public BudgetExceededException(String message, double overspendAmount) {
        super(message);
        this.overspendAmount = overspendAmount;
    }

    public double getOverspendAmount() {
        return overspendAmount;
    }
}
