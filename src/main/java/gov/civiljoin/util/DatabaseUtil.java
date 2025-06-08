package gov.civiljoin.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced Database Utility for CivilJoin 2.0
 * Provides connection pooling, schema management, and performance optimization
 */
public class DatabaseUtil {
    private static final Logger LOGGER = Logger.getLogger(DatabaseUtil.class.getName());
    
    // Database configuration
    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3306;
    private static final String DB_NAME = "civiljoin";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123123";
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + 
        "?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8";
    
    private static HikariDataSource dataSource;
    private static boolean isInitialized = false;
    
    static {
        try {
            initializeDatabase();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    /**
     * Initialize the database with connection pool and schema
     */
    private static void initializeDatabase() throws SQLException {
        if (isInitialized) {
            return;
        }
        
        LOGGER.info("Initializing CivilJoin database...");
        
        // Setup connection pool
        setupConnectionPool();
        
        // Execute schema if needed
        executeSchemaScript();
        
        // Verify database structure
        verifyDatabaseStructure();
        
        isInitialized = true;
        LOGGER.info("Database initialized successfully");
    }
    
    /**
     * Setup HikariCP connection pool with optimized settings
     */
    private static void setupConnectionPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        
        // Optimized pool sizing for desktop application
        int coreCount = Runtime.getRuntime().availableProcessors();
        config.setMaximumPoolSize(Math.min(coreCount * 2, 15)); // Reduced from 20
        config.setMinimumIdle(3); // Reduced from 5 for desktop app
        config.setIdleTimeout(180000); // 3 minutes (reduced from 5)
        config.setMaxLifetime(900000); // 15 minutes (reduced from 30)
        config.setConnectionTimeout(10000); // 10 seconds (reduced from 30)
        config.setValidationTimeout(3000); // 3 seconds (reduced from 5)
        config.setLeakDetectionThreshold(30000); // 30 seconds (reduced from 60)
        
        // Pool naming
        config.setPoolName("CivilJoin-Pool");
        
        // Advanced performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "500"); // Increased from 250
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "4096"); // Increased from 2048
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // Additional MySQL optimizations for performance
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("useLocalTransactionState", "true");
        config.addDataSourceProperty("useCompression", "true"); // Enable compression
        config.addDataSourceProperty("useCursorFetch", "true"); // Use cursor fetch for large results
        config.addDataSourceProperty("defaultFetchSize", "100"); // Optimize fetch size
        config.addDataSourceProperty("useUnbufferedInput", "false"); // Buffer input for performance
        config.addDataSourceProperty("useReadAheadInput", "true"); // Read ahead optimization
        config.addDataSourceProperty("tcpKeepAlive", "true"); // Keep connections alive
        config.addDataSourceProperty("tcpNoDelay", "true"); // Disable Nagle algorithm
        
        // Connection validation
        config.setConnectionTestQuery("SELECT 1");
        config.setConnectionInitSql("SET SESSION sql_mode = 'TRADITIONAL'");
        
        dataSource = new HikariDataSource(config);
        LOGGER.info("Optimized connection pool initialized with " + config.getMaximumPoolSize() + " max connections on " + coreCount + " cores");
    }
    
