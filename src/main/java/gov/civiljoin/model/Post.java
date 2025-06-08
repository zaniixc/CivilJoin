package gov.civiljoin.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Post model representing the 'posts' table in the database
 */
public class Post {
    private int id;
    private int userId;
    private String title;
    private String content;
    private List<String> attachments; // Simplified; in real app would be more complex
    private LocalDateTime createdAt;
    private String authorName; // Username of the post author

    // Constructors
    public Post() {
    }

    public Post(int userId, String title, String content, List<String> attachments) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.attachments = attachments;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    @Override
    public String toString() {
        return "Post{" +
                "id=" + id +
                ", userId=" + userId +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", attachments=" + attachments +
                ", createdAt=" + createdAt +
                ", authorName='" + authorName + '\'' +
                '}';
    }
} 