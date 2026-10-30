package com.premiumatm;

import com.premiumatm.database.Database;
import com.premiumatm.view.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    @Override public void start(Stage stage) throws Exception {
        Database.ensureDemoUser();
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/view/main.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 820);
        scene.getStylesheets().add(App.class.getResource("/css/theme.css").toExternalForm());
        stage.setTitle("AUREL — Premium ATM");
        stage.setMinWidth(1000); stage.setMinHeight(700);
        stage.setScene(scene); stage.show();
        MainController controller = loader.getController();
        controller.showWelcome();
    }
    public static void main(String[] args) { launch(args); }
}
