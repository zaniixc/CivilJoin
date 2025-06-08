#!/bin/bash

# Ensure the script exits on any error
set -e

# Define repository paths
M2_REPO="/home/dan/.m2/repository"
JAVAFX_VERSION="17.0.6"
# Use Java 23 explicitly
JAVA_HOME="/usr/lib/jvm/java-23-openj9"

# Build classpath
CLASSPATH=""
# JavaFX platform-independent JARs
CLASSPATH="$CLASSPATH:$M2_REPO/org/openjfx/javafx-controls/$JAVAFX_VERSION/javafx-controls-$JAVAFX_VERSION.jar"
CLASSPATH="$CLASSPATH:$M2_REPO/org/openjfx/javafx-graphics/$JAVAFX_VERSION/javafx-graphics-$JAVAFX_VERSION.jar"
CLASSPATH="$CLASSPATH:$M2_REPO/org/openjfx/javafx-base/$JAVAFX_VERSION/javafx-base-$JAVAFX_VERSION.jar"
CLASSPATH="$CLASSPATH:$M2_REPO/org/openjfx/javafx-fxml/$JAVAFX_VERSION/javafx-fxml-$JAVAFX_VERSION.jar"
CLASSPATH="$CLASSPATH:$M2_REPO/org/openjfx/javafx-web/$JAVAFX_VERSION/javafx-web-$JAVAFX_VERSION.jar"
CLASSPATH="$CLASSPATH:$M2_REPO/org/openjfx/javafx-media/$JAVAFX_VERSION/javafx-media-$JAVAFX_VERSION.jar"
CLASSPATH="$CLASSPATH:$M2_REPO/org/openjfx/javafx-swing/$JAVAFX_VERSION/javafx-swing-$JAVAFX_VERSION.jar"

# MySQL and other dependencies
CLASSPATH="$CLASSPATH:$M2_REPO/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar"
CLASSPATH="$CLASSPATH:$M2_REPO/com/google/protobuf/protobuf-java/3.21.9/protobuf-java-3.21.9.jar"
CLASSPATH="$CLASSPATH:$M2_REPO/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar"

# Add target/classes
CLASSPATH="$CLASSPATH:target/classes"
CLASSPATH=${CLASSPATH#:}

# Build module path for dependencies
MODULE_PATH=""
MODULE_PATH="$MODULE_PATH:$M2_REPO/eu/hansolo/toolboxfx/21.0.3/toolboxfx-21.0.3.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/eu/hansolo/fx/countries/21.0.3/countries-21.0.3.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/com/zaxxer/HikariCP/5.0.1/HikariCP-5.0.1.jar" 
MODULE_PATH="$MODULE_PATH:$M2_REPO/eu/hansolo/fx/heatmap/21.0.3/heatmap-21.0.3.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/openjfx/javafx-graphics/$JAVAFX_VERSION/javafx-graphics-$JAVAFX_VERSION-linux.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/slf4j/slf4j-api/2.0.0-alpha1/slf4j-api-2.0.0-alpha1.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/openjfx/javafx-web/$JAVAFX_VERSION/javafx-web-$JAVAFX_VERSION-linux.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/net/synedra/validatorfx/0.5.0/validatorfx-0.5.0.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/controlsfx/controlsfx/11.2.1/controlsfx-11.2.1.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/kordamp/bootstrapfx/bootstrapfx-core/0.4.0/bootstrapfx-core-0.4.0.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/openjfx/javafx-swing/$JAVAFX_VERSION/javafx-swing-$JAVAFX_VERSION-linux.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/com/dlsc/formsfx/formsfx-core/11.6.0/formsfx-core-11.6.0.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/openjfx/javafx-fxml/$JAVAFX_VERSION/javafx-fxml-$JAVAFX_VERSION-linux.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/eu/hansolo/tilesfx/21.0.3/tilesfx-21.0.3.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/eu/hansolo/toolbox/21.0.5/toolbox-21.0.5.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/openjfx/javafx-controls/$JAVAFX_VERSION/javafx-controls-$JAVAFX_VERSION-linux.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/openjfx/javafx-media/$JAVAFX_VERSION/javafx-media-$JAVAFX_VERSION-linux.jar"
MODULE_PATH="$MODULE_PATH:$M2_REPO/org/openjfx/javafx-base/$JAVAFX_VERSION/javafx-base-$JAVAFX_VERSION-linux.jar"
MODULE_PATH=${MODULE_PATH#:}

# Remove target/classes from module path since it's already in classpath

# Run the application directly with the class path using Java 23
"$JAVA_HOME/bin/java" -classpath "$CLASSPATH" \
     --module-path "$MODULE_PATH" \
     --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.media,javafx.swing,javafx.base,javafx.graphics \
     gov.civiljoin.CivilJoinApplication 