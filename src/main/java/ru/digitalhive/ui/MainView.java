package ru.digitalhive.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import ru.digitalhive.agent.AgentService;
import ru.digitalhive.data.DataRepository;

import java.io.File;

public final class MainView extends BorderPane {
    private final AppState state;

    public MainView(AppState state, AgentService agent) {
        this.state = state;
        AssistantPanel assistant = new AssistantPanel(state, agent);
        DashboardView dashboard = new DashboardView(state, agent, assistant::askScenario);
        Sidebar sidebar = new Sidebar(this::chooseFile, assistant::askScenario);

        ScrollPane center = new ScrollPane(dashboard);
        center.setFitToWidth(true);
        center.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        center.getStyleClass().add("main-scroll");

        setLeft(sidebar);
        setCenter(center);
        setRight(assistant);
    }

    private void chooseFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите файл с данными улья");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Таблица CSV", "*.csv", "*.txt"));
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            state.setData(DataRepository.loadCsv(f.toPath()), f.getName());
        } catch (Exception ignored) {
            Alert a = new Alert(Alert.AlertType.ERROR,
                    "Не удалось прочитать таблицу. Проверьте заголовки, разделитель и формат дат/чисел, затем попробуйте снова.");
            a.setHeaderText("Данные не загружены");
            a.initOwner(getScene().getWindow());

            // Делаем окно ошибки темным
            a.getDialogPane().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            a.showAndWait();
        }
    }
}
