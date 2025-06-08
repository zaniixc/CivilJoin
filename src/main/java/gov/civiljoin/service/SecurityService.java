package gov.civiljoin.service;

import gov.civiljoin.model.SecurityEvent;
import gov.civiljoin.model.SecurityEvent.EventType;
import gov.civiljoin.model.SecurityEvent.Severity;
import gov.civiljoin.model.SystemActivity;
import gov.civiljoin.model.SystemActivity.ActivityType;
import gov.civiljoin.util.DatabaseUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for managing security events and admin security features
 */
public class SecurityService {
    private static final Logger LOGGER = Logger.getLogger(SecurityService.class.getName());
    private final SystemActivityService activityService;
    
    // Track failed login attempts
    private final Map<String, Integer> failedLoginAttempts = new HashMap<>();
    private final Map<String, LocalDateTime> lastAttemptTime = new HashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    public SecurityService() {
        this.activityService = new SystemActivityService();
        // Remove table initialization from constructor
    }

    /**
     * Initialize security-related database tables
     * This should be called explicitly during application initialization
     */
    public void initializeTables() {
        createSecurityEventsTable();
        createUserSessionsTable();
        createSystemSettingsTable();
    }

    /**
     * Create security_events table
     */
    private void createSecurityEventsTable() {
        Connection conn = null;
        Statement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.createStatement();
            
            String sql = """
                CREATE TABLE IF NOT EXISTS security_events (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    event_type ENUM('FAILED_LOGIN', 'BRUTE_FORCE_ATTEMPT', 'SUSPICIOUS_ACTIVITY', 
                                   'UNAUTHORIZED_ACCESS', 'ACCOUNT_LOCKOUT', 'SECURITY_BREACH') NOT NULL,
                    user_id INT NULL,
                    ip_address VARCHAR(45),
                    user_agent TEXT,
                    description TEXT NOT NULL,
                    severity ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') DEFAULT 'MEDIUM',
                    resolved BOOLEAN DEFAULT FALSE,
                    resolved_by INT NULL,
                    resolved_at TIMESTAMP NULL,
                    metadata JSON,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_event_type (event_type),
                    INDEX idx_severity (severity),
                    INDEX idx_created_at (created_at),
                    INDEX idx_resolved (resolved),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
                    FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE SET NULL
                )
                """;
            
            stmt.execute(sql);
            LOGGER.log(Level.INFO, "Security events table initialized successfully");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize security events table", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
    }

    /**
     * Create user_sessions table
     */
    private void createUserSessionsTable() {
        Connection conn = null;
        Statement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.createStatement();
            
            String sql = """
                CREATE TABLE IF NOT EXISTS user_sessions (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    user_id INT NOT NULL,
                    session_token VARCHAR(255) UNIQUE NOT NULL,
                    ip_address VARCHAR(45),
                    user_agent TEXT,
                    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    logout_time TIMESTAMP NULL,
                    is_active BOOLEAN DEFAULT TRUE,
                    login_method ENUM('PASSWORD', 'TOKEN', 'ADMIN_OVERRIDE') DEFAULT 'PASSWORD',
                    INDEX idx_user_active (user_id, is_active),
                    INDEX idx_session_token (session_token),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """;
            
            stmt.execute(sql);
            LOGGER.log(Level.INFO, "User sessions table initialized successfully");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize user sessions table", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
    }

    /**
     * Create system_settings table
     */
    private void createSystemSettingsTable() {
        Connection conn = null;
        Statement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.createStatement();
            
            String sql = """
                CREATE TABLE IF NOT EXISTS system_settings (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    setting_key VARCHAR(100) UNIQUE NOT NULL,
                    setting_value TEXT NOT NULL,
                    setting_type ENUM('STRING', 'INTEGER', 'BOOLEAN', 'JSON') DEFAULT 'STRING',
                    description TEXT,
                    is_public BOOLEAN DEFAULT FALSE,
                    updated_by INT NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_setting_key (setting_key),
                    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE CASCADE
                )
                """;
            
            stmt.execute(sql);
            
            // Insert default settings
            insertDefaultSettings();
            
            LOGGER.log(Level.INFO, "System settings table initialized successfully");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize system settings table", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
    }

