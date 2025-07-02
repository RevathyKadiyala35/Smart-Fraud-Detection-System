package app;

import javafx.animation.*;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Random;

public class FraudCheckerWithHistory extends Application {

    private TableView<Transaction> transactionTable;
    private Label locationTransitionLabel = new Label();
    private boolean otpVerified = false;
    private String generatedOTP = "";
    private Button submitButton;
    private TextField amountField, mobileField, upiField, locationField;
    private ComboBox<String> typeBox;
    private Label otpCountdownLabel = new Label();
    private Timeline otpTimer;
    private BarChart<String, Number> riskBarChart;
    private XYChart.Series<String, Number> riskData;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Login screen
        Label userLabel = new Label("Username:");
        TextField userField = new TextField();
        Label passLabel = new Label("Password:");
        PasswordField passField = new PasswordField();
        Label passwordStrengthLabel = new Label();
        passwordStrengthLabel.setTextFill(Color.RED);
        Button loginButton = new Button("Login");

        passField.textProperty().addListener((obs, oldVal, newVal) -> {
            String strength = getPasswordStrength(newVal);
            passwordStrengthLabel.setText(strength);
            passwordStrengthLabel.setTextFill(
                strength.contains("Strong") ? Color.GREEN :
                strength.contains("Moderate") ? Color.ORANGE : Color.RED);
        });

        VBox loginVBox = new VBox(10, userLabel, userField, passLabel, passField, passwordStrengthLabel, loginButton);
        loginVBox.setAlignment(Pos.CENTER);
        loginVBox.setPadding(new Insets(20));
        loginVBox.setStyle("-fx-background-color: linear-gradient(to right, #fbc7a4, #f1c0e8);");

        loginButton.setOnAction(e -> {
            if (!userField.getText().isEmpty() && !passField.getText().isEmpty()) {
                if (!passwordStrengthLabel.getText().contains("Weak")) {
                    showMainDashboard(primaryStage);
                } else {
                    showToast(primaryStage, "Password too weak!");
                }
            } else {
                showToast(primaryStage, "Please enter username and password.");
            }
        });

