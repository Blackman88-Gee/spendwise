package com.spendwise.dao;

import com.spendwise.exception.DataAccessException;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Budget;
import com.spendwise.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteBudgetRepository implements BudgetRepository {

    private final CategoryRepository categoryRepository;

    public SqliteBudgetRepository(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Budget save(Budget budget) {
        String sql = budget.getId() == 0
                ? "INSERT INTO budgets (category_id, monthly_limit, alert_threshold_pct) VALUES (?, ?, ?)"
                : "UPDATE budgets SET category_id = ?, monthly_limit = ?, alert_threshold_pct = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, budget.getCategory().getId());
            ps.setDouble(2, budget.getMonthlyLimit());
            ps.setDouble(3, budget.getAlertThresholdPct());
            if (budget.getId() != 0) {
                ps.setInt(4, budget.getId());
            }
            ps.executeUpdate();

            if (budget.getId() == 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new Budget(keys.getInt(1), budget.getCategory(), budget.getMonthlyLimit(),
                                budget.getAlertThresholdPct());
                    }
                }
            }
            return budget;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save budget", e);
        } catch (ValidationException e) {
            throw new DataAccessException("Budget returned from database was invalid", e);
        }
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM budgets WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete budget", e);
        }
    }

    @Override
    public List<Budget> findAll() {
        String sql = "SELECT id, category_id, monthly_limit, alert_threshold_pct FROM budgets";
        List<Budget> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load budgets", e);
        }
    }

    @Override
    public Optional<Budget> findByCategoryId(int categoryId) {
        String sql = "SELECT id, category_id, monthly_limit, alert_threshold_pct FROM budgets WHERE category_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load budget", e);
        }
    }

    private Budget mapRow(ResultSet rs) throws SQLException {
        int categoryId = rs.getInt("category_id");
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DataAccessException("Budget references missing category id " + categoryId, null));
        try {
            return new Budget(rs.getInt("id"), category, rs.getDouble("monthly_limit"), rs.getDouble("alert_threshold_pct"));
        } catch (ValidationException e) {
            throw new DataAccessException("Budget row in database was invalid", e);
        }
    }
}
