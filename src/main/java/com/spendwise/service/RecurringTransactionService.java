package com.spendwise.service;

import com.spendwise.dao.RecurringRuleRepository;
import com.spendwise.dao.TransactionRepository;
import com.spendwise.exception.DataAccessException;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.RecurringRule;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RecurringTransactionService {

    private static final int MAX_CATCHUP_OCCURRENCES = 366;

    private final RecurringRuleRepository ruleRepository;
    private final TransactionRepository transactionRepository;

    public RecurringTransactionService(RecurringRuleRepository ruleRepository, TransactionRepository transactionRepository) {
        this.ruleRepository = ruleRepository;
        this.transactionRepository = transactionRepository;
    }

    /** Runs on app startup: turns any recurring rules that have come due (possibly several times) into real transactions. */
    public List<Transaction> generateDueTransactions() {
        List<Transaction> generated = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (RecurringRule rule : ruleRepository.findDue(today)) {
            int safetyCounter = 0;
            while (!rule.getNextDueDate().isAfter(today) && safetyCounter < MAX_CATCHUP_OCCURRENCES) {
                Transaction transaction = transactionRepository.save(createTransactionFromRule(rule));
                generated.add(transaction);
                rule.setNextDueDate(rule.computeNextDueDate());
                safetyCounter++;
            }
            ruleRepository.save(rule);
        }
        return generated;
    }

    private Transaction createTransactionFromRule(RecurringRule rule) {
        try {
            return rule.getType() == TransactionType.INCOME
                    ? new Income(0, rule.getAmount(), rule.getCurrency(), rule.getDescription(), rule.getCategory(), rule.getNextDueDate(), rule.getId())
                    : new Expense(0, rule.getAmount(), rule.getCurrency(), rule.getDescription(), rule.getCategory(), rule.getNextDueDate(), rule.getId());
        } catch (ValidationException e) {
            throw new DataAccessException("Recurring rule '" + rule.getDescription() + "' produced an invalid transaction", e);
        }
    }
}
