package ru.digitalhive.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polygon;
import ru.digitalhive.model.Scenario;

import java.util.function.Consumer;

/** Левая панель: логотип, навигация, сценарии помощника, загрузка данных. */
final class Sidebar extends VBox {

    Sidebar(Runnable onLoad, Consumer<Scenario> onScenario) {
        getStyleClass().add("sidebar");
        setPrefWidth(224);
        setMinWidth(224);
        setMaxWidth(224);
        setSpacing(2);

        Polygon hex = new Polygon(11, 0, 20.5, 5.5, 20.5, 16.5, 11, 22, 1.5, 16.5, 1.5, 5.5);
        hex.getStyleClass().add("logo-hex");
        Label brand = new Label("Цифровой улей");
        brand.getStyleClass().add("brand-title");
        HBox logo = new HBox(10, hex, brand);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.setPadding(new Insets(2, 8, 16, 8));
        getChildren().add(logo);

        getChildren().add(section("МОИ УЛЬИ"));
        getChildren().add(nav("Сводка по улью", true, null));

        getChildren().add(section("СЦЕНАРИИ ПОМОЩНИКА"));
        Scenario weather = Scenario.WEATHER;
        getChildren().add(nav(weather.title, false, () -> onScenario.accept(weather)));

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        getChildren().add(grow);

        Button load = new Button("Загрузить данные");
        load.getStyleClass().add("primary-button");
        load.setMaxWidth(Double.MAX_VALUE);
        load.setOnAction(e -> onLoad.run());
        Label hint = new Label("CSV-таблица с показаниями датчиков");
        hint.getStyleClass().add("card-caption");
        hint.setPadding(new Insets(6, 4, 14, 4));
        getChildren().addAll(load, hint);

        Label avatar = new Label("U");
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(34, 34);
        avatar.setAlignment(Pos.CENTER);
        Label who = new Label("User");
        who.getStyleClass().add("card-title");
        Label mode = new Label("Локальный режим");
        mode.getStyleClass().add("card-caption");
        HBox user = new HBox(10, avatar, new VBox(1, who, mode));
        user.setAlignment(Pos.CENTER_LEFT);
        user.getStyleClass().add("user-row");
        getChildren().add(user);
    }

    private static Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("nav-section");
        return l;
    }

    private static Button nav(String text, boolean active, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("nav-item");
        if (active) b.getStyleClass().add("nav-item-active");
        b.setMaxWidth(Double.MAX_VALUE);
        if (action != null) b.setOnAction(e -> action.run());
        return b;
    }
}

