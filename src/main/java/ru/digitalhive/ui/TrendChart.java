package ru.digitalhive.ui;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import ru.digitalhive.model.Period;
import ru.digitalhive.model.Reading;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** График показателя во времени (область или линия). */
final class TrendChart extends StackPane {
    private final NumberAxis x = new NumberAxis();
    private final NumberAxis y = new NumberAxis();
    private final XYChart<Number, Number> chart;

    TrendChart(boolean area, String styleClass) {
        x.setAutoRanging(false);
        x.setMinorTickVisible(false);
        y.setForceZeroInRange(false);
        y.setMinorTickVisible(false);
        if (area) {
            AreaChart<Number, Number> c = new AreaChart<>(x, y);
            c.setCreateSymbols(false);
            chart = c;
        } else {
            LineChart<Number, Number> c = new LineChart<>(x, y);
            c.setCreateSymbols(false);
            chart = c;
        }
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setMinHeight(210);
        chart.setPrefHeight(230);
        getStyleClass().addAll("trend-chart", styleClass);
        getChildren().add(chart);
    }

    void update(List<Reading> data, Period p, ToDoubleFunction<Reading> f, String name) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(name);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            s.getData().add(new XYChart.Data<>(i, f.applyAsDouble(data.get(i))));
            labels.add(p.label(data.get(i).time()));
        }
        int n = data.size();
        x.setLowerBound(0);
        x.setUpperBound(Math.max(1, n - 1));
        x.setTickUnit(Math.max(1, Math.ceil(n / 6.0)));
        x.setTickLabelFormatter(new StringConverter<Number>() {
            @Override public String toString(Number v) {
                int i = (int) Math.round(v.doubleValue());
                return i >= 0 && i < labels.size() ? labels.get(i) : "";
            }
            @Override public Number fromString(String s) { return 0; }
        });
        chart.getData().setAll(List.of(s));
    }

    void clear() {
        chart.getData().clear();
    }
}

