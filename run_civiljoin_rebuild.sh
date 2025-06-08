#!/bin/bash

# CivilJoin Database Rebuild Test Script
# This script tests the completely rebuilt CivilJoin application with the new master schema

echo "🚀 Starting CivilJoin 2.0 with Rebuilt Database Schema"
echo "======================================================="

# Check if database is running
echo "📊 Checking database connection..."
if mysql -u root -p123123 -e "USE civiljoin; SELECT COUNT(*) as user_count FROM users;" 2>/dev/null; then
    echo "✅ Database connection successful!"
else
    echo "❌ Database connection failed!"
    exit 1
fi

# Display database info
echo ""
echo "📈 Database Statistics:"
echo "----------------------"
mysql -u root -p123123 civiljoin -e "
SELECT 
    (SELECT COUNT(*) FROM users) as total_users,
    (SELECT COUNT(*) FROM key_ids WHERE is_used = FALSE) as available_keys,
    (SELECT COUNT(*) FROM posts) as total_posts,
    (SELECT COUNT(*) FROM categories) as total_categories,
    (SELECT COUNT(*) FROM notifications) as total_notifications;
"

echo ""
echo "👥 Test User Accounts:"
echo "---------------------"
mysql -u root -p123123 civiljoin -e "
SELECT id, username, email, role, is_active, 
       CASE WHEN password_hash IS NOT NULL THEN 'BCrypt Hash' ELSE 'No Hash' END as password_status
FROM users;
"

echo ""
echo "🔐 Available Registration Keys:"
echo "------------------------------"
mysql -u root -p123123 civiljoin -e "
SELECT key_value, key_type, description 
FROM key_ids 
WHERE is_used = FALSE 
LIMIT 5;
"

echo ""
echo "🎯 Login Test Credentials:"
echo "========================="
echo "👑 Owner Account:"
echo "   Username: admin"
echo "   Password: admin123"
echo ""
echo "👤 Test User Account:"  
echo "   Username: testuser"
echo "   Password: user123"
echo ""

# Compile the application if needed
echo "🔨 Checking compilation..."
if [ ! -d "target/classes" ]; then
    echo "Compiling application..."
    mvn compile -q
fi

echo "🚀 Launching CivilJoin 2.0..."
echo "=============================="
echo ""

# Launch the application using the proper command configuration
/home/dan/.jdks/openjdk-23.0.2/bin/java \
    -javaagent:/home/dan/.local/share/JetBrains/Toolbox/apps/intellij-idea-ultimate/lib/idea_rt.jar=41167 \
    -Dfile.encoding=UTF-8 \
    -Dsun.stdout.encoding=UTF-8 \
    -Dsun.stderr.encoding=UTF-8 \
    -classpath /home/dan/.m2/repository/org/openjfx/javafx-controls/17.0.6/javafx-controls-17.0.6.jar:/home/dan/.m2/repository/org/openjfx/javafx-fxml/17.0.6/javafx-fxml-17.0.6.jar:/home/dan/.m2/repository/org/openjfx/javafx-web/17.0.6/javafx-web-17.0.6.jar:/home/dan/.m2/repository/org/openjfx/javafx-swing/17.0.6/javafx-swing-17.0.6.jar:/home/dan/.m2/repository/org/openjfx/javafx-base/17.0.6/javafx-base-17.0.6.jar:/home/dan/.m2/repository/org/openjfx/javafx-graphics/17.0.6/javafx-graphics-17.0.6.jar:/home/dan/.m2/repository/org/openjfx/javafx-media/17.0.6/javafx-media-17.0.6.jar:/home/dan/.m2/repository/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar:/home/dan/.m2/repository/com/google/protobuf/protobuf-java/3.21.9/protobuf-java-3.21.9.jar:/home/dan/.m2/repository/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar \
    -p /home/dan/.m2/repository/org/openjfx/javafx-media/17.0.6/javafx-media-17.0.6-linux.jar:/home/dan/.m2/repository/org/openjfx/javafx-controls/17.0.6/javafx-controls-17.0.6-linux.jar:/home/dan/.m2/repository/org/openjfx/javafx-graphics/17.0.6/javafx-graphics-17.0.6-linux.jar:/home/dan/.m2/repository/eu/hansolo/toolboxfx/21.0.3/toolboxfx-21.0.3.jar:/home/dan/.m2/repository/eu/hansolo/fx/countries/21.0.3/countries-21.0.3.jar:/home/dan/.m2/repository/eu/hansolo/fx/heatmap/21.0.3/heatmap-21.0.3.jar:/home/dan/IdeaProjects/VibeCoded/target/classes:/home/dan/.m2/repository/org/openjfx/javafx-base/17.0.6/javafx-base-17.0.6-linux.jar:/home/dan/.m2/repository/eu/hansolo/toolbox/21.0.5/toolbox-21.0.5.jar:/home/dan/.m2/repository/com/dlsc/formsfx/formsfx-core/11.6.0/formsfx-core-11.6.0.jar:/home/dan/.m2/repository/net/synedra/validatorfx/0.5.0/validatorfx-0.5.0.jar:/home/dan/.m2/repository/org/openjfx/javafx-web/17.0.6/javafx-web-17.0.6-linux.jar:/home/dan/.m2/repository/org/controlsfx/controlsfx/11.2.1/controlsfx-11.2.1.jar:/home/dan/.m2/repository/com/zaxxer/HikariCP/5.0.1/HikariCP-5.0.1.jar:/home/dan/.m2/repository/eu/hansolo/tilesfx/21.0.3/tilesfx-21.0.3.jar:/home/dan/.m2/repository/org/openjfx/javafx-fxml/17.0.6/javafx-fxml-17.0.6-linux.jar:/home/dan/.m2/repository/org/kordamp/bootstrapfx/bootstrapfx-core/0.4.0/bootstrapfx-core-0.4.0.jar:/home/dan/.m2/repository/org/openjfx/javafx-swing/17.0.6/javafx-swing-17.0.6-linux.jar:/home/dan/.m2/repository/org/slf4j/slf4j-api/2.0.0-alpha1/slf4j-api-2.0.0-alpha1.jar \
    -m gov.civiljoin/gov.civiljoin.CivilJoinApplication

echo ""
echo "🎉 CivilJoin session ended." 