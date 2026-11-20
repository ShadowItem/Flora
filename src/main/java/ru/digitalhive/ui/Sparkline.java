package ru.digitalhive.ui;

import javafx.scene.layout.Pane;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;

import java.util.Arrays;

/** Мини-график без осей для карточек показателей. */
final class Sparkline extends Pane {
    private final Polyline line = new Polyline();
    private final Polygon area = new Polygon();
    private double[] data = new double[0];

    Sparkline() {
        line.getStyleClass().add("spark-line");
        area.getStyleClass().add("spark-area");
        line.setFill(null);
        line.setManaged(false);
        area.setManaged(false);
        getChildren().addAll(area, line);
        setMinHeight(52);
        setPrefHeight(52);
        setMaxHeight(52);
    }

    void setData(double[] values) {
        data = values;
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        line.getPoints().clear();
        area.getPoints().clear();
        if (data.length < 2 || w <= 0) return;
        double min = Arrays.stream(data).min().orElse(0);
        double max = Arrays.stream(data).max().orElse(1);
        double span = max - min < 1e-9 ? 1 : max - min;
        double pad = 4;
        for (int i = 0; i < data.length; i++) {
            double x = i * (w / (data.length - 1));
            double y = pad + (h - 2 * pad) * (1 - (data[i] - min) / span);
            line.getPoints().addAll(x, y);
            area.getPoints().addAll(x, y);
        }
        area.getPoints().addAll(w, h, 0.0, h);
    }
}

