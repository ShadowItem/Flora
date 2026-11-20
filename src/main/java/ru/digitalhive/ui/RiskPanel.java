package ru.digitalhive.ui;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ru.digitalhive.agent.RiskItem;
import ru.digitalhive.model.Scenario;
import ru.digitalhive.model.Urgency;

import java.util.List;
import java.util.function.Consumer;

/** Панель «Оценка рисков»: по клику на строку помощнику уходит вопрос по сценарию. */
final class RiskPanel extends VBox {
    private final Consumer<Scenario> onClick;

    RiskPanel(Consumer<Scenario> onClick) {
        this.onClick = onClick;
        setSpacing(14);
        getStyleClass().add("risk-panel");
    }

    void update(List<RiskItem> items) {
        getChildren().clear();
        for (RiskItem it : items) {
            Label name = new Label(it.scenario().title);
            name.getStyleClass().add("risk-name");

            Label value = new Label(it.displayValue());
            value.getStyleClass().addAll("risk-pct", it.urgency().css);
            value.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            HBox head = new HBox(8, name, sp, value);
            head.setAlignment(Pos.CENTER_LEFT);

            ProgressBar bar = new ProgressBar(Double.isFinite(it.level()) ? it.level() : 0.0);
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.getStyleClass().add(it.urgency().css);
            bar.setVisible(it.showBar());
            bar.setManaged(it.showBar());

            Label note = new Label(it.note());
            note.getStyleClass().add("card-caption");
            note.setWrapText(true);

            VBox row = new VBox(6, head, bar, note);
            row.getStyleClass().add("risk-row");

            if (it.urgency() == Urgency.HIGH) {
                row.setStyle("-fx-border-color: -danger; -fx-border-width: 0 0 0 4; -fx-padding: 8 0 8 12;");
            } else {
                row.setStyle("-fx-padding: 8 0 8 16;");
            }

            row.setCursor(Cursor.HAND);
            row.setOnMouseClicked(e -> onClick.accept(it.scenario()));
            getChildren().add(row);
        }
    }
}
