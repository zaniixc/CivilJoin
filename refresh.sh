#!/bin/bash
# Script to refresh the database and rebuild the application

# Database credentials
DB_USER="root"
DB_PASS="123123"
DB_NAME="civiljoin"

echo "Dropping existing database..."
mysql -u$DB_USER -p$DB_PASS -e "DROP DATABASE IF EXISTS $DB_NAME;"

echo "Running Maven clean and package..."
./mvnw clean package -DskipTests

echo "Database will be recreated on application start."
echo "Launch the application with ./run.sh"

echo "Done. Database and application have been refreshed." 