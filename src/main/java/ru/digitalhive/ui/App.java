package ru.digitalhive.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.digitalhive.agent.AgentService;
import ru.digitalhive.agent.NeuralAgentService;
import ru.digitalhive.data.DataRepository;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        AgentService agent = new NeuralAgentService();

        AppState state = new AppState();
        try {
            state.setData(DataRepository.loadBundledExpertData(), "таблица эксперта");
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось загрузить встроенную таблицу эксперта.", e);
        }

        Scene scene = new Scene(new MainView(state, agent), 1500, 920);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        stage.setTitle("Цифровой улей ИИ-помощник пчеловода");
        stage.setMinWidth(1240);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.setOnHidden(e -> agent.close());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

