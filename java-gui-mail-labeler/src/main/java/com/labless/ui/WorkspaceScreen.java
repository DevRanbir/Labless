package com.labless.ui;

import com.labless.model.AppConfig;
import com.labless.model.EmailMessage;
import com.labless.model.JobProgress;
import com.labless.model.JobSummary;
import com.labless.model.MailboxStats;
import com.labless.model.MailboxLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.SVGPath;
import javafx.stage.Popup;
import javafx.geometry.Bounds;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.control.ProgressIndicator;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import java.util.Arrays;

public class WorkspaceScreen extends BorderPane {

    private final javafx.scene.control.ListView<EmailMessage> mailList = new javafx.scene.control.ListView<>();
    private final Label emptyListLabel = new Label("No messages to show.");
    private final TextField listSearchField = new TextField();
    private final Label statsPrimaryLabel = new Label("Messages: -");
    private final Label statsSecondaryLabel = new Label("Inbox: -  Unread: -  Threads: -");
    private final Label fetchProgressLabel = new Label("Loading: idle");
    private final ProgressIndicator navSpinner = new ProgressIndicator();
    private final Label navLoadingLabel = new Label();
    private final HBox navLoadingBox = new HBox(8, navLoadingLabel, navSpinner);
    private boolean isLoadingEmails = false;

    private final Label subjectLabelHeader = new Label("Select a message");
    private final Label dateValueLabel = new Label("-");
    private final Label fromValueLabel = new Label("-");
    private final Label toMeLabel = new Label("to me");
    private final Label typeValueLabel = new Label("Type: -");
    private final FlowPane headerLabelsPane = new FlowPane();
    private final HBox labelIconContainer = new HBox();
    private final javafx.scene.web.WebView bodyView = new javafx.scene.web.WebView();

    private final VBox messageView = new VBox(20);
    private final VBox messagePlaceholder = new VBox(8);
    private final VBox sidebarContent = new VBox();
    
    // Layout containers for animated three-column layout
    private HBox contentArea;
    private Region leftSpacer;
    private VBox listPane;
    private Region rightSpacer;
    private VBox viewPane;
    
    // Labeling mode
    private boolean isLabelingMode = false;
    private VBox labelingPanel;
    private Thread labelingThread; // Track the labeling thread for cancellation
    
    // History mode
    private boolean isHistoryMode = false;
    private VBox historyPanel;
    
    // Labeling UI components (preserved across panel show/hide)
    private Label labelingStatusLabel;
    private ProgressIndicator labelingProgressIndicator;
    private Label labelingProgressText;
    private Button labelingStartButton;
    private Button labelingStopButton;
    private javafx.scene.control.ListView<com.labless.model.LabelingResult> labelingResultsList;
    private Label labelingResultsTitle;
    private ScrollPane labelingResultsScroll;
    private VBox labelingResultsContainer; // Container for results section
    private javafx.scene.control.TextField labelingEmailCountField; // Input for number of emails to process

    private final List<EmailMessage> allEmails = new ArrayList<>();
    private final List<MailboxLabel> allLabels = new ArrayList<>();
    private final List<String> configuredCategories = new ArrayList<>();
    private EmailMessage selectedEmail;

    // Sidebar filter state: null = show all
    private String activeFilterLabel = null;

    private final Runnable onStartJob;
    private final Runnable onRunOnboardingAgain;
    private final Runnable onRefreshInbox;
    private final Runnable onLogout;
    private final String profileImageUrl;
    private final com.labless.gmail.GmailClient gmailClient;
    private final AppConfig config; // Store config for accessing API keys and categories
    private final com.labless.database.DatabaseManager databaseManager; // For loading labeling history
    private Consumer<String> onLabelFetchRequested; // set by MainApplication
    private MailboxStats mailboxStats;
    private long loadedMailCount;
    private long targetMailCount;
    private final Set<String> locallyReadMessageIds = new HashSet<>();

    private static final Set<String> PERSONAL_DEFAULTS = Set.of(
        "ACCOUNT SECURITY", "BILLS PAYMENTS", "RECEIPTS INVOICES", "TRAVEL BOOKINGS", "TRANSACTION"
    );
    private static final Set<String> WORK_DEFAULTS = Set.of(
        "UNIVERSITY", "WORK", "ACTION REQUIRED", "EVENTS INVITATIONS", "CERTIFICATES"
    );
    private static final Set<String> MISC_DEFAULTS = Set.of(
        "PROMOTIONS", "SUBSCRIPTIONS", "ALERTS", "NOTES", "SPAM LOW PRIORITY"
    );

    private final Map<String, Image> labelAvatarCache = new java.util.HashMap<>();

    public WorkspaceScreen(
        AppConfig config,
        Runnable onRunOnboardingAgain,
        Runnable onStartJob,
        Runnable onRefreshInbox,
        Runnable onStopJob,
        Runnable onLogout,
        String profileImageUrl,
        com.labless.gmail.GmailClient gmailClient,
        com.labless.database.DatabaseManager databaseManager
    ) {
        this.config = config;
        this.onStartJob = onStartJob;
        this.onRunOnboardingAgain = onRunOnboardingAgain;
        this.onRefreshInbox = onRefreshInbox;
        this.onLogout = onLogout;
        this.profileImageUrl = profileImageUrl;
        this.gmailClient = gmailClient;
        this.databaseManager = databaseManager;
        getStyleClass().add("workspace-shell");
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        setStyle("-fx-background-color: -sidebar;");

        configuredCategories.addAll(config.getCategories());

        HBox rootLayout = new HBox();
        rootLayout.setFillHeight(true);

        Node sidebar = buildSidebar();
        Node mainArea = buildMainArea();

        rootLayout.getChildren().addAll(sidebar, mainArea);
        setCenter(rootLayout);

        refreshMessageList();
        refreshSidebarLabels();
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(16);
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setStyle("-fx-padding: 20 12; -fx-background-color: transparent;");

        HBox brandBox = new HBox(8);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.setStyle("-fx-padding: 0 8;");
        
        // Add logo image
        try {
            javafx.scene.image.Image logoImage = new javafx.scene.image.Image(
                getClass().getResourceAsStream("/logo.png")
            );
            javafx.scene.image.ImageView logoImageView = new javafx.scene.image.ImageView(logoImage);
            logoImageView.setFitHeight(24);
            logoImageView.setPreserveRatio(true);
            logoImageView.setSmooth(true);
            brandBox.getChildren().add(logoImageView);
        } catch (Exception e) {
            System.err.println("Failed to load sidebar logo: " + e.getMessage());
        }
        
        Label brandName = new Label("Labless");
        brandName.setStyle("-fx-text-fill: -sidebar-foreground; -fx-font-size: 14px; -fx-font-weight: bold;");
        brandBox.getChildren().add(brandName);

        VBox coreActions = new VBox(2);
        coreActions.getChildren().add(createSidebarAction("sparkles", "Start Labeling", this::showLabelingPanel));
        coreActions.getChildren().add(createSidebarAction("search", "Search", () -> listSearchField.requestFocus()));
        coreActions.getChildren().add(createSidebarAction("settings", "Settings", onRunOnboardingAgain));

        sidebarContent.setSpacing(16);
        sidebarContent.setStyle("-fx-background-color: transparent;");

        ScrollPane scroll = new ScrollPane(sidebarContent);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("workspace-scroll");
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        sidebar.getChildren().addAll(brandBox, coreActions, scroll);
        return sidebar;
    }

    private HBox createSidebarAction(String iconKey, String labelTxt, Runnable action) {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-padding: 8; -fx-background-radius: 6; -fx-cursor: hand;");
        Label text = new Label(labelTxt);
        text.setStyle("-fx-text-fill: -sidebar-foreground; -fx-font-size: 13px;");
        box.getChildren().addAll(createIcon(iconKey, 14, "-muted-foreground"), text);

        box.setOnMouseEntered(e -> box.setStyle("-fx-padding: 8; -fx-background-radius: 6; -fx-cursor: hand; -fx-background-color: -sidebar-accent;"));
        box.setOnMouseExited(e -> box.setStyle("-fx-padding: 8; -fx-background-radius: 6; -fx-cursor: hand; -fx-background-color: transparent;"));
        box.setOnMouseClicked(e -> action.run());
        return box;
    }

