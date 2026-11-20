package ru.digitalhive.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Период отображения. bucketHours — шаг усреднения для графиков. */
public enum Period {
    DAY("Сутки", "за сутки", 1, 1),
    THREE_DAYS("3 дня", "за 3 дня", 3, 3),
    WEEK("Неделя", "за неделю", 7, 24),
    MONTH("Месяц", "за месяц", 30, 24);

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter DMH = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    public final String title;
    public final String caption;
    public final int days;
    public final int bucketHours;

    Period(String title, String caption, int days, int bucketHours) {
        this.title = title;
        this.caption = caption;
        this.days = days;
        this.bucketHours = bucketHours;
    }

    public LocalDateTime start(LocalDateTime end) { return end.minusDays(days); }

    public int buckets() { return days * 24 / bucketHours; }

    public List<Reading> filter(List<Reading> all) {
        if (all.isEmpty()) return List.of();
        LocalDateTime from = start(all.get(all.size() - 1).time());
        return all.stream().filter(r -> !r.time().isBefore(from)).toList();
    }

    public String label(LocalDateTime t) {
        if (bucketHours >= 24) return DM.format(t);
        return days <= 1 ? HM.format(t) : DMH.format(t);
    }
}

