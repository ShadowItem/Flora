package ru.digitalhive.ui;

import javafx.application.Application;

/**
 * Plain Java entry point for the packaged classpath application.
 * Keeping the launcher separate from Application avoids the JavaFX module-path
 * check performed by the Java command launcher for Application subclasses.
 */
public final class Launcher {
    private Launcher() { }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
