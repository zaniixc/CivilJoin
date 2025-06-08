package gov.civiljoin.service;

import gov.civiljoin.model.SystemActivity;
import gov.civiljoin.model.SystemActivity.ActivityType;
import gov.civiljoin.model.SystemActivity.EntityType;
import gov.civiljoin.model.SystemActivity.Severity;
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
 * Service for managing system activities and timeline events
 */
public class SystemActivityService {
    private static final Logger LOGGER = Logger.getLogger(SystemActivityService.class.getName());
    
    /**
     * Initialize the system_activities table
     */
    public void initializeTable() {
        Connection conn = null;
        Statement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.createStatement();
            
            // Read and execute the enhanced schema
            String sql = """
                CREATE TABLE IF NOT EXISTS system_activities (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT,
                    activity_type ENUM('USER_REGISTRATION', 'USER_LOGIN', 'USER_LOGOUT', 'POST_CREATED', 'POST_DELETED', 
                                      'COMMENT_ADDED', 'COMMENT_DELETED', 'KEY_GENERATED', 'KEY_USED', 'ADMIN_ACTION', 
                                      'SYSTEM_ANNOUNCEMENT', 'SECURITY_EVENT', 'POLICY_CHANGE', 'EMERGENCY_ALERT') NOT NULL,
                    description TEXT NOT NULL,
                    entity_type ENUM('USER', 'POST', 'COMMENT', 'KEY_ID', 'SYSTEM') DEFAULT 'SYSTEM',
                    entity_id INT DEFAULT NULL,
                    severity ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') DEFAULT 'LOW',
                    ip_address VARCHAR(45),
                    user_agent TEXT,
                    metadata JSON,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_activity_type (activity_type),
                    INDEX idx_created_at (created_at),
                    INDEX idx_user_id (user_id),
                    INDEX idx_severity (severity),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
                )
                """;
            
            stmt.execute(sql);
            LOGGER.log(Level.INFO, "System activities table initialized successfully");
            
            // Insert sample data if table is empty
            insertSampleData();
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize system activities table", e);
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
     * Log a new system activity
     */
    public void logActivity(Integer userId, ActivityType activityType, String description, 
                           EntityType entityType, Integer entityId, Severity severity, 
                           String ipAddress, String userAgent, Map<String, Object> metadata) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = """
                INSERT INTO system_activities 
                (user_id, activity_type, description, entity_type, entity_id, severity, ip_address, user_agent, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setObject(1, userId);
            stmt.setString(2, activityType.name());
            stmt.setString(3, description);
            stmt.setString(4, entityType.name());
            stmt.setObject(5, entityId);
            stmt.setString(6, severity.name());
            stmt.setString(7, ipAddress);
            stmt.setString(8, userAgent);
            stmt.setString(9, metadata != null ? convertMapToJson(metadata) : null);
            
            stmt.executeUpdate();
            LOGGER.log(Level.INFO, "Activity logged: {0} - {1}", new Object[]{activityType, description});
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to log system activity", e);
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
     * Simplified activity logging method
     */
    public void logActivity(Integer userId, ActivityType activityType, String description) {
        logActivity(userId, activityType, description, EntityType.SYSTEM, null, Severity.LOW, null, null, null);
    }
    
    /**
     * Get recent activities for timeline
     */
    public List<SystemActivity> getRecentActivities(int limit, List<ActivityType> filterTypes, 
                                                   Severity minSeverity, String searchText) {
        List<SystemActivity> activities = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            
            // Build dynamic query based on filters
            StringBuilder sql = new StringBuilder("""
                SELECT sa.*, u.username 
                FROM system_activities sa 
                LEFT JOIN users u ON sa.user_id = u.id 
                WHERE 1=1
                """);
            
            List<Object> params = new ArrayList<>();
            
            // Filter by activity types
            if (filterTypes != null && !filterTypes.isEmpty()) {
                sql.append(" AND sa.activity_type IN (");
                for (int i = 0; i < filterTypes.size(); i++) {
                    sql.append(i > 0 ? ",?" : "?");
                    params.add(filterTypes.get(i).name());
                }
                sql.append(")");
            }
            
            // Filter by minimum severity
            if (minSeverity != null) {
                sql.append(" AND sa.severity IN (");
                for (Severity sev : Severity.values()) {
                    if (sev.ordinal() >= minSeverity.ordinal()) {
                        if (params.size() > (filterTypes != null ? filterTypes.size() : 0)) {
                            sql.append(",?");
                        } else {
                            sql.append("?");
                        }
                        params.add(sev.name());
                    }
                }
                sql.append(")");
            }
            
            // Search in description
            if (searchText != null && !searchText.trim().isEmpty()) {
                sql.append(" AND sa.description LIKE ?");
                params.add("%" + searchText.trim() + "%");
            }
            
            sql.append(" ORDER BY sa.created_at DESC LIMIT ?");
            params.add(limit);
            
            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                SystemActivity activity = createActivityFromResultSet(rs);
                activities.add(activity);
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve system activities", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return activities;
    }
    
    /**
     * Get recent activities without filters
     */
    public List<SystemActivity> getRecentActivities(int limit) {
        return getRecentActivities(limit, null, null, null);
    }
    
    /**
     * Get activity statistics for dashboard
     */
    public Map<String, Object> getActivityStatistics(int hoursBack) {
        Map<String, Object> stats = new HashMap<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            
            // Total activities in time period
            String sql = """
                SELECT 
                    COUNT(*) as total_activities,
                    COUNT(DISTINCT user_id) as active_users,
                    SUM(CASE WHEN severity = 'CRITICAL' THEN 1 ELSE 0 END) as critical_events,
                    SUM(CASE WHEN severity = 'HIGH' THEN 1 ELSE 0 END) as high_events,
                    SUM(CASE WHEN activity_type = 'USER_REGISTRATION' THEN 1 ELSE 0 END) as new_registrations,
                    SUM(CASE WHEN activity_type = 'POST_CREATED' THEN 1 ELSE 0 END) as new_posts
                FROM system_activities 
                WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, hoursBack);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                stats.put("totalActivities", rs.getInt("total_activities"));
                stats.put("activeUsers", rs.getInt("active_users"));
                stats.put("criticalEvents", rs.getInt("critical_events"));
                stats.put("highEvents", rs.getInt("high_events"));
                stats.put("newRegistrations", rs.getInt("new_registrations"));
                stats.put("newPosts", rs.getInt("new_posts"));
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve activity statistics", e);
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
     * Clean up old activities (retention policy)
     */
    public int cleanupOldActivities(int daysToKeep) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "DELETE FROM system_activities WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, daysToKeep);
            
