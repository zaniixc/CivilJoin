#!/bin/bash

# This script helps IntelliJ properly include all necessary modules and libraries
# Save this file to idea.sh, make it executable with: chmod +x idea.sh

# Display information about Java and library paths
echo "Using Java: $(java -version 2>&1 | head -1)"
echo "HikariCP JAR path: $(find ~/.m2/repository -name "HikariCP-*.jar" | head -1)"
echo "JavaFX JARs:"
find ~/.m2/repository/org/openjfx -name "*.jar" | grep -v "javadoc\|sources" | head -10

# The script doesn't need to do anything else, just set these VM options in Run Configuration:
#
# --add-modules=javafx.controls,javafx.fxml,javafx.web,javafx.swing
# --add-exports=javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED
#
echo -e "\nIn IntelliJ, set these VM options in Run Configuration:\n"
echo "--add-modules=javafx.controls,javafx.fxml,javafx.web,javafx.swing"
echo "--add-exports=javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED" 