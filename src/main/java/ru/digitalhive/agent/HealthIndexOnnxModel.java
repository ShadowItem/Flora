package ru.digitalhive.agent;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OnnxValue;
import ru.digitalhive.model.Hive;
import ru.digitalhive.model.Reading;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Local ONNX inference for the supplied expert Health_Index target. */
public final class HealthIndexOnnxModel implements AutoCloseable {
    private static final String MODEL_RESOURCE = "/models/health-index-mlp.onnx";
    private static final String RANGES_RESOURCE = "/models/health-index-feature-ranges.csv";
    private static final String INFO_RESOURCE = "/models/model-info.properties";
    private static final String TRAIN_HASHES_RESOURCE = "/models/training-row-hashes.txt";
    private static final DateTimeFormatter TRAIN_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] FEATURE_NAMES = {"Weight_kg", "Temp_In_C", "Humidity_In_P", "CO2_ppm", "Ext_Temp_C",
            "Pesticide_Risk", "MonthSin", "MonthCos", "HourSin", "HourCos", "WeightChangePerHour",
            "TempChangePerHour", "HumidityChangePerHour", "CO2ChangePerHour", "HasPreviousReading"};
    private static final int INPUTS = 15;
    private static final OrtEnvironment ENVIRONMENT = OrtEnvironment.getEnvironment();

    private final OrtSession session;
    private final OrtSession.SessionOptions sessionOptions;
    private final String inputName;
    private final String outputName;
    private final Map<String, FeatureRange> featureRanges;
    private final ModelInfo info;
    private final Set<String> trainingRowHashes;

    public HealthIndexOnnxModel() throws IOException, ai.onnxruntime.OrtException {
        byte[] modelBytes;
        try (InputStream in = HealthIndexOnnxModel.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (in == null) throw new IOException("Не найден ONNX-файл " + MODEL_RESOURCE);
            modelBytes = in.readAllBytes();
        }
        this.featureRanges = loadFeatureRanges();
        this.info = loadModelInfo();
        this.trainingRowHashes = loadTrainingRowHashes();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        OrtSession created;
        try { created = ENVIRONMENT.createSession(modelBytes, options); }
        catch (ai.onnxruntime.OrtException e) { options.close(); throw e; }
        this.session = created;
        this.sessionOptions = options;
        Set<String> inputs = session.getInputNames();
        Set<String> outputs = session.getOutputNames();
        if (inputs.size() != 1 || outputs.size() != 1) {
            session.close();
            sessionOptions.close();
            throw new IOException("Неожиданная схема ONNX: один вход и один выход ожидались.");
        }
        this.inputName = inputs.iterator().next();
        this.outputName = outputs.iterator().next();
    }

    public Prediction predict(Hive hive) throws ai.onnxruntime.OrtException {
        List<Reading> readings = hive.readings();
        if (readings.isEmpty()) return Prediction.missing(List.of("нет измерений"), null);
        Reading latest = readings.get(readings.size() - 1);
        List<String> missing = new ArrayList<>();
        if (!Double.isFinite(latest.weightKg())) missing.add("Weight_kg");
        if (!Double.isFinite(latest.tempC())) missing.add("Temp_In_C");
        if (!Double.isFinite(latest.humidity())) missing.add("Humidity_In_P");
        if (!Double.isFinite(latest.co2())) missing.add("CO2_ppm");
        if (latest.extTempC() == null || !Double.isFinite(latest.extTempC())) missing.add("Ext_Temp_C");
        if (latest.pesticideRisk() == null || !Double.isFinite(latest.pesticideRisk())) missing.add("Pesticide_Risk");
        if (!missing.isEmpty()) return Prediction.missing(missing, latest.time());
        if (trainingRowHashes.contains(trainingRowHash(hive.name(), latest.time()))) {
            return Prediction.missing(List.of("точная запись входит в Train; in-sample прогноз не показывается"), latest.time());
        }

        double[] x = featureVector(readings, latest);
        float[] input = new float[INPUTS];
        for (int i = 0; i < INPUTS; i++) input[i] = (float) x[i];
        try (OnnxTensor tensor = OnnxTensor.createTensor(ENVIRONMENT,
                     FloatBuffer.wrap(input), new long[]{1, INPUTS});
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            OnnxValue output = result.get(outputName).orElseThrow(
                    () -> new IllegalStateException("ONNX не вернул выход " + outputName));
            Object value = output.getValue();
            if (!(value instanceof float[][] matrix) || matrix.length != 1 || matrix[0].length != 1) {
                throw new IllegalStateException("ONNX вернул выход неожиданной формы.");
            }
            double score = Math.max(0.5, Math.min(5.0, matrix[0][0]));
            LocalDate date = latest.time().toLocalDate();
            return new Prediction(score, List.of(), latest.time(), info.covers(date), x, outOfTrainingRange(x));
        }
    }

    private static double[] featureVector(List<Reading> readings, Reading current) {
        double weightChange = 0, tempChange = 0, humidityChange = 0, co2Change = 0;
        double hasPrevious = 0;
        if (readings.size() >= 2) {
            Reading previous = readings.get(readings.size() - 2);
            if (Double.isFinite(previous.weightKg()) && Double.isFinite(previous.tempC())
                    && Double.isFinite(previous.humidity()) && Double.isFinite(previous.co2())) {
                double hours = Math.max(1.0 / 60.0,
                        Duration.between(previous.time(), current.time()).toMinutes() / 60.0);
                weightChange = (current.weightKg() - previous.weightKg()) / hours;
                tempChange = (current.tempC() - previous.tempC()) / hours;
                humidityChange = (current.humidity() - previous.humidity()) / hours;
                co2Change = (current.co2() - previous.co2()) / hours;
                hasPrevious = 1.0;
            }
        }
        double monthAngle = 2.0 * Math.PI * (current.time().getMonthValue() - 1) / 12.0;
        double hour = current.time().getHour() + current.time().getMinute() / 60.0;
        double hourAngle = 2.0 * Math.PI * hour / 24.0;
        return new double[]{current.weightKg(), current.tempC(), current.humidity(), current.co2(),
                current.extTempC(), current.pesticideRisk(), Math.sin(monthAngle), Math.cos(monthAngle),
                Math.sin(hourAngle), Math.cos(hourAngle), weightChange, tempChange, humidityChange,
                co2Change, hasPrevious};
    }

    private List<String> outOfTrainingRange(double[] x) {
        List<String> result = new ArrayList<>();
        // Focus on measured inputs first; engineered calendar/trend fields remain model internals.
        for (int i = 0; i < 6; i++) {
            FeatureRange range = featureRanges.get(FEATURE_NAMES[i]);
            if (range != null && (x[i] < range.min() || x[i] > range.max())) {
                result.add(FEATURE_NAMES[i] + "=" + x[i] + " вне диапазона Train ["
                        + range.min() + "; " + range.max() + "]");
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, FeatureRange> loadFeatureRanges() throws IOException {
        InputStream in = HealthIndexOnnxModel.class.getResourceAsStream(RANGES_RESOURCE);
        if (in == null) throw new IOException("Не найдены диапазоны Train " + RANGES_RESOURCE);
        Map<String, FeatureRange> ranges = new java.util.LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cells = line.replace("\uFEFF", "").split(";", -1);
                if (cells.length < 3) throw new IOException("Повреждена строка диапазонов Train: " + line);
                ranges.put(cells[0].trim(), new FeatureRange(Double.parseDouble(cells[1]), Double.parseDouble(cells[2])));
            }
        }
        return Map.copyOf(ranges);
    }

    private static Set<String> loadTrainingRowHashes() throws IOException {
        InputStream in = HealthIndexOnnxModel.class.getResourceAsStream(TRAIN_HASHES_RESOURCE);
        if (in == null) throw new IOException("Не найдены контрольные подписи Train " + TRAIN_HASHES_RESOURCE);
        Set<String> hashes = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) if (!line.isBlank()) hashes.add(line.trim());
        }
        return Set.copyOf(hashes);
    }

    private static String trainingRowHash(String hiveId, LocalDateTime time) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((hiveId + "|" + TRAIN_TIME_FORMAT.format(time)).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private record FeatureRange(double min, double max) { }

    public String coverageDescription() { return info.coverageDescription(); }
    public double validationMae() { return info.validationMae(); }
    public double validationRmse() { return info.validationRmse(); }

    private static ModelInfo loadModelInfo() throws IOException {
        InputStream in = HealthIndexOnnxModel.class.getResourceAsStream(INFO_RESOURCE);
        if (in == null) throw new IOException("Не найдены метаданные модели " + INFO_RESOURCE);
        Properties p = new Properties();
        try (InputStream stream = in) { p.load(stream); }
        try {
            boolean validationUsedForTuning = Boolean.parseBoolean(p.getProperty("validation_used_for_tuning", "true"));
            if (validationUsedForTuning) throw new IOException("Метаданные сообщают, что Validation использовалась для настройки; модель не активирована.");
            return new ModelInfo(LocalDate.parse(p.getProperty("train_date_min")),
                    LocalDate.parse(p.getProperty("train_date_max")),
                    LocalDate.parse(p.getProperty("validation_date_min")),
                    LocalDate.parse(p.getProperty("validation_date_max")),
                    Double.parseDouble(p.getProperty("validation_mae")),
                    Double.parseDouble(p.getProperty("validation_rmse")));
        } catch (RuntimeException ex) {
            throw new IOException("Некорректные метаданные модели: " + ex.getMessage(), ex);
        }
    }

    private record ModelInfo(LocalDate trainStart, LocalDate trainEnd,
                             LocalDate validationStart, LocalDate validationEnd,
                             double validationMae, double validationRmse) {
        boolean covers(LocalDate date) {
            return (!date.isBefore(trainStart) && !date.isAfter(trainEnd))
                    || (!date.isBefore(validationStart) && !date.isAfter(validationEnd));
        }
        String coverageDescription() {
            return "Train: " + trainStart + "–" + trainEnd + "; отдельная Validation: "
                    + validationStart + "–" + validationEnd;
        }
    }

    @Override public void close() throws ai.onnxruntime.OrtException {
        session.close();
        sessionOptions.close();
    }

    public record Prediction(Double score, List<String> missingFields, LocalDateTime time,
                             boolean coveredPeriod, double[] featureVector, List<String> outOfTrainingRange) {
        static Prediction missing(List<String> fields, LocalDateTime time) {
            return new Prediction(null, List.copyOf(fields), time, false, null, List.of());
        }
        public boolean available() { return score != null; }
    }
}
