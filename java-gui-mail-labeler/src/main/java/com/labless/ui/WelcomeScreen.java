package com.labless.ui;

import com.labless.model.AppConfig;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

public class WelcomeScreen extends StackPane {
    private final VBox welcomeFrame;
    private final FlowPane heroRow;
    private InvalidationListener widthListener;

    public WelcomeScreen(
        AppConfig currentConfig,
        Runnable onStartSetup,
        Runnable onOpenWorkspace
    ) {
        // Create logo with PNG image
        HBox logo = new HBox(8);
        logo.setAlignment(Pos.CENTER_LEFT);
        
        try {
            Image logoImage = new Image(getClass().getResourceAsStream("/logo.png"));
            ImageView logoImageView = new ImageView(logoImage);
            logoImageView.setFitHeight(32);
            logoImageView.setPreserveRatio(true);
            logoImageView.setSmooth(true);
            
            Label logoText = new Label("labless");
            logoText.getStyleClass().add("brand");
            Label logoAccent = new Label("2.0");
            logoAccent.getStyleClass().add("brand-accent");
            
            logo.getChildren().addAll(logoImageView, logoText, logoAccent);
        } catch (Exception e) {
            // Fallback to text-only logo if image fails to load
            System.err.println("Failed to load logo image: " + e.getMessage());
            Label logoHead = new Label("labless");
            logoHead.getStyleClass().add("brand");
            Label logoAccent = new Label("2.0");
            logoAccent.getStyleClass().add("brand-accent");
            logo.getChildren().addAll(logoHead, logoAccent);
        }

        Button topCtaButton = new Button("Try It Now");
        topCtaButton.getStyleClass().add("welcome-top-cta");
        topCtaButton.setOnAction(event -> onStartSetup.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(22, logo, spacer, topCtaButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("welcome-topbar");

        Label headline = new Label("The only Mail Labeler\nfor scaling your\ninbox decisions.");
        headline.getStyleClass().addAll("welcome-hero-text", "welcome-hero-italic-word");
        headline.setWrapText(true);
        headline.setMaxWidth(470);

        Label subtitle = new Label(
            "A precision communications tool that turns inbox chaos into clean, actionable categories."
        );
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("welcome-subtitle");
        subtitle.setMaxWidth(460);

        Button heroCtaButton = new Button("Try It Now");
        heroCtaButton.getStyleClass().add("welcome-hero-cta");
        heroCtaButton.setOnAction(event -> onStartSetup.run());

        Button openWorkspaceButton = new Button("Open Existing Workspace");
        openWorkspaceButton.getStyleClass().add("onboard-secondary-btn");
        openWorkspaceButton.setDisable(!currentConfig.isOnboardingCompleted());
        openWorkspaceButton.setOnAction(event -> onOpenWorkspace.run());

        Label downArrow = new Label("v");
        downArrow.getStyleClass().add("welcome-arrow");

        VBox leftColumn = new VBox(18, headline, subtitle, heroCtaButton, openWorkspaceButton, downArrow);
        leftColumn.getStyleClass().add("welcome-left");

        Pane illustrationPane = buildWelcomeIllustration();

        heroRow = new FlowPane();
        heroRow.setHgap(36);
        heroRow.setVgap(26);
        heroRow.setAlignment(Pos.CENTER_LEFT);
        heroRow.getChildren().addAll(leftColumn, illustrationPane);
        heroRow.getStyleClass().add("welcome-hero-row");

        welcomeFrame = new VBox(28, topBar, heroRow);
        welcomeFrame.getStyleClass().add("welcome-frame");
        welcomeFrame.setMinHeight(600); // Prevent content from moving down in smaller windows
        welcomeFrame.setMinWidth(700); // Set minimum width to maintain layout
        VBox.setVgrow(heroRow, Priority.ALWAYS); // Allow hero row to grow

        StackPane wrapper = new StackPane(welcomeFrame);
        wrapper.getStyleClass().add("screen-shell");
        wrapper.setMinWidth(700); // Set minimum width on wrapper as well
        StackPane.setAlignment(welcomeFrame, Pos.TOP_CENTER); // Keep content at top
        
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(wrapper);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false); // Changed to false to prevent vertical centering
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
        scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED); // Show horizontal scrollbar if needed
        scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);

