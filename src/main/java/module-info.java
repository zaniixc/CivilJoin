module com.example.civiljoin {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;

    opens com.example.civiljoin to javafx.fxml;
    exports com.example.civiljoin;
}