package com.example.BuddyLink.Controller;

import com.example.BuddyLink.GlobalContainer;
import com.example.BuddyLink.Navigation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class TimerController {

    @FXML private Label timerLabel;
    @FXML private Label cycleLabel;
    @FXML private Button quitButton;
    @FXML private Label statusLabel;

    private long studyDuration;
    private long breakDuration;
    private int totalCycles;
    private int currentCycle = 0;

    private Timeline timeline;
    private boolean onStudy = true;
    private long timeLeft;

    @FXML
    public void initialize() {

        studyDuration = GlobalContainer.studyDuration;
        breakDuration = GlobalContainer.breakDuration;
        totalCycles = GlobalContainer.totalCycles;

        currentCycle = 0;
        startStudy();
    }

    @FXML
    public void quit(){
        Navigation.goTo("main.fxml", quitButton);
    }

    private void startStudy() {
        currentCycle++;
        timeLeft = studyDuration;
        onStudy = true;
        statusLabel.setText("Studying");
        cycleLabel.setText("Cycle " + currentCycle + " / " + totalCycles);
        startTimer();
    }

    private void startBreak() {
        timeLeft = breakDuration;
        onStudy = false;
        statusLabel.setText("Break");
        startTimer();
    }

    private void startTimer() {
        if (timeline != null) timeline.stop();

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (timeLeft <= 0) {
                timeline.stop();
                if (onStudy) {
                    startBreak();
                } else {
                    if (currentCycle < totalCycles) {
                        startStudy();
                    } else {
                        timerLabel.setText("Done!");
                        statusLabel.setText("");
                    }
                }
            } else {
                timeLeft--;
                timerLabel.setText(formatTime(timeLeft));
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%02d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }
}
