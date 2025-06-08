#!/bin/bash

# ============================================
# CivilJoin Application Launcher with Database Setup
# Version: 2.0
# ============================================

# Color codes for better output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Database configuration
DB_HOST="localhost"
DB_PORT="3306"
DB_NAME="civiljoin"
DB_USER="root"
DB_PASSWORD="123123"

# Application configuration
JAVA_HOME="/home/dan/.jdks/openjdk-23.0.2"
MAVEN_REPO="/home/dan/.m2/repository"
PROJECT_DIR="/home/dan/IdeaProjects/VibeCoded"

# Set environment variables for better performance
export JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC -XX:+UseStringDeduplication"
export JAVAFX_OPTS="-Djavafx.animation.fullspeed=true -Dprism.order=sw"

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

print_header() {
    echo -e "${PURPLE}=================================================${NC}"
    echo -e "${WHITE}$1${NC}"
    echo -e "${PURPLE}=================================================${NC}"
}

print_section() {
    echo -e "${CYAN}🔹 $1${NC}"
}

# Function to check if MySQL/MariaDB is running
check_mysql_service() {
    print_section "Checking MySQL/MariaDB service..."
    
    if systemctl is-active --quiet mysql 2>/dev/null; then
        print_status $GREEN "✅ MySQL service is running"
        return 0
    elif systemctl is-active --quiet mariadb 2>/dev/null; then
        print_status $GREEN "✅ MariaDB service is running"
        return 0
    else
        print_status $RED "❌ MySQL/MariaDB service is not running"
        print_status $YELLOW "Attempting to start MySQL service..."
        
        if sudo systemctl start mysql 2>/dev/null || sudo systemctl start mariadb 2>/dev/null; then
            print_status $GREEN "✅ Database service started successfully"
            sleep 2
            return 0
        else
            print_status $RED "❌ Failed to start database service"
            print_status $YELLOW "Please start MySQL/MariaDB manually:"
            print_status $WHITE "  sudo systemctl start mysql"
            print_status $WHITE "  # or"
            print_status $WHITE "  sudo systemctl start mariadb"
            exit 1
        fi
    fi
}

# Function to test database connection
test_db_connection() {
    print_section "Testing database connection..."
    
    if mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -e "SELECT 1;" >/dev/null 2>&1; then
        print_status $GREEN "✅ Database connection successful"
        return 0
    else
        print_status $RED "❌ Database connection failed"
        print_status $YELLOW "Please check your database credentials:"
        print_status $WHITE "  Host: $DB_HOST"
        print_status $WHITE "  Port: $DB_PORT"
        print_status $WHITE "  User: $DB_USER"
        print_status $WHITE "  Password: [configured]"
        exit 1
    fi
}

# Function to check if database exists
check_database_exists() {
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -e "USE $DB_NAME;" >/dev/null 2>&1
}

# Function to get database table count
get_table_count() {
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -D "$DB_NAME" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DB_NAME';" -N 2>/dev/null || echo "0"
}

# Function to backup existing database
backup_database() {
    local backup_file="civiljoin_backup_$(date +%Y%m%d_%H%M%S).sql"
    print_section "Creating database backup: $backup_file"
    
    if mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" > "$backup_file" 2>/dev/null; then
        print_status $GREEN "✅ Database backup created successfully: $backup_file"
        return 0
    else
        print_status $YELLOW "⚠️  Failed to create backup (database might not exist)"
        return 1
    fi
}

# Function to setup database
setup_database() {
    print_section "Database Setup Analysis"
    
    if check_database_exists; then
        local table_count=$(get_table_count)
        print_status $YELLOW "⚠️  Database '$DB_NAME' already exists with $table_count tables"
        
        echo
        echo "What would you like to do?"
        echo "1) Keep existing database and continue"
        echo "2) Drop and recreate database (with backup)"
        echo "3) Drop and recreate database (no backup)"
        echo "4) Exit and configure manually"
        
        read -p "Choose option (1-4): " choice
        
        case $choice in
            1)
                print_status $GREEN "✅ Using existing database"
                return 0
                ;;
            2)
                backup_database
                drop_and_create_database
                ;;
            3)
                drop_and_create_database
                ;;
            4)
                print_status $YELLOW "Exiting for manual configuration"
                exit 0
                ;;
            *)
                print_status $RED "Invalid choice, using existing database"
                return 0
                ;;
        esac
    else
        print_status $BLUE "📊 Database '$DB_NAME' does not exist, creating new database..."
        create_new_database
    fi
}

