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
                    currency TEXT NOT NULL DEFAULT 'GHS' CHECK (currency IN ('GHS', 'USD')),
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
                    currency TEXT NOT NULL DEFAULT 'GHS' CHECK (currency IN ('GHS', 'USD')),
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
            migrateAddCurrencyColumn(conn, "transactions");
            migrateAddCurrencyColumn(conn, "recurring_rules");
            seedDefaultCategories(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to initialize database schema", e);
        }
    }

    /** Existing installs created before multi-currency support won't have this column yet. */
    private static void migrateAddCurrencyColumn(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if ("currency".equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN currency TEXT NOT NULL DEFAULT 'GHS'");
        }
    }

    private static void seedDefaultCategories(Connection conn) throws SQLException {
        Object[][] defaults = {
                {"Salary", "INCOME", "#0F766E"},
                {"Freelance", "INCOME", "#4F46E5"},
                {"Groceries", "EXPENSE", "#D97706"},
                {"Dining Out", "EXPENSE", "#DB2777"},
                {"Transport", "EXPENSE", "#0284C7"},
                {"Rent", "EXPENSE", "#9333EA"},
                {"Utilities", "EXPENSE", "#0891B2"},
                {"Entertainment", "EXPENSE", "#E11D48"},
                {"Health", "EXPENSE", "#65A30D"},
                {"Shopping", "EXPENSE", "#78716C"},
                {"Uncategorized", "EXPENSE", "#9CA3AF"},
                {"Uncategorized Income", "INCOME", "#9CA3AF"},
        };

        String checkSql = "SELECT COUNT(*) FROM categories WHERE name = ?";
        String insertSql = "INSERT INTO categories (name, type, color) VALUES (?, ?, ?)";
        try (var check = conn.prepareStatement(checkSql); var insert = conn.prepareStatement(insertSql)) {
            for (Object[] row : defaults) {
                check.setString(1, (String) row[0]);
                boolean exists;
                try (var rs = check.executeQuery()) {
                    rs.next();
                    exists = rs.getInt(1) > 0;
                }
                if (exists) {
                    continue;
                }
                insert.setString(1, (String) row[0]);
                insert.setString(2, (String) row[1]);
                insert.setString(3, (String) row[2]);
                insert.executeUpdate();
            }
        }
    }
}
