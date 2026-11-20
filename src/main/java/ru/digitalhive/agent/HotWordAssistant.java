package ru.digitalhive.agent;

import ru.digitalhive.model.Fmt;
import ru.digitalhive.model.Hive;
import ru.digitalhive.model.Period;
import ru.digitalhive.model.Reading;
import ru.digitalhive.model.Urgency;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Small offline glossary for common table fields; it explains observations, not diagnoses. */
public final class HotWordAssistant {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private HotWordAssistant() { }

    public static AgentResponse answer(String question, Hive hive) {
        return answer(question, hive, Period.DAY);
    }

    public static AgentResponse answer(String question, Hive hive, Period selectedPeriod) {
        if (question == null || hive == null || hive.readings().isEmpty()) return null;
        String q = question.toLowerCase(Locale.ROOT).replace('ё', 'е');
        Reading last = hive.readings().get(hive.readings().size() - 1);
        Reading previousReading = hive.readings().size() > 1 ? hive.readings().get(hive.readings().size() - 2) : null;
        Period period = selectedPeriod == null ? Period.DAY : selectedPeriod;
        List<Reading> selectedReadings = period.filter(hive.readings());
        Reading baseline = selectedReadings.size() > 1 ? selectedReadings.get(0) : previousReading;
        String when = STAMP.format(last.time());

        if (has(q, "health_index", "health index", "индекс здоровья")) {
            return info("Health_Index — экспертная оценка по шкале 0–5 в исходной таблице; это не вероятность и не диагноз.",
                    "Представьте его как итоговую отметку по экспертной шкале, а не процент риска.",
                    "Числовую оценку можно показывать только там, где её применимость подтверждена. Погодный индекс оценивает условия для лёта отдельно и не заменяет этот показатель.",
                    "Последнее значение и пояснение по шкале", last);
        }
        if (has(q, "co2", "co₂", "углекисл", "углекислый газ")) {
            String value = Double.isFinite(last.co2()) ? Fmt.num(last.co2(), 0) + " ppm (" + when + ")"
                    : "нет значения в последней строке таблицы";
            return info("CO₂ по последнему измерению: " + value + ".",
                    co2Recommendation(baseline, last),
                    "Bencsik et al. (Sensors, 2023; DOI 10.3390/s23073588) описывают измерения в нескольких семьях и изменение CO₂ во времени. Сравнивайте тот же датчик в похожие часы: универсальную тревожную границу по одному числу эти данные не задают.",
                    Double.isFinite(last.co2()) ? "Последнее значение CO₂: " + Fmt.num(last.co2(), 0) + " ppm" : "Значение CO₂ отсутствует", last);
        }
        if (has(q, "влажн", "humidity", "сырост")) {
            return info("Влажность по последнему измерению: " + Fmt.num(last.humidity(), 0) + "% (" + when + ").",
                    humidityRecommendation(baseline, last),
                    "Влажность зависит от места датчика и условий семьи. В мониторинговой работе Gil-Lebrero et al. (Sensors, 2017; DOI 10.3390/s17010055) использовали датчики в разных зонах улья; сравнивайте один и тот же датчик, а не считайте одно число универсальной нормой.",
                    "Последнее значение влажности: " + Fmt.num(last.humidity(), 0) + "%", last);
        }
        boolean outside = has(q, "снаруж", "наруж", "внешн", "улич", "ext_temp", "ext temp");
        if (outside && has(q, "температур", "temp", "тепл")) {
            String value = last.extTempC() == null || !Double.isFinite(last.extTempC())
                    ? "нет значения в этой строке таблицы"
                    : Fmt.num(last.extTempC(), 1) + " °C (" + when + ")";
            return info("Наружная температура: " + value + ".",
                    "Это уличный термометр, не датчик температуры в расплодной зоне.",
                    "Flora не подменяет её погодным прогнозом: погодный индекс рассчитывается отдельно по дневной погодной таблице и экспертной формуле.",
                    "Последнее значение уличной температуры", last);
        }
        if (has(q, "температур", "temp_in", "температура в улье", "температура в гнезде", "тепл")) {
            return info("Температура внутри улья по последнему измерению: " + Fmt.num(last.tempC(), 1)
                            + " °C (" + when + ").",
                    temperatureRecommendation(baseline, last),
                    "Температура меняется между зонами улья: Gil-Lebrero et al. (Sensors, 2017; DOI 10.3390/s17010055) измеряли разные позиции и отмечали различия. Место вашего датчика неизвестно, поэтому по этому числу нельзя подтвердить температуру в центре расплодного гнезда или сравнить его с универсальным порогом.",
                    "Последняя температура внутри улья: " + Fmt.num(last.tempC(), 1) + " °C", last);
        }
        if (has(q, "вес", "масса", "weight")) {
            String change = baseline == null ? "Предыдущей записи в выбранном интервале нет для сравнения."
                    : "За выбранный интервал " + Fmt.signed(last.weightKg() - baseline.weightKg(), 2)
                    + " кг за " + elapsed(baseline, last) + ".";
            return info("Вес улья по последнему измерению: " + Fmt.num(last.weightKg(), 2) + " кг (" + when + ").",
                    weightRecommendation(baseline, last),
                    change + " Meikle et al. (Journal of Apicultural Research, 2006; публикация USDA ARS №175865) наблюдали влияние лёта, притока нектара и дождя на вес. Сравнивайте записи в похожее время суток: это подсказка для наблюдения, не доказательство медосбора.",
                    "Последнее значение веса и сравнение с предыдущей записью", last);
        }
        return null;
    }

