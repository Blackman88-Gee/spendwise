package com.spendwise.dao;

import com.spendwise.model.RecurringRule;

import java.time.LocalDate;
import java.util.List;

public interface RecurringRuleRepository {

    RecurringRule save(RecurringRule rule);

    void delete(int id);

    List<RecurringRule> findAll();

    List<RecurringRule> findDue(LocalDate asOf);
}
