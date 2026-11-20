package ru.digitalhive.agent;

import ru.digitalhive.model.Scenario;
import ru.digitalhive.model.Urgency;

/** A display row. level is an internal normalized bar value, not always a probability. */
public record RiskItem(Scenario scenario, double level, String displayValue, Urgency urgency,
                       String note, boolean showBar, boolean hasData, boolean riskSignal) {
    /** Legacy constructor for any still-used demo rows; explicitly displays a probability. */
    public RiskItem(Scenario scenario, double probability, Urgency urgency, String note) {
        this(scenario, probability, Math.round(probability * 100) + " %", urgency, note, true, true, true);
    }

    public static RiskItem unavailable(Scenario scenario, String note) {
        return new RiskItem(scenario, Double.NaN, "Нет оценки", Urgency.LOW, note, false, false, false);
    }

    public static RiskItem weatherIndex(Scenario scenario, double index, String category, String note) {
        double level = Math.max(0.0, Math.min(1.0, index / 5.0));
        Urgency urgency = index < 1.0 ? Urgency.HIGH : index < 2.5 ? Urgency.MEDIUM : Urgency.LOW;
        return new RiskItem(scenario, level,
                String.format(java.util.Locale.forLanguageTag("ru-RU"), "%.2f / 5", index),
                urgency, note + " Категория: " + category
                        + ". Полоса показывает значение на шкале 0–5; низкий индекс означает, что погода может ограничивать лёт.",
                true, true, index < 2.5);
    }

    public static RiskItem observedPesticideFlag(Scenario scenario, double flag) {
        boolean active = flag > 0.0;
        return new RiskItem(scenario, active ? 1.0 : 0.0, active ? "Есть отметка" : "Нет отметки",
                active ? Urgency.HIGH : Urgency.LOW,
                active ? "В исходной записи Pesticide_Risk отмечен как ненулевой. Это флаг данных, не вероятность и не подтверждение воздействия."
                        : "В исходной записи нет отметки Pesticide_Risk. Это не доказывает отсутствие обработки рядом.",
                false, true, active);
    }
}
