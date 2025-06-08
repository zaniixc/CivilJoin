package gov.civiljoin.service;

import gov.civiljoin.model.Post;
import gov.civiljoin.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for handling search functionality
 * Implements keyword-based search functionality as required by PRD
 */
public class SearchService {
    
    private static final Logger LOGGER = Logger.getLogger(SearchService.class.getName());
    
    /**
     * Search posts by keyword in titles and content
     * 
     * @param keyword The search keyword
     * @param limit Maximum number of results to return
     * @return List of matching posts
     */
    public List<Post> searchPosts(String keyword, int limit) {
        List<Post> results = new ArrayList<>();
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return results;
        }
        
        String searchTerm = "%" + keyword.trim().toLowerCase() + "%";
        
        String sql = """
            SELECT p.id, p.user_id, p.title, p.content, p.attachments, p.created_at,
                   u.username 
            FROM posts p 
            JOIN users u ON p.user_id = u.id 
            WHERE LOWER(p.title) LIKE ? OR LOWER(p.content) LIKE ?
            ORDER BY p.created_at DESC 
            LIMIT ?
            """;
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, searchTerm);
            stmt.setString(2, searchTerm);
            stmt.setInt(3, limit);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Post post = createPostFromResultSet(rs);
                results.add(post);
            }
            
            LOGGER.info("Found " + results.size() + " posts matching keyword: " + keyword);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error searching posts", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return results;
    }
    
    /**
     * Search posts by keyword with date range filter
     * 
     * @param keyword The search keyword
     * @param startDate Start date for filter (inclusive)
     * @param endDate End date for filter (inclusive)
     * @param limit Maximum number of results to return
     * @return List of matching posts
     */
    public List<Post> searchPostsWithDateRange(String keyword, LocalDateTime startDate, 
                                             LocalDateTime endDate, int limit) {
        List<Post> results = new ArrayList<>();
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return results;
        }
        
        String searchTerm = "%" + keyword.trim().toLowerCase() + "%";
        
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT p.id, p.user_id, p.title, p.content, p.attachments, p.created_at,
                   u.username 
            FROM posts p 
            JOIN users u ON p.user_id = u.id 
            WHERE (LOWER(p.title) LIKE ? OR LOWER(p.content) LIKE ?)
            """);
        
        // Add date range filters if provided
        if (startDate != null) {
            sqlBuilder.append(" AND p.created_at >= ?");
        }
        if (endDate != null) {
            sqlBuilder.append(" AND p.created_at <= ?");
        }
        
        sqlBuilder.append(" ORDER BY p.created_at DESC LIMIT ?");
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.prepareStatement(sqlBuilder.toString());
            
            int paramIndex = 1;
            stmt.setString(paramIndex++, searchTerm);
            stmt.setString(paramIndex++, searchTerm);
            
            if (startDate != null) {
                stmt.setObject(paramIndex++, startDate);
            }
            if (endDate != null) {
                stmt.setObject(paramIndex++, endDate);
            }
            
            stmt.setInt(paramIndex, limit);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Post post = createPostFromResultSet(rs);
                results.add(post);
            }
            
            LOGGER.info("Found " + results.size() + " posts matching keyword: " + keyword + 
                       " with date range: " + startDate + " to " + endDate);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error searching posts with date range", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return results;
    }
    
    /**
     * Search posts by category/type (based on content patterns or tags)
     * Note: This is a basic implementation. In a real system, you might have a separate
     * categories/tags table.
     * 
     * @param category The category to search for
     * @param limit Maximum number of results to return
     * @return List of matching posts
     */
    public List<Post> searchPostsByCategory(String category, int limit) {
        List<Post> results = new ArrayList<>();
        
        if (category == null || category.trim().isEmpty()) {
            return results;
        }
        
        // For this basic implementation, we'll search for category keywords in content
        String categoryTerm = "%" + category.trim().toLowerCase() + "%";
        
        String sql = """
            SELECT p.id, p.user_id, p.title, p.content, p.attachments, p.created_at,
                   u.username 
            FROM posts p 
            JOIN users u ON p.user_id = u.id 
            WHERE LOWER(p.content) LIKE ? 
            ORDER BY p.created_at DESC 
            LIMIT ?
            """;
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, categoryTerm);
            stmt.setInt(2, limit);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Post post = createPostFromResultSet(rs);
                results.add(post);
            }
            
            LOGGER.info("Found " + results.size() + " posts in category: " + category);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error searching posts by category", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return results;
    }
    
    /**
     * Get search suggestions based on partial keyword
     * 
     * @param partialKeyword Partial keyword for suggestions
     * @param limit Maximum number of suggestions
     * @return List of suggested search terms
     */
    public List<String> getSearchSuggestions(String partialKeyword, int limit) {
        List<String> suggestions = new ArrayList<>();
        
        if (partialKeyword == null || partialKeyword.trim().isEmpty()) {
            return suggestions;
        }
        
        String searchTerm = partialKeyword.trim().toLowerCase() + "%";
        
        String sql = """
            SELECT DISTINCT SUBSTRING_INDEX(SUBSTRING_INDEX(LOWER(title), ' ', numbers.n), ' ', -1) as word
            FROM posts
            CROSS JOIN (
                SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
                UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
            ) numbers
            WHERE CHAR_LENGTH(title) - CHAR_LENGTH(REPLACE(title, ' ', '')) >= numbers.n - 1
            AND SUBSTRING_INDEX(SUBSTRING_INDEX(LOWER(title), ' ', numbers.n), ' ', -1) LIKE ?
            AND LENGTH(SUBSTRING_INDEX(SUBSTRING_INDEX(LOWER(title), ' ', numbers.n), ' ', -1)) > 2
            ORDER BY word
            LIMIT ?
            """;
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, searchTerm);
            stmt.setInt(2, limit);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                String suggestion = rs.getString("word");
                if (suggestion != null && !suggestion.trim().isEmpty()) {
                    suggestions.add(suggestion.trim());
                }
            }
            
        } catch (SQLException e) {
            // Fallback to simpler suggestions if the complex query fails
            LOGGER.log(Level.WARNING, "Complex suggestion query failed, using fallback", e);
            suggestions = getSimpleSearchSuggestions(partialKeyword, limit);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return suggestions;
    }
    
    /**
     * Simple fallback method for search suggestions
     */
    private List<String> getSimpleSearchSuggestions(String partialKeyword, int limit) {
        List<String> suggestions = new ArrayList<>();
        String searchTerm = "%" + partialKeyword.trim().toLowerCase() + "%";
        
        String sql = "SELECT DISTINCT title FROM posts WHERE LOWER(title) LIKE ? LIMIT ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, searchTerm);
            stmt.setInt(2, limit);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                suggestions.add(rs.getString("title"));
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting simple search suggestions", e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
        
        return suggestions;
    }
    
    /**
     * Create a Post object from ResultSet
     */
    private Post createPostFromResultSet(ResultSet rs) throws SQLException {
        Post post = new Post();
        post.setId(rs.getInt("id"));
        post.setUserId(rs.getInt("user_id"));
        post.setTitle(rs.getString("title"));
        post.setContent(rs.getString("content"));
        
        // Handle attachments JSON - basic implementation
        String attachmentsJson = rs.getString("attachments");
        if (attachmentsJson != null && !attachmentsJson.trim().isEmpty()) {
            // Simple JSON parsing for attachments array
            List<String> attachments = parseAttachmentsJson(attachmentsJson);
            post.setAttachments(attachments);
        }
        
        post.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        
        // Set username if available
        try {
            String username = rs.getString("username");
            post.setAuthorName(username);
        } catch (SQLException e) {
            // Username might not be available in all queries
        }
        
        return post;
    }
    
    /**
     * Simple JSON parsing for attachments
     * In a real application, you'd use a proper JSON library
     */
    private List<String> parseAttachmentsJson(String json) {
        List<String> attachments = new ArrayList<>();
        
        if (json != null && json.startsWith("[") && json.endsWith("]")) {
            // Remove brackets and split by comma
            String content = json.substring(1, json.length() - 1);
            if (!content.trim().isEmpty()) {
                String[] parts = content.split(",");
                for (String part : parts) {
                    String cleaned = part.trim().replaceAll("\"", "");
                    if (!cleaned.isEmpty()) {
                        attachments.add(cleaned);
                    }
                }
            }
        }
        
        return attachments;
    }
} 