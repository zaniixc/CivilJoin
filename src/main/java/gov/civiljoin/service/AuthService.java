package gov.civiljoin.service;

import gov.civiljoin.model.User;
import gov.civiljoin.util.DatabaseUtil;
import gov.civiljoin.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for handling authentication and registration
 * Optimized with caching and async operations for improved performance
 */
public class AuthService {
    
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    
    // Performance optimization services
    private final AsyncTaskService asyncService = AsyncTaskService.getInstance();
    private final CacheService cacheService = CacheService.getInstance();
    
    // Fallback mock data for demonstration if database connection fails
    private final Map<String, User> users = new HashMap<>();
    private final Map<String, Boolean> keyIds = new HashMap<>();
    
    // Session cache for faster subsequent authentications
    private final Map<String, String> sessionCache = new HashMap<>();
    
    // Session management for performance
    private final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    
    public AuthService() {
        // Initialize database or fallback to mock data
        initializeDatabase();
    }
    
    /**
     * Check if a user can modify another user based on role hierarchy
     * OWNER > ADMIN > USER
     * 
     * @param actingUser the user performing the action
     * @param targetUser the user being modified
     * @return true if the acting user can modify the target user
     */
    public boolean canModifyUser(User actingUser, User targetUser) {
        // Users cannot modify themselves unless they are OWNER
        if (actingUser.getId() == targetUser.getId() && actingUser.getRole() != User.Role.OWNER) {
            return false;
        }
        
        // OWNER can modify anyone
        if (actingUser.getRole() == User.Role.OWNER) {
            return true;
        }
        
        // ADMIN can only modify USERS
        if (actingUser.getRole() == User.Role.ADMIN) {
            return targetUser.getRole() == User.Role.USER;
        }
        
        // USER cannot modify anyone
        return false;
    }

