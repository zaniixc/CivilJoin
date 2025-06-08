package gov.civiljoin.service;

import gov.civiljoin.model.Comment;
import gov.civiljoin.util.DatabaseUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for handling comment-related operations
 */
public class CommentService {
    private static final Logger LOGGER = Logger.getLogger(CommentService.class.getName());
    
    /**
     * Create the comments table if it doesn't exist
     */
    public void initializeCommentTable() {
        Connection conn = null;
        Statement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            stmt = conn.createStatement();
            
            String sql = "CREATE TABLE IF NOT EXISTS comments ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "post_id INT NOT NULL,"
                    + "user_id INT NOT NULL,"
                    + "content TEXT NOT NULL,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")";
            
            stmt.execute(sql);
            LOGGER.log(Level.INFO, "Comments table initialized successfully");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize comments table", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing resources", e);
            }
        }
    }
    
    /**
     * Get all comments for a specific post
     * 
     * @param postId the post ID
     * @return list of comments
     */
    public List<Comment> getCommentsForPost(int postId) {
        List<Comment> comments = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "SELECT c.*, u.username FROM comments c "
                    + "JOIN users u ON c.user_id = u.id "
                    + "WHERE c.post_id = ? "
                    + "ORDER BY c.created_at ASC";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, postId);
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Comment comment = new Comment();
                comment.setId(rs.getInt("id"));
                comment.setPostId(rs.getInt("post_id"));
                comment.setUserId(rs.getInt("user_id"));
                comment.setContent(rs.getString("content"));
                comment.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                comment.setUsername(rs.getString("username"));
                
                comments.add(comment);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting comments for post", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing resources", e);
            }
        }
        
        return comments;
    }
    
    /**
     * Create a new comment
     * 
     * @param comment the comment to create
     * @return true if successful, false otherwise
     */
    public boolean createComment(Comment comment) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "INSERT INTO comments (post_id, user_id, content) VALUES (?, ?, ?)";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, comment.getPostId());
            stmt.setInt(2, comment.getUserId());
            stmt.setString(3, comment.getContent());
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error creating comment", e);
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing resources", e);
            }
        }
    }
    
    /**
     * Delete a comment
     * 
     * @param commentId the comment ID to delete
     * @param userId the user attempting to delete (for security)
     * @param isAdmin whether the user is an admin
     * @return true if successful, false otherwise
     */
    public boolean deleteComment(int commentId, int userId, boolean isAdmin) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql;
            
            if (isAdmin) {
                // Admins can delete any comment
                sql = "DELETE FROM comments WHERE id = ?";
                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, commentId);
            } else {
                // Regular users can only delete their own comments
                sql = "DELETE FROM comments WHERE id = ? AND user_id = ?";
                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, commentId);
                stmt.setInt(2, userId);
            }
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting comment", e);
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing resources", e);
            }
        }
    }
} 