    private static String weightRecommendation(Reading prev, Reading last) {
        if (prev == null) {
            return "Для изменения веса нужна хотя бы одна предыдущая запись. Проверьте, что платформа весов устойчива, и сравните следующую запись в то же время суток; решение о подкормке по одному значению не принимайте.";
        }
        double change = last.weightKg() - prev.weightKg();
        String related = relatedSensorChanges(prev, last, "weight");
        if (change < 0) {
            return "Вес уменьшился на " + Fmt.num(Math.abs(change), 2) + " кг за " + elapsed(prev, last)
                    + ". Проверьте, не сдвинулись ли весы или основание. " + related
                    + " Сравните следующую запись в то же время суток; если снижение повторяется в нескольких записях, при плановом осмотре проверьте запасы корма. Не назначайте подкормку по одному короткому интервалу.";
        }
        if (change > 0) {
            return "Вес вырос на " + Fmt.num(change, 2) + " кг за " + elapsed(prev, last)
                    + ". Сверьте его с записью в то же время следующего измерительного цикла и учитывайте дождь и вылет пчёл: короткий прирост ещё не подтверждает медосбор. "
                    + related + " Вмешательство по одной прибавке не требуется.";
        }
        return "Между двумя последними записями вес не изменился. Проверьте устойчивость платформы и продолжайте сравнение в одинаковое время суток. "
                + related + " По этой паре измерений подкормка не обоснована.";
    }

    private static String temperatureRecommendation(Reading prev, Reading last) {
        if (prev == null) {
            return "В таблице пока одна температура: проверьте крепление датчика и сравните следующую запись. Место установки неизвестно, поэтому один показатель не задаёт порог вмешательства.";
        }
        double change = last.tempC() - prev.tempC();
        String movement = change > 0 ? "повысилась на " : change < 0 ? "снизилась на " : "не изменилась за ";
        String amount = change == 0 ? elapsed(prev, last) : Fmt.num(Math.abs(change), 1) + " °C за " + elapsed(prev, last);
        String related = relatedSensorChanges(prev, last, "temp");
        if (change > 0) {
            return "Температура " + movement + amount + " и составляет " + Fmt.num(last.tempC(), 1)
                    + " °C. Проверьте крепление датчика и сравните показание с независимым термометром рядом с ним при следующей безопасной проверке. "
                    + related + " Не меняйте вентиляцию только по одному измерению: место датчика в таблице не указано.";
        }
        if (change < 0) {
            return "Температура " + movement + amount + " и составляет " + Fmt.num(last.tempC(), 1)
                    + " °C. Убедитесь, что датчик не сдвинулся; сопоставьте следующие показания в той же точке. "
                    + related + " По одному снижению вмешательство не назначайте.";
        }
        return "Температура не изменилась между этими записями: " + Fmt.num(last.tempC(), 1)
                + " °C. Проверьте закрепление датчика и продолжайте сравнение в той же точке. " + related;
    }

