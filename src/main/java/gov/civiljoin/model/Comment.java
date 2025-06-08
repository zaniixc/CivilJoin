package gov.civiljoin.model;

import java.time.LocalDateTime;

/**
 * Comment model representing the 'comments' table in the database
 */
public class Comment {
    private int id;
    private int postId;
    private int userId;
    private String content;
    private LocalDateTime createdAt;
    private String username; // For display purposes, not stored in DB

    // Constructors
    public Comment() {
    }

    public Comment(int postId, int userId, String content) {
        this.postId = postId;
        this.userId = userId;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id=" + id +
                ", postId=" + postId +
                ", userId=" + userId +
                ", content='" + content + '\'' +
                ", createdAt=" + createdAt +
                ", username='" + username + '\'' +
                '}';
    }
} 