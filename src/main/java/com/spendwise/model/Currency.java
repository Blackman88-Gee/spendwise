package com.spendwise.model;

public enum Currency {
    GHS("GH₵"),
    USD("$");

    private final String symbol;

    Currency(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public String format(double amount) {
        return String.format("%s%.2f", symbol, amount);
    }
}