    /**
     * Insert default system settings
     */
    private void insertDefaultSettings() {
        Map<String, Object[]> defaultSettings = Map.of(
            "registration_enabled", new Object[]{"true", "BOOLEAN", "Whether user registration is enabled", true},
            "maintenance_mode", new Object[]{"false", "BOOLEAN", "Whether system is in maintenance mode", true},
            "max_login_attempts", new Object[]{"5", "INTEGER", "Maximum failed login attempts before lockout", false},
            "session_timeout_minutes", new Object[]{"30", "INTEGER", "Session timeout in minutes", false},
            "auto_refresh_interval", new Object[]{"30", "INTEGER", "Timeline auto-refresh interval in seconds", true},
            "emergency_mode", new Object[]{"false", "BOOLEAN", "Emergency lockdown mode", false}
        );

        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = """
                INSERT INTO system_settings (setting_key, setting_value, setting_type, description, is_public, updated_by)
                VALUES (?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)
                """;
            
            stmt = conn.prepareStatement(sql);
            
            for (Map.Entry<String, Object[]> entry : defaultSettings.entrySet()) {
                Object[] values = entry.getValue();
                stmt.setString(1, entry.getKey());
                stmt.setString(2, (String) values[0]);
                stmt.setString(3, (String) values[1]);
                stmt.setString(4, (String) values[2]);
                stmt.setBoolean(5, (Boolean) values[3]);
                stmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert default settings", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
    }

    /**
     * Log a security event
     */
    public void logSecurityEvent(EventType eventType, Integer userId, String ipAddress, 
                                String userAgent, String description, Severity severity, 
                                Map<String, Object> metadata) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = """
                INSERT INTO security_events 
                (event_type, user_id, ip_address, user_agent, description, severity, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, eventType.name());
            stmt.setObject(2, userId);
            stmt.setString(3, ipAddress);
            stmt.setString(4, userAgent);
            stmt.setString(5, description);
            stmt.setString(6, severity.name());
            stmt.setString(7, metadata != null ? convertMapToJson(metadata) : null);
            
            stmt.executeUpdate();
            
            // Also log as system activity
            activityService.logActivity(
                userId, 
                ActivityType.SECURITY_EVENT, 
                description, 
                SystemActivity.EntityType.SYSTEM, 
                null,
                SystemActivity.Severity.valueOf(severity.name()),
                ipAddress,
                userAgent,
                metadata
            );
            
            LOGGER.log(Level.WARNING, "Security event logged: {0} - {1}", new Object[]{eventType, description});
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to log security event", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
    }

    /**
     * Handle failed login attempt
     */
    public boolean handleFailedLogin(String identifier, String ipAddress, String userAgent) {
        // Track failed attempts
        int attempts = failedLoginAttempts.getOrDefault(identifier, 0) + 1;
        failedLoginAttempts.put(identifier, attempts);
        lastAttemptTime.put(identifier, LocalDateTime.now());
        
        // Check if account should be locked
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            logSecurityEvent(
                EventType.ACCOUNT_LOCKOUT,
                null,
                ipAddress,
                userAgent,
                String.format("Account locked due to %d failed login attempts for identifier: %s", attempts, identifier),
                Severity.HIGH,
                Map.of("identifier", identifier, "attempts", attempts)
            );
            return true; // Account is locked
        } else if (attempts > 2) {
            // Log as suspicious activity after multiple attempts
            logSecurityEvent(
                EventType.SUSPICIOUS_ACTIVITY,
                null,
                ipAddress,
                userAgent,
                String.format("Multiple failed login attempts (%d) for identifier: %s", attempts, identifier),
                Severity.MEDIUM,
                Map.of("identifier", identifier, "attempts", attempts)
            );
        } else {
            // Log regular failed login
            logSecurityEvent(
                EventType.FAILED_LOGIN,
                null,
                ipAddress,
                userAgent,
                String.format("Failed login attempt for identifier: %s", identifier),
                Severity.LOW,
                Map.of("identifier", identifier, "attempts", attempts)
            );
        }
        
        return false; // Account is not locked
    }

    /**
     * Check if account is locked
     */
    public boolean isAccountLocked(String identifier) {
        LocalDateTime lastAttempt = lastAttemptTime.get(identifier);
        if (lastAttempt == null) return false;
        
        // Check if lockout period has expired
        if (lastAttempt.plusMinutes(LOCKOUT_DURATION_MINUTES).isBefore(LocalDateTime.now())) {
            // Reset failed attempts
            failedLoginAttempts.remove(identifier);
            lastAttemptTime.remove(identifier);
            return false;
        }
        
        return failedLoginAttempts.getOrDefault(identifier, 0) >= MAX_FAILED_ATTEMPTS;
    }

    /**
     * Handle successful login (reset failed attempts)
     */
    public void handleSuccessfulLogin(String identifier, int userId, String ipAddress, String userAgent) {
        // Reset failed attempts
        failedLoginAttempts.remove(identifier);
        lastAttemptTime.remove(identifier);
        
        // Log successful login
        activityService.logActivity(
            userId, 
            ActivityType.USER_LOGIN, 
            "User logged in successfully", 
            SystemActivity.EntityType.USER, 
            userId,
            SystemActivity.Severity.LOW,
            ipAddress,
            userAgent,
            Map.of("identifier", identifier)
        );
        
        // Create user session
        createUserSession(userId, ipAddress, userAgent);
    }

    /**
     * Create user session
     */
    private void createUserSession(int userId, String ipAddress, String userAgent) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            
            // Generate session token
            String sessionToken = generateSessionToken();
            
            String sql = """
                INSERT INTO user_sessions (user_id, session_token, ip_address, user_agent)
                VALUES (?, ?, ?, ?)
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setString(2, sessionToken);
            stmt.setString(3, ipAddress);
            stmt.setString(4, userAgent);
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to create user session", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
    }