            int deleted = stmt.executeUpdate();
            LOGGER.log(Level.INFO, "Cleaned up {0} old system activities", deleted);
            return deleted;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to cleanup old activities", e);
            return 0;
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
     * Create SystemActivity from ResultSet
     */
    private SystemActivity createActivityFromResultSet(ResultSet rs) throws SQLException {
        SystemActivity activity = new SystemActivity();
        activity.setId(rs.getInt("id"));
        activity.setUserId(rs.getObject("user_id", Integer.class));
        activity.setUsername(rs.getString("username"));
        activity.setActivityType(ActivityType.valueOf(rs.getString("activity_type")));
        activity.setDescription(rs.getString("description"));
        activity.setEntityType(EntityType.valueOf(rs.getString("entity_type")));
        activity.setEntityId(rs.getObject("entity_id", Integer.class));
        activity.setSeverity(Severity.valueOf(rs.getString("severity")));
        activity.setIpAddress(rs.getString("ip_address"));
        activity.setUserAgent(rs.getString("user_agent"));
        activity.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        
        // Parse metadata JSON if present
        String metadataJson = rs.getString("metadata");
        if (metadataJson != null && !metadataJson.trim().isEmpty()) {
            activity.setMetadata(parseJsonToMap(metadataJson));
        }
        
        return activity;
    }
    
    /**
     * Insert sample data for demonstration
     */
    private void insertSampleData() {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            
            // Check if data already exists
            String checkSql = "SELECT COUNT(*) FROM system_activities";
            stmt = conn.prepareStatement(checkSql);
            rs = stmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Data already exists
            }
            
            // Insert sample activities
            String insertSql = """
                INSERT INTO system_activities (user_id, activity_type, description, entity_type, severity, metadata)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            
            stmt = conn.prepareStatement(insertSql);
            
            // Sample activities
            Object[][] sampleData = {
                {1, "SYSTEM_ANNOUNCEMENT", "CivilJoin platform initialized successfully", "SYSTEM", "MEDIUM", "{\"component\":\"system\"}"},
                {1, "ADMIN_ACTION", "Initial admin account configured", "USER", "LOW", "{\"action\":\"user_creation\"}"},
                {1, "KEY_GENERATED", "Initial registration keys generated", "KEY_ID", "LOW", "{\"count\":3}"},
                {null, "SYSTEM_ANNOUNCEMENT", "Platform ready for citizen registration", "SYSTEM", "HIGH", "{\"status\":\"operational\"}"}
            };
            
            for (Object[] data : sampleData) {
                stmt.setObject(1, data[0]);
                stmt.setString(2, (String) data[1]);
                stmt.setString(3, (String) data[2]);
                stmt.setString(4, (String) data[3]);
                stmt.setString(5, (String) data[4]);
                stmt.setString(6, (String) data[5]);
                stmt.executeUpdate();
            }
            
            LOGGER.log(Level.INFO, "Sample system activities inserted successfully");
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert sample activities", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
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
    
    /**
     * Parse JSON string to Map (simplified)
     */
    private Map<String, Object> parseJsonToMap(String json) {
        Map<String, Object> map = new HashMap<>();
        // This is a simplified JSON parser - in production, use a proper JSON library
        if (json != null && json.trim().startsWith("{") && json.trim().endsWith("}")) {
            // Basic parsing - would need proper JSON library for production
            map.put("data", json);
        }
        return map;
    }
} 