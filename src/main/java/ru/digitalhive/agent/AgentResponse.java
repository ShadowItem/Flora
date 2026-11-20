package ru.digitalhive.agent;

import ru.digitalhive.model.Urgency;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Public-facing response payload. Internal implementation and file identifiers are redacted. */
public record AgentResponse(String answer,
                            List<String> dataUsed,
                            String recommendation,
                            String reasoning,
                            Urgency urgency,
                            String combinedAssessment,
                            String urgencyDetail) {
    private static final Pattern FILE_PATH = Pattern.compile(
            "(?i)(?:[a-z]:\\\\[^\\s,;]+|(?<![\\w:])/[^\\s,;]+)"
                    + "|\\b[\\w.-]+\\.(?:java|csv|onnx|txt|json|properties|pom|bat|exe|jar|md|xlsx|docx|pdf|xml|ya?ml|class|dll)\\b");
    private static final Pattern INTERNAL_METRIC = Pattern.compile(
            "(?i)\\b(?:Weight_kg|Temp_In_C|Humidity_In_P|CO2_ppm|Ext_Temp_C|Pesticide_Risk)\\b");
    private static final Pattern IMPLEMENTATION_TERM = Pattern.compile(
            "(?i)\\b(?:ONNX|MLP|Validation|Train|in-sample|MAE|RMSE)\\b");
    private static final Pattern PRIVATE_SENTENCE = Pattern.compile(
            "(?i)(?:покрытие данных|период модели|диапазон модели|оценка на обучающей|валидац|\\btrain\\b|\\bvalidation\\b|\\bmae\\b|\\brmse\\b|in-sample|onnx|mlp)");

    public AgentResponse {
        answer = clean(answer);
        recommendation = clean(recommendation);
        reasoning = clean(reasoning);
        combinedAssessment = clean(combinedAssessment);
        urgencyDetail = clean(urgencyDetail);
        dataUsed = dataUsed == null ? List.of() : dataUsed.stream().map(AgentResponse::clean).toList();
        urgency = Objects.requireNonNullElse(urgency, Urgency.LOW);
    }

    public AgentResponse(String answer, List<String> dataUsed, String recommendation,
                         String reasoning, Urgency urgency) {
        this(answer, dataUsed, recommendation, reasoning, urgency, "", "");
    }

    public AgentResponse(String answer, List<String> dataUsed, String recommendation,
                         String reasoning, Urgency urgency, String combinedAssessment) {
        this(answer, dataUsed, recommendation, reasoning, urgency, combinedAssessment, "");
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) return text;
        String result = FILE_PATH.matcher(text).replaceAll("служебные сведения");
        StringBuilder visible = new StringBuilder();
        for (String sentence : result.split("(?<=[.!?])\\s+")) {
            if (!PRIVATE_SENTENCE.matcher(sentence).find()) {
                if (visible.length() > 0) visible.append(' ');
                visible.append(sentence);
            }
        }
        result = INTERNAL_METRIC.matcher(visible.toString()).replaceAll("показатель");
        result = IMPLEMENTATION_TERM.matcher(result).replaceAll("");
        return result.replaceAll("\\s+([,.;:])", "$1")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }
}
