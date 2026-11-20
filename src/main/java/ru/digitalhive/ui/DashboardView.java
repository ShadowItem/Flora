package ru.digitalhive.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import ru.digitalhive.agent.AgentRequest;
import ru.digitalhive.agent.AgentService;
import ru.digitalhive.agent.RiskItem;
import ru.digitalhive.model.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

final class DashboardView extends VBox {
    private final AppState state;
    private final AgentService agent;

    private final Label breadcrumb = new Label();
    private final Label title = new Label();
    private final Label statusChip = new Label();
    private final ComboBox<Apiary> apiaryBox = new ComboBox<>();
    private final ComboBox<Hive> hiveBox = new ComboBox<>();

    private final MetricCard weightCard = new MetricCard("Вес улья");
    private final MetricCard tempCard = new MetricCard("Температура внутри улья");
    private final MetricCard humCard = new MetricCard("Влажность");
    private final MetricCard co2Card = new MetricCard("CO₂");

    private final TrendChart weightChart = new TrendChart(true, "weight-chart");
    private final TrendChart tempChart = new TrendChart(false, "temp-chart");
    private final BarChart<String, Number> deltaChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final RiskPanel risks;
    private final GridPane chartsGrid; // Вынесено в поле для анимации
    private boolean updating;

    DashboardView(AppState state, AgentService agent, Consumer<Scenario> onScenario) {
        this.state = state;
        this.agent = agent;
        this.risks = new RiskPanel(onScenario);
        this.chartsGrid = new GridPane();

        getStyleClass().add("dashboard");
        setSpacing(18);
        setPadding(new Insets(22, 28, 28, 28));

        breadcrumb.getStyleClass().add("breadcrumb");

        Button prev = roundButton("‹", -1);
        Button next = roundButton("›", 1);
        title.getStyleClass().add("page-title");
        statusChip.getStyleClass().add("status-chip");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        apiaryBox.setPromptText("Пасека");
        hiveBox.setPromptText("Улей");
        apiaryBox.setPrefWidth(190);
        hiveBox.setPrefWidth(130);
        apiaryBox.setOnAction(e -> { if (!updating && apiaryBox.getValue() != null) state.apiary.set(apiaryBox.getValue()); });
        hiveBox.setOnAction(e -> { if (!updating && hiveBox.getValue() != null) state.hive.set(hiveBox.getValue()); });
        HBox titleRow = new HBox(10, prev, title, next, statusChip, grow, apiaryBox, hiveBox);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup tg = new ToggleGroup();
        HBox chips = new HBox(6);
        for (Period p : Period.values()) {
            ToggleButton b = new ToggleButton(p.title);
            b.setToggleGroup(tg);
            b.setUserData(p);
            b.getStyleClass().add("chip-toggle");
            b.setSelected(p == state.period.get());
            chips.getChildren().add(b);
        }
        tg.selectedToggleProperty().addListener((o, a, n) -> {
            if (n == null) a.setSelected(true);
            else state.period.set((Period) n.getUserData());
        });
        HBox periodRow = new HBox(10, chips);
        periodRow.setAlignment(Pos.CENTER_LEFT);

        GridPane metrics = grid(4);
        metrics.add(weightCard, 0, 0);
        metrics.add(tempCard, 1, 0);
        metrics.add(humCard, 2, 0);
        metrics.add(co2Card, 3, 0);

        deltaChart.setLegendVisible(false);
        deltaChart.setAnimated(false);
        deltaChart.setBarGap(2);
        deltaChart.setCategoryGap(6);
        deltaChart.setMinHeight(210);
        deltaChart.setPrefHeight(230);
        deltaChart.getStyleClass().addAll("trend-chart", "delta-chart");

        chartsGrid.setHgap(16);
        chartsGrid.setVgap(16);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(60);
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(40);
        col2.setHgrow(Priority.ALWAYS);
        chartsGrid.getColumnConstraints().addAll(col1, col2);

        RowConstraints row1 = new RowConstraints();
        row1.setPercentHeight(50);
        row1.setVgrow(Priority.ALWAYS);
        RowConstraints row2 = new RowConstraints();
        row2.setPercentHeight(50);
        row2.setVgrow(Priority.ALWAYS);
        chartsGrid.getRowConstraints().addAll(row1, row2);

        chartsGrid.add(card("Масса улья", "кг", weightChart), 0, 0, 1, 2);
        chartsGrid.add(card("Температура внутри улья", "°C", tempChart), 1, 0);
        chartsGrid.add(card("Погода и медосбор", "индекс по экспертной формуле", risks), 1, 1);

        deltaChart.setVisible(false);
        deltaChart.setManaged(false);

        getChildren().addAll(breadcrumb, titleRow, periodRow, metrics, chartsGrid);

        state.apiaries.addListener((ListChangeListener<Apiary>) c -> sync());
        state.apiary.addListener((o, a, n) -> sync());
        state.hive.addListener((o, a, n) -> { sync(); refresh(); });
        state.period.addListener((o, a, n) -> refresh());
        sync();
        refresh();
    }

    private void sync() {
        updating = true;
        apiaryBox.getItems().setAll(state.apiaries);
        apiaryBox.setValue(state.apiary.get());
        Apiary a = state.apiary.get();
        hiveBox.getItems().setAll(a == null ? List.of() : a.hives());
        hiveBox.setValue(state.hive.get());
        updating = false;
    }

