package ru.digitalhive.model;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public final class Hive {
    private final String name;
    private final List<Reading> readings;

    public Hive(String name, List<Reading> readings) {
        this.name = name;
        this.readings = readings.stream().sorted(Comparator.comparing(Reading::time)).toList();
    }

    public String name() { return name; }

    /** Все измерения, отсортированные по времени. */
    public List<Reading> readings() { return readings; }

    public LocalDateTime end() {
        return readings.isEmpty() ? LocalDateTime.now() : readings.get(readings.size() - 1).time();
    }

    /** Измерения за последние N часов (от времени последнего измерения). */
    public List<Reading> lastHours(int hours) {
        LocalDateTime from = end().minusHours(hours);
        return readings.stream().filter(r -> r.time().isAfter(from)).toList();
    }

    @Override public String toString() { return name; }
}

