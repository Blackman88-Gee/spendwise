package com.spendwise.dao;

import com.spendwise.exception.DataAccessException;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Category;
import com.spendwise.model.Currency;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SqliteTransactionRepository implements TransactionRepository {

    private final CategoryRepository categoryRepository;

    public SqliteTransactionRepository(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        String sql = transaction.getId() == 0
                ? "INSERT INTO transactions (type, amount, currency, description, category_id, transaction_date, recurring_rule_id) VALUES (?, ?, ?, ?, ?, ?, ?)"
                : "UPDATE transactions SET type = ?, amount = ?, currency = ?, description = ?, category_id = ?, transaction_date = ?, recurring_rule_id = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, transaction.getType().name());
            ps.setDouble(2, transaction.getAmount());
            ps.setString(3, transaction.getCurrency().name());
            ps.setString(4, transaction.getDescription());
            ps.setInt(5, transaction.getCategory().getId());
            ps.setString(6, transaction.getDate().toString());
            if (transaction.getRecurringRuleId() != null) {
                ps.setInt(7, transaction.getRecurringRuleId());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }
            if (transaction.getId() != 0) {
                ps.setInt(8, transaction.getId());
            }
            ps.executeUpdate();

            if (transaction.getId() == 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return build(keys.getInt(1), transaction.getType(), transaction.getAmount(),
                                transaction.getCurrency(), transaction.getDescription(), transaction.getCategory(),
                                transaction.getDate(), transaction.getRecurringRuleId());
                    }
                }
            }
            return transaction;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save transaction", e);
        } catch (ValidationException e) {
            throw new DataAccessException("Transaction returned from database was invalid", e);
        }
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete transaction", e);
        }
    }

    @Override
    public List<Transaction> findAll() {
        return query("SELECT id, type, amount, currency, description, category_id, transaction_date, recurring_rule_id " +
                "FROM transactions ORDER BY transaction_date DESC, id DESC", ps -> {});
    }

    @Override
    public List<Transaction> findByDateRange(LocalDate start, LocalDate end) {
        return query("SELECT id, type, amount, currency, description, category_id, transaction_date, recurring_rule_id " +
                "FROM transactions WHERE transaction_date BETWEEN ? AND ? ORDER BY transaction_date DESC, id DESC",
                ps -> {
                    ps.setString(1, start.toString());
                    ps.setString(2, end.toString());
                });
    }

    @Override
    public List<Transaction> findByCategory(int categoryId) {
        return query("SELECT id, type, amount, currency, description, category_id, transaction_date, recurring_rule_id " +
                "FROM transactions WHERE category_id = ? ORDER BY transaction_date DESC, id DESC",
                ps -> ps.setInt(1, categoryId));
    }

    private interface StatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<Transaction> query(String sql, StatementBinder binder) {
        List<Transaction> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load transactions", e);
        }
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        int categoryId = rs.getInt("category_id");
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DataAccessException(
                        "Transaction references missing category id " + categoryId, null));

        int recurringRuleId = rs.getInt("recurring_rule_id");
        Integer ruleId = rs.wasNull() ? null : recurringRuleId;

        try {
            return build(
                    rs.getInt("id"),
                    TransactionType.valueOf(rs.getString("type")),
                    rs.getDouble("amount"),
                    Currency.valueOf(rs.getString("currency")),
                    rs.getString("description"),
                    category,
                    LocalDate.parse(rs.getString("transaction_date")),
                    ruleId
            );
        } catch (ValidationException e) {
            throw new DataAccessException("Transaction row in database was invalid", e);
        }
    }

    private Transaction build(int id, TransactionType type, double amount, Currency currency, String description,
                               Category category, LocalDate date, Integer recurringRuleId) throws ValidationException {
        return type == TransactionType.INCOME
                ? new Income(id, amount, currency, description, category, date, recurringRuleId)
                : new Expense(id, amount, currency, description, category, date, recurringRuleId);
    }
}
