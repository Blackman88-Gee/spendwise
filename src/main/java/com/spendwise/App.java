package com.spendwise;

import com.spendwise.dao.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseManager.initSchema();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/spendwise/fxml/main.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(getClass().getResource("/com/spendwise/css/styles.css").toExternalForm());

        stage.setTitle("SpendWise — Expense Tracking & Budgeting");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