    /**
     * Execute the master schema script if database is empty
     */
    private static void executeSchemaScript() {
        try (Connection conn = getConnection()) {
            // Check if the database has been initialized
            if (isDatabaseEmpty(conn)) {
                LOGGER.info("Database is empty, executing master schema...");
                
                // Load and execute master schema
                InputStream is = DatabaseUtil.class.getClassLoader().getResourceAsStream("master_schema.sql");
                if (is == null) {
                    LOGGER.warning("master_schema.sql not found in resources");
                    return;
                }
                
                String schema = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A").next();
                String[] statements = schema.split(";");
                
                try (Statement stmt = conn.createStatement()) {
                    conn.setAutoCommit(false);
                    
                    for (String statement : statements) {
                        String trimmed = statement.trim();
                        if (!trimmed.isEmpty() && 
                            !trimmed.startsWith("--") && 
                            !trimmed.startsWith("SET @") &&
                            !trimmed.startsWith("DROP DATABASE") &&
                            !trimmed.startsWith("CREATE DATABASE") &&
                            !trimmed.startsWith("USE ")) {
                            
                            try {
                                stmt.execute(trimmed);
                            } catch (SQLException e) {
                                LOGGER.log(Level.WARNING, "Error executing statement: " + trimmed.substring(0, Math.min(100, trimmed.length())), e);
                            }
                        }
                    }
                    
                    conn.commit();
                    LOGGER.info("Master schema executed successfully");
                } catch (SQLException e) {
                    conn.rollback();
                    LOGGER.log(Level.SEVERE, "Failed to execute schema, rolling back", e);
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } else {
                LOGGER.info("Database already initialized, skipping schema execution");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Schema execution failed, database may need manual setup", e);
        }
    }
    
    /**
     * Check if the database is empty (no tables)
     */
    private static boolean isDatabaseEmpty(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
            return !rs.next();
        }
    }
    
    /**
     * Verify that critical database tables exist
     */
    private static void verifyDatabaseStructure() {
        String[] requiredTables = {
            "users", "key_ids", "posts", "comments", "categories", 
            "notifications", "activity_log", "user_sessions"
        };
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            for (String tableName : requiredTables) {
                try (ResultSet rs = stmt.executeQuery("SELECT 1 FROM " + tableName + " LIMIT 1")) {
                    LOGGER.fine("Table '" + tableName + "' verified");
                } catch (SQLException e) {
                    LOGGER.warning("Table '" + tableName + "' not found or accessible: " + e.getMessage());
                }
            }
            
            // Verify users table has the required columns
            try (ResultSet rs = stmt.executeQuery("SELECT password_hash, salt, role, created_at FROM users LIMIT 1")) {
                LOGGER.info("Users table structure verified");
            } catch (SQLException e) {
                LOGGER.severe("Users table missing required columns: " + e.getMessage());
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Database structure verification failed", e);
        }
    }
    
    /**
     * Get a connection from the pool
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is not available");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Close a connection (returns it to the pool)
     */
    public static void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing connection", e);
            }
        }
    }
    
    /**
     * Test database connectivity
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Connection test failed", e);
            return false;
        }
    }
    
    /**
     * Get connection pool statistics
     */
    public static String getPoolStats() {
        if (dataSource == null) {
            return "Connection pool not initialized";
        }
        
        return String.format(
            "Pool: %s | Active: %d | Idle: %d | Total: %d | Waiting: %d",
            dataSource.getPoolName(),
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
    
    /**
     * Execute a simple database health check
     */
    public static boolean isDatabaseHealthy() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            
            rs.next();
            int userCount = rs.getInt(1);
            LOGGER.info("Database health check passed. User count: " + userCount);
            return true;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database health check failed", e);
            return false;
        }
    }
    
    /**
     * Clean up expired sessions and notifications
     */
    public static void performMaintenanceTasks() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Clean up expired sessions
            int expiredSessions = stmt.executeUpdate(
                "DELETE FROM user_sessions WHERE expires_at < NOW()"
            );
            
            // Clean up old notifications (older than 30 days)
            int oldNotifications = stmt.executeUpdate(
                "DELETE FROM notifications WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY) AND is_read = TRUE"
            );
            
            // Clean up old activity logs (older than 90 days)
            int oldLogs = stmt.executeUpdate(
                "DELETE FROM activity_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY)"
            );
            
            LOGGER.info(String.format(
                "Maintenance completed: %d expired sessions, %d old notifications, %d old logs cleaned",
                expiredSessions, oldNotifications, oldLogs
            ));
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Database maintenance failed", e);
        }
    }
    
    /**
     * Shutdown the connection pool gracefully
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Connection pool shut down successfully");
        }
    }
    
    /**
     * Force a fresh initialization (for testing/development)
     */
    public static void reinitialize() throws SQLException {
        if (dataSource != null) {
            dataSource.close();
        }
        isInitialized = false;
        initializeDatabase();
    }
} 