package com.example.BuddyLink.Controller;

import com.example.BuddyLink.GlobalContainer;
import com.example.BuddyLink.Navigation;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;

public class TimeInputController {

    @FXML private TextField studyHours;
    @FXML private TextField studyMinutes;
    @FXML private TextField studySeconds;
    @FXML private TextField breakHours;
    @FXML private TextField breakMinutes;
    @FXML private TextField breakSeconds;
    @FXML private TextField numCycles;
    @FXML private Button startButton;

    @FXML
    public void handleStartTimer(ActionEvent event) {
        try {
            int studyH = Integer.parseInt(studyHours.getText());
            int studyM = Integer.parseInt(studyMinutes.getText());
            int studyS = Integer.parseInt(studySeconds.getText());
            int breakH = Integer.parseInt(breakHours.getText());
            int breakM = Integer.parseInt(breakMinutes.getText());
            int breakS = Integer.parseInt(breakSeconds.getText());
            int cycles = Integer.parseInt(numCycles.getText());

            long studyDuration = (studyH * 3600 + studyM * 60 + studyS);
            long breakDuration = (breakH * 3600 + breakM * 60 + breakS);

            if (studyDuration <= 0 || breakDuration <= 0 || cycles <= 0) {
                showError("Please enter valid durations and cycle count.");
                return;
            }

            // Pass data using GlobalContainer
            GlobalContainer.studyDuration = studyDuration;
            GlobalContainer.breakDuration = breakDuration;
            GlobalContainer.totalCycles = cycles;

            Navigation.goTo("timer.fxml", startButton);

        } catch (NumberFormatException ex) {
            showError("Please enter only numbers for time and cycles.");
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Invalid Input");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
