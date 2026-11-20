package ru.digitalhive.weather;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Реализация формулы листа «Расчет_индекса» из погодной книги.
 * Это отдельный индекс погодных условий для лёта/медосбора; он НЕ является Health_Index.
 * Пороговые параметры формулы относятся к июльским ориентирам Башкирии.
 */
public final class WeatherIndexCalculator {
    private static final double W_TEMP = 0.40;
    private static final double W_HUMIDITY = 0.30;
    private static final double W_WIND = 0.20;
    private static final double W_DIRECTION = 0.10;

    private WeatherIndexCalculator() { }

    /** Возвращает empty, если на дату нет погодной записи: индекс не подменяется нулём. */
    public static Optional<Result> calculate(LocalDate date, Map<LocalDate, WeatherDay> forecast) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(forecast, "forecast");
        WeatherDay today = forecast.get(date);
        if (today == null) return Optional.empty();

        double temp = today.tempC();
        double humidity = today.humidityPercent();
        double wind = today.windMps();
        double gate = (temp < 10.0 || wind >= 7.0) ? 0.0 : 1.0;

        double sTemp = tempScore(temp);
        double sHumidity = humidityScore(humidity);
        double sWind = windScore(wind);
        double sDirection = directionScore(today.windDirection());
        double weatherCoefficient = weatherCoefficient(today.condition());
        double rainBonus = delayedRainBonus(date, today, forecast);
        double heatDryPenalty = temp > 30.0 && humidity < 50.0 ? 0.35 : 0.0;

        // Decimal arithmetic at the final stage matches Excel ROUND(..., 2),
        // including exact half-cent cases such as 3.245 -> 3.25.
        BigDecimal weighted = BigDecimal.valueOf(W_TEMP).multiply(decimalComponent(sTemp))
                .add(BigDecimal.valueOf(W_HUMIDITY).multiply(decimalComponent(sHumidity)))
                .add(BigDecimal.valueOf(W_WIND).multiply(decimalComponent(sWind)))
                .add(BigDecimal.valueOf(W_DIRECTION).multiply(decimalComponent(sDirection)));
        BigDecimal raw = weighted.multiply(BigDecimal.valueOf(5.0))
                .multiply(BigDecimal.valueOf(weatherCoefficient))
                .add(BigDecimal.valueOf(rainBonus)).subtract(BigDecimal.valueOf(heatDryPenalty));
        if (gate == 0.0) raw = BigDecimal.ZERO;
        double index = raw.max(BigDecimal.ZERO).min(BigDecimal.valueOf(5.0))
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
        boolean julyReferenceForAugust = date.getYear() == 2026 && date.getMonthValue() == 8
                && date.getDayOfMonth() <= 3;
        boolean monthConfirmed = date.getYear() == 2026
                && (date.getMonthValue() == 7 || julyReferenceForAugust);

        List<String> factors = new ArrayList<>();
        if (gate == 0.0) factors.add(temp < 10.0
                ? "Температура ниже 10 °C: используемая формула относит условия к ограничивающим лёт."
                : "Ветер 7 м/с или сильнее: используемая формула относит условия к ограничивающим лёт.");
        if (rainBonus > 0) factors.add("Формула учитывает недавний слабый или средний дождь за 2–3 дня до этой даты.");
        if (heatDryPenalty > 0) factors.add("Сочетание температуры выше 30 °C и влажности ниже 50% снижает оценку по формуле.");
        if (weatherCoefficient <= 0.15) factors.add("Указанное погодное явление заметно снижает оценку по формуле.");
        if (!monthConfirmed) factors.add("Применимость июльских параметров к этому месяцу не подтверждена.");
        if (julyReferenceForAugust) factors.add("Для 1–3 августа июльские нормы использованы только как временный ориентир.");
        if (factors.isEmpty()) factors.add("По используемой формуле отдельных погодных ограничений для этой даты не выделено.");

