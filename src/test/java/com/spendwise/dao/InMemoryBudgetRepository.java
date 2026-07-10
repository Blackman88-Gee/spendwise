package com.spendwise.dao;

import com.spendwise.model.Budget;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryBudgetRepository implements BudgetRepository {

    private final List<Budget> store = new ArrayList<>();

    @Override
    public Budget save(Budget budget) {
        store.removeIf(b -> b.getId() == budget.getId());
        store.add(budget);
        return budget;
    }

    @Override
    public void delete(int id) {
        store.removeIf(b -> b.getId() == id);
    }

    @Override
    public List<Budget> findAll() {
        return List.copyOf(store);
    }

    @Override
    public Optional<Budget> findByCategoryId(int categoryId) {
        return store.stream().filter(b -> b.getCategory().getId() == categoryId).findFirst();
    }
}