    private static String humidityRecommendation(Reading prev, Reading last) {
        if (prev == null) {
            return "Сравните следующую запись того же датчика. Проверьте, не попала ли на датчик вода; при плановом осмотре обратите внимание на видимую влагу или конденсат. Не меняйте вентиляцию по одному числу.";
        }
        double change = last.humidity() - prev.humidity();
        String related = relatedSensorChanges(prev, last, "humidity");
        if (change > 0) {
            return "Влажность выросла на " + Fmt.num(change, 0) + " п.п. за " + elapsed(prev, last)
                    + " и составляет " + Fmt.num(last.humidity(), 0) + "%. Проверьте датчик на намокание; при плановом осмотре осмотрите улей на видимую влагу или конденсат. "
                    + related + " По одной записи не меняйте вентиляцию.";
        }
        if (change < 0) {
            return "Влажность снизилась на " + Fmt.num(Math.abs(change), 0) + " п.п. за " + elapsed(prev, last)
                    + " и составляет " + Fmt.num(last.humidity(), 0) + "%. Проверьте, что датчик сухой и закреплён; сравните следующую запись в той же точке. "
                    + related + " Отдельное вмешательство по одному снижению не требуется.";
        }
        return "Влажность не изменилась между этими записями и составляет " + Fmt.num(last.humidity(), 0)
                + "%. Продолжайте сравнение того же датчика; при осмотре отмечайте только видимую влагу или конденсат. " + related;
    }

    private static String co2Recommendation(Reading prev, Reading last) {
        if (!Double.isFinite(last.co2())) {
            return "В последней записи нет значения CO₂. Проверьте подключение датчика; не подменяйте пропуск нулём.";
        }
        if (prev == null || !Double.isFinite(prev.co2())) {
            return "Для сравнения CO₂ нужна ещё одна запись того же датчика. Сопоставьте её в похожее время суток; не меняйте вентиляцию по одному значению.";
        }
        double change = last.co2() - prev.co2();
        String related = relatedSensorChanges(prev, last, "co2");
        if (change > 0) {
            return "CO₂ вырос на " + Fmt.num(change, 0) + " ppm за " + elapsed(prev, last)
                    + " и составляет " + Fmt.num(last.co2(), 0) + " ppm. Проверьте крепление и повторяемость показания в похожее время следующей записи. "
                    + related + " Не меняйте вентиляцию по одному скачку.";
        }
        if (change < 0) {
            return "CO₂ снизился на " + Fmt.num(Math.abs(change), 0) + " ppm за " + elapsed(prev, last)
                    + " и составляет " + Fmt.num(last.co2(), 0) + " ppm. Сравните с записью в похожее время: суточный лёт влияет на ряд. "
                    + related + " По одной паре показаний вентиляцию не меняйте.";
        }
        return "CO₂ не изменился между этими записями и составляет " + Fmt.num(last.co2(), 0)
                + " ppm. Продолжайте сравнение в похожее время суток; отдельное вмешательство по этому значению не требуется. " + related;
    }

    private static String relatedSensorChanges(Reading prev, Reading last, String excluded) {
        List<String> changes = new java.util.ArrayList<>();
        if (!excluded.equals("weight")) changes.add("вес " + Fmt.signed(last.weightKg() - prev.weightKg(), 2) + " кг");
        if (!excluded.equals("temp")) changes.add("температура " + Fmt.signed(last.tempC() - prev.tempC(), 1) + " °C");
        if (!excluded.equals("humidity")) changes.add("влажность " + Fmt.signed(last.humidity() - prev.humidity(), 0) + " п.п.");
        if (!excluded.equals("co2") && Double.isFinite(prev.co2()) && Double.isFinite(last.co2())) {
            changes.add("CO₂ " + Fmt.signed(last.co2() - prev.co2(), 0) + " ppm");
        }
        return changes.isEmpty() ? "" : "За этот же интервал изменились и другие датчики: " + String.join(", ", changes) + ".";
    }

    private static AgentResponse info(String answer, String recommendation, String reasoning, String data, Reading last) {
        String timingAdvice = " Рекомендация относится к записи от " + last.time().toLocalDate()
                + "; сравните её со следующей плановой записью. Это осторожный шаг наблюдения, не нормативный срок или порог вмешательства.";
        return new AgentResponse(answer, List.of(data), recommendation + timingAdvice, reasoning,
                Urgency.LOW, "", "Справка по данным таблицы");
    }

    private static boolean has(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private static String elapsed(Reading a, Reading b) {
        long minutes = Math.max(0, Duration.between(a.time(), b.time()).toMinutes());
        return minutes < 60 ? minutes + " мин" : Fmt.num(minutes / 60.0, 1) + " ч";
    }
}
