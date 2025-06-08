package gov.civiljoin.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Model for security events tracked by the admin security system
 */
public class SecurityEvent {
    private int id;
    private EventType eventType;
    private Integer userId;
    private String username; // For display purposes
    private String ipAddress;
    private String userAgent;
    private String description;
    private Severity severity;
    private boolean resolved;
    private Integer resolvedBy;
    private String resolvedByUsername; // For display purposes
    private LocalDateTime resolvedAt;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    public enum EventType {
        FAILED_LOGIN("🔐", "Failed Login Attempt", "A user attempted to login with incorrect credentials"),
        BRUTE_FORCE_ATTEMPT("🚫", "Brute Force Attack", "Multiple failed login attempts detected"),
        SUSPICIOUS_ACTIVITY("⚠️", "Suspicious Activity", "Unusual user behavior detected"),
        UNAUTHORIZED_ACCESS("🔒", "Unauthorized Access", "Attempt to access restricted resources"),
        ACCOUNT_LOCKOUT("🔴", "Account Lockout", "User account has been temporarily locked"),
        SECURITY_BREACH("🚨", "Security Breach", "Potential security vulnerability exploited");

        private final String icon;
        private final String displayName;
        private final String description;

        EventType(String icon, String displayName, String description) {
            this.icon = icon;
            this.displayName = displayName;
            this.description = description;
        }

        public String getIcon() { return icon; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum Severity {
        LOW("🟢", "#4ade80", "Low priority, monitor only"),
        MEDIUM("🟡", "#fbbf24", "Medium priority, investigate when possible"),
        HIGH("🟠", "#f97316", "High priority, investigate immediately"),
        CRITICAL("🔴", "#ef4444", "Critical priority, immediate action required");

        private final String icon;
        private final String color;
        private final String description;

        Severity(String icon, String color, String description) {
            this.icon = icon;
            this.color = color;
            this.description = description;
        }

        public String getIcon() { return icon; }
        public String getColor() { return color; }
        public String getDescription() { return description; }
    }

    // Constructors
    public SecurityEvent() {}

    public SecurityEvent(EventType eventType, Integer userId, String ipAddress, 
                        String description, Severity severity) {
        this.eventType = eventType;
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.description = description;
        this.severity = severity;
        this.resolved = false;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public Integer getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Integer resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolvedByUsername() { return resolvedByUsername; }
    public void setResolvedByUsername(String resolvedByUsername) { this.resolvedByUsername = resolvedByUsername; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Get display text for security dashboard
     */
    public String getDisplayText() {
        String userDisplay = username != null ? username : (userId != null ? "User " + userId : "Unknown");
        return String.format("%s %s - %s", 
            eventType.getIcon(), 
            eventType.getDisplayName(),
            userDisplay
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

    /**
     * Mark event as resolved
     */
    public void resolve(int resolvedByUserId) {
        this.resolved = true;
        this.resolvedBy = resolvedByUserId;
        this.resolvedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "SecurityEvent{" +
                "id=" + id +
                ", eventType=" + eventType +
                ", userId=" + userId +
                ", ipAddress='" + ipAddress + '\'' +
                ", severity=" + severity +
                ", resolved=" + resolved +
                ", createdAt=" + createdAt +
                '}';
    }
} 