        return Optional.of(new Result(index, category(index), List.copyOf(factors), monthConfirmed,
                julyReferenceForAugust, sTemp, sHumidity, sWind, sDirection,
                weatherCoefficient, rainBonus, heatDryPenalty, gate));
    }

    private static double tempScore(double t) {
        if (t < 10.0) return 0.0;
        if (t < 20.0) return (t - 10.0) / (20.0 - 10.0);
        if (t <= 27.0) return 1.0;
        if (t <= 30.0) return 1.0 - 0.08 * (t - 27.0);
        return Math.max(0.0, 0.76 - 0.10 * (t - 30.0));
    }

    private static double humidityScore(double h) {
        if (h >= 65.0 && h <= 78.0) return 1.0;
        if (h < 65.0) return Math.max(0.0, 1.0 - (65.0 - h) / 40.0);
        return Math.max(0.0, 1.0 - (h - 78.0) / 22.0);
    }

    private static double windScore(double v) {
        if (v >= 7.0) return 0.0;
        if (v <= 2.0) return 1.0;
        if (v <= 5.0) return 1.0 - 0.12 * (v - 2.0);
        return 0.64 * (7.0 - v) / (7.0 - 5.0);
    }

    private static double directionScore(String raw) {
        return switch (normalize(raw)) {
            case "З", "ЮЗ", "СЗ" -> 1.0;
            case "В" -> 0.75;
            case "Ю" -> 0.70;
            case "ЮВ" -> 0.60;
            case "С", "СВ" -> 0.50;
            default -> 0.70; // Excel IFERROR(VLOOKUP(...), 0.7)
        };
    }

    private static double weatherCoefficient(String raw) {
        return switch (normalize(raw)) {
            case "СОЛНЕЧНО" -> 1.00;
            case "ОБЛАЧНО" -> 0.90;
            case "СЛАБЫЙ" -> 0.65;
            case "СРЕДНИЙ" -> 0.40;
            case "ГРОЗА" -> 0.15;
            default -> 0.50; // Excel IFERROR(VLOOKUP(...), 0.5)
        };
    }

    /** Бонус применяется к солнечному/облачному дню через 2 или 3 дня после слабого/среднего дождя. */
    private static double delayedRainBonus(LocalDate date, WeatherDay today,
                                           Map<LocalDate, WeatherDay> forecast) {
        String condition = normalize(today.condition());
        boolean clearOrCloudy = condition.equals("СОЛНЕЧНО") || condition.equals("ОБЛАЧНО");
        if (!clearOrCloudy) return 0.0;
        for (int daysAgo : new int[]{2, 3}) {
            WeatherDay previous = forecast.get(date.minusDays(daysAgo));
            if (previous == null) continue;
            String prior = normalize(previous.condition());
            if (prior.equals("СЛАБЫЙ") || prior.equals("СРЕДНИЙ")) return 0.25;
        }
        return 0.0;
    }

    private static String category(double value) {
        if (value >= 4.0) return "Высокая погодная оценка";
        if (value >= 2.5) return "Умеренная погодная оценка";
        if (value >= 1.0) return "Низкая погодная оценка";
        return "Очень низкая оценка; условия могут ограничивать лёт";
    }

    private static BigDecimal decimalComponent(double value) {
        // Removes binary artefacts from formula sub-scores (e.g. 0.6599999999999999).
        return BigDecimal.valueOf(value).setScale(12, RoundingMode.HALF_UP);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('Ё', 'Е');
    }

    /** Одна погодная запись. Нет данных — это отсутствие записи, а не нулевое измерение. */
    public record WeatherDay(double tempC, double humidityPercent, String condition,
                             double windMps, String windDirection) {
        public WeatherDay {
            if (!Double.isFinite(tempC) || !Double.isFinite(humidityPercent) || !Double.isFinite(windMps)) {
                throw new IllegalArgumentException("Погодные числовые поля должны быть конечными значениями.");
            }
            if (condition == null || condition.isBlank() || windDirection == null || windDirection.isBlank()) {
                throw new IllegalArgumentException("Тип погоды и направление ветра обязательны.");
            }
        }
    }

    public record Result(double index, String category, List<String> factors,
                         boolean monthSupported, boolean julyNormsTemporarilyUsed,
                         double temperatureScore, double humidityScore, double windScore,
                         double directionScore, double weatherCoefficient, double rainBonus,
                         double heatDryPenalty, double flightGate) { }
}
