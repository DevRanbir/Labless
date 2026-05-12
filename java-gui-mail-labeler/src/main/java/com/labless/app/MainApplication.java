package com.labless.app;

import com.labless.config.ConfigLoadResult;
import com.labless.config.ConfigManager;
import com.labless.model.AppConfig;
import com.labless.model.CachedInboxData;
import com.labless.model.EmailMessage;
import com.labless.model.EmailPage;
import com.labless.model.InboxBootstrap;
import com.labless.model.InboxSnapshot;
import com.labless.model.JobConfig;
import com.labless.model.JobProgress;
import com.labless.model.JobSummary;
import com.labless.model.MailboxLabel;
import com.labless.model.MailboxStats;
import com.labless.processor.EmailProcessor;
import com.labless.service.AppServices;
import com.labless.ui.LoadingScreen;
import com.labless.ui.OnboardingScreen;
import com.labless.ui.WelcomeScreen;
import com.labless.ui.WorkspaceScreen;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class MainApplication extends Application {
    private static final Path CONFIG_PATH = Path.of("config", "app-config.yaml");
    private static final int INITIAL_FETCH_SIZE = 10;
    private static final int PAGE_FETCH_SIZE = 25;
    private static final long CACHE_FRESHNESS_MS = 15L * 60L * 1000L;
    private static final long AUTO_REFRESH_INTERVAL_MS = 2L * 60L * 1000L; // Check for new emails every 2 minutes

    private final ConfigManager configManager = new ConfigManager();
    private final ExecutorService jobExecutor = Executors.newSingleThreadExecutor();
    // Single-thread executor dedicated to label priority fetches
    private final ExecutorService labelFetchExecutor = Executors.newSingleThreadExecutor();
    // Reference to the running label-fetch future so it can be cancelled on deselect
    private final AtomicReference<Future<?>> activeLabelFetch = new AtomicReference<>();
    // Scheduled executor for periodic email refresh
    private ScheduledExecutorService autoRefreshExecutor;

    private AppConfig currentConfig;
    private AppServices services;
    private StackPane sceneRoot;

    private volatile EmailProcessor activeProcessor;
    private boolean googleConnected;

    private WelcomeScreen welcomeScreen;
    private WorkspaceScreen workspaceScreen;
    private LoadingScreen loadingScreen;
    private final AtomicInteger inboxFetchGeneration = new AtomicInteger(0);

    @Override
    public void start(Stage stage) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable instanceof IllegalArgumentException && throwable.getMessage() != null && throwable.getMessage().contains("unsupported URI")) {
                // Suppress JavaFX WebView HTTP2Loader exceptions for malformed tracking pixels
                return;
            }
            throwable.printStackTrace();
        });

        ConfigLoadResult loadResult = configManager.loadOrCreate(CONFIG_PATH);
        currentConfig = loadResult.getConfig();
        googleConnected = currentConfig.isOnboardingCompleted();
        services = AppServices.fromConfig(currentConfig);

        sceneRoot = new StackPane();
        sceneRoot.getStyleClass().add("app-root");
        sceneRoot.getStyleClass().add("light");

        Scene scene = new Scene(sceneRoot, 1260, 820);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        stage.setTitle("Labless");
        
        // Set minimum window size
        stage.setMinWidth(700);
        stage.setMinHeight(600);
        
        // Set application icon for taskbar
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(
                getClass().getResourceAsStream("/logo.png")
            );
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Failed to load application icon: " + e.getMessage());
        }
        
        stage.setScene(scene);
        stage.setMaximized(true); // Open in maximized mode by default
        stage.show();

        if (currentConfig.isOnboardingCompleted()) {
            showMainAppScreen();
        } else {
            showWelcomeScreen();
        }
    }

    private void showWelcomeScreen() {
        applyLightTheme();

        welcomeScreen = new WelcomeScreen(
            currentConfig,
            this::showOnboardingScreen,
            this::showMainAppScreen
        );
        welcomeScreen.attachResponsiveBehavior(sceneRoot);
        sceneRoot.getChildren().setAll(welcomeScreen);
    }

    private void showOnboardingScreen() {
        detachWelcomeResponsiveBehavior();
        applyLightTheme();
        OnboardingScreen onboardingScreen = new OnboardingScreen(
            currentConfig,
            googleConnected,
            this::showWelcomeScreen,
            this::finishOnboarding
        );
        sceneRoot.getChildren().setAll(onboardingScreen);
    }

    private void finishOnboarding(AppConfig config) {
        try {
            configManager.save(CONFIG_PATH, config);
            currentConfig = config;
            googleConnected = true;
            workspaceScreen = null; // Force recreation with new config
            recreateServices();
            showMainAppScreen();
        } catch (Exception ex) {
            showWelcomeScreen();
        }
    }

    private void showMainAppScreen() {
        detachWelcomeResponsiveBehavior();
        applyDarkTheme();
        if (openWorkspaceFromCacheIfPresent()) {
            return;
        }
        showInboxLoadingScreen();
    }

    private boolean openWorkspaceFromCacheIfPresent() {
        Optional<CachedInboxData> cached = services.loadInboxCache(currentConfig.getGmailQuery());
        if (cached.isEmpty()) {
            return false;
        }
        CachedInboxData data = cached.get();
        openWorkspace(data.getBootstrap(), !data.isFullyLoaded());

        long ageMs = Math.max(0, System.currentTimeMillis() - data.getCachedAtEpochMillis());
        boolean shouldRefresh = data.isFullyLoaded() && ageMs > CACHE_FRESHNESS_MS;
        if (shouldRefresh) {
            refreshInboxInBackground();
        }
        
        // Start automatic periodic refresh
        startAutoRefresh();
        
        return true;
    }

    private void showInboxLoadingScreen() {
        loadingScreen = new LoadingScreen();
        loadingScreen.setStatus("Connecting to Gmail. Loading first 10 emails and mailbox metadata...");
        StackPane.setAlignment(loadingScreen, Pos.CENTER);
        sceneRoot.getChildren().setAll(loadingScreen);

        Task<InboxBootstrap> fetchTask = new Task<>() {
            @Override
            protected InboxBootstrap call() {
                return services.fetchInboxBootstrap(currentConfig.getGmailQuery(), INITIAL_FETCH_SIZE);
            }
        };

        fetchTask.setOnSucceeded(event -> {
            InboxBootstrap bootstrap = fetchTask.getValue();
            services.saveInboxCache(currentConfig.getGmailQuery(), bootstrap, false);
            openWorkspace(bootstrap, true);
        });

        fetchTask.setOnFailed(event -> {
            workspaceScreen = new WorkspaceScreen(
                currentConfig,
                this::showOnboardingScreen,
                this::startJob,
                this::refreshFetchedInbox,
                this::stopJob,
                this::logout,
                services.getProfileImageUrl(),
                services.getGmailClient(),
                services.getDatabaseManager()
            );
            StackPane.setAlignment(workspaceScreen, Pos.TOP_LEFT);
            workspaceScreen.showError("Failed to load inbox: " + fetchTask.getException().getMessage());
            sceneRoot.getChildren().setAll(workspaceScreen);
        });

        Thread thread = new Thread(fetchTask, "inbox-bootstrap-task");
        thread.setDaemon(true);
        thread.start();
    }

    private WorkspaceScreen ensureWorkspaceScreen() {
        if (workspaceScreen == null) {
            workspaceScreen = new WorkspaceScreen(
                currentConfig,
                this::showOnboardingScreen,
                this::startJob,
                this::refreshFetchedInbox,
                this::stopJob,
                this::logout,
                services.getProfileImageUrl(),
                services.getGmailClient(),
                services.getDatabaseManager()
            );
            StackPane.setAlignment(workspaceScreen, Pos.TOP_LEFT);
            // Wire priority label fetching: sidebar click -> fetchLabelEmails
            workspaceScreen.setOnLabelFetchRequested(this::fetchLabelEmails);
        }
        return workspaceScreen;
    }

    private void openWorkspace(InboxBootstrap bootstrap, boolean fetchRemainingInBackground) {
        ensureWorkspaceScreen();
        workspaceScreen.setEmails(bootstrap.getSnapshot().getEmails());
        workspaceScreen.setLabels(bootstrap.getSnapshot().getLabels());
        workspaceScreen.setMailboxStats(bootstrap.getStats());
        long target = resolveTargetCount(bootstrap.getStats(), currentConfig.getGmailQuery());
        boolean hasMore = bootstrap.getNextPageToken() != null && !bootstrap.getNextPageToken().isBlank();
        workspaceScreen.updateFetchProgress(bootstrap.getSnapshot().getEmails().size(), target, hasMore);
        sceneRoot.getChildren().setAll(workspaceScreen);
        if (fetchRemainingInBackground) {
            startBackgroundInboxFetch(
                bootstrap.getSnapshot().getEmails(),
                bootstrap.getSnapshot().getLabels(),
                bootstrap.getStats(),
                bootstrap.getNextPageToken()
            );
        }
        
        // Start automatic periodic refresh
        startAutoRefresh();
    }

    /**
     * Priority-fetches all Gmail emails with the given label.
     *
     * Flow:
     * 1. Cancel any running label-fetch (user switched labels quickly).
     * 2. Pause the background inbox fetch by bumping the generation counter.
     *    Save the current page-token + email list so we can resume later.
     * 3. Immediately push locally-cached emails that match the label to the UI.
     * 4. On a dedicated thread, query Gmail "label:<name>" page-by-page,
     *    merging new results into allEmails and refreshing the UI after each page.
     * 5. When done (or cancelled), resume the background inbox fetch.
     */
    private void fetchLabelEmails(String labelName) {
        if (workspaceScreen == null) return;

        // Cancel any previous label fetch that is still running
        Future<?> prev = activeLabelFetch.getAndSet(null);
        if (prev != null) prev.cancel(true);

        // Pause the background inbox fetch
        cancelActiveInboxIndexing();
        final int myGeneration = inboxFetchGeneration.get();

        // Build the Gmail label query (Gmail API accepts "label:labelname")
        String gmailLabelQuery = "label:" + labelName.replace(" ", "-").toLowerCase(Locale.ROOT);

        String displayLabel = labelName;
        if (labelName.toUpperCase().startsWith("CATEGORY_")) {
            displayLabel = "Category: " + labelName.substring(9).replace("_", " ");
        } else if (!List.of("INBOX", "SENT", "DRAFT", "TRASH", "IMPORTANT", "STARRED").contains(labelName.toUpperCase())) {
            displayLabel = "Label: " + labelName;
        }

        final String finalDisplayLabel = displayLabel;
        Platform.runLater(() -> {
            if (workspaceScreen != null) {
                workspaceScreen.setLoadingState(true, "Loading " + finalDisplayLabel);
            }
        });

        Future<?> future = labelFetchExecutor.submit(() -> {
            try {
                // ── Phase 1: fetch first page (fast — gives immediate results) ──
                List<EmailMessage> labelEmails = new ArrayList<>();
                MailboxStats stats;
                try {
                    EmailPage firstPage = services.fetchInboxPage(gmailLabelQuery, null, PAGE_FETCH_SIZE);
                    labelEmails.addAll(firstPage.getEmails());
                    stats = services.fetchMailboxStats(gmailLabelQuery);
                    String nextToken = firstPage.getNextPageToken();

                    // Push first-page results to UI immediately
                    final List<EmailMessage> initialBatch = new ArrayList<>(labelEmails);
                    Platform.runLater(() -> {
                        if (workspaceScreen == null || myGeneration != inboxFetchGeneration.get()) return;
                        workspaceScreen.setLoadingState(false, null);
                        workspaceScreen.mergeEmails(initialBatch);
                        long total = stats != null ? stats.getQueryMessageCount() : 0;
                        workspaceScreen.updateFetchProgress(labelEmails.size(), total, nextToken != null);
                    });

                    // ── Phase 2: continue paging until all label emails fetched ──
                    String token = nextToken;
                    while (token != null && !token.isBlank()) {
                        if (Thread.currentThread().isInterrupted() || myGeneration != inboxFetchGeneration.get()) return;
                        EmailPage page = services.fetchInboxPage(gmailLabelQuery, token, PAGE_FETCH_SIZE);
                        if (page.getEmails().isEmpty()) break;
                        labelEmails.addAll(page.getEmails());
                        token = page.getNextPageToken();
                        final List<EmailMessage> batch = new ArrayList<>(labelEmails);
                        final String finalToken = token;
                        final long totalCount = stats != null ? stats.getQueryMessageCount() : 0;
                        Platform.runLater(() -> {
                            if (workspaceScreen == null || myGeneration != inboxFetchGeneration.get()) return;
                            workspaceScreen.mergeEmails(batch);
                            workspaceScreen.updateFetchProgress(batch.size(), totalCount, finalToken != null);
                        });
                        Thread.sleep(150);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        if (workspaceScreen != null) {
                            workspaceScreen.setLoadingState(false, "Error: " + ex.getMessage());
                            workspaceScreen.setStatus("Label fetch failed: " + ex.getMessage());
                        }
                    });
                    return;
                }

                // ── Phase 3: resume the background inbox fetch if it was us who paused it ──
                if (myGeneration != inboxFetchGeneration.get()) return; // someone else took over
                Platform.runLater(() -> {
                    if (workspaceScreen == null || myGeneration != inboxFetchGeneration.get()) return;
                    workspaceScreen.setLoadingState(false, null);
                    workspaceScreen.setStatus("Label loaded. Resuming background fetch...");
                });
                // Trigger a full background resume — pick up from where we were
                // by re-fetching from cache and continuing pages
                Platform.runLater(this::resumeBackgroundFetch);

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (workspaceScreen != null) {
                        workspaceScreen.setLoadingState(false, "Error: " + ex.getMessage());
                        workspaceScreen.setStatus("Error: " + ex.getMessage());
                    }
                });
            }
        });
        activeLabelFetch.set(future);
    }

    /**
     * Resumes the background inbox fetch from cache (picks up any pages not yet loaded).
     * Called after a priority label fetch completes.
     */
    private void resumeBackgroundFetch() {
        if (workspaceScreen == null) return;
        Optional<com.labless.model.CachedInboxData> cached = services.loadInboxCache(currentConfig.getGmailQuery());
        if (cached.isEmpty()) return;
        com.labless.model.CachedInboxData data = cached.get();
        if (data.getBootstrap().getNextPageToken() == null || data.getBootstrap().getNextPageToken().isBlank()) {
            return; // nothing left to load
        }
        startBackgroundInboxFetch(
            data.getBootstrap().getSnapshot().getEmails(),
            data.getBootstrap().getSnapshot().getLabels(),
            data.getBootstrap().getStats(),
            data.getBootstrap().getNextPageToken()
        );
    }

    private void cancelActiveInboxIndexing() {
        inboxFetchGeneration.incrementAndGet();
    }

    private void startBackgroundInboxFetch(
        List<EmailMessage> seedEmails,
        List<MailboxLabel> seedLabels,
        MailboxStats stats,
        String nextPageToken
    ) {
        if (workspaceScreen == null || nextPageToken == null || nextPageToken.isBlank()) {
            return;
        }
        final int generation = inboxFetchGeneration.incrementAndGet();
        Thread thread = new Thread(() -> {
            List<EmailMessage> all = new ArrayList<>(seedEmails);
            String token = nextPageToken;
            long target = resolveTargetCount(stats, currentConfig.getGmailQuery());
            while (token != null && !token.isBlank()) {
                if (generation != inboxFetchGeneration.get()) {
                    return;
                }
                EmailPage page = services.fetchInboxPage(currentConfig.getGmailQuery(), token, PAGE_FETCH_SIZE);
                if (page.getEmails().isEmpty()) {
                    token = null;
                } else {
                    all.addAll(page.getEmails());
                    token = page.getNextPageToken();
                }
                String currentToken = token;
                List<EmailMessage> snapshot = new ArrayList<>(all);
                InboxSnapshot partialSnapshot = new InboxSnapshot(snapshot, seedLabels == null ? List.of() : new ArrayList<>(seedLabels));
                services.saveInboxCache(
                    currentConfig.getGmailQuery(),
                    new InboxBootstrap(partialSnapshot, stats, currentToken),
                    currentToken == null || currentToken.isBlank()
                );
                Platform.runLater(() -> {
                    if (workspaceScreen == null || generation != inboxFetchGeneration.get()) {
                        return;
                    }
                    workspaceScreen.setEmails(snapshot);
                    workspaceScreen.updateFetchProgress(snapshot.size(), target, currentToken != null && !currentToken.isBlank());
                });
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Platform.runLater(() -> {
                if (workspaceScreen != null && generation == inboxFetchGeneration.get()) {
                    workspaceScreen.updateFetchProgress(all.size(), target, false);
                }
            });
            if (generation != inboxFetchGeneration.get()) {
                return;
            }
            InboxSnapshot finalSnapshot = new InboxSnapshot(all, seedLabels == null ? List.of() : new ArrayList<>(seedLabels));
            services.saveInboxCache(
                currentConfig.getGmailQuery(),
                new InboxBootstrap(finalSnapshot, stats, null),
                true
            );
        }, "inbox-full-fetch-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshFetchedInbox() {
        if (workspaceScreen == null) {
            return;
        }
        cancelActiveInboxIndexing();
        final int generation = inboxFetchGeneration.get();
        workspaceScreen.setStatus("Refreshing fetched emails...");

        Thread thread = new Thread(() -> {
            try {
                Optional<CachedInboxData> cacheOpt = services.loadInboxCache(currentConfig.getGmailQuery());
                if (cacheOpt.isEmpty()) return;

                List<EmailMessage> existingEmails = new ArrayList<>(cacheOpt.get().getBootstrap().getSnapshot().getEmails());
                List<MailboxLabel> existingLabels = cacheOpt.get().getBootstrap().getSnapshot().getLabels();
                String token = null;
                boolean foundOverlap = false;
                List<EmailMessage> newEmails = new ArrayList<>();

                do {
                    if (generation != inboxFetchGeneration.get()) return;
                    EmailPage page = services.fetchInboxPage(currentConfig.getGmailQuery(), token, PAGE_FETCH_SIZE);
                    if (page.getEmails().isEmpty()) break;

                    for (EmailMessage fetched : page.getEmails()) {
                        if (existingEmails.stream().anyMatch(e -> e.getId().equals(fetched.getId()))) {
                            foundOverlap = true;
                            break;
                        }
                        newEmails.add(fetched);
                    }
                    token = page.getNextPageToken();
                } while (!foundOverlap && token != null && !token.isBlank());

                if (generation != inboxFetchGeneration.get()) return;

                if (!newEmails.isEmpty()) {
                    existingEmails.addAll(0, newEmails);
                    MailboxStats stats = services.fetchMailboxStats(currentConfig.getGmailQuery());
                    boolean fullyLoaded = cacheOpt.get().isFullyLoaded();
                    String oldToken = cacheOpt.get().getBootstrap().getNextPageToken();

                    InboxSnapshot snapshot = new InboxSnapshot(existingEmails, existingLabels);
                    services.saveInboxCache(currentConfig.getGmailQuery(), new InboxBootstrap(snapshot, stats, oldToken), fullyLoaded);

                    Platform.runLater(() -> {
                        if (workspaceScreen == null || generation != inboxFetchGeneration.get()) return;
                        workspaceScreen.setEmails(existingEmails);
                        workspaceScreen.setLabels(existingLabels);
                        workspaceScreen.setMailboxStats(stats);
                        long target = resolveTargetCount(stats, currentConfig.getGmailQuery());
                        workspaceScreen.updateFetchProgress(existingEmails.size(), target, !fullyLoaded);
                        workspaceScreen.setStatus("Refreshed. " + newEmails.size() + " new emails.");
                    });
                } else {
                    Platform.runLater(() -> {
                        if (workspaceScreen != null && generation == inboxFetchGeneration.get()) {
                            workspaceScreen.setStatus("Inbox is up to date.");
                        }
                    });
                }
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (workspaceScreen != null) {
                        workspaceScreen.setStatus("Refresh failed: " + ex.getMessage());
                    }
                });
            }
        }, "inbox-refresh-fetched-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshInboxInBackground() {
        refreshFetchedInbox();
    }
    
    /**
     * Starts automatic periodic refresh to check for new emails every 2 minutes.
     * This ensures the GUI stays up-to-date with new incoming emails.
     */
    private void startAutoRefresh() {
        // Stop any existing auto-refresh
        stopAutoRefresh();
        
        // Create new executor
        autoRefreshExecutor = Executors.newSingleThreadScheduledExecutor();
        
        autoRefreshExecutor.scheduleWithFixedDelay(
            () -> {
                try {
                    if (workspaceScreen != null && currentConfig != null) {
                        refreshFetchedInbox();
                    }
                } catch (Exception ex) {
                    // Log but don't crash the scheduler
                    System.err.println("Auto-refresh failed: " + ex.getMessage());
                }
            },
            AUTO_REFRESH_INTERVAL_MS,
            AUTO_REFRESH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Stops the automatic refresh scheduler.
     */
    private void stopAutoRefresh() {
        if (autoRefreshExecutor != null && !autoRefreshExecutor.isShutdown()) {
            autoRefreshExecutor.shutdownNow();
            autoRefreshExecutor = null;
        }
    }

    private static long resolveTargetCount(MailboxStats stats, String query) {
        if (stats == null) {
            return 0;
        }
        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery && stats.getQueryMessageCount() > 0) {
            return stats.getQueryMessageCount();
        }
        return stats.getTotalMessages();
    }

    private void applyLightTheme() {
        if (!sceneRoot.getStyleClass().contains("light")) {
            sceneRoot.getStyleClass().add("light");
        }
    }

    private void applyDarkTheme() {
        sceneRoot.getStyleClass().remove("light");
    }

    private void startJob() {
        if (workspaceScreen == null) {
            return;
        }
        workspaceScreen.setRunning(true);
        workspaceScreen.setStatus("Running");
        workspaceScreen.appendLog("Starting labeling job...");

        JobConfig jobConfig = new JobConfig(
            currentConfig.getCategories(),
            currentConfig.getGmailQuery(),
            currentConfig.getBatchSize(),
            currentConfig.isDryRun()
        );

        Task<JobSummary> task = new Task<>() {
            @Override
            protected JobSummary call() {
                activeProcessor = services.getEmailProcessor();
                return activeProcessor.run(jobConfig, MainApplication.this::handleProgress);
            }
        };

        task.setOnSucceeded(event -> {
            if (workspaceScreen != null) {
                workspaceScreen.complete(task.getValue());
            }
        });

        task.setOnFailed(event -> {
            if (workspaceScreen != null) {
                workspaceScreen.setStatus("Failed");
                workspaceScreen.appendLog("Job failed: " + task.getException().getMessage());
                workspaceScreen.setRunning(false);
            }
        });

        jobExecutor.submit(task);
    }

    private void stopJob() {
        if (activeProcessor != null) {
            activeProcessor.requestStop();
            if (workspaceScreen != null) {
                workspaceScreen.setStatus("Stopping");
                workspaceScreen.appendLog("Stop requested...");
            }
        }
    }

    private void logout() {
        stopJob();
        
        // Stop auto-refresh
        stopAutoRefresh();
        
        Future<?> labelFuture = activeLabelFetch.get();
        if (labelFuture != null) {
            labelFuture.cancel(true);
        }
        
        if (services != null) {
            try {
                services.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            services = null;
        }
        
        try {
            Path tokensDir = Path.of("tokens");
            if (Files.exists(tokensDir)) {
                try (Stream<Path> walk = Files.walk(tokensDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            if (currentConfig != null && currentConfig.getDatabasePath() != null) {
                Path dbPath = Path.of(currentConfig.getDatabasePath());
                Files.deleteIfExists(dbPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        if (currentConfig != null) {
            currentConfig.setOnboardingCompleted(false);
            try {
                configManager.save(CONFIG_PATH, currentConfig);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        googleConnected = false;
        workspaceScreen = null;
        
        Platform.runLater(this::showWelcomeScreen);
    }

    private void handleProgress(JobProgress progress) {
        Platform.runLater(() -> {
            if (workspaceScreen != null) {
                workspaceScreen.updateProgress(progress);
            }
        });
    }

    private void detachWelcomeResponsiveBehavior() {
        if (welcomeScreen != null) {
            welcomeScreen.detachResponsiveBehavior(sceneRoot);
            welcomeScreen = null;
        }
    }

    private void recreateServices() throws SQLException {
        if (services != null) {
            services.close();
        }
        services = AppServices.fromConfig(currentConfig);
    }

    @Override
    public void stop() throws Exception {
        detachWelcomeResponsiveBehavior();
        jobExecutor.shutdownNow();
        labelFetchExecutor.shutdownNow();
        stopAutoRefresh();
        Future<?> lf = activeLabelFetch.getAndSet(null);
        if (lf != null) lf.cancel(true);
        if (services != null) {
            services.close();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
