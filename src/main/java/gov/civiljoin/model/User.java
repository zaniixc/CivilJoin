package gov.civiljoin.model;

import java.time.LocalDateTime;

/**
 * Enhanced User model representing the 'users' table in the database
 * Updated for CivilJoin 2.0 master schema with advanced security features
 */
public class User {
    private int id;
    private String username;
    private String email;
    private String password; // password_hash in database
    private String salt;
    private Role role;
    private String keyId;
    private boolean isActive = true;
    private boolean emailVerified = false;
    private int failedLoginAttempts = 0;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLogin;
    private LocalDateTime lastPasswordChange;
    private String profilePictureUrl;
    private String bio;
    private String themePreference = "DARK";
    private String languagePreference = "en";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Enum for user roles - enhanced with MODERATOR
    public enum Role {
        OWNER, ADMIN, MODERATOR, USER
    }
    
    // Constructors
    public User() {
        this.createdAt = LocalDateTime.now();
    }
    
    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = Role.USER; // Default role
        this.createdAt = LocalDateTime.now();
    }
    
    // Constructor with role parameter
    public User(String username, String email, String password, Role role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.createdAt = LocalDateTime.now();
    }
    
    // Enhanced constructor with all security fields
    public User(String username, String email, String password, String salt, Role role, String keyId) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.salt = salt;
        this.role = role;
        this.keyId = keyId;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and setters for all fields
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getSalt() {
        return salt;
    }
    
    public void setSalt(String salt) {
        this.salt = salt;
    }
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public String getKeyId() {
        return keyId;
    }
    
    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public boolean isEmailVerified() {
        return emailVerified;
    }
    
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
    
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
    
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }
    
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
    
    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public LocalDateTime getLastPasswordChange() {
        return lastPasswordChange;
    }
    
    public void setLastPasswordChange(LocalDateTime lastPasswordChange) {
        this.lastPasswordChange = lastPasswordChange;
    }
    
    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }
    
    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }
    
    public String getBio() {
        return bio;
    }
    
    public void setBio(String bio) {
        this.bio = bio;
    }
    
    public String getThemePreference() {
        return themePreference;
    }
    
    public void setThemePreference(String themePreference) {
        this.themePreference = themePreference;
    }
    
    public String getLanguagePreference() {
        return languagePreference;
    }
    
    public void setLanguagePreference(String languagePreference) {
        this.languagePreference = languagePreference;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // Utility methods
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
    
    public boolean canLogin() {
        return isActive && !isLocked();
    }
    
    public boolean hasAdminPrivileges() {
        return role == Role.OWNER || role == Role.ADMIN;
    }
    
    public boolean hasModerationPrivileges() {
        return role == Role.OWNER || role == Role.ADMIN || role == Role.MODERATOR;
    }
    
    public String getRoleDisplayName() {
        switch (role) {
            case OWNER: return "Owner";
            case ADMIN: return "Administrator";
            case MODERATOR: return "Moderator";
            case USER: return "User";
            default: return "Unknown";
        }
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", isActive=" + isActive +
                ", emailVerified=" + emailVerified +
                ", createdAt=" + createdAt +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id;
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
} 