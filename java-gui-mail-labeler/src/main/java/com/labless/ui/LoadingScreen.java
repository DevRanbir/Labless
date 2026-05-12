package com.labless.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class LoadingScreen extends StackPane {
    private final Label statusLabel = new Label("Loading...");

    public LoadingScreen() {
        getStyleClass().add("workspace-shell");
        VBox content = new VBox(18);
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-background-color: #121212; -fx-padding: 36; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #2b2b2b;");

        Label title = new Label("Preparing Your Inbox");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");

        statusLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 13px;");

        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(72, 72);

        content.getChildren().addAll(title, statusLabel, indicator);
        getChildren().add(content);
    }

    public void setStatus(String status) {
        statusLabel.setText(status == null || status.isBlank() ? "Loading..." : status);
    }
}
