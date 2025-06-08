package gov.civiljoin.component;

import gov.civiljoin.model.Comment;
import gov.civiljoin.model.Post;
import gov.civiljoin.model.User;
import gov.civiljoin.service.CommentService;
import gov.civiljoin.service.PostService;
import gov.civiljoin.util.AlertUtil;
import gov.civiljoin.util.ThemeManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modern post card component with enhanced styling
 */
public class PostCardComponent extends VBox {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private final Post post;
    private final User currentUser;
    private final PostService postService;
    private final CommentService commentService = new CommentService();
    private final Consumer<Post> onDeleteCallback;
    private final Consumer<Post> onDownloadCallback;
    
    private boolean commentsExpanded = false;
    private VBox commentsContainer;
    private Button commentsButton;
    
    /**
     * Create a post card component
     * 
     * @param post The post to display
     * @param currentUser The current user
     * @param postService Post service for operations
     * @param onDeleteCallback Callback when post is deleted
     * @param onDownloadCallback Callback when attachments download is requested
     */
    public PostCardComponent(Post post, User currentUser, PostService postService, 
                           Consumer<Post> onDeleteCallback, Consumer<Post> onDownloadCallback) {
        this.post = post;
        this.currentUser = currentUser;
        this.postService = postService;
        this.onDeleteCallback = onDeleteCallback;
        this.onDownloadCallback = onDownloadCallback;
        
        setupComponent();
    }
    
    /**
     * Setup the component UI
     */
    private void setupComponent() {
        // Add style class for CSS styling
        this.getStyleClass().add("post-card");
        
        // Configure the container
        this.setSpacing(16);
        this.setPadding(new Insets(24));
        this.setMinWidth(300);
        this.setMaxWidth(600);
        
        // Add components in modern layout
        createHeader();
        createContent();
        createFooter();
    }
    
    /**
     * Create the header section with title and author info
     */
    private void createHeader() {
        VBox headerSection = new VBox(12);
        headerSection.getStyleClass().add("header-section");
        
        // Title with modern styling
        Label titleLabel = new Label(post.getTitle());
        titleLabel.getStyleClass().add("title");
        titleLabel.setWrapText(true);
        
        // Metadata section
        HBox metadata = new HBox(8);
        metadata.getStyleClass().add("metadata");
        metadata.setAlignment(Pos.CENTER_LEFT);
        
        String author = postService.getUsernameForUserId(post.getUserId());
        Label authorLabel = new Label("Posted by " + author);
        
        Label separator = new Label("•");
        separator.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.4);");
        
        Label dateLabel = new Label(post.getCreatedAt() != null ? 
            post.getCreatedAt().format(DATE_FORMATTER) : "Just now");
        
        metadata.getChildren().addAll(authorLabel, separator, dateLabel);
        
