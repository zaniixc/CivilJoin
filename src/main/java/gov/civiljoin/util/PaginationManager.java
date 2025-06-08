package gov.civiljoin.util;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.TilePane;
import javafx.scene.Node;

import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Utility class for handling infinite scroll pagination in the timeline
 * Implements the scrollable timeline interface with pagination requirement from PRD
 */
public class PaginationManager<T> {
    
    private static final Logger LOGGER = Logger.getLogger(PaginationManager.class.getName());
    
    private final ScrollPane scrollPane;
    private final TilePane tilePane;
    private final Function<Integer, List<T>> dataLoader;
    private final Function<T, Node> nodeCreator;
    
    private int currentPage = 0;
    private final int pageSize;
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    
    // Threshold for triggering load - when user scrolls to 80% of content
    private static final double LOAD_THRESHOLD = 0.8;
    
    /**
     * Create a pagination manager
     * 
     * @param scrollPane The ScrollPane containing the content
     * @param tilePane The TilePane that holds the items
     * @param dataLoader Function that loads data for a given page number
     * @param nodeCreator Function that creates UI nodes from data items
     * @param pageSize Number of items per page
     */
    public PaginationManager(ScrollPane scrollPane, TilePane tilePane, 
                           Function<Integer, List<T>> dataLoader, 
                           Function<T, Node> nodeCreator, 
                           int pageSize) {
        this.scrollPane = scrollPane;
        this.tilePane = tilePane;
        this.dataLoader = dataLoader;
        this.nodeCreator = nodeCreator;
        this.pageSize = pageSize;
        
        setupScrollListener();
    }
    
    /**
     * Set up scroll listener for infinite scroll
     */
    private void setupScrollListener() {
        scrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> {
            // Check if we've scrolled past the threshold and should load more
            double scrollPosition = newValue.doubleValue();
            
            if (scrollPosition >= LOAD_THRESHOLD && !isLoading && hasMoreData) {
                loadNextPage();
            }
        });
    }
    
    /**
     * Load the first page of data
     */
    public void loadInitialData() {
        currentPage = 0;
        hasMoreData = true;
        tilePane.getChildren().clear();
        loadNextPage();
    }
    
    /**
     * Load the next page of data
     */
    private void loadNextPage() {
        if (isLoading || !hasMoreData) {
            return;
        }
        
        isLoading = true;
        
        // Show loading indicator
        showLoadingIndicator();
        
        // Load data in background thread to avoid UI blocking
        Thread loadingThread = new Thread(() -> {
            try {
                List<T> newData = dataLoader.apply(currentPage);
                
                Platform.runLater(() -> {
                    try {
                        // Remove loading indicator
                        hideLoadingIndicator();
                        
                        // Check if we have more data
                        if (newData == null || newData.isEmpty()) {
                            hasMoreData = false;
                            showNoMoreDataMessage();
                            return;
                        }
                        
                        // If we got less than a full page, we've reached the end
                        if (newData.size() < pageSize) {
                            hasMoreData = false;
                        }
                        
                        // Add new items to the tile pane
                        for (T item : newData) {
                            Node node = nodeCreator.apply(item);
                            if (node != null) {
                                tilePane.getChildren().add(node);
                            }
                        }
                        
                        currentPage++;
                        
                        LOGGER.info("Loaded page " + currentPage + " with " + newData.size() + " items");
                        
                    } finally {
                        isLoading = false;
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    hideLoadingIndicator();
                    isLoading = false;
                    LOGGER.severe("Error loading page " + currentPage + ": " + e.getMessage());
                });
            }
        });
        
        loadingThread.setDaemon(true);
        loadingThread.start();
    }
    
    /**
     * Show loading indicator
     */
    private void showLoadingIndicator() {
        Platform.runLater(() -> {
            // Check if loading indicator already exists
            boolean hasLoadingIndicator = tilePane.getChildren().stream()
                .anyMatch(node -> node.getId() != null && node.getId().equals("loading-indicator"));
            
            if (!hasLoadingIndicator) {
                javafx.scene.control.Label loadingLabel = new javafx.scene.control.Label("Loading...");
                loadingLabel.setId("loading-indicator");
                loadingLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic; -fx-padding: 20;");
                tilePane.getChildren().add(loadingLabel);
            }
        });
    }
    
    /**
     * Hide loading indicator
     */
    private void hideLoadingIndicator() {
        Platform.runLater(() -> {
            tilePane.getChildren().removeIf(node -> 
                node.getId() != null && node.getId().equals("loading-indicator"));
        });
    }
    
    /**
     * Show message when no more data is available
     */
    private void showNoMoreDataMessage() {
        Platform.runLater(() -> {
            // Check if message already exists
            boolean hasEndMessage = tilePane.getChildren().stream()
                .anyMatch(node -> node.getId() != null && node.getId().equals("end-message"));
            
            if (!hasEndMessage) {
                javafx.scene.control.Label endLabel = new javafx.scene.control.Label("No more items to load");
                endLabel.setId("end-message");
                endLabel.setStyle("-fx-text-fill: #888; -fx-font-style: italic; -fx-padding: 20;");
                tilePane.getChildren().add(endLabel);
            }
        });
    }
    
    /**
     * Refresh the data by reloading from the beginning
     */
    public void refresh() {
        currentPage = 0;
        hasMoreData = true;
        isLoading = false;
        tilePane.getChildren().clear();
        loadNextPage();
    }
    
    /**
     * Check if currently loading
     */
    public boolean isLoading() {
        return isLoading;
    }
    
    /**
     * Check if has more data to load
     */
    public boolean hasMoreData() {
        return hasMoreData;
    }
    
    /**
     * Get current page number
     */
    public int getCurrentPage() {
        return currentPage;
    }
} 