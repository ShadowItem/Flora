package ru.digitalhive.ui;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import ru.digitalhive.agent.AgentRequest;
import ru.digitalhive.agent.AgentService;
import ru.digitalhive.model.Scenario;

/** Правая панель: диалог с ИИ-помощником обычными словами. */
public final class AssistantPanel extends VBox {
    private final AppState state;
    private final AgentService agent;
    private final VBox messages = new VBox(14);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();
    private final Button send = new Button("→");

    public AssistantPanel(AppState state, AgentService agent) {
        this.state = state;
        this.agent = agent;
        getStyleClass().add("assistant");
        setPrefWidth(390);
        setMinWidth(390);
        setMaxWidth(390);

        ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/images/apiary-mark.png")));
        logo.setFitWidth(30);
        logo.setFitHeight(25);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        logo.setAccessibleText("Логотип Flora");
        Label name = new Label("Flora");
        name.getStyleClass().add("assistant-title");
        Label sub = new Label("ИИ-помощник пчеловода · наблюдает за ульем и погодой");
        sub.getStyleClass().add("card-caption");
        HBox top = new HBox(8, logo, name);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(3, top, sub);
        header.getStyleClass().add("assistant-header");

        messages.setPadding(new Insets(16));
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("chat-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));

        Label hotWordsTitle = new Label("HOT WORDS");
        hotWordsTitle.getStyleClass().add("card-caption");
        hotWordsTitle.setPadding(new Insets(0, 16, 0, 16));

        FlowPane suggestions = new FlowPane(6, 6);
        suggestions.setPadding(new Insets(0, 16, 8, 16));
        for (String s : new String[]{"Вес улья", "Температура в улье", "Влажность", "CO₂",
                "Температура снаружи", "Ветер и дождь", "Погодный индекс", "Индекс счастья"}) {
            Button b = new Button(s);
            b.getStyleClass().add("suggest-chip");
            b.setOnAction(e -> ask("Объясни " + s, null));
            suggestions.getChildren().add(b);
        }

        input.setPromptText("Спросите о погоде или показателе…");
        input.getStyleClass().add("chat-input");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> ask(input.getText(), null));
        send.getStyleClass().add("send-button");
        send.setOnAction(e -> ask(input.getText(), null));
        HBox inputRow = new HBox(8, input, send);
        inputRow.setAlignment(Pos.CENTER);
        inputRow.setPadding(new Insets(0, 16, 8, 16));

        Label note = new Label("Погодный индекс — подсказка по условиям лёта и медосбора, а не гарантия взятка. Flora сравнивает погоду с динамикой датчиков, но не объявляет причину по одному изменению.");
        note.getStyleClass().add("disclaimer");
        note.setWrapText(true);
        note.setPadding(new Insets(0, 16, 14, 16));

        getChildren().addAll(header, scroll, hotWordsTitle, suggestions, inputRow, note);

        addAgent(bubble("Здравствуйте! Я Flora. Могу объяснить показатели улья, индекс здоровья пчёл и погодную оценку. "
                + "Выберите Hot Word или задайте свой вопрос.", null));
    }

    public void askScenario(Scenario s) {
        ask(s.prompt, s);
    }

    public void ask(String question, Scenario hint) {
        String text = question == null ? "" : question.trim();
        if (text.isEmpty()) return;
        if (state.hive.get() == null) {
            addAgent(bubble("Сначала загрузите данные и выберите улей.", null));
            return;
        }
        addUser(text);
        input.clear();
        setBusy(true);
        Label typing = new Label("Помощник анализирует данные…");
        typing.getStyleClass().add("typing");
        messages.getChildren().add(typing);

        AgentRequest req = new AgentRequest(text, state.apiary.get(), state.hive.get(), state.period.get(), hint);
        agent.ask(req).whenComplete((resp, err) -> Platform.runLater(() -> {
            messages.getChildren().remove(typing);
            setBusy(false);
            if (err != null || resp == null) {
                addAgent(bubble("Не удалось получить ответ. Попробуйте ещё раз.", null));
            } else {
                addAgent(bubble(resp.answer(), new ResponseCard(resp)));
            }
        }));
    }

    private void setBusy(boolean busy) {
        input.setDisable(busy);
        send.setDisable(busy);
        if (!busy) input.requestFocus();
    }

    private void addUser(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add("bubble-text");
        VBox b = new VBox(l);
        b.getStyleClass().add("bubble-user");
        b.setMaxWidth(290);
        HBox row = new HBox(b);
        row.setAlignment(Pos.CENTER_RIGHT);

        FadeTransition ft = new FadeTransition(Duration.millis(300), row);
        ft.setFromValue(0); ft.setToValue(1);
        ft.play();

        messages.getChildren().add(row);
    }

    private void addAgent(Node bubble) {
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);

        TranslateTransition tt = new TranslateTransition(Duration.millis(300), row);
        tt.setFromX(-20); tt.setToX(0);
        FadeTransition ft = new FadeTransition(Duration.millis(300), row);
        ft.setFromValue(0); ft.setToValue(1);

        tt.play(); ft.play();

        messages.getChildren().add(row);
    }

    private VBox bubble(String text, Node extra) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().add("bubble-text");
        VBox b = new VBox(10, l);
        if (extra != null) b.getChildren().add(extra);
        b.getStyleClass().add("bubble-agent");
        b.setMaxWidth(346);
        return b;
    }
}
