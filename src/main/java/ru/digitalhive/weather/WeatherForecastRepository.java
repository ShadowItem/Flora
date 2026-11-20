package ru.digitalhive.weather;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only weather CSV loader; no spreadsheet/source table is rewritten. */
public final class WeatherForecastRepository {
    private static final String RESOURCE = "/data/bashkiria_july_2026_weather.csv";
    private static final Path OVERRIDE = Path.of("data", "weather.csv");

    private WeatherForecastRepository() { }

    /** An optional data/weather.csv can replace the bundled July example without UI changes. */
    public static Map<LocalDate, WeatherIndexCalculator.WeatherDay> loadConfigured() throws IOException {
        if (Files.isRegularFile(OVERRIDE)) {
            try (InputStream in = Files.newInputStream(OVERRIDE)) {
                return parse(in, OVERRIDE.toString());
            }
        }
        return loadBundled();
    }

    public static Map<LocalDate, WeatherIndexCalculator.WeatherDay> loadBundled() throws IOException {
        InputStream stream = WeatherForecastRepository.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IOException("Встроенная погодная таблица не найдена: " + RESOURCE);
        try (InputStream in = stream) { return parse(in, RESOURCE); }
    }

    private static Map<LocalDate, WeatherIndexCalculator.WeatherDay> parse(InputStream stream, String source)
            throws IOException {
        Map<LocalDate, WeatherIndexCalculator.WeatherDay> data = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) throw new IOException(source + ": погодная таблица пуста.");
            List<String> names = split(header.replace("\uFEFF", ""));
            Map<String, Integer> col = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) col.put(names.get(i).trim().toLowerCase(Locale.ROOT), i);
            for (String required : List.of("date", "temp", "wet", "weather", "wind", "direction")) {
                if (!col.containsKey(required)) throw new IOException(source + ": нет колонки " + required);
            }
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                List<String> cells = split(line);
                try {
                    LocalDate date = LocalDate.parse(value(cells, col.get("date")));
                    double temp = number(value(cells, col.get("temp")));
                    double wet = number(value(cells, col.get("wet")));
                    String weather = value(cells, col.get("weather"));
                    double wind = number(value(cells, col.get("wind")));
                    String direction = value(cells, col.get("direction"));
                    if (data.put(date, new WeatherIndexCalculator.WeatherDay(temp, wet, weather, wind, direction)) != null) {
                        throw new IOException(source + ": повторная дата " + date);
                    }
                } catch (RuntimeException ex) {
                    throw new IOException(source + ": строка " + lineNo + ": " + ex.getMessage(), ex);
                }
            }
        }
        return Map.copyOf(data);
    }

    private static String value(List<String> cells, int index) {
        if (index < 0 || index >= cells.size() || cells.get(index).isBlank()) {
            throw new IllegalArgumentException("пустое значение; это не заменяется нулём");
        }
        return cells.get(index).trim();
    }

    private static double number(String text) {
        String normalized = text.trim().replace("\u00a0", "").replace(" ", "");
        if (normalized.contains(",") && !normalized.contains(".")) normalized = normalized.replace(',', '.');
        return Double.parseDouble(normalized);
    }

    private static List<String> split(String line) { return List.of(line.split(";", -1)); }
}
