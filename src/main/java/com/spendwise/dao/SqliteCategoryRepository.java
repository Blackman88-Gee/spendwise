package com.spendwise.dao;

import com.spendwise.exception.DataAccessException;
import com.spendwise.exception.ValidationException;
import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteCategoryRepository implements CategoryRepository {

    @Override
    public Category save(Category category) {
        String sql = category.getId() == 0
                ? "INSERT INTO categories (name, type, color) VALUES (?, ?, ?)"
                : "UPDATE categories SET name = ?, type = ?, color = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getType().name());
            ps.setString(3, category.getColor());
            if (category.getId() != 0) {
                ps.setInt(4, category.getId());
            }
            ps.executeUpdate();

            if (category.getId() == 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new Category(keys.getInt(1), category.getName(), category.getType(), category.getColor());
                    }
                }
            }
            return category;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save category", e);
        } catch (ValidationException e) {
            throw new DataAccessException("Category returned from database was invalid", e);
        }
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete category", e);
        }
    }

    @Override
    public List<Category> findAll() {
        String sql = "SELECT id, name, type, color FROM categories ORDER BY type, name";
        List<Category> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load categories", e);
        }
    }

    @Override
    public Optional<Category> findById(int id) {
        String sql = "SELECT id, name, type, color FROM categories WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load category", e);
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        try {
            return new Category(
                    rs.getInt("id"),
                    rs.getString("name"),
                    TransactionType.valueOf(rs.getString("type")),
                    rs.getString("color")
            );
        } catch (ValidationException e) {
            throw new DataAccessException("Category row in database was invalid", e);
        }
    }
}
