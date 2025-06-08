-- ============================================
-- CivilJoin Advanced Master Schema
-- Version: 2.0
-- Date: 2025-01-21
-- Description: Complete production-ready schema
-- ============================================

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- Drop existing database and recreate
DROP DATABASE IF EXISTS civiljoin;
CREATE DATABASE civiljoin 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;
USE civiljoin;

-- ============================================
-- Table: users (Enhanced with security features)
-- ============================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
    salt VARCHAR(32) NOT NULL COMMENT 'Password salt for additional security',
    role ENUM('USER', 'ADMIN', 'MODERATOR', 'OWNER') NOT NULL DEFAULT 'USER',
    key_id VARCHAR(16) NOT NULL COMMENT 'Registration key used',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    last_login TIMESTAMP NULL,
    last_password_change TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    profile_picture_url VARCHAR(500) NULL,
    bio TEXT NULL,
    notification_preferences JSON NULL,
    theme_preference ENUM('LIGHT', 'DARK', 'AUTO') NOT NULL DEFAULT 'DARK',
    language_preference VARCHAR(10) NOT NULL DEFAULT 'en',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_key_id (key_id),
    INDEX idx_role (role),
    INDEX idx_active (is_active),
    INDEX idx_created_at (created_at),
    INDEX idx_failed_attempts (failed_login_attempts),
    INDEX idx_locked_until (locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: key_ids (Advanced key management)
-- ============================================
CREATE TABLE key_ids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_value VARCHAR(16) NOT NULL UNIQUE,
    key_type ENUM('USER', 'ADMIN', 'MODERATOR', 'OWNER', 'TEMPORARY') NOT NULL DEFAULT 'USER',
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_by BIGINT NULL,
    generated_by BIGINT NULL,
    expires_at TIMESTAMP NULL,
    max_uses INT NOT NULL DEFAULT 1,
    current_uses INT NOT NULL DEFAULT 0,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP NULL,
    
    INDEX idx_key_value (key_value),
    INDEX idx_key_type (key_type),
    INDEX idx_is_used (is_used),
    INDEX idx_expires_at (expires_at),
    FOREIGN KEY (used_by) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (generated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: posts (Enhanced forum posts)
-- ============================================
CREATE TABLE posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    content_type ENUM('TEXT', 'MARKDOWN', 'HTML') NOT NULL DEFAULT 'TEXT',
    status ENUM('DRAFT', 'PUBLISHED', 'ARCHIVED', 'DELETED') NOT NULL DEFAULT 'PUBLISHED',
    visibility ENUM('PUBLIC', 'PRIVATE', 'FRIENDS', 'ADMIN_ONLY') NOT NULL DEFAULT 'PUBLIC',
    category_id BIGINT NULL,
    tags JSON NULL,
    attachments JSON NULL,
    vote_score INT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    reply_count INT NOT NULL DEFAULT 0,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    edited_at TIMESTAMP NULL,
    edited_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_visibility (visibility),
    INDEX idx_category_id (category_id),
    INDEX idx_created_at (created_at),
    INDEX idx_vote_score (vote_score),
    INDEX idx_view_count (view_count),
    INDEX idx_pinned (is_pinned),
    INDEX idx_featured (is_featured),
    FULLTEXT idx_search (title, content),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (edited_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: categories (Post categorization)
-- ============================================
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NULL,
    color VARCHAR(7) NOT NULL DEFAULT '#2563eb',
    icon VARCHAR(50) NULL,
    parent_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    post_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_parent_id (parent_id),
    INDEX idx_is_active (is_active),
    INDEX idx_sort_order (sort_order),
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: post_votes (Voting system)
-- ============================================
CREATE TABLE post_votes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vote_type ENUM('UPVOTE', 'DOWNVOTE') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_user_post_vote (post_id, user_id),
    INDEX idx_post_id (post_id),
    INDEX idx_user_id (user_id),
    INDEX idx_vote_type (vote_type),
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: comments (Nested comments system)
-- ============================================
CREATE TABLE comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_comment_id BIGINT NULL,
    content TEXT NOT NULL,
    status ENUM('ACTIVE', 'DELETED', 'MODERATED', 'PENDING') NOT NULL DEFAULT 'ACTIVE',
    vote_score INT NOT NULL DEFAULT 0,
    depth INT NOT NULL DEFAULT 0,
    path VARCHAR(1000) NOT NULL DEFAULT '',
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    edited_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_post_id (post_id),
    INDEX idx_user_id (user_id),
    INDEX idx_parent_comment (parent_comment_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_path (path(255)),
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: comment_votes (Comment voting system)
-- ============================================
CREATE TABLE comment_votes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vote_type ENUM('UPVOTE', 'DOWNVOTE') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_user_comment_vote (comment_id, user_id),
    INDEX idx_comment_id (comment_id),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: notifications (Advanced notification system)
-- ============================================
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type ENUM('POST_REPLY', 'COMMENT_REPLY', 'UPVOTE', 'DOWNVOTE', 'MENTION', 'SYSTEM', 'ADMIN', 'WELCOME', 'WARNING') NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    related_post_id BIGINT NULL,
    related_comment_id BIGINT NULL,
    related_user_id BIGINT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    priority ENUM('LOW', 'NORMAL', 'HIGH', 'URGENT') NOT NULL DEFAULT 'NORMAL',
    action_url VARCHAR(500) NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    
    INDEX idx_user_id (user_id),
    INDEX idx_type (type),
    INDEX idx_is_read (is_read),
    INDEX idx_priority (priority),
    INDEX idx_created_at (created_at),
    INDEX idx_expires_at (expires_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (related_post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (related_comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    FOREIGN KEY (related_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: activity_log (Comprehensive audit trail)
-- ============================================
CREATE TABLE activity_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    session_id VARCHAR(128) NULL,
    action_type ENUM('LOGIN', 'LOGOUT', 'REGISTER', 'POST_CREATE', 'POST_EDIT', 'POST_DELETE', 'POST_VIEW', 'COMMENT_CREATE', 'COMMENT_EDIT', 'COMMENT_DELETE', 'VOTE_POST', 'VOTE_COMMENT', 'PROFILE_UPDATE', 'PASSWORD_CHANGE', 'ADMIN_ACTION', 'SYSTEM', 'KEY_GENERATE', 'KEY_USE') NOT NULL,
    action_description TEXT NOT NULL,
    target_type ENUM('USER', 'POST', 'COMMENT', 'KEY_ID', 'CATEGORY', 'NOTIFICATION', 'SYSTEM') NULL,
    target_id BIGINT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent TEXT NULL,
    request_url VARCHAR(1000) NULL,
    additional_data JSON NULL,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_action_type (action_type),
    INDEX idx_target_type_id (target_type, target_id),
    INDEX idx_created_at (created_at),
    INDEX idx_session_id (session_id),
    INDEX idx_success (success),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: user_sessions (Session management)
-- ============================================
CREATE TABLE user_sessions (
    id VARCHAR(128) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_activity TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_is_active (is_active),
    INDEX idx_last_activity (last_activity),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: user_preferences (User customization)
-- ============================================
CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    push_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    newsletter_subscription BOOLEAN NOT NULL DEFAULT FALSE,
    show_email BOOLEAN NOT NULL DEFAULT FALSE,
    show_online_status BOOLEAN NOT NULL DEFAULT TRUE,
    auto_subscribe_posts BOOLEAN NOT NULL DEFAULT TRUE,
    posts_per_page INT NOT NULL DEFAULT 20,
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    date_format VARCHAR(20) NOT NULL DEFAULT 'YYYY-MM-DD',
    time_format VARCHAR(10) NOT NULL DEFAULT '24',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: system_settings (Application configuration)
-- ============================================
CREATE TABLE system_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    setting_type ENUM('STRING', 'INTEGER', 'BOOLEAN', 'JSON') NOT NULL DEFAULT 'STRING',
    description TEXT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_setting_key (setting_key),
    INDEX idx_is_public (is_public),
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: feedback (User feedback and reports)
-- ============================================
CREATE TABLE feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    type ENUM('BUG_REPORT', 'FEATURE_REQUEST', 'GENERAL_FEEDBACK', 'ABUSE_REPORT', 'CONTENT_REPORT') NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status ENUM('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED') NOT NULL DEFAULT 'OPEN',
    priority ENUM('LOW', 'NORMAL', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'NORMAL',
    category VARCHAR(50) NULL,
    related_url VARCHAR(1000) NULL,
    related_post_id BIGINT NULL,
    related_comment_id BIGINT NULL,
    contact_email VARCHAR(100) NULL,
    admin_notes TEXT NULL,
    resolved_by BIGINT NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (related_post_id) REFERENCES posts(id) ON DELETE SET NULL,
    FOREIGN KEY (related_comment_id) REFERENCES comments(id) ON DELETE SET NULL,
    FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Insert Initial Data
-- ============================================

-- Insert system settings
INSERT INTO system_settings (setting_key, setting_value, setting_type, description, is_public, updated_by) VALUES
('app_name', 'CivilJoin', 'STRING', 'Application name', TRUE, 1),
('app_version', '2.0.0', 'STRING', 'Application version', TRUE, 1),
('maintenance_mode', 'false', 'BOOLEAN', 'Enable maintenance mode', FALSE, 1),
('registration_enabled', 'true', 'BOOLEAN', 'Allow new registrations', TRUE, 1),
('max_file_upload_size', '10485760', 'INTEGER', 'Max file upload size in bytes (10MB)', FALSE, 1),
('session_timeout', '86400', 'INTEGER', 'Session timeout in seconds (24 hours)', FALSE, 1),
('failed_login_threshold', '5', 'INTEGER', 'Maximum failed login attempts before account lock', FALSE, 1),
('account_lock_duration', '900', 'INTEGER', 'Account lock duration in seconds (15 minutes)', FALSE, 1);

-- Insert default categories
INSERT INTO categories (name, description, color, icon, sort_order) VALUES
('General Discussion', 'General topics and community discussions', '#2563eb', 'chat', 1),
('Announcements', 'Official announcements and news', '#dc2626', 'megaphone', 2),
('Help & Support', 'Get help and support from the community', '#059669', 'help-circle', 3),
('Feature Requests', 'Suggest new features and improvements', '#7c3aed', 'lightbulb', 4),
('Bug Reports', 'Report bugs and technical issues', '#ea580c', 'bug', 5);

-- Generate initial key IDs
INSERT INTO key_ids (key_value, key_type, description) VALUES
('OWNER0000000001', 'OWNER', 'Primary owner key'),
('ADMIN0000000001', 'ADMIN', 'Primary admin key'),
('ADMIN0000000002', 'ADMIN', 'Secondary admin key'),
('MOD00000000001', 'MODERATOR', 'Moderator key 1'),
('MOD00000000002', 'MODERATOR', 'Moderator key 2'),
('USER0000000001', 'USER', 'User registration key 1'),
('USER0000000002', 'USER', 'User registration key 2'),
('USER0000000003', 'USER', 'User registration key 3'),
('USER0000000004', 'USER', 'User registration key 4'),
('USER0000000005', 'USER', 'User registration key 5');

-- Insert owner user (password: admin123)
-- BCrypt hash of "admin123" with salt "civiljoin_salt_123"
INSERT INTO users (username, email, password_hash, salt, role, key_id, email_verified, is_active) VALUES
('admin', 'admin@civiljoin.gov', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeVWaplNlkKGdnG6S', 'civiljoin_salt_123', 'OWNER', 'OWNER0000000001', TRUE, TRUE);

-- Insert test user (password: user123)
-- BCrypt hash of "user123" with salt "civiljoin_salt_456"
INSERT INTO users (username, email, password_hash, salt, role, key_id, email_verified, is_active) VALUES
('testuser', 'user@civiljoin.gov', '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'civiljoin_salt_456', 'USER', 'USER0000000001', TRUE, TRUE);

-- Mark used keys
UPDATE key_ids SET is_used = TRUE, used_by = 1, used_at = NOW() WHERE key_value = 'OWNER0000000001';
UPDATE key_ids SET is_used = TRUE, used_by = 2, used_at = NOW() WHERE key_value = 'USER0000000001';

-- Create user preferences for existing users
INSERT INTO user_preferences (user_id) VALUES (1), (2);

-- Insert welcome post
INSERT INTO posts (user_id, title, content, category_id, is_pinned, is_featured) VALUES
(1, 'Welcome to CivilJoin 2.0!', 
'Welcome to the new and improved CivilJoin platform! This is your space for civic engagement, community discussions, and democratic participation.

## What''s New in v2.0:
- **Enhanced Security**: Advanced authentication and session management
- **Better Performance**: Optimized database queries and caching
- **Modern UI**: Clean, responsive design with dark mode support
- **Rich Features**: Voting, nested comments, notifications, and more
- **Accessibility**: Full keyboard navigation and screen reader support

## Getting Started:
1. Explore the different categories
2. Create your first post
3. Engage with the community through comments and votes
4. Customize your profile and preferences

We''re excited to have you as part of our democratic community!

**The CivilJoin Team**', 
1, TRUE, TRUE);

-- Insert welcome notification for all users
INSERT INTO notifications (user_id, type, title, message, related_post_id, priority) VALUES
(1, 'WELCOME', 'Welcome to CivilJoin!', 'Thank you for joining our civic engagement platform. Explore, engage, and make your voice heard!', 1, 'HIGH'),
(2, 'WELCOME', 'Welcome to CivilJoin!', 'Thank you for joining our civic engagement platform. Explore, engage, and make your voice heard!', 1, 'HIGH');

-- Update post count in categories
UPDATE categories SET post_count = 1 WHERE id = 1;

-- Reset SQL mode and checks
SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;

-- Create additional indexes for performance
CREATE INDEX idx_posts_category_status ON posts(category_id, status);
CREATE INDEX idx_posts_user_created ON posts(user_id, created_at);
CREATE INDEX idx_comments_post_status ON comments(post_id, status);
CREATE INDEX idx_activity_user_created ON activity_log(user_id, created_at);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);

-- Add security_events table for comprehensive auditing
CREATE TABLE IF NOT EXISTS security_events (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    event_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    severity ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'EMERGENCY') NOT NULL DEFAULT 'LOW',
    ip_address VARCHAR(45),
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_severity (severity),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- End of master schema 