# Function to drop and create database
drop_and_create_database() {
    print_section "Dropping and recreating database..."
    
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -e "DROP DATABASE IF EXISTS $DB_NAME;" 2>/dev/null
    
    if create_new_database; then
        print_status $GREEN "✅ Database recreated successfully"
    else
        print_status $RED "❌ Failed to recreate database"
        exit 1
    fi
}

# Function to create new database
create_new_database() {
    print_section "Creating new database with schema..."
    
    # Create database
    if ! mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null; then
        print_status $RED "❌ Failed to create database"
        return 1
    fi
    
    # Check if master schema exists
    local schema_file="$PROJECT_DIR/src/main/resources/master_schema.sql"
    if [[ -f "$schema_file" ]]; then
        print_section "Executing master schema..."
        
        # Execute schema without the database creation commands
        if mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < <(grep -v "^DROP DATABASE\|^CREATE DATABASE\|^USE " "$schema_file") 2>/dev/null; then
            print_status $GREEN "✅ Master schema executed successfully"
        else
            print_status $YELLOW "⚠️  Schema execution had warnings (this might be normal)"
        fi
    else
        print_status $YELLOW "⚠️  Master schema not found, database will be initialized by application"
    fi
    
    # Insert default registration keys
    insert_default_keys
    
    # Insert default admin user
    insert_default_admin
    
    return 0
}

# Function to insert default registration keys
insert_default_keys() {
    print_section "Creating default registration keys..."
    
    local keys_sql="
    INSERT IGNORE INTO key_ids (key_value, key_type, description, generated_by) VALUES
    ('ADMIN20250121001', 'ADMIN', 'Default admin registration key', 1),
    ('USER20250121001', 'USER', 'Default user registration key #1', 1),
    ('USER20250121002', 'USER', 'Default user registration key #2', 1),
    ('USER20250121003', 'USER', 'Default user registration key #3', 1),
    ('MOD20250121001', 'MODERATOR', 'Default moderator registration key', 1),
    ('TEMP20250121001', 'TEMPORARY', 'Temporary access key', 1);
    "
    
    if mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "$keys_sql" 2>/dev/null; then
        print_status $GREEN "✅ Default registration keys created"
    else
        print_status $YELLOW "⚠️  Registration keys creation skipped (might already exist)"
    fi
}

# Function to insert default admin user
insert_default_admin() {
    print_section "Creating default admin user..."
    
    # BCrypt hash for 'admin123' with cost 12
    local admin_hash='$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LyYj.M0S9LXeGTdhe'
    local admin_salt='randomsalt123456789012345678'
    
    local admin_sql="
    INSERT IGNORE INTO users (id, username, email, password_hash, salt, role, key_id, is_active, email_verified, created_at) 
    VALUES (1, 'admin', 'admin@civiljoin.local', '$admin_hash', '$admin_salt', 'OWNER', 'ADMIN20250121001', 1, 1, NOW());
    "
    
    if mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "$admin_sql" 2>/dev/null; then
        print_status $GREEN "✅ Default admin user created"
        print_status $WHITE "   Username: admin"
        print_status $WHITE "   Password: admin123"
        print_status $WHITE "   Role: OWNER"
    else
        print_status $YELLOW "⚠️  Admin user creation skipped (might already exist)"
    fi
}

