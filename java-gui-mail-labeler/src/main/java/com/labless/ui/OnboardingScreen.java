package com.labless.ui;

import com.labless.model.AppConfig;
import com.labless.gmail.GoogleAuthManager;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OnboardingScreen extends StackPane {
    private final AppConfig initialConfig;
    private final Runnable onCancel;
    private final Consumer<AppConfig> onFinish;

    private int onboardingStepIndex = 0;
    private boolean googleConnected;
    private String selectedTheme = "System"; 
    private Label wizardHintLabel;
    private StackPane onboardingStepContainer;
    private Button backButton;
    private Button nextButton;
    private Button finishButton;

    private Label googleStatusLabel;
    
    private List<String> personalItems = new ArrayList<>(java.util.Arrays.asList("Account Security", "Bills Payments", "Receipts Invoices", "Travel Bookings", "Transaction"));
    private List<String> workItems = new ArrayList<>(java.util.Arrays.asList("University", "Work", "Action Required", "Events Invitations", "Certificates"));
    private List<String> miscItems = new ArrayList<>(java.util.Arrays.asList("Promotions", "Subscriptions", "Alerts", "Notes", "Spam Low Priority"));
    private java.util.Set<String> uncheckedLabels = new java.util.HashSet<>();

    private TextField addLabelField;
    private ComboBox<String> providerCombo;
    private TextField modelField;
    private TextField apiKeyField;

    private HBox labelsContainer;
    
    private MediaPlayer currentMediaPlayer; // Track current video player for cleanup

    public OnboardingScreen(
        AppConfig config,
        boolean googleConnected,
        Runnable onCancel,
        Consumer<AppConfig> onFinish
    ) {
        this.initialConfig = config;
        this.googleConnected = googleConnected;
        this.onCancel = onCancel;
        this.onFinish = onFinish;
        build();
    }

    private void build() {
        onboardingStepIndex = 0;
        
        // Add logo at the top
        HBox logoBox = new HBox(8);
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setStyle("-fx-padding: 20 0 10 0;");
        
        try {
            javafx.scene.image.Image logoImage = new javafx.scene.image.Image(
                getClass().getResourceAsStream("/logo.png")
            );
            javafx.scene.image.ImageView logoImageView = new javafx.scene.image.ImageView(logoImage);
            logoImageView.setFitHeight(40);
            logoImageView.setPreserveRatio(true);
            logoImageView.setSmooth(true);
            
            Label logoText = new Label("Labless");
            logoText.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -foreground;");
            
            logoBox.getChildren().addAll(logoImageView, logoText);
        } catch (Exception e) {
            System.err.println("Failed to load onboarding logo: " + e.getMessage());
            Label logoText = new Label("Labless");
            logoText.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -foreground;");
            logoBox.getChildren().add(logoText);
        }
        
        wizardHintLabel = new Label("Step 1 of 4: Choose Theme");
        wizardHintLabel.getStyleClass().add("onboard-subtitle");
        wizardHintLabel.setStyle("-fx-font-size: 16px; -fx-padding: 30px 0;");

        onboardingStepContainer = new StackPane();
        VBox.setVgrow(onboardingStepContainer, Priority.ALWAYS);
        onboardingStepContainer.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        backButton = new Button("Back");
        nextButton = new Button("Next");
        finishButton = new Button("Finish");
        Button cancelButton = new Button("Cancel");
        
        backButton.getStyleClass().add("onboard-secondary-btn");
        nextButton.getStyleClass().add("onboard-primary-btn");
        finishButton.getStyleClass().add("onboard-primary-btn");
        cancelButton.getStyleClass().add("onboard-secondary-btn");

        backButton.setOnAction(event -> moveStep(-1));
        nextButton.setOnAction(event -> moveStep(1));
        finishButton.setOnAction(event -> finishOnboarding());
        cancelButton.setOnAction(event -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });

        VBox centerContent = new VBox(0, logoBox, wizardHintLabel, onboardingStepContainer);
        centerContent.setAlignment(Pos.TOP_CENTER);
        centerContent.setStyle("-fx-padding: 40;");
        
        // Navigation overlay
        StackPane navLayer = new StackPane();
        navLayer.setPickOnBounds(false); 
        
        StackPane.setAlignment(backButton, Pos.CENTER_LEFT);
        StackPane.setMargin(backButton, new Insets(0, 0, 0, 40));
        
        StackPane.setAlignment(cancelButton, Pos.TOP_LEFT);
        StackPane.setMargin(cancelButton, new Insets(20, 0, 0, 40));
        
        StackPane.setAlignment(nextButton, Pos.CENTER_RIGHT);
        StackPane.setMargin(nextButton, new Insets(0, 40, 0, 0));
        
        StackPane.setAlignment(finishButton, Pos.CENTER_RIGHT);
        StackPane.setMargin(finishButton, new Insets(0, 40, 0, 0));
        
        navLayer.getChildren().addAll(backButton, cancelButton, nextButton, finishButton);

        StackPane wrapper = new StackPane(centerContent, navLayer);
        wrapper.setStyle("-fx-background-color: transparent;");
        getChildren().setAll(wrapper);

        refreshWizardStep();
    }

    private void changeThemeMode(String mode) {
        if (getScene() != null && getScene().getRoot() != null) {
            getScene().getRoot().getStyleClass().removeAll("light", "dark");
            if (mode.equals("Light")) {
                getScene().getRoot().getStyleClass().add("light");
            } else if (mode.equals("Dark")) {
                getScene().getRoot().getStyleClass().add("dark");
            } else {
                getScene().getRoot().getStyleClass().add("light"); // default system simulation
            }
        }
    }

    private int labelSubStep = 0;

    private void moveStep(int delta) {
        if (delta > 0 && !validateCurrentStep()) {
            return;
        }

        if (onboardingStepIndex == 2) {
            if (delta > 0 && labelSubStep < 2) {
                labelSubStep += delta;
                refreshWizardStep(); // Will rebuild label step with new SubStep
                return;
            } else if (delta < 0 && labelSubStep > 0) {
                labelSubStep += delta;
                refreshWizardStep(); // Will rebuild label step with new SubStep
                return;
            }
        }

        onboardingStepIndex = Math.max(0, Math.min(3, onboardingStepIndex + delta));
        
        // Reset subStep when entering Step 2
        if (onboardingStepIndex == 2 && delta > 0) {
            labelSubStep = 0;
        } else if (onboardingStepIndex == 2 && delta < 0) {
            labelSubStep = 2; // Arriving from Step 4, show last sub-step
        }
        
        refreshWizardStep();
    }

    private boolean validateCurrentStep() {
        if (onboardingStepIndex == 1 && !googleConnected) {
            wizardHintLabel.setText("Please connect Google account before moving forward.");
            return false;
        }
        if (onboardingStepIndex == 2) {
            boolean pHas = personalItems.stream().anyMatch(i -> !uncheckedLabels.contains(i));
            boolean wHas = workItems.stream().anyMatch(i -> !uncheckedLabels.contains(i));
            boolean mHas = miscItems.stream().anyMatch(i -> !uncheckedLabels.contains(i));
            if (!pHas || !wHas || !mHas) {
                wizardHintLabel.setText("Please select at least one label in each column.");
                return false;
            }
        }
        return true;
    }

    private void refreshWizardStep() {
        Node stepContent;
        if (onboardingStepIndex == 0) {
            wizardHintLabel.setText("Step 1 of 4: Choose Theme");
            stepContent = buildThemeStep();
        } else if (onboardingStepIndex == 1) {
            wizardHintLabel.setText("Step 2 of 4: Authenticate Gmail");
            stepContent = buildGoogleLoginStep();
        } else if (onboardingStepIndex == 2) {
            wizardHintLabel.setText("Step 3 of 4: Define label categories");
            stepContent = buildLabelStep();
        } else {
            wizardHintLabel.setText("Step 4 of 4: Configure AI");
            stepContent = buildAiStep();
        }
        
        applyStepTransition(stepContent);

        backButton.setVisible(onboardingStepIndex > 0);
        nextButton.setVisible(onboardingStepIndex < 3);
        finishButton.setVisible(onboardingStepIndex == 3);
    }
    
    private void applyStepTransition(Node content) {
        onboardingStepContainer.getChildren().setAll(content);
        
        FadeTransition ft = new FadeTransition(Duration.millis(400), content);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        
        TranslateTransition tt = new TranslateTransition(Duration.millis(400), content);
        tt.setFromY(20);
        tt.setToY(0);
        
        ft.play();
        tt.play();
    }
    
    /**
     * Creates a video player for the logo video with optimized and reliable loading
     * @param videoFileName The video file name (logo.mp4 or loading.mp4)
     * @return MediaView node with the video player
     */
    private MediaView createVideoPlayer(String videoFileName) {
        MediaView mediaView = new MediaView();
        mediaView.setFitWidth(150);
        mediaView.setFitHeight(150);
        mediaView.setPreserveRatio(true);
        
        // Create a placeholder to show while loading
        javafx.scene.layout.StackPane placeholder = new javafx.scene.layout.StackPane();
        placeholder.setMinSize(150, 150);
        placeholder.setMaxSize(150, 150);
        placeholder.setStyle("-fx-background-color: transparent;");
        
        // Load video in background with proper error handling
        javafx.application.Platform.runLater(() -> {
            try {
                // Stop and dispose of previous media player if exists
                if (currentMediaPlayer != null) {
                    currentMediaPlayer.stop();
                    currentMediaPlayer.dispose();
                }
                
                // Load video from resources
                String videoPath = getClass().getResource("/videos/" + videoFileName).toExternalForm();
                System.out.println("Loading video from: " + videoPath);
                
                Media media = new Media(videoPath);
                MediaPlayer player = new MediaPlayer(media);
                
                // Configure media player for optimal performance
                player.setAutoPlay(true);
                player.setCycleCount(MediaPlayer.INDEFINITE);
                player.setMute(true);
                player.setRate(1.0);
                
                // Handle errors
                player.setOnError(() -> {
                    System.err.println("Media player error: " + player.getError());
                });
                
                media.setOnError(() -> {
                    System.err.println("Media error: " + media.getError());
                });
                
                // Set the player when ready
                player.setOnReady(() -> {
                    System.out.println("Video ready: " + videoFileName);
                    currentMediaPlayer = player;
                    mediaView.setMediaPlayer(currentMediaPlayer);
                });
                
                // Set immediately to start loading
                currentMediaPlayer = player;
                mediaView.setMediaPlayer(currentMediaPlayer);
                
            } catch (Exception e) {
                System.err.println("Failed to load video: " + videoFileName);
                e.printStackTrace();
            }
        });
        
        return mediaView;
    }

    private Node buildThemeStep() {
        // Add logo video at the top
        MediaView logoVideo = createVideoPlayer("logo.mp4");
        VBox videoContainer = new VBox(logoVideo);
        videoContainer.setAlignment(Pos.CENTER);
        videoContainer.setStyle("-fx-padding: 0 0 20 0;");
        
        Button lightBtn = new Button("Light Mode");
        Button darkBtn = new Button("Dark Mode");
        Button sysBtn = new Button("System Default");

        // Disable light mode permanently
        lightBtn.setDisable(true);
        lightBtn.setStyle("-fx-opacity: 0.4;");

        lightBtn.getStyleClass().add(selectedTheme.equals("Light") ? "onboard-primary-btn" : "onboard-secondary-btn");
        darkBtn.getStyleClass().add(selectedTheme.equals("Dark") ? "onboard-primary-btn" : "onboard-secondary-btn");
        sysBtn.getStyleClass().add(selectedTheme.equals("System") ? "onboard-primary-btn" : "onboard-secondary-btn");

        HBox buttonRow = new HBox(15, lightBtn, darkBtn, sysBtn);
        buttonRow.setAlignment(Pos.CENTER);

        lightBtn.setOnAction(e -> {
            // Light mode disabled
        });
        
        darkBtn.setOnAction(e -> {
            selectedTheme = "Dark";
            darkBtn.getStyleClass().set(1, "onboard-primary-btn");
            lightBtn.getStyleClass().set(1, "onboard-secondary-btn");
            sysBtn.getStyleClass().set(1, "onboard-secondary-btn");
            changeThemeMode("Dark");
        });
        
        sysBtn.setOnAction(e -> {
            selectedTheme = "System";
            sysBtn.getStyleClass().set(1, "onboard-primary-btn");
            lightBtn.getStyleClass().set(1, "onboard-secondary-btn");
            darkBtn.getStyleClass().set(1, "onboard-secondary-btn");
            changeThemeMode("System");
        });

        VBox card = new VBox(20, videoContainer, buttonRow);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: transparent;");
        return card;
    }

    private Node buildGoogleLoginStep() {
        // Add logo video at the top
        MediaView logoVideo = createVideoPlayer("logo.mp4");
        VBox videoContainer = new VBox(logoVideo);
        videoContainer.setAlignment(Pos.CENTER);
        videoContainer.setStyle("-fx-padding: 0 0 20 0;");
        
        Label description = new Label("Securely connect to your Google Account.");
        description.getStyleClass().add("onboard-card-copy");

        googleStatusLabel = new Label(
            googleConnected ? "Connected to Google account" : "Not connected"
        );
        googleStatusLabel.getStyleClass().add(googleConnected ? "ok-label" : "warn-label");

        Button connectButton = new Button(googleConnected ? "Connected Google Account" : "Login with Google");
        connectButton.getStyleClass().add("onboard-primary-btn");
        connectButton.setMaxWidth(300);
        connectButton.setStyle("-fx-padding: 15px 30px; -fx-font-size: 16px;");
        
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.15));
        shadow.setRadius(10);
        shadow.setOffsetY(5);
        connectButton.setEffect(shadow);

        connectButton.setOnAction(event -> {
            connectButton.setDisable(true);
            connectButton.setText("Connecting in Browser...");

            Task<Void> authTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    // Triggers the OAuth local-server flow
                    GoogleAuthManager.createGmailClient();
                    return null;
                }
            };

            authTask.setOnSucceeded(e -> {
                googleConnected = true;
                googleStatusLabel.setText("Connected to Google account");
                googleStatusLabel.getStyleClass().removeAll("warn-label");
                googleStatusLabel.getStyleClass().add("ok-label");
                wizardHintLabel.setText("Step 2 of 4: Google authentication complete.");
                connectButton.setText("Connected Google Account");
                connectButton.setDisable(false);
            });

            authTask.setOnFailed(e -> {
                googleStatusLabel.setText("Auth Failed: " + authTask.getException().getMessage());
                googleStatusLabel.getStyleClass().removeAll("ok-label");
                googleStatusLabel.getStyleClass().add("warn-label");
                connectButton.setText("Login with Google");
                connectButton.setDisable(false);
            });

            new Thread(authTask).start();
        });

        VBox card = new VBox(20, videoContainer, description, connectButton, googleStatusLabel);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: transparent;");
        return card;
    }

    private Node buildLabelStep() {
        HBox columnsContainer = new HBox(40);
        columnsContainer.setAlignment(Pos.CENTER);
        
        Runnable rebuildColumns = new Runnable() {
            @Override
            public void run() {
                columnsContainer.getChildren().clear();

                // Build Personal Column
                VBox personalCol = buildCategoryColumn(
                    "Personal", personalItems, 
                    java.util.Arrays.asList("Account Security", "Bills Payments", "Receipts Invoices", "Travel Bookings", "Transaction"), 
                    this, labelSubStep == 0
                );
                
                // Build Work Column
                VBox workCol = buildCategoryColumn(
                    "Work Related", workItems, 
                    java.util.Arrays.asList("University", "Work", "Action Required", "Events Invitations", "Certificates"), 
                    this, labelSubStep == 1
                );
                
                // Build Misc Column
                VBox miscCol = buildCategoryColumn(
                    "Miscellaneous", miscItems, 
                    java.util.Arrays.asList("Promotions", "Subscriptions", "Alerts", "Notes", "Spam Low Priority"), 
                    this, labelSubStep == 2
                );

                if (labelSubStep == 0) {
                    columnsContainer.getChildren().add(personalCol);
                } else if (labelSubStep == 1) {
                    columnsContainer.getChildren().addAll(personalCol, workCol);
                } else if (labelSubStep == 2) {
                    columnsContainer.getChildren().addAll(personalCol, workCol, miscCol);
                }
            }
        };

        rebuildColumns.run();
        
        DropShadow containerShadow = new DropShadow();
        containerShadow.setColor(Color.rgb(0, 0, 0, 0.1));
        containerShadow.setRadius(15);
        containerShadow.setOffsetY(5);
        columnsContainer.setEffect(containerShadow);

        VBox card = new VBox(columnsContainer);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: transparent;");
        return card;
    }

    private VBox buildCategoryColumn(String title, List<String> items, List<String> defaults, Runnable rebuild, boolean isActive) {
        VBox col = new VBox(15);
        col.setAlignment(isActive ? Pos.TOP_LEFT : Pos.TOP_RIGHT); // Just a slight visual anchor

        // If it's not the active one, we fade it out lightly but keep it visible
        if (!isActive) {
            col.setOpacity(0.5);
        }

        Label titleL = new Label(title);
        titleL.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 0 0 10 0;");
        
        VBox checksBox = new VBox(12);
        for (String item : items) {
            CheckBox cb = new CheckBox(item);
            cb.getStyleClass().add("onboard-check");
            cb.setSelected(!uncheckedLabels.contains(item));
            cb.setDisable(!isActive); // Only allow editing the active column
            
            cb.setOnAction(e -> {
                if (cb.isSelected()) {
                    uncheckedLabels.remove(item);
                } else {
                    if (defaults.contains(item)) {
                        uncheckedLabels.add(item);
                    } else {
                         items.remove(item);
                         uncheckedLabels.remove(item);
                         rebuild.run();
                    }
                }
            });
            checksBox.getChildren().add(cb);
        }

        col.getChildren().addAll(titleL, checksBox);

        // Only the active column gets the Add functionality
        if (isActive) {
            TextField addField = new TextField();
            addField.setPromptText("Add new...");
            addField.getStyleClass().add("onboard-input");
            addField.setPrefWidth(120);

            Button addBtn = new Button("Add");
            addBtn.getStyleClass().add("onboard-primary-btn");
            addBtn.setStyle("-fx-padding: 8px 12px;");
            addBtn.setOnAction(e -> {
                String candidate = addField.getText() == null ? "" : addField.getText().trim();
                if (!candidate.isEmpty() && !items.contains(candidate)) {
                    items.add(candidate);
                    uncheckedLabels.remove(candidate); // Default to checked
                    addField.clear();
                    rebuild.run();
                }
            });

            HBox inputRow = new HBox(8, addField, addBtn);
            inputRow.setAlignment(Pos.CENTER_LEFT);
            col.getChildren().add(inputRow);
        }

        return col;
    }

    private String selectedProvider = "";

    private Node buildAiStep() {
        selectedProvider = initialConfig.getLlm().getProvider();
        if (selectedProvider == null || selectedProvider.isEmpty()) selectedProvider = "Groq"; // Default to Groq

        VBox customDropdown = new VBox(5);
        Button dropdownHeader = new Button(selectedProvider);
        dropdownHeader.getStyleClass().add("onboard-input");
        dropdownHeader.setMaxWidth(Double.MAX_VALUE);
        dropdownHeader.setAlignment(Pos.CENTER_LEFT);
        
        VBox dropdownList = new VBox();
        dropdownList.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #3b3b3b; -fx-border-radius: 5; -fx-background-radius: 5;");
        dropdownList.setVisible(false);
        dropdownList.setManaged(false);
        
        for (String prov : java.util.Arrays.asList("Groq", "OpenAI", "Gemini", "Ollama")) {
            Button itemBtn = new Button(prov);
            itemBtn.setMaxWidth(Double.MAX_VALUE);
            itemBtn.setAlignment(Pos.CENTER_LEFT);
            itemBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-padding: 8 12;");
            itemBtn.setOnMouseEntered(e -> itemBtn.setStyle("-fx-background-color: #0ea5e9; -fx-text-fill: white; -fx-padding: 8 12;"));
            itemBtn.setOnMouseExited(e -> itemBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-padding: 8 12;"));
            itemBtn.setOnAction(e -> {
                selectedProvider = prov;
                dropdownHeader.setText(prov);
                dropdownList.setVisible(false);
                dropdownList.setManaged(false);
                
                // Update model field with default for selected provider
                if ("Groq".equals(prov)) {
                    modelField.setText("llama-3.1-8b-instant");
                } else if ("OpenAI".equals(prov)) {
                    modelField.setText("gpt-4o-mini");
                } else if ("Gemini".equals(prov)) {
                    modelField.setText("gemini-1.5-flash");
                } else if ("Ollama".equals(prov)) {
                    modelField.setText("llama3");
                }
            });
            dropdownList.getChildren().add(itemBtn);
        }
        
        dropdownHeader.setOnAction(e -> {
            boolean vis = dropdownList.isVisible();
            dropdownList.setVisible(!vis);
            dropdownList.setManaged(!vis);
        });
        
        customDropdown.getChildren().addAll(dropdownHeader, dropdownList);

        // Pre-fill with existing config or defaults
        String defaultModel = initialConfig.getLlm().getModel();
        if (defaultModel == null || defaultModel.isEmpty()) {
            if ("Groq".equals(selectedProvider)) {
                defaultModel = "llama-3.1-8b-instant";
            } else if ("OpenAI".equals(selectedProvider)) {
                defaultModel = "gpt-4o-mini";
            } else if ("Gemini".equals(selectedProvider)) {
                defaultModel = "gemini-1.5-flash";
            } else {
                defaultModel = "llama3";
            }
        }
        
        modelField = new TextField(defaultModel);
        apiKeyField = new TextField(initialConfig.getLlm().getApiKey() != null ? initialConfig.getLlm().getApiKey() : "");
        
        modelField.getStyleClass().add("onboard-input");
        apiKeyField.getStyleClass().add("onboard-input");
        
        // Add helpful hint for Groq
        Label groqHint = new Label();
        groqHint.setWrapText(true);
        groqHint.setMaxWidth(400);
        groqHint.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 11px; -fx-padding: 5 0 0 0;");
        
        if ("Groq".equals(selectedProvider)) {
            if (apiKeyField.getText() != null && !apiKeyField.getText().trim().isEmpty()) {
                groqHint.setText("✓ Groq API key configured. Get your free API key at: https://console.groq.com");
                groqHint.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11px; -fx-padding: 5 0 0 0;");
            } else {
                groqHint.setText("Get your free Groq API key at: https://console.groq.com");
            }
        }

        GridPane form = new GridPane();
        form.setAlignment(Pos.CENTER);
        form.setHgap(15);
        form.setVgap(15);
        form.addRow(0, fieldLabel("AI Provider"), customDropdown);
        form.addRow(1, fieldLabel("Model"), modelField);
        form.addRow(2, fieldLabel("API Key"), apiKeyField);
        
        VBox formWithHint = new VBox(10, form, groqHint);
        formWithHint.setAlignment(Pos.CENTER);

        VBox card = new VBox(20, formWithHint);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: transparent;");
        return card;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("onboard-field-label");
        return label;
    }

    private Label onboardCardTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("onboard-card-title");
        return label;
    }

    private void finishOnboarding() {
        if (!googleConnected) {
            wizardHintLabel.setText("Connect Google before finishing onboarding.");
            return;
        }
        try {
            AppConfig config = AppConfig.defaults();
            config.getLlm().setProvider(selectedProvider);
            config.getLlm().setModel(modelField.getText().trim());
            config.getLlm().setApiKey(apiKeyField.getText().trim());

            List<String> sanitized = new ArrayList<>();
            for (String item : personalItems) {
                if (!uncheckedLabels.contains(item) && !sanitized.contains(item)) sanitized.add(item);
            }
            for (String item : workItems) {
                if (!uncheckedLabels.contains(item) && !sanitized.contains(item)) sanitized.add(item);
            }
            for (String item : miscItems) {
                if (!uncheckedLabels.contains(item) && !sanitized.contains(item)) sanitized.add(item);
            }
            if (!sanitized.contains("Other")) {
                sanitized.add("Other");
            }
            config.setCategories(sanitized);
            config.setOnboardingCompleted(true);

            onFinish.accept(config);
        } catch (Exception ex) {
            wizardHintLabel.setText("Validation failed: " + ex.getMessage());
        }
    }
}
