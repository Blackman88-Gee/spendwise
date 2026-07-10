package com.spendwise.model;

import com.spendwise.exception.ValidationException;

public class Category {

    private final int id;
    private String name;
    private final TransactionType type;
    private String color;

    public Category(int id, String name, TransactionType type, String color) throws ValidationException {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Category name is required");
        }
        if (type == null) {
            throw new ValidationException("Category type is required");
        }
        this.id = id;
        this.name = name.trim();
        this.type = type;
        this.color = (color == null || color.isBlank()) ? "#4A90D9" : color;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) throws ValidationException {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Category name is required");
        }
        this.name = name.trim();
    }

    public TransactionType getType() {
        return type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Category other && other.id == this.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