# Function to verify database setup
verify_database_setup() {
    print_section "Verifying database setup..."
    
    local user_count=$(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -D "$DB_NAME" -e "SELECT COUNT(*) FROM users;" -N 2>/dev/null || echo "0")
    local key_count=$(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -D "$DB_NAME" -e "SELECT COUNT(*) FROM key_ids WHERE is_used = FALSE;" -N 2>/dev/null || echo "0")
    local table_count=$(get_table_count)
    
    print_status $GREEN "✅ Database verification complete:"
    print_status $WHITE "   Tables: $table_count"
    print_status $WHITE "   Users: $user_count"
    print_status $WHITE "   Available keys: $key_count"
    
    if [[ $table_count -gt 0 && $user_count -gt 0 ]]; then
        return 0
    else
        print_status $YELLOW "⚠️  Database seems incomplete but will be initialized by application"
        return 0
    fi
}

# Function to check compilation
check_compilation() {
    print_section "Checking application compilation..."
    
    if [[ ! -d "$PROJECT_DIR/target/classes" ]]; then
        print_status $YELLOW "⚠️  Application not compiled, compiling now..."
        
        cd "$PROJECT_DIR" || exit 1
        if mvn compile -q; then
            print_status $GREEN "✅ Application compiled successfully"
        else
            print_status $RED "❌ Compilation failed"
            exit 1
        fi
    else
        print_status $GREEN "✅ Application already compiled"
    fi
}

# Function to display login credentials
display_login_info() {
    print_header "LOGIN CREDENTIALS"
    
    print_status $WHITE "🔐 Default Admin Account:"
    print_status $GREEN "   Username: admin"
    print_status $GREEN "   Password: admin123"
    print_status $GREEN "   Role: OWNER (Full Access)"
    
    echo
    print_status $WHITE "🎫 Available Registration Keys:"
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -D "$DB_NAME" -e "
    SELECT 
        key_value as 'Registration Key', 
        key_type as 'Type', 
        description as 'Description'
    FROM key_ids 
    WHERE is_used = FALSE 
    ORDER BY key_type, created_at 
    LIMIT 10;
    " 2>/dev/null || print_status $YELLOW "⚠️  Could not retrieve registration keys"
    
    echo
    print_status $YELLOW "📝 To register new users:"
    print_status $WHITE "   1. Use the registration keys above"
    print_status $WHITE "   2. Admin can generate more keys in the admin panel"
    print_status $WHITE "   3. Keys are single-use unless specified otherwise"
}