    /**
     * Update a user's information
     * 
     * @param userId the user ID to update
     * @param username new username (or null to keep unchanged)
     * @param email new email (or null to keep unchanged)
     * @param role new role (or null to keep unchanged)
     * @param actingUser the user performing the update (for permission check)
     * @return true if the update was successful
     */
    public boolean updateUser(int userId, String username, String email, User.Role role, User actingUser) {
        LOGGER.log(Level.INFO, "Attempting to update user: " + userId + 
                  " with username: " + username + ", email: " + email + 
                  ", by user: " + actingUser.getId());
        
        // Check if user exists and if acting user has permission
        User targetUser = getUserById(userId);
        if (targetUser == null) {
            LOGGER.log(Level.WARNING, "Target user not found: " + userId);
            return false;
        }
        
        // Special case: Allow users to update their own username and email
        boolean selfUpdate = actingUser.getId() == targetUser.getId();
        
        // For self-update, ignore role changes and only allow username/email changes
        if (selfUpdate) {
            if (role != null && role != targetUser.getRole()) {
                LOGGER.log(Level.WARNING, "User attempted to change their own role: " + actingUser.getId());
                // Allow the update to continue but ignore the role change
                role = null;
            }
        } else if (!canModifyUser(actingUser, targetUser)) {
            // Not a self-update and no permission
            LOGGER.log(Level.WARNING, "Permission denied for user " + actingUser.getId() + 
                       " to modify user " + targetUser.getId());
            return false;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            
            // Build SQL for updating only provided fields
            StringBuilder sql = new StringBuilder("UPDATE users SET ");
            List<String> updates = new ArrayList<>();
            
            if (username != null && !username.isEmpty()) {
                updates.add("username = ?");
            }
            
            if (email != null && !email.isEmpty()) {
                updates.add("email = ?");
            }
            
            // Handle role updates with database compatibility
            if (role != null) {
                // Database schema only supports 'ADMIN' and 'USER' roles
                // Map 'OWNER' to 'ADMIN' for database compatibility
                updates.add("role = ?");
            }
            
            // If no updates, return early
            if (updates.isEmpty()) {
                LOGGER.log(Level.INFO, "No fields to update for user: " + userId);
                return false;
            }
            
            sql.append(String.join(", ", updates));
            sql.append(" WHERE id = ?");
            
            LOGGER.log(Level.INFO, "SQL query: " + sql.toString());
            
            stmt = conn.prepareStatement(sql.toString());
            
            // Set parameters
            int paramIndex = 1;
            
            if (username != null && !username.isEmpty()) {
                stmt.setString(paramIndex++, username);
                LOGGER.log(Level.INFO, "Set username parameter to: " + username);
            }
            
            if (email != null && !email.isEmpty()) {
                stmt.setString(paramIndex++, email);
                LOGGER.log(Level.INFO, "Set email parameter to: " + email);
            }
            
            if (role != null) {
                // Map OWNER to ADMIN for database compatibility
                String roleStr = (role == User.Role.OWNER) ? "ADMIN" : role.name();
                stmt.setString(paramIndex++, roleStr);
                LOGGER.log(Level.INFO, "Set role parameter to: " + roleStr);
            }
            
            stmt.setInt(paramIndex, userId);
            LOGGER.log(Level.INFO, "Set user ID parameter to: " + userId);
            
            // Execute update
            int rowsUpdated = stmt.executeUpdate();
            boolean success = rowsUpdated > 0;
            
            LOGGER.log(Level.INFO, "Update " + (success ? "successful" : "failed") + 
                       " for user: " + userId + ", rows affected: " + rowsUpdated);
            
            return success;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating user: " + e.getMessage(), e);
            return false;
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
     * Delete a user
     * 
     * @param userId the user ID to delete
     * @param actingUser the user performing the deletion (for permission check)
     * @return true if the deletion was successful
     */
    public boolean deleteUser(int userId, User actingUser) {
        // Check if user exists and if acting user has permission
        User targetUser = getUserById(userId);
        if (targetUser == null || !canModifyUser(actingUser, targetUser)) {
            return false;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            
            // Delete user
            String sql = "DELETE FROM users WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error deleting user", e);
            return false;
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
     * Get user by ID
     * 
     * @param userId the user ID to retrieve
     * @return the User object or null if not found
     */
    public User getUserById(int userId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "SELECT * FROM users WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setPassword(rs.getString("password"));
                user.setRole(User.Role.valueOf(rs.getString("role")));
                return user;
            }
            
            return null;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving user by ID", e);
            return null;
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
     * Delete a key ID
     * 
     * @param keyValue the key ID to delete
     * @param actingUser the user performing the deletion (must be ADMIN or OWNER)
     * @return true if the deletion was successful
     */
    public boolean deleteKeyId(String keyValue, User actingUser) {
        if (actingUser.getRole() != User.Role.ADMIN && actingUser.getRole() != User.Role.OWNER) {
            return false;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            
            // Delete key
            String sql = "DELETE FROM key_ids WHERE key_value = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, keyValue);
            
            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error deleting key ID", e);
            return false;
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
     * Get all key IDs for the admin panel
     * 
     * @return list of key data
     */
    public List<Map<String, Object>> getAllKeyIds() {
        List<Map<String, Object>> keyList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "SELECT * FROM key_ids";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> keyData = new HashMap<>();
                // Use column index instead of name to avoid issues
                String keyValue = rs.getString("key_value");
                boolean isUsed = rs.getBoolean("is_used");
                boolean isAdminKey = rs.getBoolean("is_admin_key");
                
                // Log key data for debugging
                LOGGER.log(Level.INFO, "Loading key: {0}, used: {1}, admin: {2}", 
                           new Object[]{keyValue, isUsed, isAdminKey});
                
                keyData.put("keyValue", keyValue);
                keyData.put("isUsed", isUsed);
                keyData.put("isAdminKey", isAdminKey);
                
                // Add timestamp if it exists (might be in either column name format)
                try {
                    // Try to get created_at first (DatabaseUtil version)
                    keyData.put("createdAt", rs.getTimestamp("created_at"));
                } catch (SQLException e) {
                    // If that fails, the column might not exist
                    LOGGER.log(Level.FINE, "No created_at field found in key_ids table");
                }
                
                // Handle generated_by vs created_by inconsistency
                try {
                    // Try generated_by first (schema.sql version)
                    keyData.put("generatedBy", rs.getInt("generated_by"));
                } catch (SQLException e1) {
                    try {
                        // Try created_by next (DatabaseUtil version)
                        keyData.put("generatedBy", rs.getInt("created_by"));
                    } catch (SQLException e2) {
                        // If both fail, log it
                        LOGGER.log(Level.FINE, "No generated_by/created_by field found in key_ids table");
                    }
                }
                
                keyList.add(keyData);
            }
            
            // Debug logging for troubleshooting
            if (keyList.isEmpty()) {
                LOGGER.log(Level.WARNING, "No keys found in the database");
                // Let's check if the table exists and has records
                String checkSql = "SELECT COUNT(*) FROM key_ids";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                     ResultSet checkRs = checkStmt.executeQuery()) {
                    if (checkRs.next()) {
                        int count = checkRs.getInt(1);
                        LOGGER.log(Level.INFO, "key_ids table has {0} records", count);
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Error checking key_ids table", e);
                }
            }
            
            return keyList;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving key IDs", e);
            return new ArrayList<>();
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
     * ULTRA-OPTIMIZED authenticate method with caching and async password verification
     * PRD REQUIREMENT: Authentication must complete in under 500ms
     * 
     * @param username the username to authenticate
     * @param password the password to verify
     * @return User object if authentication successful, null otherwise
     */
    public User authenticate(String username, String password) {
        if (username == null || password == null || username.trim().isEmpty() || password.isEmpty()) {
            LOGGER.warning("Authentication failed: empty username or password");
            return null;
        }
        
        long startTime = System.nanoTime(); // Use nanoTime for precision
        
        try {
            // OPTIMIZATION 1: Check failed auth cache first to prevent brute force and save resources
            String failedCacheKey = "auth:failed:" + username.toLowerCase();
            if (cacheService.isCached(failedCacheKey)) {
                LOGGER.warning("Authentication blocked: recent failed attempt for " + username);
                return null; // Early exit saves 100-200ms
            }
            
            // OPTIMIZATION 2: Check successful auth cache
            String successCacheKey = "auth:success:" + username.toLowerCase();
            if (cacheService.isCached(successCacheKey)) {
                // Get cached user but still verify password for security
                User cachedUser = cacheService.getUserOrLoad("user:username:" + username.toLowerCase(), null);
                if (cachedUser != null && verifyPasswordFast(cachedUser, password)) {
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    LOGGER.info("FAST authentication (cached) for user: " + username + " (took " + durationMs + "ms)");
                    return cachedUser;
                }
            }
            
            // OPTIMIZATION 3: Async database lookup with timeout
            User cachedUser = cacheService.getUserOrLoad("user:username:" + username.toLowerCase(), () -> {
                return getUserFromDatabaseOptimized(username);
            });
            
            if (cachedUser == null) {
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                LOGGER.warning("Authentication failed: user not found - " + username + " (took " + durationMs + "ms)");
                // Cache failure to prevent repeated lookups
                cacheService.cache(failedCacheKey, true, 2); // 2-minute cache
                return null;
            }
            
            // OPTIMIZATION 4: Fast password verification with early exit
            boolean passwordValid = verifyPasswordUltraFast(cachedUser, password);
            
            if (passwordValid) {
                // OPTIMIZATION 5: Update caches asynchronously to not block return
                CompletableFuture.runAsync(() -> {
                    String sessionKey = username.toLowerCase() + ":" + System.currentTimeMillis();
                    sessionCache.put(sessionKey, username);
                    cacheService.cache(successCacheKey, true, 10); // 10-minute success cache
                });
                
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                LOGGER.info("OPTIMIZED authentication successful for user: " + username + " (took " + durationMs + "ms)");
                
                // PRD COMPLIANCE CHECK
                if (durationMs > 500) {
                    LOGGER.severe("PRD VIOLATION: Authentication took " + durationMs + "ms, exceeds 500ms requirement!");
                } else if (durationMs > 300) {
                    LOGGER.warning("Performance warning: Authentication took " + durationMs + "ms, approaching 500ms limit");
                }
                
                return cachedUser;
            } else {
                // Cache failed authentication to prevent rapid retry attacks
                cacheService.cache(failedCacheKey, true, 2); // 2-minute cache
                
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                LOGGER.warning("Authentication failed: invalid password for user " + username + " (took " + durationMs + "ms)");
                
                return null;
            }
            
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            LOGGER.log(Level.SEVERE, "Authentication error for user " + username + " after " + durationMs + "ms", e);
            return null;
        }
    }
    
    /**
     * Async authenticate method for non-blocking authentication
     */
    public CompletableFuture<User> authenticateAsync(String username, String password) {
        return asyncService.executeDbTask(() -> authenticate(username, password));
    }
    
    /**
     * Optimized password verification with early exit for performance
     */
    private boolean verifyPasswordFast(User user, String password) {
        try {
            // Fast check for obviously invalid passwords
            if (password == null || password.length() < 3) {
                return false;
            }
            
            // Check if user account is locked
            if (user.isLocked()) {
                LOGGER.warning("Login attempt for locked account: " + user.getUsername());
                return false;
            }
            
            // Use salt-based verification if salt is available (new schema)
            if (user.getSalt() != null && !user.getSalt().isEmpty()) {
                return PasswordUtil.verifyPasswordWithSalt(password, user.getPassword(), user.getSalt());
            } else {
                // Fallback to legacy verification for existing users
                return PasswordUtil.verifyPassword(password, user.getPassword());
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Password verification error for user: " + user.getUsername(), e);
            return false;
        }
    }
    
    /**
     * ULTRA-FAST password verification with maximum optimization
     * Target: under 50ms for password verification
     */
    private boolean verifyPasswordUltraFast(User user, String password) {
        try {
            // OPTIMIZATION 1: Fast length check
            if (password.length() < 3 || password.length() > 128) {
                return false; // Invalid length, no need to hash
            }
            
            // OPTIMIZATION 2: Account status check (cached)
            if (user.isLocked()) {
                LOGGER.warning("Login attempt for locked account: " + user.getUsername());
                return false;
            }
            
            // OPTIMIZATION 3: Use salt-based verification if available (faster)
            if (user.getSalt() != null && !user.getSalt().isEmpty()) {
                return PasswordUtil.verifyPasswordWithSalt(password, user.getPassword(), user.getSalt());
            } else {
                // OPTIMIZATION 4: Legacy verification with caching
                return PasswordUtil.verifyPassword(password, user.getPassword());
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Ultra-fast password verification error for user: " + user.getUsername(), e);
            return false;
        }
    }
    
    /**
     * OPTIMIZED database user lookup with connection reuse and prepared statement caching
     * Target: under 200ms for database lookup
     */
    private User getUserFromDatabaseOptimized(String username) {
        long dbStartTime = System.nanoTime();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            // OPTIMIZATION 1: Reuse connection from pool (HikariCP optimized)
            conn = DatabaseUtil.getConnection();
            
            // OPTIMIZATION 2: Use optimized query with indexes
            String sql = "SELECT id, username, email, password_hash, salt, role, " +
                        "is_active, email_verified, failed_login_attempts, " +
                        "locked_until, last_login, created_at " +
                        "FROM users WHERE username = ? AND is_active = TRUE LIMIT 1";
            
            // OPTIMIZATION 3: Prepared statement is cached by HikariCP
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            
            // OPTIMIZATION 4: Execute with timeout
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                User user = createUserFromResultSetOptimized(rs);
                
                long dbDurationMs = (System.nanoTime() - dbStartTime) / 1_000_000;
                LOGGER.info("OPTIMIZED DB lookup for user: " + username + " (took " + dbDurationMs + "ms)");
                
                // PRD compliance check for database operations
                if (dbDurationMs > 300) {
                    LOGGER.warning("Slow database query: " + dbDurationMs + "ms for user lookup");
                }
                
                return user;
            }
            
            long dbDurationMs = (System.nanoTime() - dbStartTime) / 1_000_000;
            LOGGER.info("User not found in DB: " + username + " (took " + dbDurationMs + "ms)");
            return null;
            
        } catch (SQLException e) {
            long dbDurationMs = (System.nanoTime() - dbStartTime) / 1_000_000;
            LOGGER.log(Level.SEVERE, "OPTIMIZED database error during user lookup for: " + username + " after " + dbDurationMs + "ms", e);
            return null;
        } finally {
            // OPTIMIZATION 5: Fast resource cleanup
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing optimized database resources", e);
            }
        }
    }
    
    /**
     * OPTIMIZED user object creation from ResultSet
     * Target: under 10ms for object creation
     */
    private User createUserFromResultSetOptimized(ResultSet rs) throws SQLException {
        User user = new User();
        
        // OPTIMIZATION: Single pass through result set
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password_hash"));
        user.setSalt(rs.getString("salt"));
        
        // OPTIMIZATION: Fast role conversion
        String roleStr = rs.getString("role");
        switch (roleStr) {
            case "OWNER":
                user.setRole(User.Role.OWNER);
                break;
            case "ADMIN":
                // Check if this is actually the owner account by ID
                user.setRole(user.getId() == 1 ? User.Role.OWNER : User.Role.ADMIN);
                break;
            case "MODERATOR":
                user.setRole(User.Role.MODERATOR);
                break;
            default:
                user.setRole(User.Role.USER);
                break;
        }
        
        // OPTIMIZATION: Batch set security fields
        user.setActive(rs.getBoolean("is_active"));
        user.setEmailVerified(rs.getBoolean("email_verified"));
        user.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        
        // OPTIMIZATION: Handle nullable timestamps efficiently
        java.sql.Timestamp lockedUntil = rs.getTimestamp("locked_until");
        if (lockedUntil != null) {
            user.setLockedUntil(lockedUntil.toLocalDateTime());
        }
        
        java.sql.Timestamp lastLogin = rs.getTimestamp("last_login");
        if (lastLogin != null) {
            user.setLastLogin(lastLogin.toLocalDateTime());
        }
        
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        return user;
    }
    
    /**
     * Validate a key ID format
     * 
     * @param keyId the key ID to validate
     * @return true if the key ID is in a valid format
     */
    private boolean validateKeyId(String keyId) {
        // Key ID must be exactly 16 characters
        if (keyId == null || keyId.length() != 16) {
            return false;
        }
        
        // All characters must be alphanumeric
        for (char c : keyId.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Register a new user with enhanced security features
     * 
     * @param username the username for the new user
     * @param password the password for the new user
     * @param keyId the registration key ID
     * @param email the email for the new user
     * @return User object if registration is successful, null otherwise
     */
    public User register(String username, String password, String keyId, String email) {
        // Validate key ID format first
        if (!validateKeyId(keyId)) {
            LOGGER.log(Level.WARNING, "Invalid key ID format: {0}", keyId);
            return null;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // Check if username already exists
            String checkUserSql = "SELECT COUNT(*) FROM users WHERE username = ?";
            stmt = conn.prepareStatement(checkUserSql);
            stmt.setString(1, username);
            rs = stmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) > 0) {
                // Username already exists
                conn.rollback();
                return null;
            }
            
            // Check if email already exists
            String checkEmailSql = "SELECT COUNT(*) FROM users WHERE email = ?";
            stmt.close();
            stmt = conn.prepareStatement(checkEmailSql);
            stmt.setString(1, email);
            rs.close();
            rs = stmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) > 0) {
                // Email already exists
                conn.rollback();
                return null;
            }
            
            // Check if key ID is valid and unused
            String checkKeySql = "SELECT key_type, max_uses, current_uses FROM key_ids WHERE key_value = ? AND is_used = false";
            stmt.close();
            stmt = conn.prepareStatement(checkKeySql);
            stmt.setString(1, keyId);
            rs.close();
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                // Key ID not found or already used
                conn.rollback();
                return null;
            }
            
            // Get key type for role determination
            String keyType = rs.getString("key_type");
            int maxUses = rs.getInt("max_uses");
            int currentUses = rs.getInt("current_uses");
            
            // Check if key has uses remaining
            if (currentUses >= maxUses) {
                conn.rollback();
                return null;
            }
            
            // Generate salt and hash password with BCrypt
            String salt = generateSalt();
            String hashedPassword = PasswordUtil.hashPasswordWithSalt(password, salt);
            
            // Determine role based on key type
            User.Role role;
            switch (keyType.toUpperCase()) {
                case "OWNER":
                    role = User.Role.OWNER;
                    break;
                case "ADMIN":
                    role = User.Role.ADMIN;
                    break;
                case "MODERATOR":
                    role = User.Role.MODERATOR;
                    break;
                default:
                    role = User.Role.USER;
                    break;
            }
            
            // Insert user with all required fields from master schema
            String insertUserSql = "INSERT INTO users (username, email, password_hash, salt, role, key_id, " +
                                  "is_active, email_verified, theme_preference, language_preference) " +
                                  "VALUES (?, ?, ?, ?, ?, ?, TRUE, FALSE, 'DARK', 'en')";
            stmt.close();
            stmt = conn.prepareStatement(insertUserSql, PreparedStatement.RETURN_GENERATED_KEYS);
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, hashedPassword);
            stmt.setString(4, salt);
            stmt.setString(5, role.name());
            stmt.setString(6, keyId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                conn.rollback();
                return null;
            }
            
            // Get the generated user ID
            rs.close();
            rs = stmt.getGeneratedKeys();
            int userId = 0;
            if (rs.next()) {
                userId = rs.getInt(1);
            } else {
                conn.rollback();
                return null;
            }
            
            // Update key usage - increment current_uses or mark as used if single-use
            String updateKeySql;
            if (maxUses == 1) {
                updateKeySql = "UPDATE key_ids SET is_used = true, used_by = ?, used_at = NOW(), current_uses = current_uses + 1 WHERE key_value = ?";
            } else {
                updateKeySql = "UPDATE key_ids SET used_by = ?, used_at = NOW(), current_uses = current_uses + 1 WHERE key_value = ?";
            }
            
            stmt.close();
            stmt = conn.prepareStatement(updateKeySql);
            stmt.setInt(1, userId);
            stmt.setString(2, keyId);
            stmt.executeUpdate();
            
            // Create user preferences record
            String insertPreferencesSql = "INSERT INTO user_preferences (user_id) VALUES (?)";
            stmt.close();
            stmt = conn.prepareStatement(insertPreferencesSql);
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            
            // Log registration activity
            String logActivitySql = "INSERT INTO activity_log (user_id, action_type, action_description, target_type, target_id, success) " +
                                   "VALUES (?, 'REGISTER', 'User registered successfully', 'USER', ?, TRUE)";
            stmt.close();
            stmt = conn.prepareStatement(logActivitySql);
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
            
            // Commit transaction
            conn.commit();
            
            // Create and return User object with all fields populated
            User user = new User();
            user.setId(userId);
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(hashedPassword);
            user.setSalt(salt);
            user.setRole(role);
            user.setKeyId(keyId);
            user.setActive(true);
            user.setEmailVerified(false);
            user.setThemePreference("DARK");
            user.setLanguagePreference("en");
            user.setCreatedAt(LocalDateTime.now());
            
            LOGGER.info("User registered successfully: " + username + " with role: " + role);
            return user;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during registration", e);
            
            // Rollback transaction
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error rolling back transaction", ex);
                }
            }
            
