package ru.digitalhive.agent;

import ru.digitalhive.model.*;
import ru.digitalhive.weather.WeatherForecastRepository;
import ru.digitalhive.weather.WeatherIndexCalculator;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Real local advisor: ONNX Health_Index estimate + a separate expert weather index. */
public final class NeuralAgentService implements AgentService, AutoCloseable {
    private final HealthIndexOnnxModel healthModel;
    private final String modelLoadError;
    private final Map<LocalDate, WeatherIndexCalculator.WeatherDay> weather;
    private final String weatherLoadError;

    public NeuralAgentService() {
        HealthIndexOnnxModel loaded = null;
        String modelError = null;
        try { loaded = new HealthIndexOnnxModel(); }
        catch (Exception e) { modelError = e.getMessage(); }
        this.healthModel = loaded;
        this.modelLoadError = modelError;

        Map<LocalDate, WeatherIndexCalculator.WeatherDay> forecast = Map.of();
        String weatherError = null;
        try { forecast = WeatherForecastRepository.loadConfigured(); }
        catch (Exception e) { weatherError = e.getMessage(); }
        this.weather = forecast;
        this.weatherLoadError = weatherError;
    }

    @Override public CompletableFuture<AgentResponse> ask(AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> answer(request));
    }

    @Override public CompletableFuture<List<RiskItem>> assess(AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> risks(request));
    }

    private AgentResponse answer(AgentRequest q) {
        String question = q.question() == null ? "" : q.question().toLowerCase(Locale.ROOT).replace('ё', 'е');
        AgentResponse smallTalk = smallTalk(question);
        if (smallTalk != null) return smallTalk;
        if (isOutOfScope(question)) return scopeAnswer();
        Scenario scenario = q.scenario() != null ? q.scenario() : detect(question);
        if (scenario == Scenario.WEATHER) return weatherAnswer(q.hive(), q.period());
        if (isHealthIndexRequest(question)) return generalAnswer(q.hive(), q.period());
        if (isHiveStatusQuestion(question)) return statusAnswer(q.hive(), q.period());
        AgentResponse hotWord = HotWordAssistant.answer(question, q.hive(), q.period());
        return hotWord != null ? hotWord : safeFallback();
    }

    private static AgentResponse smallTalk(String question) {
        if (question.contains("привет") || question.contains("здравствуй") || question.contains("добрый день")
                || question.contains("как дела") || question.contains("ты кто") || question.contains("как тебя зовут")) {
            return new AgentResponse("Всё в порядке, спасибо! Я Flora. Могу объяснить показатели датчиков и погоду для лёта и медосбора.",
                    List.of("Flora"),
                    "Спросите, например, «Что значит вес улья?» или «Как погода повлияет на лёт?».",
                    "Могу объяснять доступные измерения и погодные условия, не устанавливая диагноз по одному показателю.",
                    Urgency.LOW, "", "Короткий диалог");
        }
        if (question.contains("спасибо") || question.contains("благодар")) {
            return new AgentResponse("Пожалуйста! Если хотите, объясню ещё один показатель или проверю погодный индекс.",
                    List.of("Flora"), "Можно выбрать Hot Word ниже.",
                    "Новые значения не добавлялись; ответ основан на загруженных таблицах.",
                    Urgency.LOW, "", "Короткий диалог");
        }
        return null;
    }

    private AgentResponse scopeAnswer() {
        return new AgentResponse("Я помогаю с погодой, медосбором и объяснением показателей этой таблицы.",
                List.of("Погода и медосбор", "Параметры датчиков"),
                "Спросите, например, о погодном индексе, весе, температуре, влажности или CO₂.",
                "Отдельные оценки роения, обработки полей и кочёвки в этой версии не рассчитываются.",
                Urgency.LOW, "", "Вне текущей области Flora");
    }

    private static boolean isOutOfScope(String question) {
        return question.contains("роен") || question.contains("рой")
                || question.contains("пестиц") || question.contains("обработк") || question.contains("хими")
                || question.contains("кочев") || question.contains("вывез");
    }

    private static boolean isHealthIndexRequest(String question) {
        boolean explicitHealthIndex = question.contains("health_index") || question.contains("health index")
                || question.contains("индекс здоровья") || question.contains("индекс счастья")
                || question.contains("индекс счастья пчел") || question.contains("индекс здоровья пчел");
        if (explicitHealthIndex) return true;
        boolean asksForIndex = question.contains("прогноз здоровья") || question.contains("оценка здоровья");
        return asksForIndex && (question.contains("прогноз") || question.contains("посчитай")
                || question.contains("рассчитай") || question.contains("сколько") || question.contains("оцени"));
    }

    private static boolean isHiveStatusQuestion(String question) {
        return question.contains("что происходит с ульем") || question.contains("состояние улья")
                || question.contains("как улей") || question.contains("как чувствует себя улей")
                || question.contains("состояние семьи") || question.contains("здоровье семьи")
                || question.contains("как семья") || question.contains("что с пчелами")
                || question.contains("как здоровье улья");
    }

    private AgentResponse statusAnswer(Hive hive, Period period) {
        Reading last = latest(hive);
        if (last == null) return safeFallback();
        String observed = "Последнее измерение (" + last.time().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                + "): вес " + Fmt.num(last.weightKg(), 2) + " кг; температура внутри "
                + Fmt.num(last.tempC(), 1) + " °C; влажность " + Fmt.num(last.humidity(), 0) + "%"
                + (Double.isFinite(last.co2()) ? "; CO₂ " + Fmt.num(last.co2(), 0) + " ppm" : "")
                + ". Изменение за выбранный интервал: " + recentChanges(hive, period) + ".";
        return new AgentResponse("Сводка по последним доступным измерениям таблицы.",
                List.of("Последняя запись датчиков", "Погодная оценка отдельно"),
                contextualRecommendation(hive, period),
                "Наблюдаемая динамика: " + observed + " Это не диагноз и не вывод о причине.",
                Urgency.LOW, "", "Наблюдение по данным таблицы");
    }

    private AgentResponse safeFallback() {
        return new AgentResponse("Не уверена, что поняла вопрос. Я могу объяснить показатели улья, индекс здоровья пчёл или оценить погоду для лёта и медосбора.",
                List.of("Доступные темы: вес, температура, влажность, CO₂, индекс здоровья, погодный индекс"),
                "Попробуйте спросить: «Что означает вес улья?», «Какой индекс здоровья пчёл?» или «Какой погодный индекс на дату измерения?».",
                "Непонятный вопрос не превращается в диагноз или неподтверждённый вывод.",
                Urgency.LOW, "Уточните вопрос", "Могу помочь по этим темам");
    }

    private AgentResponse generalAnswer(Hive hive, Period period) {
        HealthIndexOnnxModel.Prediction prediction = predict(hive);
        String weatherText = weatherSummary(hive);
        List<String> used = List.of("Последние измерения выбранного улья", "Индекс здоровья (Health_Index)", "Погодная оценка отдельно");
        if (!prediction.available()) {
            return new AgentResponse("Индекс здоровья пчёл (Health_Index, условно «индекс счастья») по этой записи численно не показываю.", used,
                    "Это экспертная оценка по данным улья, а не диагноз. Пустые поля не заменяйте нулями. " + contextualRecommendation(hive, period),
                    "Для этой записи оценка недоступна: данные неполны либо запись не подходит для числового расчёта. " + recentChanges(hive, period) + ". " + weatherText,
                    Urgency.LOW, "", "Оценка не выдана");
        }
        if (!prediction.coveredPeriod()) {
            return new AgentResponse("Индекс здоровья пчёл (Health_Index, условно «индекс счастья») — экспертная оценка, не диагноз. Для этой даты значение не подтверждено данными, поэтому число не показываю.",
                    used, contextualRecommendation(hive, period),
                    "Прогноз не выдан. Наблюдения по таблице: " + recentChanges(hive, period) + ". " + weatherText,
                    Urgency.LOW, "", "Нужны данные за подтверждённый период");
        }
        boolean outsideObservedRanges = !prediction.outOfTrainingRange().isEmpty();
        String recommendation = (outsideObservedRanges
                ? "Оценка выходит за привычный для неё диапазон; не используйте число как основание лечения или вмешательства. "
                : "Сопоставьте оценку с наблюдениями за семьёй; одно значение не устанавливает причину. ")
                + contextualRecommendation(hive, period);
        String reason = "Изменение за выбранный интервал: " + recentChanges(hive, period) + ". "
                + (outsideObservedRanges
                    ? "Некоторые показания отличаются от диапазона, на котором основана оценка, поэтому её надёжность ниже."
                    : "Погодный индекс отдельный и не заменяет Health_Index.")
                + " Причину по одному измерению установить нельзя. " + weatherText;
        String scoreLabel = "Индекс здоровья пчёл (Health_Index) по записи от " + prediction.time().toLocalDate() + ": ";
        return new AgentResponse(scoreLabel + Fmt.num(prediction.score(), 2)
                + " из 5. Это индекс по экспертной шкале, не диагноз и не вероятность риска.",
                used, recommendation, reason, Urgency.LOW, "", outsideObservedRanges
                    ? "Осторожно: часть показателей вне наблюдавшегося диапазона"
                    : "Сопоставьте с дальнейшей динамикой");
    }

    private AgentResponse weatherAnswer(Hive hive, Period period) {
        if (weatherLoadError != null) return noWeather("Погодные данные недоступны.");
        LocalDate date = hive.end().toLocalDate();
        var result = WeatherIndexCalculator.calculate(date, weather);
        if (result.isEmpty()) {
            return noWeather("На " + date + " нет ежедневных погодных значений; поэтому индекс не рассчитан. "
                    + "Для новой даты нужны температура, влажность, погодное явление, скорость и направление ветра.");
        }
        WeatherIndexCalculator.Result r = result.get();
        if (!r.monthSupported()) {
            return noWeather("Погодные значения есть, но формула и её нормы подтверждены только для июля 2026 "
                    + "(и временно для 1–3 августа 2026). На эту дату числовой индекс не выдаю.");
        }
        WeatherPriority priority = weatherPriority(hive, r, date, weather.get(date));
        String limit = r.monthSupported() ? "" : " Параметры формулы основаны на июльских нормах и не подтверждены для этого месяца.";
        if (r.julyNormsTemporarilyUsed()) limit += " Для 1–3 августа это только временный ориентир.";
        String answer = "Погодный индекс на " + date + ": " + Fmt.num(r.index(), 2) + " из 5 — «" + r.category() + "».";
        String data = "Погодная таблица: температура " + Fmt.num(weather.get(date).tempC(), 1) + " °C, влажность "
                + Fmt.num(weather.get(date).humidityPercent(), 0) + "%, " + weather.get(date).condition() + ", ветер "
                + Fmt.num(weather.get(date).windMps(), 1) + " м/с, направление " + weather.get(date).windDirection() + ".";
        return new AgentResponse(answer + limit,
                List.of("Погодные значения", "Экспертная формула для погодной оценки",
                        "University of Maine Cooperative Extension; Oregon State University Extension"),
                priority.recommendation(), data + "\n" + String.join("; ", r.factors())
                        + ". University of Maine Cooperative Extension (Honeybee Flight Activity Index) и Oregon State University Extension (PNW 623) отмечают, что лёт зависит также от света, дождя, сезона и местных условий. Индекс не является Health_Index и не описывает здоровье улья.",
                priority.urgency(), combinedAssessment(hive, weather.get(date), r, period), priority.label());
    }

    private AgentResponse noWeather(String explanation) {
        return new AgentResponse("Погодный индекс не показан: нет полного подтверждённого набора на дату.",
                List.of("Погодные данные / месячная применимость"),
                "Проверьте ежедневные значения погоды и подтверждённость норм для месяца. До этого не делайте вывод о пригодности дня по индексу.",
                explanation + " Пустые погодные поля не трактуются как ноль; июльская формула не распространяется автоматически на другие месяцы.",
                Urgency.LOW, "", "Оценка не определена: данных недостаточно");
    }

    private AgentResponse pesticideAnswer(Hive hive) {
        Reading last = latest(hive);
        if (last == null || last.pesticideRisk() == null || !Double.isFinite(last.pesticideRisk())) {
            return new AgentResponse("В загруженных измерениях нет оценки риска обработки рядом; установить её наличие нельзя.",
                    List.of("Нет данных о возможной обработке"),
                    "Уточните планируемые обработки у местной службы или хозяйств. При необычной гибели пчёл свяжитесь со специалистом.",
                    "Отсутствующая запись не доказывает отсутствие воздействия.", Urgency.LOW);
        }
        boolean flagged = last.pesticideRisk() > 0;
        return new AgentResponse(flagged ? "В записи есть отметка о возможном риске обработки." : "В записи отметка о риске обработки не установлена.",
                List.of("Запись о возможном риске обработки"),
                flagged ? "Проверьте источник и дату отметки, свяжитесь с ответственными за обработки и наблюдайте за состоянием семьи." 
                        : "Сохраните наблюдение и уточните локальные уведомления: нулевая отметка сама по себе не исключает обработку.",
                "Это поле данных, а не измеренная вероятность и не подтверждённая причинная связь.",
                flagged ? Urgency.MEDIUM : Urgency.LOW);
    }

    private AgentResponse migrationAnswer() {
        return new AgentResponse("Сравнить точки кочёвки по этим данным нельзя.",
                List.of("Погодная таблица", "Данные улья"),
                "Для сравнения нужны координаты вариантов, локальная погода и информация о медоносах/цветении.",
                "Карта медоносов, координаты и маршрутные ограничения не подключены; рекомендацию по месту не выдумываю.", Urgency.LOW);
    }

    private AgentResponse swarmingAnswer(Hive hive) {
        String changes = recentChanges(hive);
        return new AgentResponse("По имеющимся данным вероятность роения определить нельзя.",
                List.of("Измерения выбранного улья"),
                "Если вас беспокоит поведение семьи, обсудите ситуацию с опытным пчеловодом; не делайте вывод только по весу или температуре.",
                "Измеренное изменение: " + changes + ". Оно само по себе не подтверждает роение и не устанавливает причину.", Urgency.LOW);
    }

    private List<RiskItem> risks(AgentRequest q) {
        Hive hive = q.hive();
        LocalDate date = hive.end().toLocalDate();
        var weatherResult = weatherLoadError == null
                ? WeatherIndexCalculator.calculate(date, weather)
                : java.util.Optional.<WeatherIndexCalculator.Result>empty();
        if (weatherResult.isPresent() && weatherResult.get().monthSupported()) {
            WeatherIndexCalculator.Result r = weatherResult.get();
            String timing = r.index() < 2.5
                    ? "Для наружных работ на дату " + date + " проверьте почасовой прогноз утром и перед выездом; при дожде или опасном ветре перенесите их."
                    : "Для плановой поездки на дату " + date + " сверьте местную погоду перед выездом; отдельный срочный визит не требуется.";
            return List.of(RiskItem.weatherIndex(Scenario.WEATHER, r.index(), r.category(),
                    "Погодный ориентир для лёта и медосбора; не оценка здоровья семьи. " + timing));
        }
        String note = weatherResult.isPresent()
                ? "Погодные строки есть, но применимость формулы для этой даты не подтверждена; индекс не показан."
                : "Нет ежедневной погодной строки для " + date + "; индекс не рассчитан.";
        return List.of(RiskItem.unavailable(Scenario.WEATHER, note));
    }

    private HealthIndexOnnxModel.Prediction predict(Hive hive) {
        if (modelLoadError != null || healthModel == null) {
            return HealthIndexOnnxModel.Prediction.missing(List.of("Локальная числовая оценка недоступна."), null);
        }
        try { return healthModel.predict(hive); }
        catch (Exception e) {
            return HealthIndexOnnxModel.Prediction.missing(List.of("Не удалось рассчитать числовую оценку."), hive.end());
        }
    }

    private String weatherSummary(Hive hive) {
        if (weatherLoadError != null) return "Погодные данные недоступны.";
        LocalDate date = hive.end().toLocalDate();
        var result = WeatherIndexCalculator.calculate(date, weather);
        if (result.isEmpty()) {
            return "Погодных данных на " + date + " нет; индекс не рассчитан.";
        }
        var r = result.get();
        if (!r.monthSupported()) return "На " + date + " погодные значения есть, но месячная применимость формулы не подтверждена; индекс не показываю.";
        return "Отдельный погодный индекс на " + date + " — " + Fmt.num(r.index(), 2) + "/5 (" + r.category()
                + "); он не смешивается с Health_Index.";
    }

    private static String contextualRecommendation(Hive hive, Period selectedPeriod) {
        Period period = selectedPeriod == null ? Period.DAY : selectedPeriod;
        List<Reading> rows = period.filter(hive.readings());
        if (rows.size() < 2) {
            return "В выбранном интервале таблицы недостаточно записей для сравнения. Проверьте крепление датчиков и сопоставьте следующую запись по обычному графику.";
        }
        Reading baseline = rows.get(0);
        Reading last = rows.get(rows.size() - 1);
        double weightChange = last.weightKg() - baseline.weightKg();
        String changes = recentChanges(hive, period);
        if (weightChange < 0) {
            return "За выбранный интервал в этом улье вес снизился. " + changes
                    + ". Проверьте устойчивость весов и сравните следующий такой же интервал. "
                    + "Если снижение повторится, при плановом осмотре проверьте запасы корма. "
                    + "Одновременные изменения датчиков не устанавливают причину. " + nextMeasurementWindow(hive);
        }
        if (weightChange > 0) {
            return "За выбранный интервал в этом улье вес вырос. " + changes
                    + ". Сверьте следующий такой же интервал и учитывайте дождь и вылет пчёл; короткий прирост сам по себе не подтверждает медосбор. "
                    + nextMeasurementWindow(hive);
        }
        return "За выбранный интервал вес в этом улье не изменился. " + changes
                + ". Продолжайте сравнение тех же датчиков; вмешательство по этой паре измерений не обосновано. "
                + nextMeasurementWindow(hive);
    }

    private static String recentChanges(Hive hive) {
        return recentChanges(hive, Period.DAY);
    }

    private static String recentChanges(Hive hive, Period selectedPeriod) {
        Period period = selectedPeriod == null ? Period.DAY : selectedPeriod;
        List<Reading> rows = period.filter(hive.readings());
        if (rows.size() < 2) return "в выбранном интервале недостаточно записей для сравнения";
        Reading a = rows.get(0), b = rows.get(rows.size() - 1);
        long minutes = Math.max(0, Duration.between(a.time(), b.time()).toMinutes());
        List<String> changes = new ArrayList<>();
        changes.add("вес " + Fmt.signed(b.weightKg()-a.weightKg(), 2) + " кг");
        changes.add("температура " + Fmt.signed(b.tempC()-a.tempC(), 1) + " °C");
        changes.add("влажность " + Fmt.signed(b.humidity()-a.humidity(), 0) + " п.п.");
        if (Double.isFinite(a.co2()) && Double.isFinite(b.co2())) changes.add("CO₂ " + Fmt.signed(b.co2()-a.co2(), 0) + " ppm");
        return "за " + (minutes / 60.0 < 1 ? minutes + " мин" : Fmt.num(minutes / 60.0, 1) + " ч") + ": " + String.join(", ", changes);
    }

    private static Reading latest(Hive hive) {
        List<Reading> readings = hive.readings();
        return readings.isEmpty() ? null : readings.get(readings.size()-1);
    }

    private record WeatherPriority(Urgency urgency, String label, String recommendation) { }

    private static WeatherPriority weatherPriority(Hive hive, WeatherIndexCalculator.Result r, LocalDate date,
                                                    WeatherIndexCalculator.WeatherDay day) {
        if (r.flightGate() == 0.0 || r.index() < 1.0) {
            return new WeatherPriority(Urgency.MEDIUM,
                    "На " + date + ": перенести зависящие от лёта работы",
                    "На эту дату указаны " + day.condition() + " и ветер " + Fmt.num(day.windMps(), 1)
                            + " м/с; индекс " + Fmt.num(r.index(), 2) + "/5. Это не повод для срочного визита к улью. "
                            + "Если наружные работы или наблюдение за лётом запланированы, перенесите их на ближайшее подходящее погодное окно. "
                            + "Проверьте почасовой прогноз утром " + date + " и перед выездом; при грозе или опасном ветре не открывайте улей. "
                            + "Индекс не заменяет официальные предупреждения.");
        }
        if (r.index() < 2.5) {
            return new WeatherPriority(Urgency.MEDIUM,
                    "Утром " + date + ": решить, переносить ли наружные работы",
                    "Индекс низкий: " + Fmt.num(r.index(), 2) + "/5. Утром " + date
                            + " проверьте почасовой прогноз дождя и ветра. Если погода ухудшилась, перенесите наружные работы; отдельный визит к улью только из-за индекса не нужен. "
                            + "Снижение веса само по себе не назначает вмешательство.");
        }
        if (r.index() < 4.0) {
            return new WeatherPriority(Urgency.LOW, "Плановая поездка — проверить прогноз в день выезда",
                    "По формуле условия умеренные (" + Fmt.num(r.index(), 2)
                            + "/5). Срочного визита не требуется. В день плановой поездки сверьте дождь, ветер и цветение на месте; индекс не гарантирует медосбор.");
        }
        return new WeatherPriority(Urgency.LOW, "Погодных мер на эту дату не требуется",
                "Индекс высокий (" + Fmt.num(r.index(), 2)
                        + "/5), но это не гарантия взятка. Езжайте по обычному графику; перед выездом проверьте местную погоду и цветение.");
    }

    private static String nextMeasurementWindow(Hive hive) {
        List<Reading> rows = hive.readings();
        if (rows.size() < 2) {
            return "Сверьте следующую доступную запись по обычному графику; этих данных недостаточно для срочного порога. ";
        }
        Duration gap = Duration.between(rows.get(rows.size() - 2).time(), rows.get(rows.size() - 1).time()).abs();
        if (gap.isZero()) return "Сверьте следующую запись по обычному графику. ";
        boolean capped = gap.compareTo(Duration.ofDays(1)) > 0;
        Duration followUp = capped ? Duration.ofDays(1) : gap;
        String window = followUp.toHours() >= 1
                ? Math.max(1, followUp.toHours()) + " ч"
                : Math.max(1, followUp.toMinutes()) + " мин";
        return "Сверьте показания при следующей записи, ориентировочно в течение " + window
                + (capped
                    ? "; это осторожный срок наблюдения, а не интервал датчика или биологический порог. "
                    : "; это ориентир по интервалу между записями, а не биологический порог. ");
    }

    private static String combinedAssessment(Hive hive, WeatherIndexCalculator.WeatherDay day,
                                             WeatherIndexCalculator.Result r, Period selectedPeriod) {
        Period period = selectedPeriod == null ? Period.DAY : selectedPeriod;
        List<Reading> rows = period.filter(hive.readings());
        if (rows.size() < 2) {
            return "Погодный индекс " + Fmt.num(r.index(), 2)
                    + "/5 относится только к погоде. Для сравнения веса улья нужны хотя бы две записи.";
        }
        Reading previous = rows.get(0);
        Reading latest = rows.get(rows.size() - 1);
        double weightDelta = latest.weightKg() - previous.weightKg();
        String changes = recentChanges(hive, period);
        String weather = "Погода: " + Fmt.num(r.index(), 2) + "/5, " + r.category()
                + " (" + Fmt.num(day.tempC(), 1) + " °C; " + day.condition() + "; ветер "
                + Fmt.num(day.windMps(), 1) + " м/с). Датчики: " + changes + ". ";
        if (r.index() < 2.5 && weightDelta < 0) {
            return weather + "За этот интервал вес уменьшился одновременно с низкой погодной оценкой. Совпадение не доказывает, что погода вызвала изменение; по этим двум сигналам срочный осмотр не назначается.";
        }
        if (r.index() >= 2.5 && weightDelta > 0) {
            return weather + "Вес вырос за последний интервал. Это ещё не подтверждает медосбор: сопоставьте записи в одно и то же время суток и проверьте цветение на месте.";
        }
        if (r.index() < 2.5) {
            return weather + "Низкий погодный индекс относится к планированию лёта, а не к здоровью семьи. Изменение веса за короткий интервал не указывает причину.";
        }
        return weather + "Погодная оценка не низкая, но вес за последний интервал не вырос. Это не противоречие: короткое изменение зависит и от времени вылета, и от условий взвешивания.";
    }

    private static Scenario detect(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (t.contains("рое") || t.contains("рой")) return Scenario.SWARMING;
        if (t.contains("пестиц") || t.contains("обработ") || t.contains("хими")) return Scenario.PESTICIDES;
        if (t.contains("погод") || t.contains("медосбор") || t.contains("ветер") || t.contains("дожд")) return Scenario.WEATHER;
        if (t.contains("кочев") || t.contains("вывез")) return Scenario.MIGRATION;
        return Scenario.GENERAL;
    }

    @Override public void close() {
        if (healthModel != null) {
            try { healthModel.close(); } catch (Exception ignored) { }
        }
    }
}
