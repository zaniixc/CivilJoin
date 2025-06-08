package gov.civiljoin.service;

import gov.civiljoin.model.Post;
import gov.civiljoin.util.DatabaseUtil;
import gov.civiljoin.util.PerformanceMonitor;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;

/**
 * Optimized service for handling post operations with caching and performance monitoring
 */
public class PostService {
    
    private static final Logger LOGGER = Logger.getLogger(PostService.class.getName());
    
    // Performance services
    private final AsyncTaskService asyncService = AsyncTaskService.getInstance();
    private final CacheService cacheService = CacheService.getInstance();
    private final PerformanceMonitor performanceMonitor = PerformanceMonitor.getInstance();
    
    // Cache keys
    private static final String ALL_POSTS_CACHE_KEY = "posts:all";
    private static final String USER_POSTS_CACHE_PREFIX = "posts:user:";
    private static final int POSTS_CACHE_TTL_MINUTES = 5;
    
    /**
     * Get all posts with caching and pagination
     */
    public List<Post> getAllPosts() {
        return getAllPosts(0, 50); // Default pagination
    }
    
    /**
     * Get posts with pagination and caching
     */
    public List<Post> getAllPosts(int offset, int limit) {
        String cacheKey = String.format("%s:%d:%d", ALL_POSTS_CACHE_KEY, offset, limit);
        
        // Check cache first using correct CacheService method
        Optional<List<Post>> cachedPosts = cacheService.getCachedPosts(cacheKey);
        if (cachedPosts.isPresent()) {
            LOGGER.fine("Returning cached posts for offset=" + offset + ", limit=" + limit);
            return cachedPosts.get();
        }
        
        // Query database with performance monitoring
        performanceMonitor.startOperation("get_all_posts");
        List<Post> posts = getAllPostsFromDatabase(offset, limit);
        performanceMonitor.endOperation("get_all_posts");
        
        // Cache results using correct method
        cacheService.cachePosts(cacheKey, posts);
        
        return posts;
    }
    
    /**
     * Get posts asynchronously for better UI responsiveness
     */
    public CompletableFuture<List<Post>> getAllPostsAsync() {
        return getAllPostsAsync(0, 50);
    }
    
    /**
     * Get posts asynchronously with pagination
     */
    public CompletableFuture<List<Post>> getAllPostsAsync(int offset, int limit) {
        return asyncService.executeDbTask(() -> getAllPosts(offset, limit));
    }
    
    /**
     * Optimized database query with prepared statements and proper resource management
     */
    private List<Post> getAllPostsFromDatabase(int offset, int limit) {
        List<Post> postList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        long startTime = System.currentTimeMillis();
        
        try {
            conn = DatabaseUtil.getConnection();
            
            // Optimized query with LIMIT and proper indexing
            String sql = """
                SELECT p.*, u.username as author_name
                FROM posts p 
                LEFT JOIN users u ON p.user_id = u.id 
                ORDER BY p.created_at DESC 
                LIMIT ? OFFSET ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Post post = createOptimizedPostFromResultSet(rs);
                postList.add(post);
            }
            
            long queryTime = System.currentTimeMillis() - startTime;
            performanceMonitor.recordDbQuery(queryTime);
            
            if (queryTime > 100) {
                LOGGER.warning("Slow query: getAllPosts took " + queryTime + "ms");
            }
            
            LOGGER.fine("Retrieved " + postList.size() + " posts in " + queryTime + "ms");
            return postList;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving posts", e);
            return new ArrayList<>();
        } finally {
            closeResources(rs, stmt, conn);
        }
    }
    
    /**
     * Optimized post creation from ResultSet with minimal object allocation
     */
    private Post createOptimizedPostFromResultSet(ResultSet rs) throws SQLException {
        Post post = new Post();
        
        // Use efficient field mapping
        post.setId(rs.getInt("id"));
        post.setTitle(rs.getString("title"));
        post.setContent(rs.getString("content"));
        post.setUserId(rs.getInt("user_id"));
        
        // Handle author name if available from JOIN
        String authorName = rs.getString("author_name");
        if (authorName != null) {
            post.setAuthorName(authorName);
        }
        
        // Handle timestamps efficiently
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            post.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        return post;
    }
    
    /**
     * Optimized post creation with batch insert support
     */
    public boolean createPost(Post post) {
        performanceMonitor.startOperation("create_post");
        
        try {
            boolean success = createPostInDatabase(post);
            
            if (success) {
                // Invalidate relevant caches
                invalidatePostCaches();
                LOGGER.info("Post created successfully: " + post.getTitle());
            }
            
            return success;
            
        } finally {
            performanceMonitor.endOperation("create_post");
        }
    }
    
    /**
     * Create post asynchronously to avoid blocking UI
     */
    public CompletableFuture<Boolean> createPostAsync(Post post) {
        return asyncService.executeDbTask(() -> createPost(post));
    }
    
    /**
     * Create post in database - implementation
     */
    private boolean createPostInDatabase(Post post) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "INSERT INTO posts (user_id, title, content, attachments) VALUES (?, ?, ?, ?)";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, post.getUserId());
            stmt.setString(2, post.getTitle());
            stmt.setString(3, post.getContent());
            
            // Convert attachments list to JSON string
            if (post.getAttachments() != null && !post.getAttachments().isEmpty()) {
                String attachmentsJson = "[" + 
                    String.join(",", 
                        post.getAttachments().stream()
                            .map(s -> "\"" + s + "\"")
                            .toArray(String[]::new)
                    ) + "]";
                stmt.setString(4, attachmentsJson);
            } else {
                stmt.setString(4, "[]");
            }
            
            int result = stmt.executeUpdate();
            return result > 0;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error creating post", e);
            return false;
        } finally {
            closeResources(null, stmt, conn);
        }
    }
    
    /**
     * Efficient cache invalidation
     */
    private void invalidatePostCaches() {
        cacheService.invalidateAllPosts();
        // Also invalidate user-specific post caches if they exist
        cacheService.invalidate(USER_POSTS_CACHE_PREFIX);
    }
    
    /**
     * Proper resource cleanup
     */
    private void closeResources(ResultSet rs, PreparedStatement stmt, Connection conn) {
        try {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) DatabaseUtil.closeConnection(conn);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error closing database resources", e);
        }
    }
    
    /**
     * Delete a post by ID
     * 
     * @param postId the ID of the post to delete
     * @return true if successful, false otherwise
     */
    public boolean deletePost(int postId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "DELETE FROM posts WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, postId);
            
            int result = stmt.executeUpdate();
            return result > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error deleting post", e);
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
     * Get the username for a user ID
     * 
     * @param userId the user ID
     * @return the username
     */
    public String getUsernameForUserId(int userId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "SELECT username FROM users WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("username");
            }
            
            return "Unknown User";
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving username", e);
            return "Unknown User";
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
     * Add a new post (alias for createPost)
     * 
     * @param post the post to add
     * @return true if successful, false otherwise
     */
    public boolean addPost(Post post) {
        return createPost(post);
    }
} 