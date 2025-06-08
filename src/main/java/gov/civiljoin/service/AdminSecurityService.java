package gov.civiljoin.service;

import gov.civiljoin.model.User;
import gov.civiljoin.util.DatabaseUtil;
import gov.civiljoin.util.NotificationManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Advanced Admin Security Service for CivilJoin
 * Features: Proactive Monitoring, Granular Control, Emergency Response, Comprehensive Auditing
 */
public class AdminSecurityService {
    private static final Logger LOGGER = Logger.getLogger(AdminSecurityService.class.getName());
    private static AdminSecurityService instance;
    
    // Security monitoring
    private final ScheduledExecutorService securityMonitor = Executors.newScheduledThreadPool(2);
    private final Map<String, SecurityThreat> activeThreatMap = new ConcurrentHashMap<>();
    private final Map<Integer, UserSecurityProfile> userProfiles = new ConcurrentHashMap<>();
    
    // Emergency response
    private boolean emergencyMode = false;
    private LocalDateTime emergencyActivatedAt;
    private String emergencyReason;
    
    // Audit trail
    private final List<SecurityEvent> recentEvents = Collections.synchronizedList(new ArrayList<>());
    
    public enum ThreatLevel {
        LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4), EMERGENCY(5);
        private final int severity;
        ThreatLevel(int severity) { this.severity = severity; }
        public int getSeverity() { return severity; }
    }
    
    public enum SecurityAction {
        LOGIN_ATTEMPT, ADMIN_ACCESS, DATA_MODIFICATION, PRIVILEGE_ESCALATION, 
        EMERGENCY_ACTIVATION, SYSTEM_OVERRIDE, BULK_OPERATION, SENSITIVE_ACCESS
    }
    
    private AdminSecurityService() {
        initializeSecurityMonitoring();
    }
    
    public static AdminSecurityService getInstance() {
        if (instance == null) {
            instance = new AdminSecurityService();
        }
        return instance;
    }
    
    /**
     * Initialize proactive security monitoring
     */
    private void initializeSecurityMonitoring() {
        // Monitor for suspicious patterns every 30 seconds
        securityMonitor.scheduleAtFixedRate(this::scanForSuspiciousActivity, 0, 30, TimeUnit.SECONDS);
        
        // Cleanup old security events every hour
        securityMonitor.scheduleAtFixedRate(this::cleanupOldEvents, 0, 1, TimeUnit.HOURS);
        
        LOGGER.info("AdminSecurityService: Proactive monitoring initialized");
    }
    
    /**
     * PROACTIVE MONITORING: Scan for suspicious activity patterns
     */
    private void scanForSuspiciousActivity() {
        try {
            // Check for rapid failed login attempts
            checkFailedLoginPatterns();
            
            // Monitor privilege escalation attempts
            checkPrivilegeEscalation();
            
            // Detect unusual data access patterns
            checkDataAccessPatterns();
            
            // Monitor system resource usage
            checkSystemResourceUsage();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during security scan", e);
        }
    }
    
    /**
     * GRANULAR CONTROL: Check and enforce user permissions
     */
    public boolean checkPermission(User user, SecurityAction action, String resource) {
        if (user == null) return false;
        
        // Emergency mode restrictions
        if (emergencyMode && user.getRole() != User.Role.OWNER) {
            logSecurityEvent(user.getId(), action, "BLOCKED_EMERGENCY_MODE", ThreatLevel.HIGH,
                "Action blocked due to emergency mode: " + action);
            NotificationManager.getInstance().showNotification(
                "Emergency Mode Active - System is in emergency mode. Only owners can perform actions.",
                NotificationManager.NotificationType.ERROR
            );
            return false;
        }
        
        // Role-based access control
        switch (action) {
            case EMERGENCY_ACTIVATION:
            case SYSTEM_OVERRIDE:
                return user.getRole() == User.Role.OWNER;
                
            case PRIVILEGE_ESCALATION:
            case BULK_OPERATION:
                return user.getRole() == User.Role.OWNER || user.getRole() == User.Role.ADMIN;
                
            case ADMIN_ACCESS:
            case SENSITIVE_ACCESS:
                return user.getRole() != User.Role.USER;
                
            default:
                return true;
        }
    }
    
    /**
     * EMERGENCY RESPONSE: Activate emergency mode
     */
    public void activateEmergencyMode(User activatedBy, String reason) {
        if (!checkPermission(activatedBy, SecurityAction.EMERGENCY_ACTIVATION, "system")) {
            return;
        }
        
        emergencyMode = true;
        emergencyActivatedAt = LocalDateTime.now();
        emergencyReason = reason;
        
        // Log critical security event
        logSecurityEvent(activatedBy.getId(), SecurityAction.EMERGENCY_ACTIVATION, 
            "EMERGENCY_MODE_ACTIVATED", ThreatLevel.EMERGENCY, 
            "Emergency mode activated: " + reason);
        
        // Notify all admins
        NotificationManager.getInstance().showNotification(
            "EMERGENCY MODE ACTIVATED - System security emergency mode has been activated by " + activatedBy.getUsername() + 
            ". Reason: " + reason,
            NotificationManager.NotificationType.ERROR
        );
        
        // Force logout all non-owner users
        CompletableFuture.runAsync(this::forceLogoutNonOwners);
        
        LOGGER.log(Level.SEVERE, "EMERGENCY MODE ACTIVATED by " + activatedBy.getUsername() + ": " + reason);
    }
    
    /**
     * Deactivate emergency mode
     */
    public void deactivateEmergencyMode(User deactivatedBy) {
        if (!checkPermission(deactivatedBy, SecurityAction.EMERGENCY_ACTIVATION, "system")) {
            return;
        }
        
        emergencyMode = false;
        
        logSecurityEvent(deactivatedBy.getId(), SecurityAction.EMERGENCY_ACTIVATION, 
            "EMERGENCY_MODE_DEACTIVATED", ThreatLevel.HIGH, 
            "Emergency mode deactivated by " + deactivatedBy.getUsername());
        
        NotificationManager.getInstance().showNotification(
            "Emergency Mode Deactivated - System emergency mode has been deactivated by " + deactivatedBy.getUsername(),
            NotificationManager.NotificationType.SUCCESS
        );
        
        LOGGER.info("Emergency mode deactivated by " + deactivatedBy.getUsername());
    }
    
    /**
     * COMPREHENSIVE AUDITING: Log all security events
     */
    public void logSecurityEvent(Integer userId, SecurityAction action, String eventType, 
                                ThreatLevel threatLevel, String details) {
        try {
            SecurityEvent event = new SecurityEvent(
                userId, action, eventType, threatLevel, details, LocalDateTime.now()
            );
            
            // Add to recent events
            recentEvents.add(event);
            
            // Store in database
            storeSecurityEventInDatabase(event);
            
            // Handle high-threat events
            if (threatLevel.getSeverity() >= ThreatLevel.HIGH.getSeverity()) {
                handleHighThreatEvent(event);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to log security event", e);
        }
    }
    
    /**
     * Get real-time security dashboard data
     */
    public Map<String, Object> getSecurityDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        // Emergency status
        dashboard.put("emergencyMode", emergencyMode);
        dashboard.put("emergencyReason", emergencyReason);
        dashboard.put("emergencyActivatedAt", emergencyActivatedAt);
        
        // Threat summary
        Map<ThreatLevel, Long> threatCounts = new HashMap<>();
        for (ThreatLevel level : ThreatLevel.values()) {
            long count = recentEvents.stream()
                .filter(e -> e.getThreatLevel() == level)
                .filter(e -> e.getTimestamp().isAfter(LocalDateTime.now().minusHours(24)))
                .count();
            threatCounts.put(level, count);
        }
        dashboard.put("threatCounts", threatCounts);
        
        // Active threats
        dashboard.put("activeThreats", new ArrayList<>(activeThreatMap.values()));
        
        // System health
        dashboard.put("systemHealth", calculateSystemHealth());
        
        // Recent security events (last 50)
        List<SecurityEvent> recentEventsCopy = new ArrayList<>(recentEvents);
        Collections.reverse(recentEventsCopy);
        dashboard.put("recentEvents", recentEventsCopy.stream()
            .limit(50)
            .toList());
        
        return dashboard;
    }
    
    /**
     * Get user security profile with risk assessment
     */
    public UserSecurityProfile getUserSecurityProfile(int userId) {
        return userProfiles.computeIfAbsent(userId, id -> {
            UserSecurityProfile profile = new UserSecurityProfile(id);
            loadUserSecurityHistory(profile);
            return profile;
        });
    }
    
    /**
     * Force immediate security action
     */
    public void executeSecurityAction(User executor, String actionType, Map<String, Object> parameters) {
        if (!checkPermission(executor, SecurityAction.SYSTEM_OVERRIDE, "security")) {
            return;
        }
        
        switch (actionType) {
            case "FORCE_LOGOUT_ALL":
                forceLogoutAllUsers();
                break;
            case "LOCK_USER":
                Integer targetUserId = (Integer) parameters.get("userId");
                if (targetUserId != null) {
                    lockUserAccount(targetUserId, executor);
                }
                break;
            case "SYSTEM_LOCKDOWN":
                activateEmergencyMode(executor, "Manual system lockdown");
                break;
            case "CLEAR_THREATS":
                clearAllThreats(executor);
                break;
        }
        
        logSecurityEvent(executor.getId(), SecurityAction.SYSTEM_OVERRIDE, actionType, 
            ThreatLevel.HIGH, "Security action executed: " + actionType);
    }
    
    // Private helper methods
    
    private void checkFailedLoginPatterns() {
        // Implementation for detecting suspicious login patterns
        String sql = """
            SELECT ip_address, COUNT(*) as attempts 
            FROM security_events 
            WHERE event_type = 'FAILED_LOGIN' 
            AND created_at > DATE_SUB(NOW(), INTERVAL 15 MINUTE) 
            GROUP BY ip_address 
            HAVING attempts > 5
            """;
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String ipAddress = rs.getString("ip_address");
                int attempts = rs.getInt("attempts");
                
                SecurityThreat threat = new SecurityThreat(
                    "BRUTE_FORCE_" + ipAddress,
                    ThreatLevel.HIGH,
                    "Potential brute force attack from IP: " + ipAddress + " (" + attempts + " attempts)",
                    LocalDateTime.now()
                );
                
                activeThreatMap.put(threat.getId(), threat);
                
                NotificationManager.getInstance().showNotification(
                    "Security Alert - Suspicious login activity detected from IP: " + ipAddress,
                    NotificationManager.NotificationType.WARNING
                );
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error checking failed login patterns", e);
        }
    }
    
    private void checkPrivilegeEscalation() {
        // Check for unusual admin actions
        String sql = """
            SELECT user_id, COUNT(*) as admin_actions 
            FROM security_events 
            WHERE event_type LIKE '%ADMIN%' 
            AND created_at > DATE_SUB(NOW(), INTERVAL 1 HOUR) 
            GROUP BY user_id 
            HAVING admin_actions > 10
            """;
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                int userId = rs.getInt("user_id");
                int actions = rs.getInt("admin_actions");
                
                SecurityThreat threat = new SecurityThreat(
                    "PRIVILEGE_ESCALATION_" + userId,
                    ThreatLevel.MEDIUM,
                    "Unusual admin activity from user ID: " + userId + " (" + actions + " actions)",
                    LocalDateTime.now()
                );
                
                activeThreatMap.put(threat.getId(), threat);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error checking privilege escalation", e);
        }
    }
    
    private void checkDataAccessPatterns() {
        // Monitor for bulk data access or unusual patterns
        // Implementation would check for rapid database queries, bulk exports, etc.
    }
    
    private void checkSystemResourceUsage() {
        // Monitor CPU, memory, database connections
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsagePercent = (double) usedMemory / totalMemory * 100;
        
        if (memoryUsagePercent > 90) {
            SecurityThreat threat = new SecurityThreat(
                "HIGH_MEMORY_USAGE",
                ThreatLevel.MEDIUM,
                "High memory usage detected: " + String.format("%.1f", memoryUsagePercent) + "%",
                LocalDateTime.now()
            );
            
            activeThreatMap.put(threat.getId(), threat);
        }
    }
    
    private void handleHighThreatEvent(SecurityEvent event) {
        // Automatic response to high-threat events
        NotificationManager.getInstance().showNotification(
            "Security Alert - " + event.getThreatLevel() + ": " + event.getDetails(),
            NotificationManager.NotificationType.ERROR
        );
        
        // If critical threat, consider emergency measures
        if (event.getThreatLevel() == ThreatLevel.CRITICAL) {
            // Could auto-activate emergency mode for certain threat types
        }
    }
    
    private void storeSecurityEventInDatabase(SecurityEvent event) {
        String sql = """
            INSERT INTO security_events 
            (user_id, event_type, description, severity, ip_address, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """;
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, event.getUserId());
            stmt.setString(2, event.getEventType());
            stmt.setString(3, event.getDetails());
            stmt.setString(4, event.getThreatLevel().name());
            stmt.setString(5, null); // IP address would be passed in
            stmt.setString(6, "{}"); // JSON metadata
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to store security event in database", e);
        }
    }
    
    private void forceLogoutNonOwners() {
        // Implementation to invalidate sessions for non-owner users
        String sql = "DELETE FROM user_sessions WHERE user_id NOT IN (SELECT id FROM users WHERE role = 'OWNER')";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int sessionsRemoved = stmt.executeUpdate();
            LOGGER.info("Emergency mode: Removed " + sessionsRemoved + " non-owner sessions");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to force logout non-owners", e);
        }
    }
    
    private void forceLogoutAllUsers() {
        String sql = "DELETE FROM user_sessions";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int sessionsRemoved = stmt.executeUpdate();
            LOGGER.info("Security action: Removed all " + sessionsRemoved + " user sessions");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to force logout all users", e);
        }
    }
    
    private void lockUserAccount(int userId, User executor) {
        String sql = "UPDATE users SET locked_until = DATE_ADD(NOW(), INTERVAL 24 HOUR) WHERE id = ?";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            
            logSecurityEvent(executor.getId(), SecurityAction.SYSTEM_OVERRIDE, "USER_LOCKED",
                ThreatLevel.HIGH, "User " + userId + " locked by " + executor.getUsername());
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to lock user account", e);
        }
    }
    
    private void clearAllThreats(User executor) {
        activeThreatMap.clear();
        
        NotificationManager.getInstance().showNotification(
            "Threats Cleared - All active security threats have been cleared by " + executor.getUsername(),
            NotificationManager.NotificationType.SUCCESS
        );
    }
    
    private void loadUserSecurityHistory(UserSecurityProfile profile) {
        // Load user's security history from database
        String sql = """
            SELECT event_type, severity, created_at 
            FROM security_events 
            WHERE user_id = ? 
            ORDER BY created_at DESC 
            LIMIT 100
            """;
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, profile.getUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    profile.addHistoryEvent(
                        rs.getString("event_type"),
                        rs.getString("severity"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error loading user security history", e);
        }
    }
    
    private double calculateSystemHealth() {
        // Calculate overall system health score (0-100)
        double health = 100.0;
        
        // Deduct points for active threats
        for (SecurityThreat threat : activeThreatMap.values()) {
            health -= threat.getThreatLevel().getSeverity() * 5;
        }
        
        // Deduct points for emergency mode
        if (emergencyMode) {
            health -= 30;
        }
        
        // Deduct points for recent high-severity events
        long recentHighEvents = recentEvents.stream()
            .filter(e -> e.getTimestamp().isAfter(LocalDateTime.now().minusHours(24)))
            .filter(e -> e.getThreatLevel().getSeverity() >= ThreatLevel.HIGH.getSeverity())
            .count();
        
        health -= recentHighEvents * 3;
        
        return Math.max(0, Math.min(100, health));
    }
    
    private void cleanupOldEvents() {
        // Remove events older than 30 days
        recentEvents.removeIf(event -> 
            event.getTimestamp().isBefore(LocalDateTime.now().minusDays(30)));
        
        // Remove resolved threats older than 24 hours
        activeThreatMap.entrySet().removeIf(entry ->
            entry.getValue().getDetectedAt().isBefore(LocalDateTime.now().minusHours(24)));
    }
    
    public void shutdown() {
        securityMonitor.shutdown();
    }
    
    // Inner classes for data structures
    
    public static class SecurityThreat {
        private final String id;
        private final ThreatLevel threatLevel;
        private final String description;
        private final LocalDateTime detectedAt;
        
        public SecurityThreat(String id, ThreatLevel threatLevel, String description, LocalDateTime detectedAt) {
            this.id = id;
            this.threatLevel = threatLevel;
            this.description = description;
            this.detectedAt = detectedAt;
        }
        
        // Getters
        public String getId() { return id; }
        public ThreatLevel getThreatLevel() { return threatLevel; }
        public String getDescription() { return description; }
        public LocalDateTime getDetectedAt() { return detectedAt; }
    }
    
    public static class SecurityEvent {
        private final Integer userId;
        private final SecurityAction action;
        private final String eventType;
        private final ThreatLevel threatLevel;
        private final String details;
        private final LocalDateTime timestamp;
        
        public SecurityEvent(Integer userId, SecurityAction action, String eventType, 
                           ThreatLevel threatLevel, String details, LocalDateTime timestamp) {
            this.userId = userId;
            this.action = action;
            this.eventType = eventType;
            this.threatLevel = threatLevel;
            this.details = details;
            this.timestamp = timestamp;
        }
        
        // Getters
        public Integer getUserId() { return userId; }
        public SecurityAction getAction() { return action; }
        public String getEventType() { return eventType; }
        public ThreatLevel getThreatLevel() { return threatLevel; }
        public String getDetails() { return details; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class UserSecurityProfile {
        private final int userId;
        private final List<SecurityEvent> history = new ArrayList<>();
        private double riskScore = 0.0;
        
        public UserSecurityProfile(int userId) {
            this.userId = userId;
        }
        
        public void addHistoryEvent(String eventType, String severity, LocalDateTime timestamp) {
            // Create simplified security event for history
        }
        
        public int getUserId() { return userId; }
        public List<SecurityEvent> getHistory() { return history; }
        public double getRiskScore() { return riskScore; }
    }
} 