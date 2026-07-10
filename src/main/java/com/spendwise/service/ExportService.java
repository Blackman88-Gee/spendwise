package com.spendwise.service;

import com.spendwise.model.Transaction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ExportService {

    public void exportTransactionsToCsv(List<Transaction> transactions, Path destination) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
            writer.write("Date,Type,Category,Description,Amount");
            writer.newLine();
            for (Transaction t : transactions) {
                writer.write(String.join(",",
                        t.getDate().toString(),
                        t.getType().name(),
                        escapeCsv(t.getCategory().getName()),
                        escapeCsv(t.getDescription()),
                        String.format("%.2f", t.getAmount())));
                writer.newLine();
            }
        }
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