    /**
     * Get recent security events
     */
    public List<SecurityEvent> getRecentSecurityEvents(int limit, boolean unresolvedOnly) {
        List<SecurityEvent> events = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            
            StringBuilder sql = new StringBuilder("""
                SELECT se.*, u.username, ru.username as resolved_username
                FROM security_events se
                LEFT JOIN users u ON se.user_id = u.id
                LEFT JOIN users ru ON se.resolved_by = ru.id
                """);
            
            if (unresolvedOnly) {
                sql.append(" WHERE se.resolved = FALSE");
            }
            
            sql.append(" ORDER BY se.created_at DESC LIMIT ?");
            
            stmt = conn.prepareStatement(sql.toString());
            stmt.setInt(1, limit);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                SecurityEvent event = createSecurityEventFromResultSet(rs);
                events.add(event);
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve security events", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return events;
    }

    /**
     * Get security statistics
     */
    public Map<String, Object> getSecurityStatistics(int hoursBack) {
        Map<String, Object> stats = new HashMap<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            
            String sql = """
                SELECT 
                    COUNT(*) as total_events,
                    SUM(CASE WHEN event_type = 'FAILED_LOGIN' THEN 1 ELSE 0 END) as failed_logins,
                    SUM(CASE WHEN event_type = 'BRUTE_FORCE_ATTEMPT' THEN 1 ELSE 0 END) as brute_force_attempts,
                    SUM(CASE WHEN event_type = 'ACCOUNT_LOCKOUT' THEN 1 ELSE 0 END) as account_lockouts,
                    SUM(CASE WHEN severity = 'CRITICAL' THEN 1 ELSE 0 END) as critical_events,
                    SUM(CASE WHEN severity = 'HIGH' THEN 1 ELSE 0 END) as high_events,
                    SUM(CASE WHEN resolved = FALSE THEN 1 ELSE 0 END) as unresolved_events,
                    COUNT(DISTINCT ip_address) as unique_ips
                FROM security_events 
                WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, hoursBack);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                stats.put("totalEvents", rs.getInt("total_events"));
                stats.put("failedLogins", rs.getInt("failed_logins"));
                stats.put("bruteForceAttempts", rs.getInt("brute_force_attempts"));
                stats.put("accountLockouts", rs.getInt("account_lockouts"));
                stats.put("criticalEvents", rs.getInt("critical_events"));
                stats.put("highEvents", rs.getInt("high_events"));
                stats.put("unresolvedEvents", rs.getInt("unresolved_events"));
                stats.put("uniqueIps", rs.getInt("unique_ips"));
            }
            
            // Calculate threat level
            int threatLevel = calculateThreatLevel(stats);
            stats.put("threatLevel", threatLevel);
            stats.put("threatStatus", getThreatStatus(threatLevel));
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve security statistics", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return stats;
    }

    /**
     * Calculate threat level based on security statistics
     */
    private int calculateThreatLevel(Map<String, Object> stats) {
        int level = 0;
        
        level += (Integer) stats.getOrDefault("criticalEvents", 0) * 10;
        level += (Integer) stats.getOrDefault("highEvents", 0) * 5;
        level += (Integer) stats.getOrDefault("bruteForceAttempts", 0) * 3;
        level += (Integer) stats.getOrDefault("accountLockouts", 0) * 2;
        level += Math.min((Integer) stats.getOrDefault("failedLogins", 0), 20);
        
        return Math.min(level, 100); // Cap at 100
    }

    /**
     * Get threat status description
     */
    private String getThreatStatus(int threatLevel) {
        if (threatLevel >= 75) return "🔴 Critical";
        if (threatLevel >= 50) return "🟠 High";
        if (threatLevel >= 25) return "🟡 Medium";
        if (threatLevel > 0) return "🟢 Low";
        return "✅ Secure";
    }

    /**
     * Resolve security event
     */
    public boolean resolveSecurityEvent(int eventId, int resolvedByUserId, String resolution) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = """
                UPDATE security_events 
                SET resolved = TRUE, resolved_by = ?, resolved_at = NOW()
                WHERE id = ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, resolvedByUserId);
            stmt.setInt(2, eventId);
            
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                // Log the resolution action
                activityService.logActivity(
                    resolvedByUserId,
                    ActivityType.ADMIN_ACTION,
                    String.format("Resolved security event #%d: %s", eventId, resolution)
                );
                return true;
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to resolve security event", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return false;
    }

    /**
     * Get system setting value
     */
    public String getSystemSetting(String key, String defaultValue) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "SELECT setting_value FROM system_settings WHERE setting_key = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, key);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("setting_value");
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to get system setting: " + key, e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return defaultValue;
    }

    /**
     * Update system setting
     */
    public boolean updateSystemSetting(String key, String value, int updatedBy) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = """
                INSERT INTO system_settings (setting_key, setting_value, updated_by)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_by = VALUES(updated_by)
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.setInt(3, updatedBy);
            
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                // Log the setting change
                activityService.logActivity(
                    updatedBy,
                    ActivityType.ADMIN_ACTION,
                    String.format("Updated system setting: %s = %s", key, value)
                );
                return true;
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update system setting", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return false;
    }

    /**
     * Create SecurityEvent from ResultSet
     */
    private SecurityEvent createSecurityEventFromResultSet(ResultSet rs) throws SQLException {
        SecurityEvent event = new SecurityEvent();
        event.setId(rs.getInt("id"));
        event.setEventType(EventType.valueOf(rs.getString("event_type")));
        event.setUserId(rs.getObject("user_id", Integer.class));
        event.setUsername(rs.getString("username"));
        event.setIpAddress(rs.getString("ip_address"));
        event.setUserAgent(rs.getString("user_agent"));
        event.setDescription(rs.getString("description"));
        event.setSeverity(Severity.valueOf(rs.getString("severity")));
        event.setResolved(rs.getBoolean("resolved"));
        event.setResolvedBy(rs.getObject("resolved_by", Integer.class));
        event.setResolvedByUsername(rs.getString("resolved_username"));
        
        Timestamp resolvedAtTs = rs.getTimestamp("resolved_at");
        if (resolvedAtTs != null) {
            event.setResolvedAt(resolvedAtTs.toLocalDateTime());
        }
        
        event.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        
        return event;
    }

    /**
     * Generate session token
     */
    private String generateSessionToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Convert Map to JSON string (simplified)
     */
    private String convertMapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("}");
        return json.toString();
    }
} 