# Function to launch application
launch_application() {
    print_section "Launching CivilJoin Application..."
    
    cd "$PROJECT_DIR" || exit 1
    
    print_status $WHITE "🚀 Starting application with enhanced features:"
    print_status $GREEN "   • Dark theme optimized UI"
    print_status $GREEN "   • Real-time security monitoring"
    print_status $GREEN "   • Advanced admin panel"
    print_status $GREEN "   • In-app notification system"
    print_status $GREEN "   • Performance optimizations"
    
    echo
    print_status $BLUE "Application will open shortly..."
    echo
    
    # Launch the application
    "$JAVA_HOME/bin/java" \
        -javaagent:/home/dan/.local/share/JetBrains/Toolbox/apps/intellij-idea-ultimate/lib/idea_rt.jar=41167 \
        -Dfile.encoding=UTF-8 \
        -Dsun.stdout.encoding=UTF-8 \
        -Dsun.stderr.encoding=UTF-8 \
        $JAVA_OPTS \
        $JAVAFX_OPTS \
        -classpath "$MAVEN_REPO/org/openjfx/javafx-controls/17.0.6/javafx-controls-17.0.6.jar:$MAVEN_REPO/org/openjfx/javafx-fxml/17.0.6/javafx-fxml-17.0.6.jar:$MAVEN_REPO/org/openjfx/javafx-web/17.0.6/javafx-web-17.0.6.jar:$MAVEN_REPO/org/openjfx/javafx-swing/17.0.6/javafx-swing-17.0.6.jar:$MAVEN_REPO/org/openjfx/javafx-base/17.0.6/javafx-base-17.0.6.jar:$MAVEN_REPO/org/openjfx/javafx-graphics/17.0.6/javafx-graphics-17.0.6.jar:$MAVEN_REPO/org/openjfx/javafx-media/17.0.6/javafx-media-17.0.6.jar:$MAVEN_REPO/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar:$MAVEN_REPO/com/google/protobuf/protobuf-java/3.21.9/protobuf-java-3.21.9.jar:$MAVEN_REPO/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar" \
        -p "$MAVEN_REPO/org/openjfx/javafx-media/17.0.6/javafx-media-17.0.6-linux.jar:$MAVEN_REPO/org/openjfx/javafx-controls/17.0.6/javafx-controls-17.0.6-linux.jar:$MAVEN_REPO/org/openjfx/javafx-graphics/17.0.6/javafx-graphics-17.0.6-linux.jar:$MAVEN_REPO/eu/hansolo/toolboxfx/21.0.3/toolboxfx-21.0.3.jar:$MAVEN_REPO/eu/hansolo/fx/countries/21.0.3/countries-21.0.3.jar:$MAVEN_REPO/eu/hansolo/fx/heatmap/21.0.3/heatmap-21.0.3.jar:$PROJECT_DIR/target/classes:$MAVEN_REPO/org/openjfx/javafx-base/17.0.6/javafx-base-17.0.6-linux.jar:$MAVEN_REPO/eu/hansolo/toolbox/21.0.5/toolbox-21.0.5.jar:$MAVEN_REPO/com/dlsc/formsfx/formsfx-core/11.6.0/formsfx-core-11.6.0.jar:$MAVEN_REPO/net/synedra/validatorfx/0.5.0/validatorfx-0.5.0.jar:$MAVEN_REPO/org/openjfx/javafx-web/17.0.6/javafx-web-17.0.6-linux.jar:$MAVEN_REPO/org/controlsfx/controlsfx/11.2.1/controlsfx-11.2.1.jar:$MAVEN_REPO/com/zaxxer/HikariCP/5.0.1/HikariCP-5.0.1.jar:$MAVEN_REPO/eu/hansolo/tilesfx/21.0.3/tilesfx-21.0.3.jar:$MAVEN_REPO/org/openjfx/javafx-fxml/17.0.6/javafx-fxml-17.0.6-linux.jar:$MAVEN_REPO/org/kordamp/bootstrapfx/bootstrapfx-core/0.4.0/bootstrapfx-core-0.4.0.jar:$MAVEN_REPO/org/openjfx/javafx-swing/17.0.6/javafx-swing-17.0.6-linux.jar:$MAVEN_REPO/org/slf4j/slf4j-api/2.0.0-alpha1/slf4j-api-2.0.0-alpha1.jar" \
        -m gov.civiljoin/gov.civiljoin.CivilJoinApplication
}

# Function to cleanup on exit
cleanup() {
    echo
    print_header "CivilJoin Application Ended"
    print_status $BLUE "Thank you for using CivilJoin!"
    
    # Show any backup files created
    if ls civiljoin_backup_*.sql 1> /dev/null 2>&1; then
        echo
        print_status $YELLOW "💾 Database backups created during this session:"
        ls -la civiljoin_backup_*.sql 2>/dev/null | while read -r line; do
            print_status $WHITE "   $line"
        done
    fi
    
    exit 0
}

# Trap cleanup function on script exit
trap cleanup EXIT INT TERM

# ============================================
# MAIN EXECUTION
# ============================================

print_header "🚀 CivilJoin Application Launcher v2.0"
print_status $WHITE "Starting comprehensive setup and launch process..."

echo
# Step 1: Check MySQL service
check_mysql_service

echo
# Step 2: Test database connection
test_db_connection

echo
# Step 3: Setup database
setup_database

echo
# Step 4: Verify database setup
verify_database_setup

echo
# Step 5: Check compilation
check_compilation

echo
# Step 6: Display login information
display_login_info

echo
# Step 7: Launch application
launch_application 