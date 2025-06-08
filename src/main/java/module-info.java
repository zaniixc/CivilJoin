module gov.civiljoin {
    // JavaFX dependencies - made transitive for client code access
    requires transitive javafx.controls;
    requires transitive javafx.fxml;
    requires transitive javafx.web;
    requires transitive javafx.swing;
    requires transitive javafx.graphics;
    requires transitive javafx.base;
    requires transitive javafx.media;

    // UI libraries
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires net.synedra.validatorfx;
    
    // Database and utilities
    requires java.sql;
    requires com.zaxxer.hikari;
    requires java.logging;
    requires java.desktop;
    requires java.management;
    
    // Open packages for reflection (needed by JavaFX)
    opens gov.civiljoin to javafx.fxml, javafx.graphics;
    opens gov.civiljoin.controller to javafx.fxml;
    opens gov.civiljoin.controller.admin to javafx.fxml;
    opens gov.civiljoin.component to javafx.fxml;
    opens gov.civiljoin.model to javafx.base;
    
    // Export packages
    exports gov.civiljoin;
    exports gov.civiljoin.controller;
    exports gov.civiljoin.controller.admin;
    exports gov.civiljoin.component;
    exports gov.civiljoin.model;
    exports gov.civiljoin.service;
    exports gov.civiljoin.util;
}