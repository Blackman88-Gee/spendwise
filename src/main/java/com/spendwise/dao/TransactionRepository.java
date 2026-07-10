package com.spendwise.dao;

import com.spendwise.model.Transaction;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    void delete(int id);

    List<Transaction> findAll();

    List<Transaction> findByDateRange(LocalDate start, LocalDate end);

    List<Transaction> findByCategory(int categoryId);
}