    private void refresh() {
        Hive h = state.hive.get();
        Apiary a = state.apiary.get();
        if (h == null || a == null) {
            title.setText("Нет данных");
            breadcrumb.setText("Мои ульи");
            statusChip.setVisible(false);
            statusChip.setManaged(false);
            clearAll();
            return;
        }
        Period p = state.period.get();
        title.setText(h.name());
        breadcrumb.setText("Мои ульи   /   " + a.name() + "   /   " + h.name());

        List<Reading> raw = state.readings();
        List<Reading> b = Series.bucketize(raw, p);
        if (b.isEmpty()) {
            clearAll();
            return;
        }

        // Анимация появления графиков
        FadeTransition ft = new FadeTransition(Duration.millis(400), chartsGrid);
        ft.setFromValue(0.3);
        ft.setToValue(1.0);
        ft.play();

        Reading last = raw.get(raw.size() - 1);
        Reading first = b.get(0), lastB = b.get(b.size() - 1);

        String deltaCaption = dynamicCaption(raw, p);
        weightCard.update(arr(b, Reading::weightKg), last.weightKg(), "кг", 1,
                lastB.weightKg() - first.weightKg(), deltaCaption, true, null, true);
        tempCard.update(arr(b, Reading::tempC), last.tempC(), "°C", 1,
                lastB.tempC() - first.tempC(), deltaCaption, false, null, true);
        humCard.update(arr(b, Reading::humidity), last.humidity(), "%", 0,
                lastB.humidity() - first.humidity(), deltaCaption, false, null, true);
        if (Double.isFinite(last.co2()) && last.co2() > 0) {
            co2Card.update(arr(b, Reading::co2), last.co2(), "ppm", 0,
                    lastB.co2() - first.co2(), deltaCaption, false, null, true);
        } else {
            co2Card.clear();
        }

        weightChart.update(b, p, Reading::weightKg, "Вес");
        tempChart.update(b, p, Reading::tempC, "Температура");
        updateDelta(b, p);

        AgentRequest req = new AgentRequest("", a, h, p, null);
        agent.assess(req).whenComplete((items, err) -> Platform.runLater(() -> {
            if (err != null || items == null || state.hive.get() != h) return;
            risks.update(items);
            applyStatus(items);
        }));
    }

    /** When the requested window exceeds the source table, label the real observed span honestly. */
    private static String dynamicCaption(List<Reading> readings, Period selected) {
        if (readings.size() < 2) return "по доступным измерениям";
        long observedHours = java.time.Duration.between(readings.get(0).time(),
                readings.get(readings.size() - 1).time()).toHours();
        long requestedHours = selected.days * 24L;
        return observedHours < requestedHours ? "за весь доступный период" : selected.caption;
    }

    private void updateDelta(List<Reading> b, Period p) {
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (int i = 1; i < b.size(); i++) {
            double v = b.get(i).weightKg() - b.get(i - 1).weightKg();
            XYChart.Data<String, Number> d = new XYChart.Data<>(p.label(b.get(i).time()), v);
            d.nodeProperty().addListener((o, old, node) -> {
                if (node != null) node.getStyleClass().add(v >= 0 ? "bar-pos" : "bar-neg");
            });
            s.getData().add(d);
        }
        deltaChart.getXAxis().setTickLabelsVisible(b.size() <= 13);
        deltaChart.getData().setAll(List.of(s));
    }

    private void applyStatus(List<RiskItem> items) {
        RiskItem weather = items.stream().filter(it -> it.scenario() == Scenario.WEATHER).findFirst().orElse(null);
        statusChip.getStyleClass().removeAll("status-ok", "status-warn", "status-bad");
        if (weather == null || !weather.hasData() || !Double.isFinite(weather.level())) {
            statusChip.setText("Погодная оценка недоступна");
            statusChip.getStyleClass().add("status-warn");
        } else {
            LocalDate date = state.hive.get().end().toLocalDate();
            String day = date.format(DateTimeFormatter.ofPattern("dd.MM"));
            double index = weather.level() * 5.0;
            if (index < 1.0) {
                statusChip.setText("Перенести работы · " + day);
                statusChip.getStyleClass().add("status-warn");
            } else if (index < 2.5) {
                statusChip.setText("Проверить прогноз утром · " + day);
                statusChip.getStyleClass().add("status-warn");
            } else {
                statusChip.setText("Плановый выезд · погоду проверить " + day);
                statusChip.getStyleClass().add("status-ok");
            }
        }
        statusChip.setVisible(true);
        statusChip.setManaged(true);
    }

    private void clearAll() {
        weightCard.clear(); tempCard.clear(); humCard.clear(); co2Card.clear();
        weightChart.clear(); tempChart.clear();
        deltaChart.getData().clear();
        risks.getChildren().clear();
    }

    private Button roundButton(String text, int delta) {
        Button b = new Button(text);
        b.getStyleClass().add("round-button");
        b.setOnAction(e -> state.shiftHive(delta));
        return b;
    }

    private static double[] arr(List<Reading> rs, ToDoubleFunction<Reading> f) {
        return rs.stream().mapToDouble(f).filter(Double::isFinite).toArray();
    }

    private static GridPane grid(int cols) {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(16);
        for (int i = 0; i < cols; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().add(cc);
        }
        return g;
    }

    private static VBox card(String title, String caption, Node content) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label c = new Label(caption);
        c.getStyleClass().add("card-caption");
        HBox head = new HBox(8, t, c);
        head.setAlignment(Pos.BASELINE_LEFT);
        VBox box = new VBox(10, head, content);
        box.getStyleClass().add("card");
        box.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }
}
