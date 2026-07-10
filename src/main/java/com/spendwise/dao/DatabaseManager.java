package com.spendwise.dao;

import com.spendwise.exception.DataAccessException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private static final String DB_FILE = "spendwise.db";
    private static final String URL = "jdbc:sqlite:" + Path.of(DB_FILE).toAbsolutePath();

    private DatabaseManager() {
    }

    public static Connection getConnection() {
        try {
            Connection connection = DriverManager.getConnection(URL);
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
            }
            return connection;
        } catch (SQLException e) {
            throw new DataAccessException("Could not connect to the database", e);
        }
    }

    public static void initSchema() {
        String categories = """
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
                    color TEXT NOT NULL DEFAULT '#4A90D9'
                )""";

        String transactions = """
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
                    amount REAL NOT NULL CHECK (amount > 0),
                    description TEXT NOT NULL,
                    category_id INTEGER NOT NULL,
                    transaction_date TEXT NOT NULL,
                    recurring_rule_id INTEGER,
                    FOREIGN KEY (category_id) REFERENCES categories(id),
                    FOREIGN KEY (recurring_rule_id) REFERENCES recurring_rules(id)
                )""";

        String budgets = """
                CREATE TABLE IF NOT EXISTS budgets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category_id INTEGER NOT NULL UNIQUE,
                    monthly_limit REAL NOT NULL CHECK (monthly_limit > 0),
                    alert_threshold_pct REAL NOT NULL DEFAULT 0.8,
                    FOREIGN KEY (category_id) REFERENCES categories(id)
                )""";

        String recurringRules = """
                CREATE TABLE IF NOT EXISTS recurring_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
                    amount REAL NOT NULL CHECK (amount > 0),
                    description TEXT NOT NULL,
                    category_id INTEGER NOT NULL,
                    frequency TEXT NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
                    next_due_date TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (category_id) REFERENCES categories(id)
                )""";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(categories);
            stmt.execute(transactions);
            stmt.execute(budgets);
            stmt.execute(recurringRules);
            seedDefaultCategories(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to initialize database schema", e);
        }
    }

    private static void seedDefaultCategories(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM categories";
        try (Statement check = conn.createStatement()) {
            var rs = check.executeQuery(checkSql);
            rs.next();
            if (rs.getInt(1) > 0) {
                return;
            }
        }

        String insertSql = "INSERT INTO categories (name, type, color) VALUES (?, ?, ?)";
        Object[][] defaults = {
                {"Salary", "INCOME", "#2E7D32"},
                {"Freelance", "INCOME", "#43A047"},
                {"Groceries", "EXPENSE", "#EF6C00"},
                {"Dining Out", "EXPENSE", "#D84315"},
                {"Transport", "EXPENSE", "#6D4C41"},
                {"Rent", "EXPENSE", "#8E24AA"},
                {"Utilities", "EXPENSE", "#3949AB"},
                {"Entertainment", "EXPENSE", "#C2185B"},
                {"Health", "EXPENSE", "#00897B"},
                {"Shopping", "EXPENSE", "#F4511E"},
        };

        try (var ps = conn.prepareStatement(insertSql)) {
            for (Object[] row : defaults) {
                ps.setString(1, (String) row[0]);
                ps.setString(2, (String) row[1]);
                ps.setString(3, (String) row[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