        headerSection.getChildren().addAll(titleLabel, metadata);
        this.getChildren().add(headerSection);
    }
    
    /**
     * Create the content section
     */
    private void createContent() {
        Label contentLabel = new Label(post.getContent());
        contentLabel.getStyleClass().add("content");
        contentLabel.setWrapText(true);
        VBox.setVgrow(contentLabel, Priority.ALWAYS);
        
        this.getChildren().add(contentLabel);
    }
    
    /**
     * Create the footer with date and action buttons
     */
    private void createFooter() {
        HBox actions = new HBox(12);
        actions.getStyleClass().add("actions");
        actions.setAlignment(Pos.CENTER_LEFT);
        
        // Comments button with modern styling
        commentsButton = new Button("Comments");
        commentsButton.getStyleClass().addAll("action-button");
        commentsButton.setOnAction(e -> toggleComments());
        
        actions.getChildren().add(commentsButton);
        
        // Download button if attachments exist
        if (post.getAttachments() != null && !post.getAttachments().isEmpty()) {
            Button downloadButton = new Button("Download");
            downloadButton.getStyleClass().addAll("action-button", "download-button");
            downloadButton.setOnAction(e -> {
                if (onDownloadCallback != null) {
                    onDownloadCallback.accept(post);
                }
            });
            actions.getChildren().add(downloadButton);
        }
        
        // Delete button for admin or author
        if (currentUser != null && (currentUser.getRole() == User.Role.ADMIN || 
                                   currentUser.getId() == post.getUserId())) {
            Button deleteButton = new Button("Delete");
            deleteButton.getStyleClass().addAll("action-button", "delete-button");
            deleteButton.setOnAction(e -> {
                if (onDeleteCallback != null) {
                    onDeleteCallback.accept(post);
                }
            });
            actions.getChildren().add(deleteButton);
        }
        
        this.getChildren().add(actions);
        
        // Comments container
        commentsContainer = new VBox(16);
        commentsContainer.getStyleClass().add("comments-section");
        commentsContainer.setVisible(false);
        commentsContainer.setManaged(false);
        this.getChildren().add(commentsContainer);
    }
    
    /**
     * Toggle comments visibility
     */
    private void toggleComments() {
        commentsExpanded = !commentsExpanded;
        commentsContainer.setVisible(commentsExpanded);
        commentsContainer.setManaged(commentsExpanded);
        
        if (commentsExpanded) {
            commentsButton.setText("Hide Comments");
            loadComments();
        } else {
            commentsButton.setText("Comments");
        }
    }
    
    /**
     * Load and display comments for this post
     */
    private void loadComments() {
        commentsContainer.getChildren().clear();
        
        // Comments header with count
        HBox headerBox = new HBox(8);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        List<Comment> comments = commentService.getCommentsForPost(post.getId());
        String commentCount = comments != null ? String.valueOf(comments.size()) : "0";
        
        Label commentsHeader = new Label("Comments (" + commentCount + ")");
        commentsHeader.getStyleClass().add("comments-header");
        headerBox.getChildren().add(commentsHeader);
        
        commentsContainer.getChildren().add(headerBox);
        
        // Add comment box at the top
        commentsContainer.getChildren().add(createAddCommentBox());
        
        // Add separator
        Region separator = new Region();
        separator.getStyleClass().add("separator");
        separator.setPrefHeight(1);
        commentsContainer.getChildren().add(separator);
        
        // Load existing comments
        if (comments != null && !comments.isEmpty()) {
            VBox commentsBox = new VBox(12);
            commentsBox.getStyleClass().add("comments-list");
            
            for (Comment comment : comments) {
                commentsBox.getChildren().add(createCommentView(comment));
            }
            
            commentsContainer.getChildren().add(commentsBox);
        } else {
            Label noCommentsLabel = new Label("No comments yet. Be the first to comment!");
            noCommentsLabel.getStyleClass().add("no-comments-label");
            noCommentsLabel.setWrapText(true);
            commentsContainer.getChildren().add(noCommentsLabel);
        }
    }
    
    /**
     * Create a view for a single comment
     */
    private HBox createCommentView(Comment comment) {
        VBox commentBox = new VBox(8);
        commentBox.getStyleClass().add("comment");
        
        // Comment header with author and timestamp
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label authorLabel = new Label(postService.getUsernameForUserId(comment.getUserId()));
        authorLabel.getStyleClass().add("author");
        
        Label timestampLabel = new Label(comment.getCreatedAt().format(DATE_FORMATTER));
        timestampLabel.getStyleClass().add("timestamp");
        
        header.getChildren().addAll(authorLabel, timestampLabel);
        
        // Comment content
        Label contentLabel = new Label(comment.getContent());
        contentLabel.getStyleClass().add("content");
        contentLabel.setWrapText(true);
        
        commentBox.getChildren().addAll(header, contentLabel);
        
        // Delete option for admin or comment author
        if (currentUser != null && (currentUser.getRole() == User.Role.ADMIN || 
                                   currentUser.getId() == comment.getUserId())) {
            Button deleteButton = new Button("Delete");
            deleteButton.getStyleClass().addAll("action-button", "delete-button");
            deleteButton.setOnAction(e -> deleteComment(comment));
            
            HBox actionBox = new HBox(deleteButton);
            actionBox.setAlignment(Pos.CENTER_RIGHT);
            commentBox.getChildren().add(actionBox);
        }
        
        return new HBox(commentBox);
    }
    
    /**
     * Create the add comment box
     */
    private VBox createAddCommentBox() {
        VBox addCommentBox = new VBox(12);
        addCommentBox.getStyleClass().add("add-comment-box");
        
        Label commentLabel = new Label("Write a comment");
        commentLabel.getStyleClass().add("comment-label");
        
        TextArea commentInput = new TextArea();
        commentInput.getStyleClass().add("comment-input");
        commentInput.setPromptText("Share your thoughts...");
        commentInput.setPrefRowCount(3);
        commentInput.setWrapText(true);
        
        Button submitButton = new Button("Post Comment");
        submitButton.getStyleClass().addAll("action-button", "primary");
        submitButton.setOnAction(e -> {
            String content = commentInput.getText().trim();
            if (!content.isEmpty()) {
                addComment(content);
                commentInput.clear();
            }
        });
        
        HBox buttonBox = new HBox(submitButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(8, 0, 0, 0));
        
        addCommentBox.getChildren().addAll(commentLabel, commentInput, buttonBox);
        return addCommentBox;
    }
    
    /**
     * Add a new comment
     */
    private void addComment(String content) {
        Comment comment = new Comment(post.getId(), currentUser.getId(), content);
        
        if (commentService.createComment(comment)) {
            // Reload comments to show new comment
            loadComments();
        } else {
            AlertUtil.showErrorAlert("Error", "Failed to add comment. Please try again.");
        }
    }
    
    /**
     * Delete a comment
     */
    private void deleteComment(Comment comment) {
        boolean isAdmin = currentUser.getRole() == User.Role.ADMIN || currentUser.getRole() == User.Role.OWNER;
        
        if (commentService.deleteComment(comment.getId(), currentUser.getId(), isAdmin)) {
            // Reload comments to update the view
            loadComments();
        } else {
            AlertUtil.showErrorAlert("Error", "Failed to delete comment.");
        }
    }
    
    /**
     * Update the post card styling when theme changes
     */
    public void updateTheme() {
        // Recreate the component with updated styles
        this.getChildren().clear();
        setupComponent();
    }
} 