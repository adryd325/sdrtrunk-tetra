package io.github.dsheirer.gui.instrument;

import javafx.application.Application;

// Workaround for JavaFX and IntelliJ being weird
public class LaunchDemodulatorViewerFX {
    public static void main(String[] args) {
        Application.launch(DemodulatorViewerFX.class);
    }
}
