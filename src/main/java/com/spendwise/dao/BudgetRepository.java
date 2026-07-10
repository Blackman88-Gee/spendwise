package com.spendwise.dao;

import com.spendwise.model.Budget;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository {

    Budget save(Budget budget);

    void delete(int id);

    List<Budget> findAll();

    Optional<Budget> findByCategoryId(int categoryId);
}