        getChildren().add(scrollPane);
    }

    private Pane buildWelcomeIllustration() {
        Pane pane = new Pane();
        pane.setMinSize(420, 260);
        pane.setPrefSize(700, 520);
        pane.setMaxSize(700, 520);
        pane.getStyleClass().add("welcome-illustration");
        
        // Wrap the Pane in a Group to allow it to scale smoothly when the screen size decreases
        javafx.scene.Group scalableGroup = new javafx.scene.Group();

        Rectangle room = new Rectangle(20, 30, 650, 450);
        room.getStyleClass().add("sketch-room");

        Rectangle sofa = new Rectangle(250, 275, 260, 150);
        sofa.getStyleClass().add("sketch-outline");

        Rectangle chairLeft = new Rectangle(170, 265, 120, 165);
        chairLeft.getStyleClass().add("sketch-outline");

        Rectangle chairRight = new Rectangle(500, 270, 120, 160);
        chairRight.getStyleClass().add("sketch-outline");

        Rectangle screen = new Rectangle(300, 110, 220, 85);
        screen.getStyleClass().add("sketch-outline");
        screen.setRotate(-9);

        // Add logo image to the screen
        try {
            Image logoImage = new Image(getClass().getResourceAsStream("/logo.png"));
            ImageView screenLogo = new ImageView(logoImage);
            screenLogo.setFitWidth(60);
            screenLogo.setPreserveRatio(true);
            screenLogo.setSmooth(true);
            screenLogo.setLayoutX(320);
            screenLogo.setLayoutY(125);
            screenLogo.setRotate(-9);
            scalableGroup.getChildren().add(screenLogo);
        } catch (Exception e) {
            System.err.println("Failed to load logo for illustration: " + e.getMessage());
        }

        Label screenLabel = new Label("labless");
        screenLabel.setLayoutX(390);
        screenLabel.setLayoutY(134);
        screenLabel.setRotate(-9);
        screenLabel.getStyleClass().add("sketch-screen-label");

        Circle clock = new Circle(560, 120, 28);
        clock.getStyleClass().add("sketch-outline");

        Line clockHand1 = new Line(560, 120, 560, 105);
        clockHand1.getStyleClass().add("sketch-line");
        Line clockHand2 = new Line(560, 120, 548, 126);
        clockHand2.getStyleClass().add("sketch-line");

        Arc arc1 = new Arc(610, 190, 50, 70, 250, 170);
        arc1.getStyleClass().add("sketch-line");
        arc1.setFill(null);
        Arc arc2 = new Arc(616, 190, 70, 95, 250, 170);
        arc2.getStyleClass().add("sketch-line");
        arc2.setFill(null);

        Line floor = new Line(100, 430, 640, 430);
        floor.getStyleClass().add("sketch-line");

        scalableGroup.getChildren().addAll(
            room, sofa, chairLeft, chairRight, screen, screenLabel,
            clock, clockHand1, clockHand2, arc1, arc2, floor
        );
        pane.getChildren().add(scalableGroup);
        
        // Ensure scale adjusts based on available pane size
        pane.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
            double scale = Math.min(newVal.getWidth() / 700.0, newVal.getHeight() / 520.0);
            scale = Math.min(1.0, Math.max(0.5, scale)); // constrain scale between 50% and 100%
            scalableGroup.setScaleX(scale);
            scalableGroup.setScaleY(scale);
            scalableGroup.setTranslateX(-(700 - 700 * scale) / 2);
            scalableGroup.setTranslateY(-(520 - 520 * scale) / 2);
        });

        return pane;
    }

    public void attachResponsiveBehavior(Node widthSource) {
        Runnable updateLayout = () -> {
            double width = widthSource.getLayoutBounds().getWidth();
            if (width <= 0 && widthSource.getScene() != null) {
                width = widthSource.getScene().getWidth();
            }
            heroRow.setPrefWrapLength(Math.max(760, width - 160));

            boolean compact = width < 1320;
            welcomeFrame.getStyleClass().remove("welcome-compact");
            if (compact) {
                welcomeFrame.getStyleClass().add("welcome-compact");
            }

            if (width < 1060) {
                welcomeFrame.getStyleClass().remove("welcome-compact");
                welcomeFrame.getStyleClass().add("welcome-tight");
            } else {
                welcomeFrame.getStyleClass().remove("welcome-tight");
            }
        };
        updateLayout.run();
        widthListener = obs -> updateLayout.run();
        widthSource.layoutBoundsProperty().addListener(widthListener);
    }

    public void detachResponsiveBehavior(Node widthSource) {
        if (widthListener != null) {
            widthSource.layoutBoundsProperty().removeListener(widthListener);
            widthListener = null;
        }
    }
}
