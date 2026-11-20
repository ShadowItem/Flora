package ru.digitalhive.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ru.digitalhive.agent.AgentResponse;
import ru.digitalhive.model.Urgency;

/** Ответ агента без отдельного блока служебных данных: рекомендация, обоснование и срочность. */
final class ResponseCard extends VBox {

    ResponseCard(AgentResponse r) {
        getStyleClass().add("response-card");
        setSpacing(12);

        String urgencyText = r.urgencyDetail() == null || r.urgencyDetail().isBlank()
                ? r.urgency().text : r.urgencyDetail();
        Label badge = new Label(urgencyText);
        badge.getStyleClass().addAll("urgency-badge", r.urgency().css);

        if (r.combinedAssessment() != null && !r.combinedAssessment().isBlank()) {
            getChildren().add(section("СОВОКУПНАЯ ОЦЕНКА", text(r.combinedAssessment(), "response-text-strong")));
        }
        getChildren().addAll(
                section("РЕКОМЕНДАЦИЯ", text(r.recommendation(), "response-text-strong")),
                section("ОБОСНОВАНИЕ", text(r.reasoning(), "response-text")));
        if (r.combinedAssessment() != null && !r.combinedAssessment().isBlank()
                && r.urgency() != Urgency.LOW) {
            getChildren().add(section("СРОЧНОСТЬ", badge));
        }
    }

    private static Label text(String s, String css) {
        Label l = new Label(s);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().add(css);
        return l;
    }

    private static VBox section(String title, Node body) {
        Region dot = new Region();
        dot.getStyleClass().add("section-bullet");
        dot.setMinSize(6, 6);
        dot.setPrefSize(6, 6);
        dot.setMaxSize(6, 6);

        Label t = new Label(title);
        t.getStyleClass().add("section-title");

        HBox head = new HBox(7, dot, t);
        head.setAlignment(Pos.CENTER_LEFT);

        if (title.equals("РЕКОМЕНДАЦИЯ")) {
            VBox box = new VBox(6, head, body);
            box.setStyle("-fx-padding: 8 0 0 0; -fx-border-color: -brand; -fx-border-width: 1 0 0 0;");
            return box;
        }

        return new VBox(5, head, body);
    }
}