        Scene loginScene = new Scene(loginVBox, 400, 300);
        primaryStage.setTitle("Fraud Checker - Login");
        primaryStage.setScene(loginScene);
        primaryStage.show();
    }

    private void showMainDashboard(Stage stage) {
        VBox mainVBox = new VBox(15);
        mainVBox.setPadding(new Insets(15));
        mainVBox.setStyle("-fx-background-color: linear-gradient(to bottom, #fceaff, #b1d7f9);");

        Label heading = new Label("Enter Transaction Details:");
        heading.setFont(Font.font("Arial", 20));
        heading.setTextFill(Color.DARKVIOLET);

        amountField = new TextField();
        amountField.setPromptText("Amount");
        mobileField = new TextField();
        mobileField.setPromptText("Mobile Number");
        upiField = new TextField();
        upiField.setPromptText("UPI ID");
        locationField = new TextField();
        locationField.setPromptText("Current Location");

        typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Shopping", "Transfer", "Bill", "Recharge");
        typeBox.setPromptText("Transaction Type");

        Button otpButton = new Button("Verify OTP");
        submitButton = new Button("Submit Transaction");
        submitButton.setDisable(true);
        Button exportCSVButton = new Button("Download CSV");

        addHoverEffect(otpButton);
        addHoverEffect(submitButton);
        addHoverEffect(exportCSVButton);

        otpCountdownLabel.setTextFill(Color.DARKRED);
        otpCountdownLabel.setFont(Font.font(14));

        ImageView qrView = new ImageView();
        try {
            Image qrImage = new Image(new FileInputStream("C:/Project/qr.jpg"));
            qrView.setImage(qrImage);
            qrView.setFitWidth(200);
            qrView.setPreserveRatio(true);
            qrView.setEffect(new DropShadow(5, Color.GRAY));
        } catch (Exception e) {
            System.out.println("QR image not found.");
        }

        transactionTable = new TableView<>();
        transactionTable.setPlaceholder(new Label("No transactions yet."));
        transactionTable.getColumns().addAll(
            createColumn("Amount", "amount"),
            createColumn("Mobile", "mobile"),
            createColumn("UPI", "upi"),
            createColumn("Location", "location"),
            createColumn("Type", "type"),
            createColumn("Risk Level", "riskLevel")
        );

        transactionTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Transaction item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) setStyle("");
                else switch (item.getRiskLevel()) {
                    case "Low" -> setStyle("-fx-background-color: #d4edda;");
                    case "Medium" -> setStyle("-fx-background-color: #fff3cd;");
                    case "High" -> setStyle("-fx-background-color: #f8d7da;");
                    default -> setStyle("");
                }
            }
        });

        TextField filterField = new TextField();
        filterField.setPromptText("Search Transactions...");
        FilteredList<Transaction> filteredData = new FilteredList<>(transactionTable.getItems(), p -> true);
        filterField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal.toLowerCase();
            filteredData.setPredicate(tx -> tx.getAmount().toLowerCase().contains(filter) ||
                tx.getMobile().toLowerCase().contains(filter) ||
                tx.getUpi().toLowerCase().contains(filter) ||
                tx.getLocation().toLowerCase().contains(filter) ||
                tx.getType().toLowerCase().contains(filter) ||
                tx.getRiskLevel().toLowerCase().contains(filter));
            transactionTable.setItems(filteredData);
        });

        otpButton.setOnAction(e -> verifyOTP(stage));
        submitButton.setOnAction(e -> submitTransaction(stage));
        exportCSVButton.setOnAction(e -> exportToCSV(stage));

        VBox formBox = new VBox(10, heading, amountField, mobileField, upiField, locationField, typeBox,
                new HBox(10, otpButton, otpCountdownLabel), submitButton, qrView);
        formBox.setPadding(new Insets(10));
        formBox.setStyle("-fx-background-color: white; -fx-border-radius: 8; -fx-background-radius: 8;");

        VBox tableBox = new VBox(10, filterField, transactionTable, exportCSVButton);
        tableBox.setPadding(new Insets(10));
        tableBox.setStyle("-fx-background-color: white; -fx-border-radius: 8; -fx-background-radius: 8;");

        locationTransitionLabel.setFont(Font.font(18));
        locationTransitionLabel.setTextFill(Color.DARKMAGENTA);
        HBox locationBox = new HBox(locationTransitionLabel);
        locationBox.setAlignment(Pos.CENTER_LEFT);

        // Bar chart setup
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Risk Level");
        yAxis.setLabel("Count");
        riskBarChart = new BarChart<>(xAxis, yAxis);
        riskBarChart.setTitle("Risk Analysis");
        riskBarChart.setLegendVisible(false);
        riskBarChart.setPrefHeight(300);

        riskData = new XYChart.Series<>();
        riskData.getData().add(new XYChart.Data<>("Low", 0));
        riskData.getData().add(new XYChart.Data<>("Medium", 0));
        riskData.getData().add(new XYChart.Data<>("High", 0));
        riskBarChart.getData().add(riskData);

        VBox mainContent = new VBox(15, formBox, locationBox, tableBox, riskBarChart);
        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);

        Scene scene = new Scene(scrollPane, 750, 750);
        stage.setTitle("Fraud Checker - Dashboard");
        stage.setScene(scene);
        stage.show();

        amountField.textProperty().addListener((obs, oldV, newV) -> validateForm());
        mobileField.textProperty().addListener((obs, oldV, newV) -> validateForm());
        upiField.textProperty().addListener((obs, oldV, newV) -> validateForm());
        locationField.textProperty().addListener((obs, oldV, newV) -> validateForm());
        typeBox.valueProperty().addListener((obs, oldV, newV) -> validateForm());
    }

    private TableColumn<Transaction, String> createColumn(String title, String property) {
        TableColumn<Transaction, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        return col;
    }

    private void validateForm() {
        boolean validAmount = amountField.getText().matches("\\d+(\\.\\d{1,2})?");
        boolean validMobile = mobileField.getText().matches("\\d{10}");
        boolean validUpi = !upiField.getText().trim().isEmpty();
        boolean validLocation = !locationField.getText().trim().isEmpty();
        boolean validType = typeBox.getValue() != null;
        submitButton.setDisable(!(validAmount && validMobile && validUpi && validLocation && validType && otpVerified));
    }

    private void verifyOTP(Stage stage) {
        generatedOTP = String.format("%06d", new Random().nextInt(999999));
        System.out.println("Generated OTP: " + generatedOTP);
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("OTP Verification");
        dialog.setHeaderText("Enter the OTP sent to your mobile");
        dialog.setContentText("OTP:");
        startOtpCountdown();
        Optional<String> result = dialog.showAndWait();
        if (otpTimer != null) otpTimer.stop();
        otpCountdownLabel.setText("");
        result.ifPresent(input -> {
            if (input.equals(generatedOTP)) {
                showToast(stage, "OTP Verified Successfully!");
                otpVerified = true;
            } else {
                showToast(stage, "Invalid OTP! Please try again.");
                otpVerified = false;
            }
            validateForm();
        });
    }

    private void startOtpCountdown() {
        final int[] seconds = {60};
        otpCountdownLabel.setText("OTP expires in 60s");
        otpTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            seconds[0]--;
            otpCountdownLabel.setText("OTP expires in " + seconds[0] + "s");
            if (seconds[0] <= 0) {
                otpCountdownLabel.setText("OTP expired, please resend.");
                otpTimer.stop();
                otpVerified = false;
                validateForm();
            }
        }));
        otpTimer.setCycleCount(60);
        otpTimer.play();
    }

    private void submitTransaction(Stage stage) {
        double amount = Double.parseDouble(amountField.getText());
        String risk = amount > 1000 ? "High" : amount > 500 ? "Medium" : "Low";

        Transaction tx = new Transaction(
                amountField.getText(),
                mobileField.getText(),
                upiField.getText(),
                locationField.getText(),
                typeBox.getValue(),
                risk
        );
        transactionTable.getItems().add(tx);
        locationTransitionLabel.setText("📍 " + locationField.getText());

        for (XYChart.Data<String, Number> data : riskData.getData()) {
            if (data.getXValue().equals(risk)) {
                data.setYValue(data.getYValue().intValue() + 1);
                break;
            }
        }

        showToast(stage, "Transaction submitted!");
        clearForm();
        otpVerified = false;
        validateForm();
    }

    private void clearForm() {
        amountField.clear();
        mobileField.clear();
        upiField.clear();
        locationField.clear();
        typeBox.setValue(null);
    }

    private void exportToCSV(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Transaction History");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("Amount,Mobile,UPI,Location,Type,RiskLevel,ExportTimestamp");
                String now = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now());
                for (Transaction tx : transactionTable.getItems()) {
                    writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                            tx.getAmount(), tx.getMobile(), tx.getUpi(),
                            tx.getLocation(), tx.getType(), tx.getRiskLevel(), now);
                }
                showToast(stage, "CSV Exported Successfully.");
            } catch (Exception e) {
                showToast(stage, "Could not export CSV.");
            }
        }
    }

    private void showToast(Stage ownerStage, String message) {
        Popup popup = new Popup();
        Label label = new Label(message);
        label.setStyle("-fx-background-color: #333333cc; -fx-text-fill: white; -fx-padding: 10; -fx-background-radius: 5;");
        popup.getContent().add(label);
        popup.setAutoHide(true);
        popup.show(ownerStage);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), label);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
        PauseTransition pt = new PauseTransition(Duration.seconds(2));
        pt.setOnFinished(e -> popup.hide());
        pt.play();
    }

    private void addHoverEffect(Button button) {
        button.setStyle("-fx-background-color: #ff4081; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #e040fb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: #ff4081; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;"));
    }

    private String getPasswordStrength(String password) {
        if (password.length() < 6) return "Weak password";
        if (password.matches(".\\d.") && password.matches(".[A-Z].") && password.length() >= 8) return "Strong password";
        if (password.length() >= 6) return "Moderate password";
        return "Weak password";
    }

    public static class Transaction {
        private final SimpleStringProperty amount, mobile, upi, location, type, riskLevel;
        public Transaction(String amount, String mobile, String upi, String location, String type, String riskLevel) {
            this.amount = new SimpleStringProperty(amount);
            this.mobile = new SimpleStringProperty(mobile);
            this.upi = new SimpleStringProperty(upi);
            this.location = new SimpleStringProperty(location);
            this.type = new SimpleStringProperty(type);
            this.riskLevel = new SimpleStringProperty(riskLevel);
        }
        public String getAmount() { return amount.get(); }
        public String getMobile() { return mobile.get(); }
        public String getUpi() { return upi.get(); }
        public String getLocation() { return location.get(); }
        public String getType() { return type.get(); }
        public String getRiskLevel() { return riskLevel.get(); }
    }
}
//to compile  javac --module-path javafx-sdk-21.0.7/lib --add-modules javafx.controls,javafx.fxml,javafx.media app/FraudCheckerWithHistory.java
//to run java --module-path javafx-sdk-21.0.7/lib --add-modules javafx.controls,javafx.fxml,javafx.media app.FraudCheckerWithHistory 
