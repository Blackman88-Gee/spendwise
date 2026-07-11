# SpendWise — Expense Tracking & Budgeting System

A JavaFX desktop application for tracking income and expenses, setting category budgets with real-time alerts, automating recurring bills/subscriptions, and surfacing plain-language "spending harm" insights instead of raw numbers.

Built for **INF811D: Object-Oriented Programming** (MSc Information Technology, University of Cape Coast, College of Distance Education).

## Features

- **Transactions** — add, edit, delete, filter (category/date/search), and CSV export
- **Import from Notes** — paste a free-form expense note (a date followed by that day's expenses) and get an editable review table before anything is saved, with keyword-based category guessing (e.g. "uber" → Transport, "jollof" → Dining Out)
- **Multi-Currency (GHS/USD)** — every transaction carries its own currency; the Dashboard highlights whichever currency has more activity, while Budgets/Insights/Analytics stay GHS-focused
- **Budgets** — per-category monthly limits with color-coded progress bars and an in-app warning before you save an expense that would blow a budget
- **Recurring Transactions** — subscriptions/bills that auto-generate real transactions every time the app starts
- **Analytics & Insights** — spending-by-category pie chart, 6-month income/expense trend, and a spending-harm insight engine that flags things like "you spent more than you earned" or "this category is up 30% from last month"
- **Dashboard** — balance, this month's income/expense, recent activity

## Tech Stack

- Java 21, JavaFX 21, built with Maven (`javafx-maven-plugin`)
- SQLite via JDBC (`org.xerial:sqlite-jdbc`) — embedded, no server required
- FXML + Controller pattern, JUnit 5 for the domain/service test suite

## Object-Oriented Design

| Concept | Where |
|---|---|
| **Encapsulation** | Private fields with validating getters/setters in `Transaction`, `Category`, `Budget` |
| **Inheritance** | `Transaction` → `Income`, `Expense` |
| **Polymorphism** | `Transaction.signedAmount()` overridden per subtype; `TransactionRepository` interface implemented by SQLite classes |
| **Abstraction** | Abstract `Transaction` class; repository interfaces (`dao` package) hide SQLite/JDBC details from controllers |
| **Exception Handling** | Custom checked exceptions (`ValidationException`, `BudgetExceededException`) surfaced as JavaFX alerts; a global uncaught-exception handler for anything unexpected |
| **Collections** | `ObservableList<Transaction>` backing the transactions table; `Map`-based category aggregation in the insight engine |
| **Event-Driven GUI** | Button handlers, table selection/edit listeners, live category filtering on type change |

## Project Structure

```
src/main/java/com/spendwise/
├── App.java              # JavaFX entry point
├── model/                 # Transaction hierarchy, Category, Budget, RecurringRule, Insight, Currency
├── dao/                   # Repository interfaces + SQLite implementations
├── service/               # BudgetService, RecurringTransactionService, InsightEngine,
│                          # ExportService, ImportService (note-paste parser)
└── controller/             # JavaFX FXML controllers (incl. ImportController/ImportRow)
src/main/resources/com/spendwise/{fxml,css}/
src/test/java/com/spendwise/    # JUnit 5 tests (model + service layers)
```

## Running It

**Requirements:** JDK 21+, Maven 3.9+ (or use your IDE's bundled tooling — IntelliJ IDEA Community detects the Maven project automatically).

```bash
mvn javafx:run
```

The first launch creates a local `spendwise.db` SQLite file (ignored by git) and seeds a starter set of income/expense categories.

To run the test suite:

```bash
mvn test
```

> **Note:** if you hit a `PKIX path building failed` / certificate error running Maven on Windows, it's usually antivirus/TLS-inspection software your JDK doesn't trust by default. Add this to your environment and retry: `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`

## Screenshots

| Dashboard | Transactions |
|---|---|
| ![Dashboard](screenshots/Screenshot%202026-07-11%20014649.png) | ![Transactions](screenshots/Screenshot%202026-07-11%20014929.png) |

| Budgets | Recurring |
|---|---|
| ![Budgets](screenshots/Screenshot%202026-07-11%20014948.png) | ![Recurring](screenshots/Screenshot%202026-07-11%20015001.png) |

![Analytics & Insights](screenshots/Screenshot%202026-07-11%20015015.png)

## Author

Enock Andoh — MSc Information Technology, University of Cape Coast