    private Node buildMainArea() {
        VBox mainArea = new VBox();
        HBox.setHgrow(mainArea, Priority.ALWAYS);

        mainArea.setStyle(
            "-fx-background-color: -card; " +
            "-fx-background-radius: 20 0 0 0; " +
            "-fx-border-color: -border; " +
            "-fx-border-width: 1 0 0 1; " +
            "-fx-border-radius: 20 0 0 0;"
        );
        HBox.setMargin(mainArea, new Insets(8, 0, 0, 0));

        HBox topNavbar = new HBox(16);
        topNavbar.setAlignment(Pos.CENTER_LEFT);
        topNavbar.setStyle("-fx-padding: 12 20; -fx-border-color: -border; -fx-border-width: 0 0 1 0;");

        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setStyle("-fx-background-color: transparent; -fx-padding: 2 8; -fx-border-color: transparent;");
        listSearchField.setPromptText("Search messages...");
        listSearchField.setStyle("-fx-background-color: transparent; -fx-text-fill: -foreground; -fx-padding: 0; -fx-border-color: transparent; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        listSearchField.setPrefWidth(320);
        listSearchField.textProperty().addListener((obs, oldValue, newValue) -> refreshMessageList());
        searchBox.getChildren().addAll(createIcon("search", 14, "-muted-foreground"), listSearchField);

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        navSpinner.setPrefSize(16, 16);
        navSpinner.setStyle("-fx-progress-color: -primary;");
        navLoadingLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 12px; -fx-font-weight: bold;");
        navLoadingBox.setAlignment(Pos.CENTER_RIGHT);
        // Keep always managed so nav bar width stays stable; use opacity to hide
        navLoadingBox.setVisible(true);
        navLoadingBox.setManaged(true);
        navLoadingBox.setOpacity(0);

        Button refreshButton = new Button();
        refreshButton.setGraphic(createIcon("refresh", 14, "-muted-foreground"));
        refreshButton.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 2 4; -fx-cursor: hand;");
        refreshButton.setFocusTraversable(false);
        refreshButton.setOnAction(e -> {
            if (onRefreshInbox != null) {
                onRefreshInbox.run();
            }
        });
        
        Button labelButton = new Button();
        labelButton.setGraphic(createIcon("sparkles", 14, "-primary"));
        labelButton.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 2 4; -fx-cursor: hand;");
        labelButton.setFocusTraversable(false);
        labelButton.setOnAction(e -> toggleLabelingMode());
        
        Button historyButton = new Button();
        historyButton.setGraphic(createIcon("history", 14, "-muted-foreground"));
        historyButton.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 2 4; -fx-cursor: hand;");
        historyButton.setFocusTraversable(false);
        historyButton.setOnAction(e -> toggleHistoryMode());

        Node avatar = createProfileAvatarNode(22);
        // Layout: search | spacer | [label spinner] | refresh | label | history | avatar
        topNavbar.getChildren().addAll(searchBox, spacer1, navLoadingBox, refreshButton, labelButton, historyButton, avatar);

        // Three-column layout: leftSpacer | listPane | rightSpacer/viewPane
        contentArea = new HBox();
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        
        // Left spacer - visible when no email selected (10% width)
        leftSpacer = new Region();
        leftSpacer.setMinWidth(0);
        leftSpacer.setPrefWidth(100);
        leftSpacer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(leftSpacer, Priority.SOMETIMES);

        // Mail list pane (center column - 80% width when centered)
        listPane = new VBox();
        listPane.setMinWidth(0);
        listPane.setPrefWidth(800);
        listPane.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(listPane, Priority.ALWAYS);
        listPane.setStyle("-fx-border-color: -border; -fx-border-width: 0 1 0 0;");

        emptyListLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-padding: 20;");
        
        // Hide scrollbar and position it at the right edge with spacing
        mailList.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-control-inner-background: transparent; " +
            "-fx-background-insets: 0; " +
            "-fx-padding: 0 12 0 0; " + // Right padding for scrollbar spacing
            "-fx-border-width: 0; " +
            "-fx-focus-color: transparent; " +
            "-fx-faint-focus-color: transparent;"
        );
        VBox.setVgrow(mailList, Priority.ALWAYS);
        
        mailList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(EmailMessage email, boolean empty) {
                super.updateItem(email, empty);
                setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-width: 0;");
                if (empty || email == null) {
                    setGraphic(null);
                    setOnMouseClicked(null);
                } else {
                    boolean active = selectedEmail != null && selectedEmail.getId().equals(email.getId());
                    int idx = getIndex(); // 0-based position in visible list
                    StackPane cardNode = mailCard(email, active, idx);
                    cardNode.maxWidthProperty().bind(lv.widthProperty().subtract(15));
                    setGraphic(cardNode);
                    setOnMouseClicked(e -> {
                        selectEmail(email);
                        refreshMessageList();
                    });
                }
            }
        });
        // Suppress built-in ListView selection model visuals
        mailList.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);
        mailList.setFocusTraversable(false);

        StackPane listContainer = new StackPane(mailList, emptyListLabel);
        StackPane.setAlignment(emptyListLabel, Pos.TOP_CENTER);
        VBox.setVgrow(listContainer, Priority.ALWAYS);
        listPane.getChildren().add(listContainer);

        // Right spacer - visible when no email selected (10% width)
        rightSpacer = new Region();
        rightSpacer.setMinWidth(0);
        rightSpacer.setPrefWidth(100);
        rightSpacer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(rightSpacer, Priority.SOMETIMES);

        // Email view pane (right column when email selected)
        viewPane = new VBox();
        HBox.setHgrow(viewPane, Priority.ALWAYS);
        viewPane.setStyle("-fx-background-color: transparent;");

        HBox topActionsRow = new HBox(2);
        topActionsRow.setAlignment(Pos.CENTER_LEFT);
        
        Button closeBtn = new Button();
        closeBtn.setGraphic(createIcon("arrow_left", 14, "-muted-foreground"));
        closeBtn.setStyle("-fx-background-color: transparent; -fx-padding: 6 8; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> {
            selectedEmail = null;
            animateToEmptyState();
            renderMessageState();
            refreshMessageList();
        });

        Button processBtn = new Button();
        processBtn.setGraphic(createIcon("sparkles", 14, "-primary"));
        processBtn.setStyle("-fx-background-color: transparent; -fx-padding: 6 8; -fx-cursor: hand;");

        Button replyBtn = new Button();
        replyBtn.setGraphic(createIcon("reply", 14, "-muted-foreground"));
        replyBtn.setStyle("-fx-background-color: transparent; -fx-padding: 6 8; -fx-cursor: hand;");
        
        // Just let it initialize cleanly
        subjectLabelHeader.setStyle("-fx-text-fill: -foreground; -fx-font-weight: bold; -fx-font-size: 16px;");
        subjectLabelHeader.setMaxWidth(Double.MAX_VALUE);
        subjectLabelHeader.setMinWidth(0);
        subjectLabelHeader.setEllipsisString("...");
        HBox.setHgrow(subjectLabelHeader, Priority.ALWAYS);

        topActionsRow.getChildren().addAll(closeBtn, processBtn, replyBtn, subjectLabelHeader);

        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        
        fromValueLabel.setStyle("-fx-text-fill: -foreground; -fx-font-size: 13px;");
        fromValueLabel.setMaxWidth(Double.MAX_VALUE);
        fromValueLabel.setMinWidth(0);
        fromValueLabel.setEllipsisString("...");

        typeValueLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 12px;");
        typeValueLabel.setMinWidth(Region.USE_PREF_SIZE);

        dateValueLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 12px;");
        dateValueLabel.setMinWidth(Region.USE_PREF_SIZE);
        
        Label metaDot1 = new Label("•");
        metaDot1.setStyle("-fx-text-fill: -muted-foreground;");
        
        Label metaDot2 = new Label("•");
        metaDot2.setStyle("-fx-text-fill: -muted-foreground;");

        headerLabelsPane.setHgap(6);
        headerLabelsPane.setVgap(6);

        metaRow.getChildren().addAll(fromValueLabel, metaDot1, typeValueLabel, metaDot2, dateValueLabel, headerLabelsPane);

        VBox headerArea = new VBox(6);
        headerArea.getChildren().addAll(topActionsRow, metaRow);
        headerArea.setStyle("-fx-padding: 12 24 12 12; -fx-border-color: -border; -fx-border-width: 0 0 1 0;");

        VBox.setVgrow(bodyView, Priority.ALWAYS);
        bodyView.setContextMenuEnabled(false);

        messageView.getChildren().setAll(headerArea, bodyView);
        messageView.setFillWidth(true);
        messageView.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(messageView, Priority.ALWAYS);

        messagePlaceholder.getChildren().clear();
        messagePlaceholder.setAlignment(Pos.CENTER);
        messagePlaceholder.setPadding(new Insets(48, 32, 32, 32));
        VBox emptyStateStats = new VBox(8);
        emptyStateStats.setMaxWidth(560);
        emptyStateStats.setStyle("-fx-padding: 16 18; -fx-border-color: -border; -fx-border-width: 1; -fx-background-color: -card; -fx-background-radius: 10; -fx-border-radius: 10;");
        Label emptyTitle = new Label("Select an email to preview");
        emptyTitle.setStyle("-fx-text-fill: -foreground; -fx-font-size: 18px; -fx-font-weight: bold;");
        statsPrimaryLabel.setStyle("-fx-text-fill: -foreground; -fx-font-size: 13px; -fx-font-weight: bold;");
        statsSecondaryLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 12px;");
        fetchProgressLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 12px;");
        emptyStateStats.getChildren().addAll(emptyTitle, statsPrimaryLabel, statsSecondaryLabel, fetchProgressLabel);
        messagePlaceholder.getChildren().add(emptyStateStats);

        StackPane messageContainer = new StackPane(messagePlaceholder, messageView);
        VBox.setVgrow(messageContainer, Priority.ALWAYS);

        viewPane.getChildren().add(messageContainer);

        // Initial layout: centered mail list with spacers on both sides
        contentArea.getChildren().addAll(leftSpacer, listPane, rightSpacer);

        mainArea.getChildren().addAll(topNavbar, contentArea);
        return mainArea;
    }

    private void refreshSidebarLabels() {
        sidebarContent.getChildren().clear();

        VBox foldersBox = new VBox(2);
        Label foldersHeader = new Label("FOLDERS");
        foldersHeader.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 0 8 4 8;");
        foldersBox.getChildren().add(foldersHeader);

        VBox categoriesBox = new VBox(2);
        Label categoriesHeader = new Label("CATEGORIES");
        categoriesHeader.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 0 8 4 8;");
        categoriesBox.getChildren().add(categoriesHeader);

        List<MailboxLabel> sorted = new ArrayList<>(allLabels);
        sorted.sort(Comparator.comparing(MailboxLabel::getName, String.CASE_INSENSITIVE_ORDER));

        Map<String, MailboxLabel> categoryByName = new LinkedHashMap<>();
        for (MailboxLabel label : sorted) {
            String labelName = label.getName();
            if (labelName == null || labelName.isBlank()) {
                continue;
            }
            String normalized = labelName.toUpperCase(Locale.ROOT);
            if (label.isSystem() || normalized.equals("INBOX")) {
                foldersBox.getChildren().add(createSidebarItem(iconForFolder(labelName), labelName, label.getMessageCount(), 0, false));
            } else {
                String displayName = normalized.startsWith("CATEGORY_") ? normalizeCategoryLabel(labelName) : labelName;
                categoryByName.put(displayName.toUpperCase(Locale.ROOT), new MailboxLabel(displayName, label.getMessageCount(), false));
            }
        }
        if (foldersBox.getChildren().size() == 1) {
            foldersBox.getChildren().add(createSidebarItem("inbox", "INBOX", 0, 0, false));
        }

        for (String configured : configuredCategories) {
            if (configured == null || configured.isBlank()) {
                continue;
            }
            String key = configured.toUpperCase(Locale.ROOT);
            categoryByName.putIfAbsent(key, new MailboxLabel(configured, 0, false));
        }

        List<MailboxLabel> personal = new ArrayList<>();
        List<MailboxLabel> work = new ArrayList<>();
        List<MailboxLabel> misc = new ArrayList<>();
        List<MailboxLabel> other = new ArrayList<>();

        for (MailboxLabel label : categoryByName.values()) {
            if (label.getMessageCount() <= 0) {
                continue;
            }
            String bucket = bucketForCategory(label.getName());
            switch (bucket) {
                case "personal" -> personal.add(label);
                case "work" -> work.add(label);
                case "misc" -> misc.add(label);
                default -> other.add(label);
            }
        }

        addCategoryGroup(categoriesBox, "Personal", personal);
        addCategoryGroup(categoriesBox, "Work Related", work);
        addCategoryGroup(categoriesBox, "Miscellaneous", misc);
        addCategoryGroup(categoriesBox, "Other", other);

        sidebarContent.getChildren().addAll(categoriesBox, foldersBox);
    }

    private void addCategoryGroup(VBox container, String groupName, List<MailboxLabel> labels) {
        if (labels.isEmpty()) {
            return;
        }
        labels.sort(Comparator.comparing(MailboxLabel::getName, String.CASE_INSENSITIVE_ORDER));
        int total = labels.stream().mapToInt(MailboxLabel::getMessageCount).sum();
        container.getChildren().add(createSidebarItem("folder", groupName, total, 0, false));
        for (MailboxLabel label : labels) {
            container.getChildren().add(createSidebarItem(iconForCategory(label.getName()), label.getName(), label.getMessageCount(), 18, true));
        }
    }

    private HBox createSidebarItem(String iconKey, String labelTxt, int count, int indentPx, boolean useLabelColor) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);

        String textColorToken = "-sidebar-foreground";
        String iconColorToken = "-muted-foreground";

        if (useLabelColor) {
            javafx.scene.paint.Color lc = getLabelColor(labelTxt);
            String hex = String.format("#%02x%02x%02x",
                (int) (lc.getRed() * 255),
                (int) (lc.getGreen() * 255),
                (int) (lc.getBlue() * 255));
            textColorToken = hex;
            iconColorToken = hex;
        }

        final String baseStyle = "-fx-padding: 6 8 6 " + (8 + indentPx) + "; -fx-background-radius: 6; -fx-cursor: hand;";
        box.setStyle(baseStyle + " -fx-background-color: transparent;");

        Label text = new Label(labelTxt);
        text.setStyle("-fx-text-fill: " + textColorToken + "; -fx-font-size: 12px;");
        Label countLabel = new Label(String.valueOf(Math.max(0, count)));
        countLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 11px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        box.getChildren().addAll(createIcon(iconKey, 12, iconColorToken), text, spacer, countLabel);

        box.setOnMouseEntered(e -> box.setStyle(baseStyle + " -fx-background-color: -sidebar-accent;"));
        box.setOnMouseExited(e -> {
            boolean isActive = labelTxt.equalsIgnoreCase(activeFilterLabel);
            box.setStyle(baseStyle + (isActive ? " -fx-background-color: -sidebar-accent;" : " -fx-background-color: transparent;"));
        });
        box.setOnMouseClicked(e -> {
            // Toggle filter: click same item again to show all
            if (labelTxt.equalsIgnoreCase(activeFilterLabel)) {
                activeFilterLabel = null;
                refreshSidebarLabels();
                refreshMessageList();
            } else {
                activeFilterLabel = labelTxt;
                refreshSidebarLabels();
                refreshMessageList();
                // Notify MainApplication to priority-fetch this label from Gmail
                // (only fires for custom labels, not system folders like INBOX/SENT)
                if (!isGmailSystemLabel(labelTxt) && onLabelFetchRequested != null) {
                    onLabelFetchRequested.accept(labelTxt);
                }
            }
        });

        return box;
    }

    private Node createIcon(String iconKey, double size, String colorToken) {
        SVGPath path = new SVGPath();
        path.setContent(iconPath(iconKey));
        path.setStyle("-fx-fill: " + colorToken + ";");

        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);

        StackPane icon = new StackPane(path);
        icon.setMinSize(size, size);
        icon.setPrefSize(size, size);
        icon.setMaxSize(size, size);
        return icon;
    }

    private String iconPath(String iconKey) {
        return switch (iconKey) {
            case "search" -> "M10.5 3a7.5 7.5 0 1 0 4.72 13.332l4.24 4.24a.75.75 0 1 0 1.06-1.06l-4.24-4.24A7.5 7.5 0 0 0 10.5 3Z";
            case "bell" -> "M5.25 9a6.75 6.75 0 0 1 13.5 0v.75c0 2.123.8 4.057 2.118 5.52a.75.75 0 0 1-.297 1.206c-1.544.57-3.16.99-4.831 1.243a3.75 3.75 0 1 1-7.48 0 24.585 24.585 0 0 1-4.831-1.244.75.75 0 0 1-.298-1.205A8.217 8.217 0 0 0 5.25 9.75V9Zm4.502 8.9a2.25 2.25 0 1 0 4.496 0 25.057 25.057 0 0 1-4.496 0Z";
            case "settings" -> "M11.078 2.25c-.917 0-1.699.663-1.85 1.567L9.05 4.889c-.02.12-.115.26-.297.348a7.493 7.493 0 0 0-.986.57c-.166.115-.334.126-.45.083L6.3 5.508a1.875 1.875 0 0 0-2.282.819l-.922 1.597a1.875 1.875 0 0 0 .432 2.385l.84.692c.095.078.17.229.154.43a7.598 7.598 0 0 0 0 1.139c.015.2-.059.352-.153.43l-.841.692a1.875 1.875 0 0 0-.432 2.385l.922 1.597a1.875 1.875 0 0 0 2.282.818l1.019-.382c.115-.043.283-.031.45.082.312.214.641.405.985.57.182.088.277.228.297.35l.178 1.071c.151.904.933 1.567 1.85 1.567h1.844c.916 0 1.699-.663 1.85-1.567l.178-1.072c.02-.12.114-.26.297-.349.344-.165.673-.356.985-.57.167-.114.335-.125.45-.082l1.02.382a1.875 1.875 0 0 0 2.28-.819l.923-1.597a1.875 1.875 0 0 0-.432-2.385l-.84-.692c-.095-.078-.17-.229-.154-.43a7.614 7.614 0 0 0 0-1.139c-.016-.2.059-.352.153-.43l.84-.692c.708-.582.891-1.59.433-2.385l-.922-1.597a1.875 1.875 0 0 0-2.282-.818l-1.02.382c-.114.043-.282.031-.449-.083a7.49 7.49 0 0 0-.985-.57c-.183-.087-.277-.227-.297-.348l-.179-1.072a1.875 1.875 0 0 0-1.85-1.567h-1.843ZM12 15.75a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5Z";
            case "sparkles" -> "M10.7881 3.213C11.2364 2.13505 12.7635 2.13505 13.2118 3.213L15.2938 8.21871L20.6979 8.65196C21.8616 8.74525 22.3335 10.1975 21.4469 10.957L17.3295 14.484L18.5874 19.7575C18.8583 20.8931 17.6229 21.7906 16.6266 21.1821L11.9999 18.3561L7.37329 21.1821C6.37697 21.7906 5.14158 20.8931 5.41246 19.7575L6.67038 14.484L2.55303 10.957C1.66639 10.1975 2.13826 8.74525 3.302 8.65196L8.70609 8.21871L10.7881 3.213Z";
            case "arrow_left" -> "M15.28 5.22a.75.75 0 0 1 0 1.06L9.56 12l5.72 5.72a.75.75 0 0 1-1.06 1.06l-6.25-6.25a.75.75 0 0 1 0-1.06l6.25-6.25a.75.75 0 0 1 1.06 0Z";
            case "reply" -> "M3.44 8.47a.75.75 0 0 0 0 1.06l5.25 5.25a.75.75 0 1 0 1.06-1.06L5.81 9.97h9.44a5.25 5.25 0 1 1 0 10.5h-3a.75.75 0 0 0 0 1.5h3a6.75 6.75 0 1 0 0-13.5H5.81l3.94-3.94a.75.75 0 0 0-1.06-1.06L3.44 8.47Z";
            case "xmark" -> "M5.46967 5.46967C5.76256 5.17678 6.23744 5.17678 6.53033 5.46967L12 10.9393L17.4697 5.46967C17.7626 5.17678 18.2374 5.17678 18.5303 5.46967C18.8232 5.76256 18.8232 6.23744 18.5303 6.53033L13.0607 12L18.5303 17.4697C18.8232 17.7626 18.8232 18.2374 18.5303 18.5303C18.2374 18.8232 17.7626 18.8232 17.4697 18.5303L12 13.0607L6.53033 18.5303C6.23744 18.8232 5.76256 18.8232 5.46967 18.5303C5.17678 18.2374 5.17678 17.7626 5.46967 17.4697L10.9393 12L5.46967 6.53033C5.17678 6.23744 5.17678 5.76256 5.46967 5.46967Z";
            case "envelope" -> "M1.5 8.67v8.58a3 3 0 0 0 3 3h15a3 3 0 0 0 3-3V8.67l-8.928 5.493a3 3 0 0 1-3.144 0L1.5 8.67Z M22.5 6.908V6.75a3 3 0 0 0-3-3h-15a3 3 0 0 0-3 3v.158l9.714 5.978a1.5 1.5 0 0 0 1.572 0L22.5 6.908Z";
            case "folder" -> "M3.75 5.25A2.25 2.25 0 0 1 6 3h2.25c.414 0 .81.168 1.103.467l1.28 1.316c.14.144.333.225.533.225H18A2.25 2.25 0 0 1 20.25 7.25v9.5A2.25 2.25 0 0 1 18 19H6A2.25 2.25 0 0 1 3.75 16.75v-11.5Z";
            case "inbox" -> "M1.5 3.75A2.25 2.25 0 0 1 3.75 1.5h16.5a2.25 2.25 0 0 1 2.25 2.25v10.06a2.25 2.25 0 0 1-1.02 1.89l-7.73 5.15a3 3 0 0 1-3.34 0l-7.73-5.15a2.25 2.25 0 0 1-1.02-1.89V3.75Zm4.5 8.25a.75.75 0 0 0 0 1.5h12a.75.75 0 0 0 0-1.5H6Z";
            case "paper" -> "M3.105 3.105a.75.75 0 0 1 .826-.164l17.25 7.5a.75.75 0 0 1 0 1.376l-17.25 7.5a.75.75 0 0 1-1.06-.824l1.89-6.62-1.89-6.62a.75.75 0 0 1 .234-.768Zm3.455 9.425-1.384 4.846L18.95 11.5 5.176 5.624 6.56 10.47h5.69a.75.75 0 0 1 0 1.5H6.56Z";
            case "trash" -> "M5 3.25V4H2.75C2.33579 4 2 4.33579 2 4.75C2 5.16421 2.33579 5.5 2.75 5.5H3.05L3.86493 13.6493C3.94161 14.4161 4.58685 15 5.35748 15H10.6425C11.4131 15 12.0584 14.4161 12.1351 13.6493L12.95 5.5H13.25C13.6642 5.5 14 5.16421 14 4.75C14 4.33579 13.6642 4 13.25 4H11V3.25C11 2.00736 9.99264 1 8.75 1H7.25C6.00736 1 5 2.00736 5 3.25Z";
            case "star" -> "M10.788 3.21c.448-1.077 1.976-1.077 2.424 0l2.082 5.006 5.404.434c1.164.093 1.636 1.545.749 2.305l-4.117 3.527 1.257 5.273c.271 1.136-.964 2.033-1.96 1.425L12 18.354 7.373 21.18c-.996.608-2.231-.29-1.96-1.425l1.257-5.273-4.117-3.527c-.887-.76-.415-2.212.749-2.305l5.404-.434 2.082-5.005Z";
            case "chat" -> "M20.25 12a8.25 8.25 0 1 1-14.08-5.82A8.25 8.25 0 0 1 20.25 12Zm-6.53-1.28a.75.75 0 0 0-1.06-1.06L11.25 11.07l-1.41-1.41a.75.75 0 0 0-1.06 1.06l1.41 1.41-1.41 1.41a.75.75 0 1 0 1.06 1.06l1.41-1.41 1.41 1.41a.75.75 0 1 0 1.06-1.06l-1.41-1.41 1.41-1.41Z";
            case "refresh" -> "M12 4.5a7.5 7.5 0 0 1 6.53 3.81.75.75 0 1 0 1.3-.75A9 9 0 1 0 21 12a.75.75 0 0 0-1.5 0A7.5 7.5 0 1 1 12 4.5Zm8.03-1.28a.75.75 0 0 0-1.06 0L16.5 5.69V3.75a.75.75 0 0 0-1.5 0V7.5a.75.75 0 0 0 .75.75h3.75a.75.75 0 0 0 0-1.5h-1.94l2.47-2.47a.75.75 0 0 0 0-1.06Z";
            case "history" -> "M12 2.25c-5.385 0-9.75 4.365-9.75 9.75s4.365 9.75 9.75 9.75 9.75-4.365 9.75-9.75S17.385 2.25 12 2.25ZM12.75 6a.75.75 0 0 0-1.5 0v6c0 .414.336.75.75.75h4.5a.75.75 0 0 0 0-1.5h-3.75V6Z";
            default -> "M12 3c4.97 0 9 4.03 9 9s-4.03 9-9 9-9-4.03-9-9 4.03-9 9-9Z";
        };
    }

    private Node createProfileAvatarNode(double size) {
        Circle fallback = new Circle(size / 2.0, Color.web("#9da38f"));
        Node fallbackIcon = createIcon("chat", size * 0.5, "-primary-foreground");
        StackPane avatarStack = new StackPane(fallback, fallbackIcon);
        avatarStack.setMinSize(size, size);
        avatarStack.setPrefSize(size, size);

        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return avatarStack;
        }

        Image image = new Image(profileImageUrl, size, size, true, true, true);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(false);
        Circle clip = new Circle(size / 2.0, size / 2.0, size / 2.0);
        imageView.setClip(clip);

        image.errorProperty().addListener((obs, wasError, isError) -> {
            if (Boolean.TRUE.equals(isError)) {
                imageView.setVisible(false);
            }
        });

        avatarStack.getChildren().add(imageView);
        
        avatarStack.setOnMouseClicked(e -> showProfileMenu(avatarStack));
        avatarStack.setStyle("-fx-cursor: hand;");
        
        return avatarStack;
    }

    private void showProfileMenu(Node anchorNode) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        VBox menuBox = new VBox();
        menuBox.setPrefWidth(220);
        menuBox.setStyle("-fx-background-color: #0f0f0f; -fx-padding: 0; -fx-background-radius: 8; -fx-border-color: #27272a; -fx-border-radius: 8; -fx-border-width: 1; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 15, 0, 0, 8);");

        // Header
        VBox header = new VBox(2);
        header.setStyle("-fx-padding: 12 16 12 16;");
        Label nameLbl = new Label("My Account");
        nameLbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        String emailStr = (mailboxStats != null && mailboxStats.getEmailAddress() != null && !mailboxStats.getEmailAddress().isBlank()) ? mailboxStats.getEmailAddress() : "user@example.com";
        Label emailLbl = new Label(emailStr);
        emailLbl.setStyle("-fx-text-fill: #a1a1aa; -fx-font-size: 12px;");
        header.getChildren().addAll(nameLbl, emailLbl);

        Region sep1 = new Region();
        sep1.setMinHeight(1);
        sep1.setStyle("-fx-background-color: #27272a;");

        Region sep2 = new Region();
        sep2.setMinHeight(1);
        sep2.setStyle("-fx-background-color: #27272a;");

        HBox logoutBox = createMenuOption("Logout", "arrow_left");
        // Make logout button red
        logoutBox.setOnMouseEntered(e -> logoutBox.setStyle("-fx-padding: 10 16; -fx-cursor: hand; -fx-background-color: #dc2626;"));
        logoutBox.setOnMouseExited(e -> logoutBox.setStyle("-fx-padding: 10 16; -fx-cursor: hand; -fx-background-color: #b91c1c;"));
        logoutBox.setStyle("-fx-padding: 10 16; -fx-cursor: hand; -fx-background-color: #b91c1c;");
        logoutBox.setOnMouseClicked(e -> {
            popup.hide();
            if (onLogout != null) {
                onLogout.run();
            }
        });

        VBox section2 = new VBox(logoutBox);
        section2.setStyle("-fx-padding: 4 0;");

        menuBox.getChildren().addAll(header, sep1, sep2, section2);

        popup.getContent().add(menuBox);

        Bounds bounds = anchorNode.localToScreen(anchorNode.getBoundsInLocal());
        if (bounds != null) {
            popup.show(anchorNode, bounds.getMaxX() - 220, bounds.getMaxY() + 8);
        }
    }

    private HBox createMenuOption(String text, String iconKey) {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-padding: 10 16; -fx-cursor: hand;");
        box.setOnMouseEntered(e -> box.setStyle("-fx-padding: 10 16; -fx-cursor: hand; -fx-background-color: #27272a;"));
        box.setOnMouseExited(e -> box.setStyle("-fx-padding: 10 16; -fx-cursor: hand; -fx-background-color: transparent;"));
        Node icon = createIcon(iconKey, 16, "-muted-foreground");
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: -foreground; -fx-font-size: 13px; -fx-font-weight: 600;");
        box.getChildren().addAll(icon, lbl);
        return box;
    }

    private String iconForFolder(String folderName) {
        String normalized = folderName == null ? "" : folderName.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INBOX" -> "inbox";
            case "SENT" -> "paper";
            case "DRAFT" -> "envelope";
            case "IMPORTANT" -> "star";
            case "STARRED" -> "star";
            case "TRASH" -> "trash";
            case "CHAT" -> "chat";
            case "SPAM" -> "chat";
            default -> "folder";
        };
    }

    private String iconForCategory(String categoryName) {
        String normalized = categoryName == null ? "" : categoryName.toLowerCase(Locale.ROOT);
        if (normalized.contains("alert") || normalized.contains("action") || normalized.contains("security")) {
            return "bell";
        }
        if (normalized.contains("promotion") || normalized.contains("social") || normalized.contains("forum")) {
            return "chat";
        }
        if (normalized.contains("bill") || normalized.contains("payment") || normalized.contains("account")) {
            return "star";
        }
        return "folder";
    }

    private String bucketForCategory(String categoryName) {
        String key = canonicalCategoryKey(categoryName);
        if (PERSONAL_DEFAULTS.contains(key)) {
            return "personal";
        }
        if (WORK_DEFAULTS.contains(key)) {
            return "work";
        }
        if (MISC_DEFAULTS.contains(key)) {
            return "misc";
        }
        return "other";
    }

    private static String canonicalCategoryKey(String categoryName) {
        if (categoryName == null) {
            return "";
        }
        return categoryName
            .replace("CATEGORY_", "")
            .replace('_', ' ')
            .replace('&', ' ')
            .replaceAll("\\s+", " ")
            .trim()
            .toUpperCase(Locale.ROOT);
    }

    private static String normalizeCategoryLabel(String name) {
        String cleaned = name.replace("CATEGORY_", "");
        cleaned = cleaned.replace('_', ' ').toLowerCase(Locale.ROOT);
        String[] tokens = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return sb.toString();
    }

    private void refreshMessageList() {
        String query = safeLower(listSearchField.getText());
        List<EmailMessage> visibleEmails = new ArrayList<>();
        for (EmailMessage email : allEmails) {
            if (matchesQuery(email, query) && matchesActiveFilter(email)) {
                visibleEmails.add(email);
            }
        }

        if (selectedEmail == null && !visibleEmails.isEmpty()) {
            renderMessageState();
        }

        mailList.getItems().setAll(visibleEmails);
        mailList.refresh(); // force cells to re-render in case selectedEmail changed

        if (isLoadingEmails) {
            emptyListLabel.setVisible(false);
            emptyListLabel.setManaged(false);
        } else {
            emptyListLabel.setVisible(visibleEmails.isEmpty());
            emptyListLabel.setManaged(visibleEmails.isEmpty());
            if (visibleEmails.isEmpty() && !allEmails.isEmpty()) {
                emptyListLabel.setText("No messages to show.");
            }
        }
    }

    private boolean matchesActiveFilter(EmailMessage email) {
        if (activeFilterLabel == null || activeFilterLabel.isBlank()) return true;
        String filterKey = canonicalCategoryKey(activeFilterLabel);
        if (email.getLabels() != null) {
            for (String lbl : email.getLabels()) {
                if (lbl == null) continue;
                // Exact case-insensitive match
                if (lbl.equalsIgnoreCase(activeFilterLabel)) return true;
                // Canonical key match (handles CATEGORY_ prefix, underscores, ampersands)
                if (canonicalCategoryKey(lbl).equals(filterKey)) return true;
                // Comma-separated fallback (labels stored from processed_emails)
                for (String part : lbl.split(",")) {
                    String trimmed = part.trim();
                    if (trimmed.equalsIgnoreCase(activeFilterLabel)) return true;
                    if (canonicalCategoryKey(trimmed).equals(filterKey)) return true;
                }
            }
        }
        return false;
    }

    private boolean matchesQuery(EmailMessage email, String query) {
        if (query.isBlank()) return true;
        return safeLower(email.getSender()).contains(query)
            || safeLower(email.getSubject()).contains(query)
            || safeLower(email.getBody()).contains(query);
    }

    private StackPane mailCard(EmailMessage email, boolean active, int listIndex) {
        // listIndex: 0-based position in the currently visible list (newest = 0)
        int displayNumber;
        if (activeFilterLabel != null && !activeFilterLabel.isBlank()) {
            // Filtered view: #1 = first result (newest), #2 second, etc.
            displayNumber = listIndex + 1;
        } else {
            // Unfiltered view: #1 = oldest, #N = newest
            // allEmails list is newest-first (Gmail order), so oldest = allEmails.size()
            // visible list is also newest-first, so index 0 = allEmails.size()-th item
            int total = mailList.getItems().size();
            displayNumber = total - listIndex;
        }
        // Derive custom labels early so we can use the primary label color for the sender
        List<String> customLabels = customLabelsOnly(email.getLabels());
        String primaryLabel = customLabels.isEmpty() ? null : customLabels.get(0);
        String labelColorHex = primaryLabel != null ? "#" + getLabelColorHex(primaryLabel) : null;

        // --- Row 1: @senderEmail (colored by label) ---
        boolean showUnread = isUnreadLocally(email);

        String rawSender = safeText(email.getSender());
        // Extract the email address inside '<...>' if present, otherwise use raw string
        String senderEmail;
        if (rawSender.contains("<") && rawSender.contains(">")) {
            int start = rawSender.indexOf('<') + 1;
            int end = rawSender.indexOf('>');
            senderEmail = (start < end) ? rawSender.substring(start, end).trim() : rawSender;
        } else {
            senderEmail = rawSender;
        }
        if (senderEmail.isBlank()) senderEmail = rawSender;

        Label senderLabel = new Label("@" + senderEmail);
        String senderColor = (labelColorHex != null) ? labelColorHex : "-muted-foreground";
        senderLabel.setStyle("-fx-text-fill: " + senderColor + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        senderLabel.setMaxWidth(Double.MAX_VALUE);
        senderLabel.setMinWidth(0);
        senderLabel.setWrapText(false);
        senderLabel.setEllipsisString("...");
        HBox.setHgrow(senderLabel, Priority.ALWAYS);

        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().add(senderLabel);

        // --- Row 2: subject ---
        Label subject = new Label(safeText(email.getSubject()));
        subject.setStyle("-fx-text-fill: -foreground; -fx-font-size: 13px; -fx-font-weight: bold;");
        subject.setMaxWidth(Double.MAX_VALUE);
        subject.setMinWidth(0);
        subject.setWrapText(false);
        subject.setEllipsisString("...");

        HBox midRow = new HBox();
        midRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(subject, Priority.ALWAYS);
        midRow.getChildren().add(subject);

        // --- Row 3: #N · date · time ---
        Label posLabel = new Label("#" + displayNumber);
        posLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 10px; -fx-opacity: 0.6;");

        Label dateLabel = new Label(formatDateShort(email.getDate()));
        dateLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 10px;");

        Label timeLabel = new Label(formatTimeOnly(email.getDate()));
        timeLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 10px;");

        Label dot1 = new Label("·");
        dot1.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 10px; -fx-opacity: 0.4;");

        Label dot2 = new Label("·");
        dot2.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 10px; -fx-opacity: 0.4;");

        HBox bottomRow = new HBox(5);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.getChildren().addAll(posLabel, dot1, dateLabel, dot2, timeLabel);

        VBox card = new VBox(3);
        card.setStyle("-fx-background-color: transparent; -fx-padding: 10 20;");
        card.getChildren().addAll(topRow, midRow, bottomRow);

        StackPane root = new StackPane();
        root.setStyle(
            "-fx-background-color: " + (active ? "-accent" : "transparent") +
            "; -fx-border-color: transparent transparent -border transparent; -fx-border-width: 0 0 1 0; -fx-cursor: hand;"
        );

        // customLabels already derived above

        if (!customLabels.isEmpty()) {
            Image avatarImg;
            if (labelAvatarCache.containsKey(primaryLabel)) {
                avatarImg = labelAvatarCache.get(primaryLabel);
            } else {
                String seed = java.net.URLEncoder.encode(primaryLabel, java.nio.charset.StandardCharsets.UTF_8);
                String hex = getLabelColorHex(primaryLabel);
                String diceBearUrl = "https://api.dicebear.com/9.x/initials/png?seed=" + seed + "&backgroundColor=" + hex + "&radius=0&size=120&backgroundType=solid";
                avatarImg = new Image(diceBearUrl, true);
                labelAvatarCache.put(primaryLabel, avatarImg);
            }

            ImageView bgImage = new ImageView(avatarImg);
            bgImage.setPreserveRatio(false);
            bgImage.setFitWidth(70);
            bgImage.fitHeightProperty().bind(card.heightProperty());

            javafx.scene.shape.Rectangle fadeMask = new javafx.scene.shape.Rectangle();
            fadeMask.widthProperty().bind(bgImage.fitWidthProperty());
            fadeMask.heightProperty().bind(card.heightProperty());
            javafx.scene.paint.LinearGradient fadeGrad = new javafx.scene.paint.LinearGradient(
                0, 0, 1, 0, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.color(1, 1, 1, 0.0)),
                new javafx.scene.paint.Stop(0.15, javafx.scene.paint.Color.color(1, 1, 1, 0.4)),
                new javafx.scene.paint.Stop(0.6, javafx.scene.paint.Color.color(1, 1, 1, 1.0)),
                new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.color(1, 1, 1, 1.0))
            );
            fadeMask.setFill(fadeGrad);
            bgImage.setOpacity(1.0);
            bgImage.setClip(fadeMask);
            bgImage.setMouseTransparent(true);
            bgImage.setManaged(false);
            bgImage.layoutXProperty().bind(root.widthProperty().subtract(bgImage.fitWidthProperty()));
            bgImage.layoutYProperty().bind(card.layoutYProperty());

            card.setStyle("-fx-background-color: transparent; -fx-padding: 10 60 10 20;");
            root.getChildren().addAll(bgImage, card);
        } else {
            // No custom labels — show plain chips if any (will be empty for system-only emails)
            FlowPane chipsRow = createLabelChips(customLabels);
            if (!chipsRow.getChildren().isEmpty()) {
                card.getChildren().add(chipsRow);
            }
            root.getChildren().add(card);
        }

        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(0);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setMinWidth(0);

        // Add unread indicator triangle in top-left corner (absolutely positioned)
        if (showUnread) {
            javafx.scene.shape.Polygon unreadTriangle = new javafx.scene.shape.Polygon();
            unreadTriangle.getPoints().addAll(
                0.0, 0.0,     // top-left corner
                16.0, 0.0,    // top-right (16px wide)
                0.0, 16.0     // bottom-left (16px tall)
            );
            unreadTriangle.setFill(Color.web("#ef4444")); // Bright red
            unreadTriangle.setManaged(false); // Don't let layout manager position it
            unreadTriangle.setMouseTransparent(true); // Allow clicks to pass through
            
            // Position at absolute top-left corner
            unreadTriangle.setLayoutX(0);
            unreadTriangle.setLayoutY(0);
            
            root.getChildren().add(unreadTriangle);
            StackPane.setAlignment(unreadTriangle, Pos.TOP_LEFT);
        }

        // NOTE: primary click is handled by ListCell.setOnMouseClicked above.
        // This fallback ensures clicking directly on card graphics also works.
        root.setOnMouseClicked(e -> {
            selectEmail(email);
            refreshMessageList();
        });

        return root;
    }

    private void selectEmail(EmailMessage email) {
        selectedEmail = email;
        if (isUnreadLocally(email)) {
            locallyReadMessageIds.add(email.getId());
            email.setUnread(false);
        }
        
        // Animate to show email content
        animateToEmailView();
        
        subjectLabelHeader.setText(safeText(email.getSubject()));
        fromValueLabel.setText(safeText(email.getSender()));
        String combinedDateTime = formatDateShort(email.getDate()) + " " + formatTimeOnly(email.getDate());
        dateValueLabel.setText(combinedDateTime);
        typeValueLabel.setText("Type: Unknown");
        
        headerLabelsPane.getChildren().clear();
        labelIconContainer.getChildren().clear();
        List<String> customLabels = customLabelsOnly(email.getLabels());
        if (!customLabels.isEmpty()) {
            for (String lbl : customLabels) {
                headerLabelsPane.getChildren().add(createChip(lbl, getLabelColor(lbl)));
            }
            String pLabel = customLabels.get(0);
            Node icon = createIcon(iconForCategory(pLabel), 16, getLabelColorHex(pLabel));
            labelIconContainer.getChildren().add(icon);
            typeValueLabel.setText("Type: " + pLabel);
        } else {
            labelIconContainer.getChildren().add(createIcon("folder", 16, "-muted-foreground"));
            typeValueLabel.setText("Type: Unknown");
        }
        
        bodyView.getEngine().loadContent(safeText(email.getBody()));
        renderMessageState();
    }

    public void setEmails(List<EmailMessage> emails) {
        String previouslySelectedId = selectedEmail == null ? null : selectedEmail.getId();
        allEmails.clear();
        if (emails != null) {
            allEmails.addAll(emails);
        }
        for (EmailMessage email : allEmails) {
            if (locallyReadMessageIds.contains(email.getId())) {
                email.setUnread(false);
            }
        }
        selectedEmail = null;
        if (previouslySelectedId != null) {
            for (EmailMessage email : allEmails) {
                if (previouslySelectedId.equals(email.getId())) {
                    selectedEmail = email;
                    subjectLabelHeader.setText(safeText(email.getSubject()));
                    fromValueLabel.setText(shortSender(email.getSender()));
                    dateValueLabel.setText(formatDate(email.getDate()));
                    bodyView.getEngine().loadContent(safeText(email.getBody()));
                    break;
                }
            }
        }
        loadedMailCount = allEmails.size();
        refreshStatsUi();
        renderMessageState();
        refreshMessageList();
        if (!allEmails.isEmpty() && isLoadingEmails) {
            setLoadingState(false, null);
        }
    }

    public void setLabels(List<MailboxLabel> labels) {
        allLabels.clear();
        if (labels != null) {
            allLabels.addAll(labels);
        }
        refreshSidebarLabels();
    }

    /**
     * Merges the given emails into the current list without discarding existing ones.
     * Emails already present (by ID) are updated in-place; new ones are appended.
     * Called by the priority label-fetch to push freshly fetched label emails.
     */
    public void mergeEmails(List<EmailMessage> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        // Build a fast ID lookup of what we already have
        java.util.Map<String, EmailMessage> existing = new java.util.LinkedHashMap<>();
        for (EmailMessage e : allEmails) {
            if (e.getId() != null) existing.put(e.getId(), e);
        }
        for (EmailMessage e : incoming) {
            if (e == null || e.getId() == null) continue;
            if (!existing.containsKey(e.getId())) {
                existing.put(e.getId(), e);
                allEmails.add(e);
            }
        }
        // Apply local read-state
        for (EmailMessage email : allEmails) {
            if (locallyReadMessageIds.contains(email.getId())) {
                email.setUnread(false);
            }
        }
        loadedMailCount = allEmails.size();
        refreshStatsUi();
        refreshMessageList();
        if (!allEmails.isEmpty() && isLoadingEmails) {
            setLoadingState(false, null);
        }
    }
    
    /**
     * Updates local email labels based on the labeling results.
     * This avoids the need to refresh from Gmail after labeling.
     * 
     * @param labeledEmails Map of email ID to new label
     */
    private void updateLocalEmailLabels(java.util.Map<String, String> labeledEmails) {
        if (labeledEmails == null || labeledEmails.isEmpty()) {
            return;
        }
        
        int updated = 0;
        java.util.List<EmailMessage> updatedEmails = new java.util.ArrayList<>();
        
        for (EmailMessage email : allEmails) {
            String newLabel = labeledEmails.get(email.getId());
            if (newLabel != null) {
                // Create a new mutable list with updated labels
                java.util.List<String> updatedLabels = new java.util.ArrayList<>();
                
                // Keep system labels (INBOX, UNREAD, etc.) but remove old category labels
                if (email.getLabels() != null) {
                    for (String label : email.getLabels()) {
                        if (label == null) continue;
                        
                        // Check if it's a system label
                        boolean isSystemLabel = label.equals("INBOX") || label.equals("UNREAD") || 
                            label.equals("SENT") || label.equals("DRAFT") || label.equals("STARRED") ||
                            label.equals("IMPORTANT") || label.equals("TRASH") || label.equals("SPAM");
                        
                        // Check if it's a configured category (to be removed)
                        boolean isCategoryLabel = false;
                        for (String category : configuredCategories) {
                            if (label.equalsIgnoreCase(category) || 
                                label.replace("CATEGORY_", "").replace('_', ' ').equalsIgnoreCase(category)) {
                                isCategoryLabel = true;
                                break;
                            }
                        }
                        
                        // Keep system labels, remove category labels
                        if (isSystemLabel && !isCategoryLabel) {
                            updatedLabels.add(label);
                        }
                    }
                }
                
                // Add the new category label
                updatedLabels.add(newLabel);
                
                // Create new EmailMessage with updated labels
                EmailMessage updatedEmail = new EmailMessage(
                    email.getId(),
                    email.getSubject(),
                    email.getSender(),
                    email.getDate(),
                    email.getBody(),
                    updatedLabels,
                    email.isUnread()
                );
                updatedEmails.add(updatedEmail);
                updated++;
                System.out.println("Updated email '" + email.getSubject() + "' with label: " + newLabel);
                System.out.println("  Old labels: " + email.getLabels());
                System.out.println("  New labels: " + updatedLabels);
            } else {
                // Keep email as-is
                updatedEmails.add(email);
            }
        }
        
        // Replace allEmails with updated list
        allEmails.clear();
        allEmails.addAll(updatedEmails);
        
        System.out.println("Updated " + updated + " emails with new labels locally");
        System.out.println("Total emails in list: " + allEmails.size());
        
        // Update counts
        loadedMailCount = allEmails.size();
        refreshStatsUi();
        
        // Update label counts in sidebar
        refreshSidebarLabels();
    }

    public void showError(String message) {
        allEmails.clear();
        selectedEmail = null;
        subjectLabelHeader.setText("Inbox unavailable");
        fromValueLabel.setText("-");
        dateValueLabel.setText("-");
        String msg = message == null ? "Unknown error while loading inbox." : message;
        bodyView.getEngine().loadContent("<p>" + msg + "</p>");
        renderMessageState();
        refreshMessageList();
        setLoadingState(false, "Failed to load emails");
    }

    public void setLoadingState(boolean isLoading, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setLoadingState(isLoading, message));
            return;
        }
        this.isLoadingEmails = isLoading;
        navLoadingBox.setOpacity(isLoading ? 1.0 : 0.0);
        if (message != null) {
            navLoadingLabel.setText(message);
        }
        
        if (isLoading) {
            emptyListLabel.setVisible(false);
            emptyListLabel.setManaged(false);
        } else {
            boolean empty = allEmails.isEmpty();
            emptyListLabel.setVisible(empty);
            emptyListLabel.setManaged(empty);
            if (empty && message != null && (message.toLowerCase().contains("failed") || message.toLowerCase().contains("error"))) {
                emptyListLabel.setText("Failed to load emails");
            } else if (empty) {
                emptyListLabel.setText("No emails found");
            }
        }
    }

    /** Called by MainApplication to hook priority-label fetching into the sidebar clicks. */
    public void setOnLabelFetchRequested(Consumer<String> callback) {
        this.onLabelFetchRequested = callback;
    }

    private void renderMessageState() {
        boolean hasSelection = selectedEmail != null;
        messageView.setManaged(hasSelection);
        messageView.setVisible(hasSelection);
        messagePlaceholder.setManaged(!hasSelection);
        messagePlaceholder.setVisible(!hasSelection);
    }

    private static String shortSender(String sender) {
        String text = safeText(sender);
        int lt = text.indexOf('<');
        if (lt > 0) return text.substring(0, lt).trim();
        return text;
    }

    // ---- Date/time helpers: parse once, convert to system locale & timezone ----

    /** Parses a raw RFC-1123 date string into a ZonedDateTime in the system timezone. */
    private static ZonedDateTime parseToSystemZone(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return null;
        try {
            ZonedDateTime utc = ZonedDateTime.parse(rawDate, DateTimeFormatter.RFC_1123_DATE_TIME);
            return utc.withZoneSameInstant(ZoneId.systemDefault());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * Formats only the time portion using the system locale's SHORT time style
     * (honours 12/24-hr preference from the OS locale).
     */
    private static String formatTimeOnly(String rawDate) {
        ZonedDateTime zdt = parseToSystemZone(rawDate);
        if (zdt == null) return "-";
        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(Locale.getDefault());
        return zdt.format(fmt);
    }

    /**
     * Formats a full date+time using the system locale (used in the detail view).
     */
    private static String formatDate(String rawDate) {
        ZonedDateTime zdt = parseToSystemZone(rawDate);
        if (zdt == null) return "-";
        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(Locale.getDefault());
        return zdt.format(fmt);
    }

    /**
     * Formats just the date portion using the system locale's SHORT date style
     * (honours dd/MM/yyyy vs MM/dd/yyyy etc.).
     */
    private static String formatDateShort(String rawDate) {
        ZonedDateTime zdt = parseToSystemZone(rawDate);
        if (zdt == null) return "-";
        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault());
        return zdt.format(fmt);
    }

    private boolean isUnreadLocally(EmailMessage email) {
        if (email == null || email.getId() == null) {
            return false;
        }
        return email.isUnread() && !locallyReadMessageIds.contains(email.getId());
    }

    private static String safeText(String value) {
        if (value == null || value.isBlank()) return "(empty)";
        return value;
    }

    private static String safeLower(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).trim();
    }

    public void setMailboxStats(MailboxStats stats) {
        mailboxStats = stats;
        refreshStatsUi();
    }

    public void updateFetchProgress(long loaded, long target, boolean loadingMore) {
        loadedMailCount = Math.max(0, loaded);
        targetMailCount = Math.max(0, target);
        if (loadingMore) {
            fetchProgressLabel.setText("Loading more emails: " + loadedMailCount + " / " + formatCount(targetMailCount));
        } else {
            fetchProgressLabel.setText("Loaded " + loadedMailCount + " emails");
        }
        refreshStatsUi();
    }

    private void refreshStatsUi() {
        String totalText = mailboxStats == null ? "-" : formatCount(mailboxStats.getTotalMessages());
        String ownerText = mailboxStats == null || mailboxStats.getEmailAddress() == null || mailboxStats.getEmailAddress().isBlank()
            ? ""
            : " (" + mailboxStats.getEmailAddress() + ")";
        statsPrimaryLabel.setText("Mailbox: " + totalText + ownerText);

        if (mailboxStats == null) {
            statsSecondaryLabel.setText("Inbox: -  Unread: -  Threads: -");
        } else {
            statsSecondaryLabel.setText(
                "Inbox: " + formatCount(mailboxStats.getInboxMessages()) +
                "  Unread: " + formatCount(mailboxStats.getUnreadMessages()) +
                "  Threads: " + formatCount(mailboxStats.getTotalThreads())
            );
        }

        if (targetMailCount > 0 && fetchProgressLabel.getText().startsWith("Loading more")) {
            fetchProgressLabel.setText("Loading more emails: " + loadedMailCount + " / " + formatCount(targetMailCount));
        }
    }

    private static String formatCount(long count) {
        return String.format(Locale.ROOT, "%,d", Math.max(0, count));
    }

    public void setStatus(String status) {
        if (status != null && !status.isBlank()) {
            fetchProgressLabel.setText(status);
        }
        if (status != null && (status.toLowerCase().startsWith("error") || status.toLowerCase().contains("failed"))) {
            showError(status);
        }
    }

    public int getLoadedMailCount() {
        return allEmails.size();
    }

    public void setRunning(boolean running) {}

    public void appendLog(String message) {}

    public void updateProgress(JobProgress progress) {}

    public void complete(JobSummary summary) {}

    private FlowPane createLabelChips(List<String> labels) {
        FlowPane flow = new FlowPane();
        flow.setHgap(6);
        flow.setVgap(6);
        flow.setPadding(new Insets(2, 0, 0, 12));
        if (labels == null || labels.isEmpty()) {
            return flow;
        }

        int maxVisible = 2;
        int shown = 0;
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            flow.getChildren().add(createChip(label.trim(), getLabelColor(label.trim())));
            shown++;
            if (shown >= maxVisible) {
                break;
            }
        }
        int hidden = labels.size() - shown;
        if (hidden > 0) {
            flow.getChildren().add(createChip("+" + hidden, javafx.scene.paint.Color.GRAY));
        }
        return flow;
    }

    /**
     * Returns only user-created (custom) labels, stripping all Gmail built-in
     * labels (INBOX, SENT, DRAFT, SPAM, TRASH, STARRED, CATEGORY_*, etc.).
     * Use this for UI rendering; keep the full list for filter matching.
     */
    private static List<String> customLabelsOnly(List<String> labels) {
        if (labels == null || labels.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String lbl : labels) {
            if (lbl == null || lbl.isBlank()) continue;
            if (!isGmailSystemLabel(lbl)) result.add(lbl);
        }
        return result;
    }

    private static boolean isGmailSystemLabel(String label) {
        String upper = label.toUpperCase(Locale.ROOT).trim();
        if (upper.startsWith("CATEGORY_")) return true;
        return switch (upper) {
            case "INBOX", "SENT", "DRAFT", "SPAM", "TRASH",
                 "STARRED", "UNREAD", "IMPORTANT", "CHAT",
                 "YELLOW_STAR", "BLUE_STAR", "RED_STAR", "ORANGE_STAR",
                 "GREEN_STAR", "PURPLE_STAR", "ALL_MAIL" -> true;
            default -> false;
        };
    }

    private javafx.scene.paint.Color getLabelColor(String label) {
        int hash = Math.abs(label.toLowerCase(Locale.ROOT).hashCode());
        double hue = hash % 360;
        return javafx.scene.paint.Color.hsb(hue, 0.6, 0.85);
    }

    private String getLabelColorHex(String label) {
        javafx.scene.paint.Color color = getLabelColor(label);
        return String.format("%02x%02x%02x",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }

    private Label createChip(String text, javafx.scene.paint.Color color) {
        Label chip = new Label(text);
        String hex = String.format("#%02x%02x%02x",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
        
        chip.setStyle(
            "-fx-text-fill: -foreground; " +
            "-fx-font-size: 10px; " +
            "-fx-padding: 2 8; " +
            "-fx-background-color: transparent; " +
            "-fx-border-color: " + hex + "; " +
            "-fx-border-width: 1; " +
            "-fx-background-radius: 999; " +
            "-fx-border-radius: 999;"
        );
        return chip;
    }
    
    /**
     * Animates the layout to show email content on the right.
     * Removes left spacer, keeps list on left, shows viewPane on right.
     */
    private void animateToEmailView() {
        if (contentArea.getChildren().contains(viewPane)) {
            return; // Already in email view
        }
        
        // Create fade-in animation for viewPane
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(300), viewPane
        );
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        
        // Remove spacers and add viewPane
        contentArea.getChildren().clear();
        contentArea.getChildren().addAll(listPane, viewPane);
        
        // Start animation
        viewPane.setOpacity(0.0);
        fadeIn.play();
    }
    
    /**
     * Animates the layout back to centered mail list.
     * Shows spacers on both sides, hides viewPane.
     */
    private void animateToEmptyState() {
        if (!contentArea.getChildren().contains(viewPane)) {
            return; // Already in empty state
        }
        
        // Create fade-out animation for viewPane
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(200), viewPane
        );
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        fadeOut.setOnFinished(e -> {
            // After fade out, switch to centered layout
            contentArea.getChildren().clear();
            contentArea.getChildren().addAll(leftSpacer, listPane, rightSpacer);
        });
        
        fadeOut.play();
    }
    
    /**
     * Toggles between mail list view and labeling mode.
     */
    private void toggleLabelingMode() {
        // Close history mode if open
        if (isHistoryMode) {
            isHistoryMode = false;
            hideHistoryPanel();
        }
        
        isLabelingMode = !isLabelingMode;
        
        if (isLabelingMode) {
            showLabelingPanel();
        } else {
            hideLabelingPanel();
        }
    }
    
    /**
     * Shows the labeling panel and hides the mail list.
     */
    private void showLabelingPanel() {
        if (labelingPanel == null) {
            labelingPanel = createLabelingPanel();
        }
        
        // Animate transition
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(200), listPane
        );
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        fadeOut.setOnFinished(e -> {
            contentArea.getChildren().remove(listPane);
            contentArea.getChildren().add(1, labelingPanel);
            
            javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(300), labelingPanel
            );
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            labelingPanel.setOpacity(0.0);
            fadeIn.play();
        });
        
        fadeOut.play();
    }
    
    /**
     * Hides the labeling panel and shows the mail list.
     */
    private void hideLabelingPanel() {
        if (labelingPanel == null) return;
        
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(200), labelingPanel
        );
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        fadeOut.setOnFinished(e -> {
            contentArea.getChildren().remove(labelingPanel);
            contentArea.getChildren().add(1, listPane);
            
            javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(300), listPane
            );
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            listPane.setOpacity(0.0);
            fadeIn.play();
        });
        
        fadeOut.play();
    }
    
    /**
     * Creates the labeling panel UI with results list.
     */
    private VBox createLabelingPanel() {
        VBox panel = new VBox(20);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setStyle("-fx-padding: 40 20; -fx-background-color: transparent;");
        panel.setMinWidth(0);
        panel.setPrefWidth(800);
        panel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(panel, Priority.ALWAYS);
        
        // Main content container that will be centered when results are empty
        VBox mainContent = new VBox(20);
        mainContent.setAlignment(Pos.CENTER);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        
        // Title
        Label title = new Label("Email Labeling");
        title.setStyle("-fx-text-fill: -foreground; -fx-font-size: 24px; -fx-font-weight: bold;");
        
        // Description
        Label description = new Label("Automatically categorize your emails using AI");
        description.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 14px;");
        
        // Email count input
        VBox emailCountBox = new VBox(8);
        emailCountBox.setAlignment(Pos.CENTER);
        emailCountBox.setMaxWidth(400);
        
        Label emailCountLabel = new Label("Number of emails to process:");
        emailCountLabel.setStyle("-fx-text-fill: -foreground; -fx-font-size: 13px;");
        
        if (labelingEmailCountField == null) {
            labelingEmailCountField = new javafx.scene.control.TextField("100");
            labelingEmailCountField.setPromptText("Enter number (e.g., 100, 500, 1000)");
            labelingEmailCountField.setStyle(
                "-fx-background-color: -card; " +
                "-fx-text-fill: -foreground; " +
                "-fx-prompt-text-fill: -muted-foreground; " +
                "-fx-padding: 8 12; " +
                "-fx-font-size: 14px; " +
                "-fx-border-color: -border; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6;"
            );
            labelingEmailCountField.setMaxWidth(200);
            
            // Only allow numbers
            labelingEmailCountField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.matches("\\d*")) {
                    labelingEmailCountField.setText(newValue.replaceAll("[^\\d]", ""));
                }
            });
        }
        
        emailCountBox.getChildren().addAll(emailCountLabel, labelingEmailCountField);
        
        // Progress area
        VBox progressArea = new VBox(12);
        progressArea.setAlignment(Pos.CENTER);
        progressArea.setMaxWidth(600);
        progressArea.setStyle("-fx-padding: 24; -fx-border-color: -border; -fx-border-width: 1; -fx-background-color: -card; -fx-background-radius: 10; -fx-border-radius: 10;");
        
        // Create or reuse status label
        if (labelingStatusLabel == null) {
            labelingStatusLabel = new Label("Ready to start");
            labelingStatusLabel.setStyle("-fx-text-fill: -foreground; -fx-font-size: 14px; -fx-font-weight: bold;");
        }
        
        // Create or reuse progress indicator
        if (labelingProgressIndicator == null) {
            labelingProgressIndicator = new ProgressIndicator();
            labelingProgressIndicator.setPrefSize(40, 40);
            labelingProgressIndicator.setStyle("-fx-progress-color: -primary;");
            labelingProgressIndicator.setVisible(false);
        }
        
        // Create or reuse progress text
        if (labelingProgressText == null) {
            labelingProgressText = new Label("0 / 0 emails processed");
            labelingProgressText.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 12px;");
        }
        
        progressArea.getChildren().clear();
        progressArea.getChildren().addAll(labelingStatusLabel, labelingProgressIndicator, labelingProgressText);
        
        // Results list
        if (labelingResultsContainer == null) {
            labelingResultsContainer = new VBox(8);
            labelingResultsContainer.setMaxWidth(700);
            labelingResultsContainer.setStyle("-fx-background-color: transparent;");
        }
        labelingResultsContainer.getChildren().clear();
        
        // Create or reuse results title
        if (labelingResultsTitle == null) {
            labelingResultsTitle = new Label("Processing Results");
            labelingResultsTitle.setStyle("-fx-text-fill: -foreground; -fx-font-size: 16px; -fx-font-weight: bold;");
            labelingResultsTitle.setVisible(false);
        }
        
        // Create or reuse results list
        if (labelingResultsList == null) {
            labelingResultsList = new javafx.scene.control.ListView<>();
            labelingResultsList.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-control-inner-background: transparent; " +
                "-fx-background-insets: 0; " +
                "-fx-padding: 0; " +
                "-fx-border-width: 0; " +
                "-fx-focus-color: transparent; " +
                "-fx-faint-focus-color: transparent;"
            );
            labelingResultsList.setPrefHeight(400);
            labelingResultsList.setVisible(false);
            
            labelingResultsList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(com.labless.model.LabelingResult result, boolean empty) {
                    super.updateItem(result, empty);
                    setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-width: 0;");
                    if (empty || result == null) {
                        setGraphic(null);
                    } else {
                        setGraphic(createResultCard(result));
                    }
                }
            });
        }
        
        // Create or reuse results scroll pane
        if (labelingResultsScroll == null) {
            labelingResultsScroll = new ScrollPane(labelingResultsList);
            labelingResultsScroll.setFitToWidth(true);
            labelingResultsScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
            labelingResultsScroll.setVisible(false);
            VBox.setVgrow(labelingResultsScroll, Priority.ALWAYS);
        }
        
        labelingResultsContainer.getChildren().clear();
        labelingResultsContainer.getChildren().addAll(labelingResultsTitle, labelingResultsScroll);
        
        // Load labeling history when panel is first created
        if (labelingResultsList.getItems().isEmpty()) {
            loadLabelingHistory();
        }
        
        // Create or reuse start button
        if (labelingStartButton == null) {
            labelingStartButton = new Button("Start Labeling");
            labelingStartButton.setStyle(
                "-fx-background-color: -primary; " +
                "-fx-text-fill: -primary-foreground; " +
                "-fx-padding: 12 24; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;"
            );
            labelingStartButton.setOnAction(e -> startLabeling());
        }
        
        // Create or reuse stop button
        if (labelingStopButton == null) {
            labelingStopButton = new Button("Stop");
            labelingStopButton.setStyle(
                "-fx-background-color: #ef4444; " +
                "-fx-text-fill: white; " +
                "-fx-padding: 12 24; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;"
            );
            labelingStopButton.setVisible(false);
            labelingStopButton.setManaged(false);
            labelingStopButton.setOnAction(e -> stopLabeling());
        }
        
        // Close button
        Button closeButton = new Button("Back to Emails");
        closeButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: -muted-foreground; " +
            "-fx-padding: 8 16; " +
            "-fx-font-size: 13px; " +
            "-fx-background-radius: 6; " +
            "-fx-border-color: -border; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 6; " +
            "-fx-cursor: hand;"
        );
        closeButton.setOnAction(e -> toggleLabelingMode());
        
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().clear();
        buttons.getChildren().addAll(labelingStartButton, labelingStopButton, closeButton);
        
        // Add main content items
        mainContent.getChildren().addAll(title, description, emailCountBox, progressArea, buttons);
        
        // Add mainContent and resultsContainer to panel
        panel.getChildren().addAll(mainContent, labelingResultsContainer);
        
        // Initially hide results and center content
        labelingResultsContainer.setVisible(false);
        labelingResultsContainer.setManaged(false);
        
        return panel;
    }
    
    /**
     * Creates a result card for a labeled email.
     */
    private VBox createResultCard(com.labless.model.LabelingResult result) {
        VBox card = new VBox(6);
        card.setStyle(
            "-fx-padding: 12 16; " +
            "-fx-background-color: -card; " +
            "-fx-border-color: -border; " +
            "-fx-border-width: 1; " +
            "-fx-background-radius: 8; " +
            "-fx-border-radius: 8;"
        );
        card.setMaxWidth(Double.MAX_VALUE);
        
        // Subject line
        Label subjectLabel = new Label(result.getSubject());
        subjectLabel.setStyle("-fx-text-fill: -foreground; -fx-font-size: 13px; -fx-font-weight: bold;");
        subjectLabel.setWrapText(true);
        subjectLabel.setMaxWidth(Double.MAX_VALUE);
        
        // Sender
        Label senderLabel = new Label("From: " + result.getSender());
        senderLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 11px;");
        
        // Category with color and actions
        HBox categoryBox = new HBox(8);
        categoryBox.setAlignment(Pos.CENTER_LEFT);
        
        Label categoryLabel = new Label(result.getCategory());
        javafx.scene.paint.Color labelColor = getLabelColor(result.getCategory());
        String colorHex = String.format("#%02x%02x%02x",
            (int) (labelColor.getRed() * 255),
            (int) (labelColor.getGreen() * 255),
            (int) (labelColor.getBlue() * 255));
        categoryLabel.setStyle(
            "-fx-text-fill: " + colorHex + "; " +
            "-fx-font-size: 12px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 4 8; " +
            "-fx-background-color: " + colorHex + "22; " +
            "-fx-background-radius: 4;"
        );
        
        Label statusIcon = new Label(result.isSuccess() ? "✓" : "✗");
        statusIcon.setStyle(
            "-fx-text-fill: " + (result.isSuccess() ? "#10b981" : "#ef4444") + "; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold;"
        );
        
        categoryBox.getChildren().addAll(statusIcon, categoryLabel);
        
        // Show if archived
        if (result.isSuccess() && (result.getCategory().equals("Spam / Low Priority") || 
            result.getCategory().equals("Promotions") || 
            result.getCategory().equals("Subscriptions"))) {
            Label archivedLabel = new Label("📦 Archived");
            archivedLabel.setStyle(
                "-fx-text-fill: -muted-foreground; " +
                "-fx-font-size: 10px; " +
                "-fx-padding: 2 6; " +
                "-fx-background-color: -muted-foreground; " +
                "-fx-background-radius: 3; " +
                "-fx-opacity: 0.3;"
            );
            categoryBox.getChildren().add(archivedLabel);
        }
        
        // Explanation
        Label explanationLabel = new Label(result.getExplanation());
        explanationLabel.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 11px; -fx-font-style: italic;");
        explanationLabel.setWrapText(true);
        explanationLabel.setMaxWidth(Double.MAX_VALUE);
        
        card.getChildren().addAll(subjectLabel, senderLabel, categoryBox, explanationLabel);
        
        if (!result.isSuccess() && result.getError() != null) {
            Label errorLabel = new Label("Error: " + result.getError());
            errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 10px;");
            errorLabel.setWrapText(true);
            card.getChildren().add(errorLabel);
        }
        
        return card;
    }
    
    /**
     * Stops the email labeling process.
     */
    private void stopLabeling() {
        if (labelingThread != null && labelingThread.isAlive()) {
            labelingThread.interrupt();
            Platform.runLater(() -> {
                labelingStatusLabel.setText("Stopped by user");
                labelingProgressIndicator.setVisible(false);
                labelingStartButton.setDisable(false);
                labelingStartButton.setText("Start Again");
                labelingStopButton.setVisible(false);
                labelingStopButton.setManaged(false);
            });
        }
    }
    
    /**
     * Loads recent labeling history from database and displays it in the results list.
     */
    private void loadLabelingHistory() {
        if (databaseManager == null) {
            return;
        }
        
        // Load in background thread
        Thread historyThread = new Thread(() -> {
            try {
                System.out.println("Loading labeling history from database...");
                java.util.List<com.labless.database.DatabaseManager.ProcessedEmailRecord> history = 
                    databaseManager.getRecentLabelingHistory(50);
                
                System.out.println("Found " + history.size() + " processed emails in history");
                
                // Convert to LabelingResult objects and update UI
                Platform.runLater(() -> {
                    labelingResultsList.getItems().clear();
                    
                    for (com.labless.database.DatabaseManager.ProcessedEmailRecord record : history) {
                        // Try to find the email in allEmails to get subject and sender
                        EmailMessage email = allEmails.stream()
                            .filter(e -> e.getId().equals(record.getEmailId()))
                            .findFirst()
                            .orElse(null);
                        
                        String subject = email != null ? email.getSubject() : "Email ID: " + record.getEmailId();
                        String sender = email != null ? email.getSender() : "Unknown";
                        
                        com.labless.model.LabelingResult result = new com.labless.model.LabelingResult(
                            record.getEmailId(),
                            subject,
                            sender,
                            record.getCategory(),
                            record.getExplanation() != null && !record.getExplanation().isEmpty() 
                                ? record.getExplanation() 
                                : "Previously labeled on " + formatTimestamp(record.getProcessedAt()),
                            true,
                            null
                        );
                        
                        labelingResultsList.getItems().add(result);
                    }
                    
                    // Show results if we have history
                    if (!history.isEmpty()) {
                        labelingResultsTitle.setVisible(true);
                        labelingResultsScroll.setVisible(true);
                        labelingResultsList.setVisible(true);
                        System.out.println("Displayed " + history.size() + " historical results in UI");
                    }
                });
                
            } catch (Exception e) {
                System.err.println("Error loading labeling history: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        historyThread.setDaemon(true);
        historyThread.setName("LabelingHistoryLoader");
        historyThread.start();
    }
    
    /**
     * Formats a timestamp string for display.
     */
    private String formatTimestamp(String timestamp) {
        try {
            java.time.Instant instant = java.time.Instant.parse(timestamp);
            java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
            return zdt.format(formatter);
        } catch (Exception e) {
            return timestamp;
        }
    }
    
    /**
     * Toggles between mail list view and history mode.
     */
    private void toggleHistoryMode() {
        // Close labeling mode if open
        if (isLabelingMode) {
            isLabelingMode = false;
            hideLabelingPanel();
        }
        
        isHistoryMode = !isHistoryMode;
        
        if (isHistoryMode) {
            showHistoryPanel();
        } else {
            hideHistoryPanel();
        }
    }
    
    /**
     * Shows the history panel and hides the mail list.
     */
    private void showHistoryPanel() {
        if (historyPanel == null) {
            historyPanel = createHistoryPanel();
        }
        
        // Load fresh history data
        loadHistoryData();
        
        // Animate transition
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(200), listPane
        );
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        fadeOut.setOnFinished(e -> {
            contentArea.getChildren().remove(listPane);
            contentArea.getChildren().add(1, historyPanel);
            
            javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(300), historyPanel
            );
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            historyPanel.setOpacity(0.0);
            fadeIn.play();
        });
        
        fadeOut.play();
    }
    
    /**
     * Hides the history panel and shows the mail list.
     */
    private void hideHistoryPanel() {
        if (historyPanel == null) return;
        
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(200), historyPanel
        );
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        fadeOut.setOnFinished(e -> {
            contentArea.getChildren().remove(historyPanel);
            contentArea.getChildren().add(1, listPane);
            
            javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(300), listPane
            );
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            listPane.setOpacity(0.0);
            fadeIn.play();
        });
        
        fadeOut.play();
    }
    
    /**
     * Creates the history panel UI with a table view.
     */
    private VBox createHistoryPanel() {
        VBox panel = new VBox(20);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setStyle("-fx-padding: 40 20; -fx-background-color: transparent;");
        panel.setMinWidth(0);
        panel.setPrefWidth(900);
        panel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(panel, Priority.ALWAYS);
        
        // Title
        Label title = new Label("Labeling History");
        title.setStyle("-fx-text-fill: -foreground; -fx-font-size: 24px; -fx-font-weight: bold;");
        
        // Description
        Label description = new Label("View all emails that have been automatically labeled");
        description.setStyle("-fx-text-fill: -muted-foreground; -fx-font-size: 14px;");
        
        // Create TableView
        javafx.scene.control.TableView<com.labless.database.DatabaseManager.ProcessedEmailRecord> tableView = new javafx.scene.control.TableView<>();
        tableView.setStyle(
            "-fx-background-color: -card; " +
            "-fx-control-inner-background: -card; " +
            "-fx-background-insets: 0; " +
            "-fx-border-color: -border; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8;"
        );
        tableView.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Subject column
        javafx.scene.control.TableColumn<com.labless.database.DatabaseManager.ProcessedEmailRecord, String> subjectCol = 
            new javafx.scene.control.TableColumn<>("Subject");
        subjectCol.setPrefWidth(250);
        subjectCol.setCellValueFactory(cellData -> {
            String emailId = cellData.getValue().getEmailId();
            EmailMessage email = allEmails.stream()
                .filter(e -> e.getId().equals(emailId))
                .findFirst()
                .orElse(null);
            String subject = email != null ? email.getSubject() : "Email ID: " + emailId;
            return new javafx.beans.property.SimpleStringProperty(subject);
        });
        
        // Sender column
        javafx.scene.control.TableColumn<com.labless.database.DatabaseManager.ProcessedEmailRecord, String> senderCol = 
            new javafx.scene.control.TableColumn<>("Sender");
        senderCol.setPrefWidth(200);
        senderCol.setCellValueFactory(cellData -> {
            String emailId = cellData.getValue().getEmailId();
            EmailMessage email = allEmails.stream()
                .filter(e -> e.getId().equals(emailId))
                .findFirst()
                .orElse(null);
            String sender = email != null ? email.getSender() : "Unknown";
            return new javafx.beans.property.SimpleStringProperty(sender);
        });
        
        // Category column
        javafx.scene.control.TableColumn<com.labless.database.DatabaseManager.ProcessedEmailRecord, String> categoryCol = 
            new javafx.scene.control.TableColumn<>("Label");
        categoryCol.setPrefWidth(150);
        categoryCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCategory())
        );
        categoryCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String category, boolean empty) {
                super.updateItem(category, empty);
                if (empty || category == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label label = new Label(category);
                    javafx.scene.paint.Color labelColor = getLabelColor(category);
                    String colorHex = String.format("#%02x%02x%02x",
                        (int) (labelColor.getRed() * 255),
                        (int) (labelColor.getGreen() * 255),
                        (int) (labelColor.getBlue() * 255));
                    label.setStyle(
                        "-fx-text-fill: " + colorHex + "; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 4 8; " +
                        "-fx-background-color: " + colorHex + "22; " +
                        "-fx-background-radius: 4;"
                    );
                    setGraphic(label);
                    setText(null);
                }
            }
        });
        
        // Reason column (from explanation field)
        javafx.scene.control.TableColumn<com.labless.database.DatabaseManager.ProcessedEmailRecord, String> reasonCol = 
            new javafx.scene.control.TableColumn<>("Reason");
        reasonCol.setPrefWidth(200);
        reasonCol.setCellValueFactory(cellData -> {
            String explanation = cellData.getValue().getExplanation();
            // Use the actual AI explanation if available, otherwise show default
            String reason = (explanation != null && !explanation.isEmpty()) 
                ? explanation 
                : "Categorized as " + cellData.getValue().getCategory();
            return new javafx.beans.property.SimpleStringProperty(reason);
        });
        
        // Timestamp column
        javafx.scene.control.TableColumn<com.labless.database.DatabaseManager.ProcessedEmailRecord, String> timestampCol = 
            new javafx.scene.control.TableColumn<>("Labeled At");
        timestampCol.setPrefWidth(150);
        timestampCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(formatTimestamp(cellData.getValue().getProcessedAt()))
        );
        
        tableView.getColumns().addAll(subjectCol, senderCol, categoryCol, reasonCol, timestampCol);
        
        // Wrap in ScrollPane
        ScrollPane scrollPane = new ScrollPane(tableView);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // Close button
        Button closeButton = new Button("Back to Emails");
        closeButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: -muted-foreground; " +
            "-fx-padding: 8 16; " +
            "-fx-font-size: 13px; " +
            "-fx-background-radius: 6; " +
            "-fx-border-color: -border; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 6; " +
            "-fx-cursor: hand;"
        );
        closeButton.setOnAction(e -> toggleHistoryMode());
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().add(closeButton);
        
        panel.getChildren().addAll(title, description, scrollPane, buttonBox);
        
        // Store reference to table for data loading
        panel.setUserData(tableView);
        
        return panel;
    }
    
    /**
     * Loads history data into the history table.
     */
    private void loadHistoryData() {
        if (historyPanel == null || databaseManager == null) {
            return;
        }
        
        @SuppressWarnings("unchecked")
        javafx.scene.control.TableView<com.labless.database.DatabaseManager.ProcessedEmailRecord> tableView = 
            (javafx.scene.control.TableView<com.labless.database.DatabaseManager.ProcessedEmailRecord>) historyPanel.getUserData();
        
        if (tableView == null) {
            return;
        }
        
        // Load in background thread
        Thread loadThread = new Thread(() -> {
            try {
                System.out.println("Loading history data from database...");
                java.util.List<com.labless.database.DatabaseManager.ProcessedEmailRecord> history = 
                    databaseManager.getRecentLabelingHistory(100);
                
                System.out.println("Found " + history.size() + " processed emails in history");
                
                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    tableView.getItems().clear();
                    tableView.getItems().addAll(history);
                    System.out.println("Displayed " + history.size() + " records in history table");
                });
                
            } catch (Exception e) {
                System.err.println("Error loading history data: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        loadThread.setDaemon(true);
        loadThread.setName("HistoryDataLoader");
        loadThread.start();
    }
    
    /**
     * Starts the email labeling process.
     */
    private void startLabeling() {
        // Get user-specified email count
        int requestedEmailCount = 100; // Default
        try {
            String countText = labelingEmailCountField.getText().trim();
            if (!countText.isEmpty()) {
                requestedEmailCount = Integer.parseInt(countText);
                if (requestedEmailCount <= 0) {
                    requestedEmailCount = 100;
                } else if (requestedEmailCount > 10000) {
                    requestedEmailCount = 10000; // Max limit
                }
            }
        } catch (NumberFormatException e) {
            requestedEmailCount = 100;
        }
        
        final int totalEmailsToProcess = requestedEmailCount;
        
        labelingStatusLabel.setText("Preparing to process " + totalEmailsToProcess + " emails...");
        labelingProgressIndicator.setVisible(true);
        labelingStartButton.setDisable(true);
        labelingStartButton.setVisible(false);
        labelingStartButton.setManaged(false);
        labelingStopButton.setVisible(true);
        labelingStopButton.setManaged(true);
        labelingEmailCountField.setDisable(true); // Disable input during processing
        
        // Hide results container initially (will show when first result arrives)
        labelingResultsContainer.setVisible(false);
        labelingResultsContainer.setManaged(false);
        labelingResultsTitle.setVisible(false);
        labelingResultsScroll.setVisible(false);
        labelingResultsList.setVisible(false);
        labelingResultsList.getItems().clear();
        
        // Get Groq API key and categories from config
        String groqApiKey = null;
        String model = "llama-3.1-8b-instant";
        java.util.List<String> categories = new java.util.ArrayList<>();
        
        // Check if config has Groq provider configured
        if (config != null && config.getLlm() != null) {
            if ("Groq".equalsIgnoreCase(config.getLlm().getProvider())) {
                groqApiKey = config.getLlm().getApiKey();
                if (config.getLlm().getModel() != null && !config.getLlm().getModel().isEmpty()) {
                    model = config.getLlm().getModel();
                }
            }
            
            // Use categories from config
            if (config.getCategories() != null && !config.getCategories().isEmpty()) {
                categories.addAll(config.getCategories());
            }
        }
        
        // Validate configuration
        if (groqApiKey == null || groqApiKey.trim().isEmpty()) {
            Platform.runLater(() -> {
                labelingStatusLabel.setText("Error: Groq API key not configured");
                labelingProgressIndicator.setVisible(false);
                labelingStartButton.setDisable(false);
                labelingStartButton.setVisible(true);
                labelingStartButton.setManaged(true);
                labelingStopButton.setVisible(false);
                labelingStopButton.setManaged(false);
                
                // Show error in results list
                com.labless.model.LabelingResult errorResult = new com.labless.model.LabelingResult(
                    "config-error",
                    "Configuration Error",
                    "System",
                    "Error",
                    "Groq API key not configured. Please go to Settings and configure Groq as your AI provider with an API key.",
                    false,
                    "Missing Groq API key in configuration"
                );
                labelingResultsList.getItems().add(errorResult);
            });
            return;
        }
        
        if (categories.isEmpty()) {
            Platform.runLater(() -> {
                labelingStatusLabel.setText("Error: No categories configured");
                labelingProgressIndicator.setVisible(false);
                labelingStartButton.setDisable(false);
                labelingStartButton.setVisible(true);
                labelingStartButton.setManaged(true);
                labelingStopButton.setVisible(false);
                labelingStopButton.setManaged(false);
                
                // Show error in results list
                com.labless.model.LabelingResult errorResult = new com.labless.model.LabelingResult(
                    "config-error",
                    "Configuration Error",
                    "System",
                    "Error",
                    "No categories configured. Please go to Settings and define your email categories.",
                    false,
                    "Missing categories in configuration"
                );
                labelingResultsList.getItems().add(errorResult);
            });
            return;
        }
        
        final String finalApiKey = groqApiKey;
        final String finalModel = model;
        final java.util.List<String> finalCategories = categories;
        
        // Track labeled emails for local update
        final java.util.Map<String, String> labeledEmailsMap = new java.util.concurrent.ConcurrentHashMap<>();
        
        // Run labeling in background thread
        labelingThread = new Thread(() -> {
            com.labless.service.GroqApiClient groqClient = null;
            
            try {
                System.out.println("=== Starting Email Labeling Process ===");
                System.out.println("Requested emails to process: " + totalEmailsToProcess);
                System.out.println("Using Groq API with model: " + finalModel);
                System.out.println("Categories configured: " + finalCategories.size());
                groqClient = new com.labless.service.GroqApiClient(finalApiKey, finalModel);
                System.out.println("Groq client initialized successfully");
                
                // Build query to exclude emails that already have category labels
                StringBuilder queryBuilder = new StringBuilder("in:inbox");
                for (String category : finalCategories) {
                    queryBuilder.append(" -label:\"").append(category).append("\"");
                }
                String unlabeledQuery = queryBuilder.toString();
                
                System.out.println("Query: " + unlabeledQuery);
                
                // Fetch emails in batches (Gmail API limit is 500 per request, we'll use 50 per batch)
                final int BATCH_SIZE = 50;
                java.util.List<EmailMessage> allEmailsToProcess = new java.util.ArrayList<>();
                int remainingToFetch = totalEmailsToProcess;
                
                Platform.runLater(() -> {
                    labelingStatusLabel.setText("Fetching emails from Gmail...");
                });
                
                while (remainingToFetch > 0 && !Thread.currentThread().isInterrupted()) {
                    int batchSize = Math.min(BATCH_SIZE, remainingToFetch);
                    System.out.println("Fetching batch of " + batchSize + " emails (total so far: " + allEmailsToProcess.size() + ")");
                    
                    try {
                        java.util.List<EmailMessage> batch = gmailClient.fetchEmails(unlabeledQuery, batchSize);
                        System.out.println("Fetched " + batch.size() + " emails in this batch");
                        
                        if (batch.isEmpty()) {
                            System.out.println("No more emails to fetch");
                            break;
                        }
                        
                        allEmailsToProcess.addAll(batch);
                        remainingToFetch -= batch.size();
                        
                        // If we got fewer emails than requested, we've reached the end
                        if (batch.size() < batchSize) {
                            System.out.println("Reached end of available emails");
                            break;
                        }
                        
                    } catch (Exception e) {
                        System.err.println("Error fetching batch: " + e.getMessage());
                        break;
                    }
                }
                
                System.out.println("Total emails fetched: " + allEmailsToProcess.size());
                
                if (allEmailsToProcess.isEmpty()) {
                    Platform.runLater(() -> {
                        labelingStatusLabel.setText("No unlabeled emails to process");
                        labelingProgressIndicator.setVisible(false);
                        labelingStartButton.setDisable(false);
                        labelingStartButton.setVisible(true);
                        labelingStartButton.setManaged(true);
                        labelingStopButton.setVisible(false);
                        labelingStopButton.setManaged(false);
                        labelingEmailCountField.setDisable(false);
                    });
                    return;
                }
                
                final int total = allEmailsToProcess.size();
                Platform.runLater(() -> {
                    labelingStatusLabel.setText("Labeling in process... (" + total + " emails)");
                    labelingProgressText.setText("0 / " + total + " emails processed");
                });
                
                int processed = 0;
                int successful = 0;
                int failed = 0;
                int rateLimitRetries = 0;
                final int MAX_RATE_LIMIT_RETRIES = 3;
                
                for (EmailMessage email : allEmailsToProcess) {
                    // Check if thread was interrupted (stop button clicked)
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("Labeling stopped by user");
                        break;
                    }
                    
                    // Skip emails that already have custom category labels
                    boolean alreadyLabeled = false;
                    if (email.getLabels() != null) {
                        for (String label : email.getLabels()) {
                            if (label != null && finalCategories.contains(label)) {
                                alreadyLabeled = true;
                                System.out.println("Skipping email - already has label: " + label);
                                break;
                            }
                            // Also check for normalized category names (CATEGORY_ prefix)
                            String normalized = label.replace("CATEGORY_", "").replace('_', ' ');
                            for (String category : finalCategories) {
                                if (category.equalsIgnoreCase(normalized)) {
                                    alreadyLabeled = true;
                                    System.out.println("Skipping email - already has label: " + label + " (matches " + category + ")");
                                    break;
                                }
                            }
                            if (alreadyLabeled) break;
                        }
                    }
                    
                    if (alreadyLabeled) {
                        processed++;
                        continue;
                    }
                    
                    final int currentIndex = processed;
                    final java.util.concurrent.atomic.AtomicBoolean retryWithBackoff = new java.util.concurrent.atomic.AtomicBoolean(false); // Reset for each email
                    
                    System.out.println("\n--- Processing email " + (currentIndex + 1) + "/" + total + " ---");
                    System.out.println("Subject: " + email.getSubject());
                    System.out.println("From: " + email.getSender());
                    
                    Platform.runLater(() -> {
                        labelingStatusLabel.setText("Labeling in process... (" + (currentIndex + 1) + "/" + total + "): " + 
                            (email.getSubject().length() > 50 ? email.getSubject().substring(0, 50) + "..." : email.getSubject()));
                    });
                    
                    String errorMessage = null;
                    com.labless.service.GroqApiClient.CategorizationResult result = null;
                    
                    try {
                        // Prepare email content
                        String emailContent = String.format(
                            "Subject: %s\nFrom: %s\n\n%s",
                            email.getSubject(),
                            email.getSender(),
                            email.getBody() != null ? email.getBody() : ""
                        );
                        
                        System.out.println("Email content length: " + emailContent.length() + " characters");
                        
                        // Categorize with Groq - with retry logic for rate limits
                        System.out.println("Calling Groq API...");
                        result = groqClient.categorizeEmail(emailContent, finalCategories);
                        System.out.println("Groq API response - Category: " + result.getCategory() + ", Explanation: " + result.getExplanation());
                        
                        // Special handling for Transaction category
                        // Transaction should ONLY be used for bank debit/credit notifications
                        // Otherwise, use Bills & Payments
                        if (result.getCategory().equalsIgnoreCase("Transaction")) {
                            String lowerContent = emailContent.toLowerCase();
                            String lowerSender = email.getSender().toLowerCase();
                            String lowerSubject = email.getSubject().toLowerCase();
                            
                            // Check if it's actually a bank transaction (debit/credit)
                            // Must have BOTH: bank sender AND transaction keywords
                            boolean isFromBank = lowerSender.contains("bank") || lowerSender.contains("hdfc") || 
                                 lowerSender.contains("icici") || lowerSender.contains("sbi") || 
                                 lowerSender.contains("axis") || lowerSender.contains("kotak") ||
                                 lowerSender.contains("paytm") || lowerSender.contains("phonepe");
                            
                            // Transaction keywords in subject or content
                            boolean hasTransactionKeywords = 
                                (lowerSubject.contains("debited") || lowerSubject.contains("credited") ||
                                 lowerSubject.contains("debit") || lowerSubject.contains("credit") ||
                                 lowerSubject.contains("withdrawn") || lowerSubject.contains("deposited")) ||
                                (lowerContent.contains("debited") || lowerContent.contains("credited") ||
                                 lowerContent.contains("withdrawn") || lowerContent.contains("deposited"));
                            
                            // Exclude promotional keywords
                            boolean isPromotional = 
                                lowerSubject.contains("offer") || lowerSubject.contains("reward") ||
                                lowerSubject.contains("cashback") || lowerSubject.contains("discount") ||
                                lowerSubject.contains("apply now") || lowerSubject.contains("pre-qualified") ||
                                lowerContent.contains("apply now") || lowerContent.contains("pre-qualified") ||
                                lowerContent.contains("check for your") || lowerContent.contains("exclusive offer");
                            
                            // Must be from bank, have transaction keywords, and NOT be promotional
                            boolean isBankTransaction = isFromBank && hasTransactionKeywords && !isPromotional;
                            
                            if (!isBankTransaction) {
                                System.out.println("Not a bank transaction - changing from Transaction to Bills & Payments");
                                System.out.println("  From bank: " + isFromBank + ", Has keywords: " + hasTransactionKeywords + ", Is promotional: " + isPromotional);
                                
                                // Check if Bills & Payments exists in categories
                                String billsCategory = null;
                                for (String cat : finalCategories) {
                                    if (cat.toLowerCase().contains("bill") && cat.toLowerCase().contains("payment")) {
                                        billsCategory = cat;
                                        break;
                                    }
                                }
                                
                                if (billsCategory != null) {
                                    result = new com.labless.service.GroqApiClient.CategorizationResult(
                                        billsCategory,
                                        "Reclassified from Transaction to Bills & Payments (not a bank debit/credit notification)",
                                        true
                                    );
                                    System.out.println("Reclassified to: " + billsCategory);
                                }
                            } else {
                                System.out.println("Confirmed as bank transaction (debit/credit)");
                            }
                        }
                        
                        // Reset retry counter on success
                        rateLimitRetries = 0;
                        
                        // Count as successful if Groq API returned a result
                        if (result.isSuccess()) {
                            successful++;
                        }
                        
                        // Apply label to Gmail if successful
                        if (result.isSuccess() && !result.getCategory().equals("Other")) {
                            try {
                                System.out.println("Applying label to Gmail...");
                                
                                // Ensure label exists in Gmail
                                gmailClient.ensureLabel(result.getCategory());
                                System.out.println("Label ensured: " + result.getCategory());
                                
                                // Apply ONLY ONE label to the email, removing any other managed category labels
                                // This ensures each email has exactly one category label
                                gmailClient.applySingleManagedLabel(email.getId(), result.getCategory(), new java.util.ArrayList<>(finalCategories));
                                System.out.println("Single label applied successfully (removed any other category labels)");
                                
                                // Track this email for local update
                                labeledEmailsMap.put(email.getId(), result.getCategory());
                                
                                // Note: Local email labels will be updated from labeledEmailsMap after labeling completes
                                
                                // Save to database for history
                                try {
                                    if (databaseManager != null) {
                                        java.util.List<String> labelsList = new java.util.ArrayList<>();
                                        labelsList.add(result.getCategory());
                                        databaseManager.saveProcessed(email.getId(), result.getCategory(), labelsList, result.getExplanation());
                                        System.out.println("Saved to database for history");
                                    }
                                } catch (Exception dbEx) {
                                    System.err.println("Failed to save to database: " + dbEx.getMessage());
                                }
                                
                                // Archive low-priority emails (remove from inbox)
                                if (result.getCategory().equals("Spam / Low Priority") || 
                                    result.getCategory().equals("Promotions") ||
                                    result.getCategory().equals("Subscriptions")) {
                                    gmailClient.removeFromInbox(email.getId());
                                    System.out.println("Email archived (removed from inbox)");
                                }
                            } catch (Exception e) {
                                errorMessage = "Gmail API error: " + e.getMessage();
                                System.err.println("Failed to apply label: " + e.getMessage());
                                e.printStackTrace();
                                // Don't increment failed here - the categorization was successful
                            }
                        } else {
                            System.out.println("Skipping label application - Category: " + result.getCategory());
                            if (!result.isSuccess()) {
                                failed++;
                            }
                        }
                        
                    } catch (java.io.IOException e) {
                        String errorMsg = e.getMessage();
                        
                        // Check if it's a rate limit error (429)
                        if (errorMsg != null && errorMsg.contains("429") && errorMsg.contains("rate_limit_exceeded")) {
                            System.err.println("Rate limit hit: " + errorMsg);
                            
                            // Extract wait time from error message
                            int waitTimeMs = 700; // Default
                            try {
                                if (errorMsg.contains("Please try again in")) {
                                    String waitStr = errorMsg.substring(errorMsg.indexOf("Please try again in") + 20);
                                    if (waitStr.contains("ms")) {
                                        waitTimeMs = Integer.parseInt(waitStr.substring(0, waitStr.indexOf("ms")).trim());
                                    } else if (waitStr.contains("s")) {
                                        double waitSec = Double.parseDouble(waitStr.substring(0, waitStr.indexOf("s")).trim());
                                        waitTimeMs = (int) (waitSec * 1000);
                                    }
                                }
                            } catch (Exception parseEx) {
                                // Use default wait time
                            }
                            
                            // Exponential backoff: increase wait time with each retry
                            waitTimeMs = waitTimeMs + (rateLimitRetries * 2000); // Add 2 seconds per retry
                            
                            if (rateLimitRetries < MAX_RATE_LIMIT_RETRIES) {
                                rateLimitRetries++;
                                final int finalWaitMs = waitTimeMs;
                                final int finalRetryCount = rateLimitRetries;
                                Platform.runLater(() -> {
                                    labelingStatusLabel.setText("Rate limit hit - waiting " + (finalWaitMs / 1000.0) + "s (retry " + finalRetryCount + "/" + MAX_RATE_LIMIT_RETRIES + ")");
                                });
                                System.out.println("Waiting " + waitTimeMs + "ms before retry " + rateLimitRetries + "/" + MAX_RATE_LIMIT_RETRIES);
                                Thread.sleep(waitTimeMs);
                                retryWithBackoff.set(true);
                                continue; // Retry this email
                            } else {
                                errorMessage = "Rate limit exceeded after " + MAX_RATE_LIMIT_RETRIES + " retries";
                                System.err.println(errorMessage);
                                rateLimitRetries = 0; // Reset for next email
                            }
                        } else {
                            errorMessage = "API Error: " + errorMsg;
                            System.err.println("Groq API error: " + errorMsg);
                            e.printStackTrace();
                        }
                        
                        failed++;
                        
                        // Create error result
                        result = new com.labless.service.GroqApiClient.CategorizationResult(
                            "Error",
                            "API call failed",
                            false
                        );
                    } catch (Exception e) {
                        errorMessage = "Unexpected error: " + e.getMessage();
                        System.err.println("Unexpected error: " + e.getMessage());
                        e.printStackTrace();
                        failed++;
                        
                        // Create error result
                        result = new com.labless.service.GroqApiClient.CategorizationResult(
                            "Error",
                            "Processing failed",
                            false
                        );
                    }
                    
                    // Skip UI update if we're retrying
                    if (retryWithBackoff.get()) {
                        continue;
                    }
                    
                    // Create labeling result
                    final com.labless.model.LabelingResult labelingResult = new com.labless.model.LabelingResult(
                        email.getId(),
                        email.getSubject(),
                        email.getSender(),
                        result.getCategory(),
                        result.getExplanation(),
                        result.isSuccess() && errorMessage == null,
                        errorMessage
                    );
                    
                    // Update UI
                    final int finalProcessed = processed + 1;
                    Platform.runLater(() -> {
                        System.out.println("Adding result to UI list: " + labelingResult.getSubject());
                        labelingResultsList.getItems().add(0, labelingResult);
                        labelingProgressText.setText(finalProcessed + " / " + total + " emails processed");
                        System.out.println("Results list now has " + labelingResultsList.getItems().size() + " items");
                        
                        // Show results container when first result arrives
                        if (labelingResultsList.getItems().size() == 1) {
                            labelingResultsContainer.setVisible(true);
                            labelingResultsContainer.setManaged(true);
                            labelingResultsTitle.setVisible(true);
                            labelingResultsScroll.setVisible(true);
                            labelingResultsList.setVisible(true);
                        }
                    });
                    
                    processed++;
                    
                    // Rate limiting - wait 1.5 seconds between requests to avoid rate limits
                    if (processed < total) {
                        System.out.println("Waiting 1500ms before next request...");
                        Thread.sleep(1500);
                    }
                }
                
                // Completed
                final int finalProcessed = processed;
                final int finalSuccessful = successful;
                final int finalFailed = failed;
                System.out.println("\n=== Labeling Complete ===");
                System.out.println("Total processed: " + finalProcessed);
                System.out.println("Successful: " + finalSuccessful);
                System.out.println("Failed: " + finalFailed);
                
                Platform.runLater(() -> {
                    labelingStatusLabel.setText(String.format("Completed! %d successful, %d failed out of %d emails", 
                        finalSuccessful, finalFailed, finalProcessed));
                    labelingProgressIndicator.setVisible(false);
                    labelingStartButton.setDisable(false);
                    labelingStartButton.setText("Start Again");
                    labelingStartButton.setVisible(true);
                    labelingStartButton.setManaged(true);
                    labelingStopButton.setVisible(false);
                    labelingStopButton.setManaged(false);
                    labelingEmailCountField.setDisable(false); // Re-enable input
                    
                    // Update local email list with new labels (avoids Gmail refresh)
                    System.out.println("Updating local email list with " + labeledEmailsMap.size() + " labeled emails");
                    updateLocalEmailLabels(labeledEmailsMap);
                    
                    // Refresh the UI to show updated labels
                    refreshMessageList();
                    System.out.println("Mail list refreshed with updated labels");
                });
                
            } catch (InterruptedException e) {
                System.out.println("Labeling interrupted by user");
                Platform.runLater(() -> {
                    labelingStatusLabel.setText("Stopped by user");
                    labelingProgressIndicator.setVisible(false);
                    labelingStartButton.setDisable(false);
                    labelingStartButton.setText("Start Again");
                    labelingStartButton.setVisible(true);
                    labelingStartButton.setManaged(true);
                    labelingStopButton.setVisible(false);
                    labelingStopButton.setManaged(false);
                    labelingEmailCountField.setDisable(false); // Re-enable input
                });
            } catch (Exception e) {
                System.err.println("Fatal error in labeling thread: " + e.getMessage());
                e.printStackTrace();
                
                Platform.runLater(() -> {
                    labelingStatusLabel.setText("Fatal Error: " + e.getMessage());
                    labelingProgressIndicator.setVisible(false);
                    labelingStartButton.setDisable(false);
                    labelingStartButton.setVisible(true);
                    labelingStartButton.setManaged(true);
                    labelingStopButton.setVisible(false);
                    labelingStopButton.setManaged(false);
                    labelingEmailCountField.setDisable(false); // Re-enable input
                    
                    // Show error in results list
                    com.labless.model.LabelingResult errorResult = new com.labless.model.LabelingResult(
                        "error",
                        "System Error",
                        "System",
                        "Error",
                        "Fatal error occurred",
                        false,
                        e.getMessage() + "\n" + (e.getCause() != null ? e.getCause().getMessage() : "")
                    );
                    labelingResultsList.getItems().add(0, errorResult);
                });
            }
        });
        
        labelingThread.setDaemon(true);
        labelingThread.setName("EmailLabelingThread");
        labelingThread.start();
    }
}
