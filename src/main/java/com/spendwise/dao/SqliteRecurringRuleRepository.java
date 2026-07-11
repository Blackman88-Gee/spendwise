package com.spendwise.dao;

import com.spendwise.exception.DataAccessException;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Category;
import com.spendwise.model.Currency;
import com.spendwise.model.Frequency;
import com.spendwise.model.RecurringRule;
import com.spendwise.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SqliteRecurringRuleRepository implements RecurringRuleRepository {

    private final CategoryRepository categoryRepository;

    public SqliteRecurringRuleRepository(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public RecurringRule save(RecurringRule rule) {
        String sql = rule.getId() == 0
                ? "INSERT INTO recurring_rules (type, amount, currency, description, category_id, frequency, next_due_date, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                : "UPDATE recurring_rules SET type = ?, amount = ?, currency = ?, description = ?, category_id = ?, frequency = ?, next_due_date = ?, active = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, rule.getType().name());
            ps.setDouble(2, rule.getAmount());
            ps.setString(3, rule.getCurrency().name());
            ps.setString(4, rule.getDescription());
            ps.setInt(5, rule.getCategory().getId());
            ps.setString(6, rule.getFrequency().name());
            ps.setString(7, rule.getNextDueDate().toString());
            ps.setInt(8, rule.isActive() ? 1 : 0);
            if (rule.getId() != 0) {
                ps.setInt(9, rule.getId());
            }
            ps.executeUpdate();

            if (rule.getId() == 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new RecurringRule(keys.getInt(1), rule.getType(), rule.getAmount(), rule.getCurrency(),
                                rule.getDescription(), rule.getCategory(), rule.getFrequency(), rule.getNextDueDate(),
                                rule.isActive());
                    }
                }
            }
            return rule;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save recurring rule", e);
        } catch (ValidationException e) {
            throw new DataAccessException("Recurring rule returned from database was invalid", e);
        }
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM recurring_rules WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete recurring rule", e);
        }
    }

    @Override
    public List<RecurringRule> findAll() {
        String sql = "SELECT id, type, amount, currency, description, category_id, frequency, next_due_date, active " +
                "FROM recurring_rules ORDER BY next_due_date";
        List<RecurringRule> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load recurring rules", e);
        }
    }

    @Override
    public List<RecurringRule> findDue(LocalDate asOf) {
        String sql = "SELECT id, type, amount, currency, description, category_id, frequency, next_due_date, active " +
                "FROM recurring_rules WHERE active = 1 AND next_due_date <= ?";
        List<RecurringRule> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, asOf.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load due recurring rules", e);
        }
    }

    private RecurringRule mapRow(ResultSet rs) throws SQLException {
        int categoryId = rs.getInt("category_id");
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DataAccessException("Recurring rule references missing category id " + categoryId, null));
        try {
            return new RecurringRule(
                    rs.getInt("id"),
                    TransactionType.valueOf(rs.getString("type")),
                    rs.getDouble("amount"),
                    Currency.valueOf(rs.getString("currency")),
                    rs.getString("description"),
                    category,
                    Frequency.valueOf(rs.getString("frequency")),
                    LocalDate.parse(rs.getString("next_due_date")),
                    rs.getInt("active") == 1
            );
        } catch (ValidationException e) {
            throw new DataAccessException("Recurring rule row in database was invalid", e);
        }
    }
}
