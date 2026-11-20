package ru.digitalhive.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Усреднение измерений по интервалам для графиков. */
public final class Series {
    private Series() {}

    public static List<Reading> bucketize(List<Reading> rs, Period p) {
        if (rs.isEmpty()) return List.of();
        LocalDateTime end = rs.get(rs.size() - 1).time();
        LocalDateTime start = p.start(end);
        int n = p.buckets();
        double[][] acc = new double[n][6];
        for (Reading r : rs) {
            long h = Duration.between(start, r.time()).toHours();
            if (h < 0) continue;
            int i = (int) Math.min(n - 1, h / p.bucketHours);
            acc[i][0] += r.weightKg();
            acc[i][1] += r.tempC();
            acc[i][2] += r.humidity();
            if (Double.isFinite(r.co2())) { acc[i][3] += r.co2(); acc[i][5]++; }
            acc[i][4]++;
        }
        List<Reading> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double c = acc[i][4];
            if (c == 0) continue;
            out.add(new Reading(start.plusHours((long) i * p.bucketHours),
                    acc[i][0] / c, acc[i][1] / c, acc[i][2] / c,
                    acc[i][5] == 0 ? Double.NaN : acc[i][3] / acc[i][5]));
        }
        return out;
    }
}