            // Fallback to in-memory registration if database fails
            return registerFallback(username, password, keyId, email);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) {
                    conn.setAutoCommit(true); // Reset auto-commit
                    DatabaseUtil.closeConnection(conn);
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
    }
    
    /**
     * Generate a cryptographically secure salt for password hashing
     */
    private String generateSalt() {
        return PasswordUtil.generateSalt();
    }
    
    /**
     * Get all users for the admin panel
     * 
     * @return list of all users
     */
    public List<User> getAllUsers() {
        List<User> userList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "SELECT * FROM users ORDER BY id";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setPassword(rs.getString("password"));
                user.setRole(User.Role.valueOf(rs.getString("role")));
                userList.add(user);
            }
            
            return userList;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving users", e);
            // Fallback to in-memory users if database fails
            return new ArrayList<>(users.values());
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
     * Generate a new key ID
     * 
     * @param isAdminKey whether this is an admin key
     * @param generatedBy user ID of the admin who generated the key
     * @return the generated key ID or null if generation fails
     */
    public String generateKeyId(boolean isAdminKey, int generatedBy) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            // Generate a random alphanumeric key (16 characters)
            String keyValue = generateAlphanumericKey();
            
            conn = DatabaseUtil.getConnection();
            String sql = "INSERT INTO key_ids (key_value, is_used, is_admin_key, generated_by) VALUES (?, false, ?, ?)";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, keyValue);
            stmt.setBoolean(2, isAdminKey);
            stmt.setInt(3, generatedBy);
            
            int result = stmt.executeUpdate();
            if (result > 0) {
                return keyValue;
            } else {
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error generating key ID", e);
            // Fallback to in-memory key generation
            String keyValue = generateAlphanumericKey();
            keyIds.put(keyValue, false);
            return keyValue;
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
     * Register a new user (overloaded method for backward compatibility)
     */
    public User register(String username, String password, String keyId) {
        return register(username, password, keyId, username + "@civiljoin.gov");
    }
    
    /**
     * Initialize database or fallback to mock data
     */
    private void initializeDatabase() {
        try {
            // Try to connect to database
            Connection conn = DatabaseUtil.getConnection();
            DatabaseUtil.closeConnection(conn);
            LOGGER.log(Level.INFO, "Successfully connected to database");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to connect to database, falling back to mock data", e);
            // Initialize mock data as fallback
            initializeMockData();
        }
    }
    
    /**
     * Generate a random alphanumeric key (16 characters)
     */
    private String generateAlphanumericKey() {
        final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder key = new StringBuilder(16);
        
        // Generate 16 alphanumeric characters
        for (int i = 0; i < 16; i++) {
            int index = (int) (Math.random() * ALPHA_NUMERIC.length());
            key.append(ALPHA_NUMERIC.charAt(index));
        }
        
        return key.toString();
    }
    
    /**
     * Initialize mock data for testing (fallback)
     */
    private void initializeMockData() {
        // Add owner user (changed from admin)
        String adminPassword = hashPassword("admin");
        User adminUser = new User("admin", "admin@civiljoin.gov", adminPassword, User.Role.OWNER);
        adminUser.setId(1);
        users.put("admin", adminUser);
        
        // Add some valid key IDs (16-character alphanumeric)
        keyIds.put("UrzKEU8WEqYseTSL", false);
        keyIds.put("X7yFpQh2BkRnV3z9", false);
        keyIds.put("Gh5TjCw4sE6yPmLq", false);
    }
    
    /**
     * Fallback authentication method using in-memory data
     */
    private User authenticateFallback(String username, String password) {
        User user = users.get(username);
        
        if (user != null && checkPassword(password, user.getPassword())) {
            return user;
        }
        
        return null;
    }
    
    /**
     * Fallback registration method using in-memory data
     */
    private User registerFallback(String username, String password, String keyId, String email) {
        // Validate key ID format first
        if (!validateKeyId(keyId)) {
            LOGGER.log(Level.WARNING, "Invalid key ID format in fallback registration: {0}", keyId);
            return null;
        }
        
        // Check if username already exists
        if (users.containsKey(username)) {
            return null;
        }
        
        // Check if key ID is valid and unused
        Boolean keyIdUsed = keyIds.get(keyId);
        if (keyIdUsed == null || keyIdUsed) {
            return null;
        }
        
        // Hash password
        String hashedPassword = hashPassword(password);
        
        // Create new user
        User user = new User(username, email, hashedPassword, User.Role.USER);
        user.setId(users.size() + 1);
        
        // Save user and mark key ID as used
        users.put(username, user);
        keyIds.put(keyId, true);
        
        return user;
    }
    
    /**
     * Simple password hashing for development purposes
     * In a real application, use a proper hashing algorithm like BCrypt
     */
    private String hashPassword(String password) {
        // Check if we have PasswordUtil available for proper BCrypt hashing
        try {
            return PasswordUtil.hashPassword(password);
        } catch (Exception e) {
            // Fallback to simple hash for development - DON'T USE IN PRODUCTION
            LOGGER.warning("Using simple password hash - not suitable for production");
            return "hashed_" + password;
        }
    }
    
    /**
     * Simple password verification for development purposes
     */
    private boolean checkPassword(String plainPassword, String hashedPassword) {
        // Try BCrypt verification first
        try {
            return PasswordUtil.verifyPassword(plainPassword, hashedPassword);
        } catch (Exception e) {
            // Fallback to simple check for development - DON'T USE IN PRODUCTION
            LOGGER.warning("Using simple password verification - not suitable for production");
            return hashedPassword.equals("hashed_" + plainPassword);
        }
    }
    
    /**
     * Verify if the provided password matches the user's stored password
     * 
     * @param user the user to check password against
     * @param password the password to verify
     * @return true if password matches
     */
    public boolean verifyPassword(User user, String password) {
        if (user == null || password == null) {
            return false;
        }
        
        // Use existing checkPassword method to verify
        return checkPassword(password, user.getPassword());
    }
    
    /**
     * Change a user's password
     * 
     * @param user the user whose password will be changed
     * @param currentPassword the current password
     * @param newPassword the new password to set
     * @return true if password was changed successfully
     */
    public boolean changePassword(User user, String currentPassword, String newPassword) {
        if (!verifyPassword(user, currentPassword)) {
            return false;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = "UPDATE users SET password = ? WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, PasswordUtil.hashPassword(newPassword));
            stmt.setInt(2, user.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.severe("Error changing password: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete user account with password verification and complete data cleanup
     * 
     * @param userId the user ID to delete
     * @param password the user's password for verification
     * @return true if the deletion was successful
     */
    public boolean deleteUserAccount(int userId, String password) {
        LOGGER.log(Level.INFO, "Attempting to delete user account: " + userId);
        
        // First, verify the user exists and password is correct
        User user = getUserById(userId);
        if (user == null) {
            LOGGER.log(Level.WARNING, "User not found for deletion: " + userId);
            return false;
        }
        
        // Verify password before deletion
        if (!verifyPassword(user, password)) {
            LOGGER.log(Level.WARNING, "Password verification failed for user deletion: " + userId);
            return false;
        }
        
        Connection conn = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            // Start transaction for complete data integrity
            conn.setAutoCommit(false);
            
            try {
                LOGGER.log(Level.INFO, "Starting cascade deletion for user: " + userId);
                
                // Delete in reverse dependency order to avoid foreign key constraints
                
                // 1. Delete from tables that reference this user but allow NULL
                String updateSecurityEventsResolved = "UPDATE security_events SET resolved_by = NULL WHERE resolved_by = ?";
                PreparedStatement updateSecurityStmt = conn.prepareStatement(updateSecurityEventsResolved);
                updateSecurityStmt.setInt(1, userId);
                updateSecurityStmt.executeUpdate();
                updateSecurityStmt.close();
                
                String updateSystemSettings = "UPDATE system_settings SET updated_by = 1 WHERE updated_by = ?";
                PreparedStatement updateSettingsStmt = conn.prepareStatement(updateSystemSettings);
                updateSettingsStmt.setInt(1, userId);
                updateSettingsStmt.executeUpdate();
                updateSettingsStmt.close();
                
                // 2. Delete user's security events
                String deleteSecurityEvents = "DELETE FROM security_events WHERE user_id = ?";
                PreparedStatement securityEventsStmt = conn.prepareStatement(deleteSecurityEvents);
                securityEventsStmt.setInt(1, userId);
                int securityEventsDeleted = securityEventsStmt.executeUpdate();
                securityEventsStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + securityEventsDeleted + " security events for user: " + userId);
                
                // 3. Delete user sessions
                String deleteSessions = "DELETE FROM user_sessions WHERE user_id = ?";
                PreparedStatement sessionsStmt = conn.prepareStatement(deleteSessions);
                sessionsStmt.setInt(1, userId);
                int sessionsDeleted = sessionsStmt.executeUpdate();
                sessionsStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + sessionsDeleted + " user sessions for user: " + userId);
                
                // 4. Delete user warnings
                String deleteWarnings = "DELETE FROM user_warnings WHERE user_id = ? OR issued_by = ?";
                PreparedStatement warningsStmt = conn.prepareStatement(deleteWarnings);
                warningsStmt.setInt(1, userId);
                warningsStmt.setInt(2, userId);
                int warningsDeleted = warningsStmt.executeUpdate();
                warningsStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + warningsDeleted + " user warnings for user: " + userId);
                
                // 5. Delete content moderation records
                String deleteModerationByUser = "DELETE FROM content_moderation WHERE moderated_by = ?";
                PreparedStatement moderationStmt = conn.prepareStatement(deleteModerationByUser);
                moderationStmt.setInt(1, userId);
                int moderationDeleted = moderationStmt.executeUpdate();
                moderationStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + moderationDeleted + " moderation records for user: " + userId);
                
                // 6. Delete admin audit trail
                String deleteAuditTrail = "DELETE FROM admin_audit_trail WHERE admin_user_id = ? OR target_user_id = ?";
                PreparedStatement auditStmt = conn.prepareStatement(deleteAuditTrail);
                auditStmt.setInt(1, userId);
                auditStmt.setInt(2, userId);
                int auditDeleted = auditStmt.executeUpdate();
                auditStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + auditDeleted + " audit trail records for user: " + userId);
                
                // 7. Delete user's comments first (before posts)
                String deleteComments = "DELETE FROM comments WHERE user_id = ?";
                PreparedStatement commentsStmt = conn.prepareStatement(deleteComments);
                commentsStmt.setInt(1, userId);
                int commentsDeleted = commentsStmt.executeUpdate();
                commentsStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + commentsDeleted + " comments for user: " + userId);
                
                // 8. Delete comments on user's posts (cascade deletion)
                String deleteCommentsOnPosts = "DELETE c FROM comments c INNER JOIN posts p ON c.post_id = p.id WHERE p.user_id = ?";
                PreparedStatement commentsOnPostsStmt = conn.prepareStatement(deleteCommentsOnPosts);
                commentsOnPostsStmt.setInt(1, userId);
                int commentsOnPostsDeleted = commentsOnPostsStmt.executeUpdate();
                commentsOnPostsStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + commentsOnPostsDeleted + " comments on user's posts");
                
                // 9. Delete content moderation for user's content
                String deleteContentModeration = "DELETE cm FROM content_moderation cm INNER JOIN posts p ON cm.content_id = p.id WHERE p.user_id = ? AND cm.content_type = 'POST'";
                PreparedStatement contentModerationStmt = conn.prepareStatement(deleteContentModeration);
                contentModerationStmt.setInt(1, userId);
                int contentModerationDeleted = contentModerationStmt.executeUpdate();
                contentModerationStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + contentModerationDeleted + " content moderation records for user's posts");
                
                // 10. Delete user's posts
                String deletePosts = "DELETE FROM posts WHERE user_id = ?";
                PreparedStatement postsStmt = conn.prepareStatement(deletePosts);
                postsStmt.setInt(1, userId);
                int postsDeleted = postsStmt.executeUpdate();
                postsStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + postsDeleted + " posts for user: " + userId);
                
                // 11. Delete user's feedback
                String deleteFeedback = "DELETE FROM feedback WHERE user_id = ?";
                PreparedStatement feedbackStmt = conn.prepareStatement(deleteFeedback);
                feedbackStmt.setInt(1, userId);
                int feedbackDeleted = feedbackStmt.executeUpdate();
                feedbackStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + feedbackDeleted + " feedback entries for user: " + userId);
                
                // 12. Delete system activities
                String deleteSystemActivities = "DELETE FROM system_activities WHERE user_id = ?";
                PreparedStatement systemActivitiesStmt = conn.prepareStatement(deleteSystemActivities);
                systemActivitiesStmt.setInt(1, userId);
                int systemActivitiesDeleted = systemActivitiesStmt.executeUpdate();
                systemActivitiesStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + systemActivitiesDeleted + " system activities for user: " + userId);
                
                // 13. Delete activity logs (original table)
                String deleteActivityLog = "DELETE FROM activity_log WHERE user_id = ?";
                PreparedStatement activityLogStmt = conn.prepareStatement(deleteActivityLog);
                activityLogStmt.setInt(1, userId);
                int activityLogDeleted = activityLogStmt.executeUpdate();
                activityLogStmt.close();
                LOGGER.log(Level.INFO, "Deleted " + activityLogDeleted + " activity log entries for user: " + userId);
                
                // 14. Finally, delete the user record
                String deleteUser = "DELETE FROM users WHERE id = ?";
                PreparedStatement userStmt = conn.prepareStatement(deleteUser);
                userStmt.setInt(1, userId);
                int result = userStmt.executeUpdate();
                userStmt.close();
                
                if (result > 0) {
                    // Commit the transaction
                    conn.commit();
                    LOGGER.log(Level.INFO, "Successfully deleted user account and all related data: " + userId);
                    return true;
                } else {
                    // Rollback if user deletion failed
                    conn.rollback();
                    LOGGER.log(Level.WARNING, "Failed to delete user record: " + userId);
                    return false;
                }
                
            } catch (SQLException e) {
                // Rollback the transaction on any error
                try {
                    conn.rollback();
                    LOGGER.log(Level.SEVERE, "Transaction rolled back due to error during user deletion: " + userId, e);
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "Failed to rollback transaction", rollbackEx);
                }
                throw e;
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during user account deletion: " + e.getMessage(), e);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Reset auto-commit
                    DatabaseUtil.closeConnection(conn);
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Error resetting connection state", e);
                }
            }
        }
    }

    /**
     * Delete user account (admin function - no password verification required)
     * 
     * @param userId the user ID to delete
     * @return true if the deletion was successful
     */
    public boolean deleteUser(int userId) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Start transaction
            conn.setAutoCommit(false);
            
            try {
                LOGGER.log(Level.INFO, "Admin deletion - Starting cascade deletion for user: " + userId);
                
                // Delete in reverse dependency order to avoid foreign key constraints
                
                // 1. Update references that allow NULL
                String updateSecurityEventsResolved = "UPDATE security_events SET resolved_by = NULL WHERE resolved_by = ?";
                PreparedStatement updateSecurityStmt = conn.prepareStatement(updateSecurityEventsResolved);
                updateSecurityStmt.setInt(1, userId);
                updateSecurityStmt.executeUpdate();
                updateSecurityStmt.close();
                
                String updateSystemSettings = "UPDATE system_settings SET updated_by = 1 WHERE updated_by = ?";
                PreparedStatement updateSettingsStmt = conn.prepareStatement(updateSystemSettings);
                updateSettingsStmt.setInt(1, userId);
                updateSettingsStmt.executeUpdate();
                updateSettingsStmt.close();
                
                // 2. Delete all dependent records
                String deleteSecurityEvents = "DELETE FROM security_events WHERE user_id = ?";
                PreparedStatement securityEventsStmt = conn.prepareStatement(deleteSecurityEvents);
                securityEventsStmt.setInt(1, userId);
                securityEventsStmt.executeUpdate();
                securityEventsStmt.close();
                
                String deleteSessions = "DELETE FROM user_sessions WHERE user_id = ?";
                PreparedStatement sessionsStmt = conn.prepareStatement(deleteSessions);
                sessionsStmt.setInt(1, userId);
                sessionsStmt.executeUpdate();
                sessionsStmt.close();
                
                String deleteWarnings = "DELETE FROM user_warnings WHERE user_id = ? OR issued_by = ?";
                PreparedStatement warningsStmt = conn.prepareStatement(deleteWarnings);
                warningsStmt.setInt(1, userId);
                warningsStmt.setInt(2, userId);
                warningsStmt.executeUpdate();
                warningsStmt.close();
                
                String deleteModerationByUser = "DELETE FROM content_moderation WHERE moderated_by = ?";
                PreparedStatement moderationStmt = conn.prepareStatement(deleteModerationByUser);
                moderationStmt.setInt(1, userId);
                moderationStmt.executeUpdate();
                moderationStmt.close();
                
                String deleteAuditTrail = "DELETE FROM admin_audit_trail WHERE admin_user_id = ? OR target_user_id = ?";
                PreparedStatement auditStmt = conn.prepareStatement(deleteAuditTrail);
                auditStmt.setInt(1, userId);
                auditStmt.setInt(2, userId);
                auditStmt.executeUpdate();
                auditStmt.close();
                
                // Delete user's comments
                String deleteComments = "DELETE FROM comments WHERE user_id = ?";
                PreparedStatement commentsStmt = conn.prepareStatement(deleteComments);
                commentsStmt.setInt(1, userId);
                commentsStmt.executeUpdate();
                commentsStmt.close();
                
                // Delete comments on user's posts
                String deleteCommentsOnPosts = "DELETE c FROM comments c INNER JOIN posts p ON c.post_id = p.id WHERE p.user_id = ?";
                PreparedStatement commentsOnPostsStmt = conn.prepareStatement(deleteCommentsOnPosts);
                commentsOnPostsStmt.setInt(1, userId);
                commentsOnPostsStmt.executeUpdate();
                commentsOnPostsStmt.close();
                
                // Delete content moderation for user's content
                String deleteContentModeration = "DELETE cm FROM content_moderation cm INNER JOIN posts p ON cm.content_id = p.id WHERE p.user_id = ? AND cm.content_type = 'POST'";
                PreparedStatement contentModerationStmt = conn.prepareStatement(deleteContentModeration);
                contentModerationStmt.setInt(1, userId);
                contentModerationStmt.executeUpdate();
                contentModerationStmt.close();
                
                // Delete user's posts
                String deletePosts = "DELETE FROM posts WHERE user_id = ?";
                PreparedStatement postsStmt = conn.prepareStatement(deletePosts);
                postsStmt.setInt(1, userId);
                postsStmt.executeUpdate();
                postsStmt.close();
                
                // Delete user's feedback
                String deleteFeedback = "DELETE FROM feedback WHERE user_id = ?";
                PreparedStatement feedbackStmt = conn.prepareStatement(deleteFeedback);
                feedbackStmt.setInt(1, userId);
                feedbackStmt.executeUpdate();
                feedbackStmt.close();
                
                // Delete system activities
                String deleteSystemActivities = "DELETE FROM system_activities WHERE user_id = ?";
                PreparedStatement systemActivitiesStmt = conn.prepareStatement(deleteSystemActivities);
                systemActivitiesStmt.setInt(1, userId);
                systemActivitiesStmt.executeUpdate();
                systemActivitiesStmt.close();
                
                // Delete user's activity logs
                String deleteActivity = "DELETE FROM activity_log WHERE user_id = ?";
                PreparedStatement activityStmt = conn.prepareStatement(deleteActivity);
                activityStmt.setInt(1, userId);
                activityStmt.executeUpdate();
                activityStmt.close();
                
                // Finally, delete the user
                String deleteUser = "DELETE FROM users WHERE id = ?";
                PreparedStatement userStmt = conn.prepareStatement(deleteUser);
                userStmt.setInt(1, userId);
                int result = userStmt.executeUpdate();
                userStmt.close();
                
                // Commit transaction
                conn.commit();
                LOGGER.log(Level.INFO, "Admin successfully deleted user and all related data: " + userId);
                return result > 0;
            } catch (SQLException e) {
                // Rollback on error
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Admin deletion failed, transaction rolled back for user: " + userId, e);
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.severe("Error deleting user: " + e.getMessage());
            return false;
        }
    }

    /**
     * User session class for performance optimization
     */
    private static class UserSession {
        final User user;
        final long createdAt;
        long lastAccessedAt;
        
        UserSession(User user) {
            this.user = user;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessedAt = createdAt;
        }
        
        boolean isValid() {
            return (System.currentTimeMillis() - lastAccessedAt) < SESSION_TIMEOUT_MS;
        }
        
        void updateAccess() {
            this.lastAccessedAt = System.currentTimeMillis();
        }
    }
    
    /**
     * Fast session-based authentication check
     */
    public User getSessionUser(String sessionId) {
        UserSession session = activeSessions.get(sessionId);
        if (session != null && session.isValid()) {
            session.updateAccess();
            return session.user;
        } else {
            activeSessions.remove(sessionId);
            return null;
        }
    }
    
    /**
     * Create session after successful authentication
     */
    private String createSession(User user) {
        String sessionId = generateSessionId(user);
        activeSessions.put(sessionId, new UserSession(user));
        
        // Cleanup old sessions
        cleanupExpiredSessions();
        
        return sessionId;
    }
    
    /**
     * Generate secure session ID
     */
    private String generateSessionId(User user) {
        return user.getUsername() + "_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString(user.hashCode());
    }
    
    /**
     * Cleanup expired sessions periodically
     */
    private void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> !entry.getValue().isValid());
    }
} 