package ru.digitalhive.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ru.digitalhive.model.Fmt;

/** Карточка показателя: значение, изменение за период, статус и мини-график. */
final class MetricCard extends VBox {
    private final Label value = new Label("—");
    private final Label unit = new Label();
    private final Label delta = new Label();
    private final Label status = new Label();
    private final Sparkline spark = new Sparkline();

    MetricCard(String title) {
        getStyleClass().add("card");
        setSpacing(6);
        setMaxWidth(Double.MAX_VALUE);

        Region dot = new Region();
        dot.getStyleClass().add("metric-dot");
        dot.setMinSize(8, 8);
        dot.setMaxSize(8, 8);
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        status.getStyleClass().add("status-chip");
        status.setManaged(false);
        status.setVisible(false);
        HBox head = new HBox(7, dot, t, sp, status);
        head.setAlignment(Pos.CENTER_LEFT);

        value.getStyleClass().add("metric-value");
        unit.getStyleClass().add("metric-unit");
        HBox big = new HBox(6, value, unit);
        big.setAlignment(Pos.BASELINE_LEFT);
        delta.getStyleClass().add("metric-delta");

        getChildren().addAll(head, big, delta, spark);
    }

    void update(double[] series, double current, String unitText, int decimals,
                double change, String caption, boolean colorDelta, String statusText, boolean ok) {
        value.setText(Fmt.num(current, decimals));
        unit.setText(unitText);
        delta.setText(Fmt.signed(change, decimals) + " " + unitText + " " + caption);
        delta.getStyleClass().removeAll("delta-up", "delta-down");
        if (colorDelta && Math.abs(change) > 1e-9) delta.getStyleClass().add(change > 0 ? "delta-up" : "delta-down");
        status.getStyleClass().removeAll("status-ok", "status-bad");
        boolean show = statusText != null;
        status.setManaged(show);
        status.setVisible(show);
        if (show) {
            status.setText(statusText);
            status.getStyleClass().add(ok ? "status-ok" : "status-bad");
        }
        spark.setData(series);
    }

    void clear() {
        value.setText("—");
        unit.setText("");
        delta.setText("нет данных");
        delta.getStyleClass().removeAll("delta-up", "delta-down");
        status.setManaged(false);
        status.setVisible(false);
        spark.setData(new double[0]);
    }
}

