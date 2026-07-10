package com.spendwise.dao;

import com.spendwise.exception.DataAccessException;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InMemoryTransactionRepository implements TransactionRepository {

    private final List<Transaction> store = new ArrayList<>();
    private int nextId = 1;

    @Override
    public Transaction save(Transaction transaction) {
        if (transaction.getId() == 0) {
            Transaction persisted = withId(transaction, nextId++);
            store.add(persisted);
            return persisted;
        }
        store.removeIf(t -> t.getId() == transaction.getId());
        store.add(transaction);
        return transaction;
    }

    private Transaction withId(Transaction transaction, int id) {
        try {
            return transaction.getType() == TransactionType.INCOME
                    ? new Income(id, transaction.getAmount(), transaction.getDescription(), transaction.getCategory(),
                            transaction.getDate(), transaction.getRecurringRuleId())
                    : new Expense(id, transaction.getAmount(), transaction.getDescription(), transaction.getCategory(),
                            transaction.getDate(), transaction.getRecurringRuleId());
        } catch (ValidationException e) {
            throw new DataAccessException("Failed to assign id to in-memory transaction", e);
        }
    }

    @Override
    public void delete(int id) {
        store.removeIf(t -> t.getId() == id);
    }

    @Override
    public List<Transaction> findAll() {
        return List.copyOf(store);
    }

    @Override
    public List<Transaction> findByDateRange(LocalDate start, LocalDate end) {
        return store.stream()
                .filter(t -> !t.getDate().isBefore(start) && !t.getDate().isAfter(end))
                .toList();
    }

    @Override
    public List<Transaction> findByCategory(int categoryId) {
        return store.stream().filter(t -> t.getCategory().getId() == categoryId).toList();
    }
}
