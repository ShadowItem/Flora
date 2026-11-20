package ru.digitalhive.ui;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import ru.digitalhive.model.*;

import java.util.List;

/** Общее состояние интерфейса: выбранные пасека, улей и период. */
public final class AppState {
    public final ObservableList<Apiary> apiaries = FXCollections.observableArrayList();
    public final ObjectProperty<Apiary> apiary = new SimpleObjectProperty<>();
    public final ObjectProperty<Hive> hive = new SimpleObjectProperty<>();
    public final ObjectProperty<Period> period = new SimpleObjectProperty<>(Period.WEEK);
    public final StringProperty source = new SimpleStringProperty("таблица эксперта");

    public AppState() {
        apiary.addListener((o, a, n) -> hive.set(n == null || n.hives().isEmpty() ? null : n.hives().get(0)));
    }

    public void setData(List<Apiary> data, String sourceName) {
        apiary.set(null);
        apiaries.setAll(data);
        source.set(sourceName);
        apiary.set(data.isEmpty() ? null : data.get(0));
    }

    public void shiftHive(int delta) {
        Apiary a = apiary.get();
        Hive h = hive.get();
        if (a == null || h == null) return;
        int i = a.hives().indexOf(h) + delta;
        if (i >= 0 && i < a.hives().size()) hive.set(a.hives().get(i));
    }

    public List<Reading> readings() {
        Hive h = hive.get();
        return h == null ? List.of() : period.get().filter(h.readings());
    }
}

