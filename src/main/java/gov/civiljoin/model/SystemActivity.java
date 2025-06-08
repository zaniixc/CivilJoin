package gov.civiljoin.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Model for system activities displayed in the enhanced timeline
 */
public class SystemActivity {
    private int id;
    private Integer userId;
    private String username; // For display purposes
    private ActivityType activityType;
    private String description;
    private EntityType entityType;
    private Integer entityId;
    private Severity severity;
    private String ipAddress;
    private String userAgent;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    public enum ActivityType {
        USER_REGISTRATION("👤", "User Registration"),
        USER_LOGIN("🔐", "User Login"),
        USER_LOGOUT("🚪", "User Logout"),
        POST_CREATED("📄", "Post Created"),
        POST_DELETED("🗑️", "Post Deleted"),
        COMMENT_ADDED("💬", "Comment Added"),
        COMMENT_DELETED("❌", "Comment Deleted"),
        KEY_GENERATED("🔑", "Key Generated"),
        KEY_USED("✅", "Key Used"),
        ADMIN_ACTION("⚡", "Admin Action"),
        SYSTEM_ANNOUNCEMENT("📢", "System Announcement"),
        SECURITY_EVENT("🔒", "Security Event"),
        POLICY_CHANGE("📜", "Policy Change"),
        EMERGENCY_ALERT("🚨", "Emergency Alert");

        private final String icon;
        private final String displayName;

        ActivityType(String icon, String displayName) {
            this.icon = icon;
            this.displayName = displayName;
        }

        public String getIcon() { return icon; }
        public String getDisplayName() { return displayName; }
    }

    public enum EntityType {
        USER, POST, COMMENT, KEY_ID, SYSTEM
    }

    public enum Severity {
        LOW("🟢", "#4ade80"),
        MEDIUM("🟡", "#fbbf24"),
        HIGH("🟠", "#f97316"),
        CRITICAL("🔴", "#ef4444");

        private final String icon;
        private final String color;

        Severity(String icon, String color) {
            this.icon = icon;
            this.color = color;
        }

        public String getIcon() { return icon; }
        public String getColor() { return color; }
    }

    // Constructors
    public SystemActivity() {}

    public SystemActivity(Integer userId, ActivityType activityType, String description, 
                         EntityType entityType, Severity severity) {
        this.userId = userId;
        this.activityType = activityType;
        this.description = description;
        this.entityType = entityType;
        this.severity = severity;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public ActivityType getActivityType() { return activityType; }
    public void setActivityType(ActivityType activityType) { this.activityType = activityType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public EntityType getEntityType() { return entityType; }
    public void setEntityType(EntityType entityType) { this.entityType = entityType; }

    public Integer getEntityId() { return entityId; }
    public void setEntityId(Integer entityId) { this.entityId = entityId; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Get display text for timeline
     */
    public String getDisplayText() {
        String userDisplay = username != null ? username : (userId != null ? "User " + userId : "System");
        return String.format("%s %s %s", 
            activityType.getIcon(), 
            userDisplay, 
            description.toLowerCase()
        );
    }

    /**
     * Get relative time string (e.g., "2 minutes ago")
     */
    public String getRelativeTime() {
        if (createdAt == null) return "Unknown time";
        
        LocalDateTime now = LocalDateTime.now();
        long minutes = java.time.Duration.between(createdAt, now).toMinutes();
        
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        
        long hours = minutes / 60;
        if (hours < 24) return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        
        long days = hours / 24;
        if (days < 7) return days + " day" + (days == 1 ? "" : "s") + " ago";
        
        long weeks = days / 7;
        return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";
    }

    @Override
    public String toString() {
        return "SystemActivity{" +
                "id=" + id +
                ", userId=" + userId +
                ", activityType=" + activityType +
                ", description='" + description + '\'' +
                ", severity=" + severity +
                ", createdAt=" + createdAt +
                '}';
    }
} 