package ru.digitalhive.data;

import ru.digitalhive.model.Apiary;
import ru.digitalhive.model.Hive;
import ru.digitalhive.model.Reading;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class DataRepository {
    private DataRepository() { }

    /** Supports the app's historic CSV schema and the supplied sensor/Health_Index table. */
    public static List<Apiary> loadCsv(Path file) throws IOException {
        return parseCsv(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    /** Load the bundled expert table using the same parser as user-imported CSV files. */
    public static List<Apiary> loadBundledExpertData() throws IOException {
        InputStream stream = DataRepository.class.getResourceAsStream("/data/expert_bee_data.csv");
        if (stream == null) throw new IOException("Не найдена встроенная таблица эксперта.");
        try (InputStream in = stream) {
            return parseCsv(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        }
    }

    private static List<Apiary> parseCsv(List<String> lines) {
        if (lines.size() < 2) throw new IllegalArgumentException("Файл пуст или содержит только заголовок.");
        String headerLine = lines.get(0).replace("\uFEFF", "");
        String sep = countOutsideQuotes(headerLine, ';') > countOutsideQuotes(headerLine, ',') ? ";" : ",";
        List<String> headers = splitCsv(headerLine, sep.charAt(0)).stream()
                .map(DataRepository::norm).toList();
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) columns.putIfAbsent(headers.get(i), i);

        int apiaryCol = find(columns, "apiary", "пасека");
        int hiveCol = find(columns, "hive", "hive_id", "улей");
        int timeCol = find(columns, "timestamp", "date_time", "datetime", "time", "дата_время");
        int weightCol = find(columns, "weight_kg", "weight", "вес_кг");
        int tempCol = find(columns, "temp_c", "temp_in_c", "temperature", "температура");
        int humidityCol = find(columns, "humidity", "humidity_in_p", "humidity_in", "влажность");
        int co2Col = find(columns, "co2_ppm", "co2");
        int extTempCol = find(columns, "ext_temp_c", "external_temp_c", "температура_снаружи");
        int pesticideCol = find(columns, "pesticide_risk", "риск_пестицидов");

        if (hiveCol < 0 || timeCol < 0 || weightCol < 0 || tempCol < 0 || humidityCol < 0) {
            throw new IllegalArgumentException("Нужны столбцы улей, дата/время, Weight_kg, Temp_In_C и Humidity_In_P. "
                    + "Опционально: CO2_ppm, Ext_Temp_C, Pesticide_Risk.");
        }

        Map<String, Map<String, List<Reading>>> tree = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            List<String> c = splitCsv(line, sep.charAt(0));
            try {
                String hiveValue = cell(c, hiveCol).trim();
                String apiary = apiaryCol < 0 || cell(c, apiaryCol).isBlank()
                        ? "№1" : cell(c, apiaryCol).trim();
                LocalDateTime time = parseDateTime(cell(c, timeCol));
                double weight = requiredNum(c, weightCol, "Weight_kg");
                double temp = requiredNum(c, tempCol, "Temp_In_C");
                double humidity = requiredNum(c, humidityCol, "Humidity_In_P");
                double co2 = optionalNum(c, co2Col, Double.NaN);
                Double extTemp = optionalNullableNum(c, extTempCol);
                Double pesticideRisk = optionalNullableNum(c, pesticideCol);
                Reading reading = new Reading(time, weight, temp, humidity, co2, extTemp, pesticideRisk);
                String hive = hiveValue.matches("\\d+") ? "Улей №" + hiveValue : hiveValue;
                tree.computeIfAbsent(apiary, k -> new LinkedHashMap<>())
                        .computeIfAbsent(hive, k -> new ArrayList<>()).add(reading);
            } catch (DateTimeParseException | NumberFormatException ex) {
                throw new IllegalArgumentException("Строка " + (i + 1) + ": не удалось прочитать значения: "
                        + ex.getMessage());
            }
        }
        if (tree.isEmpty()) throw new IllegalArgumentException("В CSV нет строк с измерениями.");
        List<Apiary> result = new ArrayList<>();
        tree.forEach((apiary, hives) -> {
            List<Hive> hs = new ArrayList<>();
            hives.forEach((name, rs) -> hs.add(new Hive(name, rs)));
            result.add(new Apiary(apiary, hs));
        });
        return result;
    }

    private static int find(Map<String, Integer> columns, String... names) {
        for (String name : names) if (columns.containsKey(name)) return columns.get(name);
        return -1;
    }

    private static String norm(String text) {
        return text == null ? "" : text.trim().replace("\uFEFF", "").toLowerCase(Locale.ROOT);
    }

    private static String cell(List<String> values, int col) {
        return col < 0 || col >= values.size() ? "" : values.get(col).trim();
    }

    private static double requiredNum(List<String> values, int col, String name) {
        String raw = cell(values, col);
        if (raw.isEmpty()) throw new NumberFormatException("пустое обязательное поле " + name);
        return num(raw);
    }

    private static double optionalNum(List<String> values, int col, double missing) {
        if (col < 0 || cell(values, col).isEmpty()) return missing;
        return num(cell(values, col));
    }

    private static Double optionalNullableNum(List<String> values, int col) {
        if (col < 0 || cell(values, col).isEmpty()) return null;
        return num(cell(values, col));
    }

    private static double num(String text) {
        String normalized = text.trim().replace("\u00a0", "").replace(" ", "");
        if (normalized.contains(",") && !normalized.contains(".")) normalized = normalized.replace(',', '.');
        return Double.parseDouble(normalized);
    }

    private static LocalDateTime parseDateTime(String text) {
        String value = text.trim();
        try { return LocalDateTime.parse(value.replace(' ', 'T')); }
        catch (DateTimeParseException ignored) { }
        for (String pattern : List.of("yyyy-MM-dd H:mm", "yyyy-MM-dd HH:mm",
                "d.M.yyyy H:mm", "d.M.yyyy HH:mm", "M/d/yy H:mm", "M/d/yy HH:mm")) {
            try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.ROOT)); }
            catch (DateTimeParseException ignored) { }
        }
        throw new DateTimeParseException("неподдерживаемый формат Date_Time", value, 0);
    }

    private static int countOutsideQuotes(String value, char target) {
        int count = 0;
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '"') i++;
                else quoted = !quoted;
            } else if (ch == target && !quoted) count++;
        }
        return count;
    }

    private static List<String> splitCsv(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"'); i++;
                } else quoted = !quoted;
            } else if (ch == delimiter && !quoted) {
                values.add(cell.toString()); cell.setLength(0);
            } else cell.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("Незакрытая кавычка в CSV.");
        values.add(cell.toString());
        return values;
    }

    public static List<Apiary> demo() {
        Random rnd = new Random(7);
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        Apiary a = new Apiary("Пасека «Липовая»", List.of(
                hive(rnd, end, "1", false), hive(rnd, end, "4", false),
                hive(rnd, end, "7", false), hive(rnd, end, "12", true)));
        Apiary b = new Apiary("Пасека «Заречная»", List.of(
                hive(rnd, end, "2", false), hive(rnd, end, "5", false), hive(rnd, end, "9", false)));
        return List.of(a, b);
    }

    private static Hive hive(Random rnd, LocalDateTime end, String no, boolean swarming) {
        int hours = 30 * 24;
        LocalDateTime start = end.minusHours(hours - 1);
        double w = 38 + rnd.nextDouble() * 8;
        List<Reading> list = new ArrayList<>(hours);
        for (int i = 0; i < hours; i++) {
            LocalDateTime t = start.plusHours(i);
            int h = t.getHour();
            double rhythm = (h >= 8 && h <= 17) ? -0.05 : (h >= 18 && h <= 21 ? 0.12 : 0.01);
            w += rhythm + 0.004 + (rnd.nextDouble() - 0.5) * 0.03;
            int k = i - (hours - 48);
            boolean spike = swarming && k >= 0;
            if (spike) w -= 0.07;
            double temp = 34.6 + Math.sin((h - 6) / 24.0 * 2 * Math.PI) * 0.25
                    + (rnd.nextDouble() - 0.5) * 0.2 + (spike ? k / 48.0 * 1.6 : 0);
            double hum = 68 + Math.cos(h / 24.0 * 2 * Math.PI) * 4 + rnd.nextGaussian();
            double co2 = 850 + Math.sin(h / 24.0 * 2 * Math.PI) * 90 + rnd.nextGaussian() * 25 + (spike ? 150 : 0);
            list.add(new Reading(t, r(w, 2), r(temp, 1), r(hum, 0), r(co2, 0)));
        }
        return new Hive("Улей №" + no, list);
    }

    private static double r(double v, int d) {
        double m = Math.pow(10, d);
        return Math.round(v * m) / m;
    }
}
