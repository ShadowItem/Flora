package ru.digitalhive.agent;

import ru.digitalhive.model.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToDoubleFunction;

/** Устаревшая демонстрационная заглушка. Активное приложение использует NeuralAgentService. */
public final class MockAgentService implements AgentService {

    @Override
    public CompletableFuture<AgentResponse> ask(AgentRequest q) {
        return CompletableFuture.supplyAsync(() -> {
            pause(900);
            return answer(q);
        });
    }

    @Override
    public CompletableFuture<List<RiskItem>> assess(AgentRequest q) {
        return CompletableFuture.supplyAsync(() -> risks(q));
    }

    private AgentResponse answer(AgentRequest q) {
        Scenario s = q.scenario() != null ? q.scenario() : detect(q.question());
        Hive h = q.hive();
        double dw = weightDelta(h, 48);
        double dt = tempShift(h);
        List<Reading> recent = h.lastHours(6);
        double temp = recent.isEmpty() ? 0 : avg(recent, Reading::tempC);
        return switch (s) {
            case PESTICIDES -> new AgentResponse(
                    "Данные об обработке полей в демо-режиме не подключены. В рабочей версии здесь появится сопоставление координат пасеки с предупреждениями.",
                    List.of("Координаты пасеки", "Предупреждения об обработке полей (демо)"),
                    "Уточните у ответственной службы, планируется ли обработка полей рядом с пасекой.",
                    "Источник предупреждений пока не подключён, поэтому исключить обработку нельзя.",
                    Urgency.MEDIUM);
            case WEATHER -> new AgentResponse(
                    "Температура внутри улья по записи: " + Fmt.num(temp, 1) + " °C. Погодная оценка в этом режиме недоступна.",
                    List.of("Температура в улье", "Влажность", "Погода (демо)"),
                    "Если днём тепло и безветренно — проверьте запасы корма и состояние улья.",
                    "Для точной оценки нужны температура воздуха, осадки, ветер и влажность за окном.",
                    Urgency.LOW);
            case MIGRATION -> new AgentResponse(
                    "Сравнение мест для кочёвки пока недоступно: нужны карта медоносов, прогноз погоды и расстояние.",
                    List.of("Положение пасеки", "Карта медоносов (демо)"),
                    "Подготовьте 2–3 возможные точки — помощник сравнит их по цветению, погоде и рискам.",
                    "ГИС-данные о медоносных растениях не подключены.",
                    Urgency.LOW);
            default -> {
                double risk = swarmRisk(h);
                boolean alert = risk >= 0.4;
                yield new AgentResponse(
                        alert ? "За последние 2 суток вес улья изменился на " + Fmt.signed(dw, 1) + " кг, а температура в гнезде — на "
                                + Fmt.signed(dt, 1) + " °C. Это повод проверить состояние семьи."
                              : "Показатели «" + h.name() + "» в пределах обычных колебаний: вес за 2 суток " + Fmt.signed(dw, 1)
                                + " кг, температура " + Fmt.num(temp, 1) + " °C.",
                        List.of("Вес улья (48 ч)", "Температура в улье", "Влажность", "Сезон"),
                        alert ? "На ближайшем плановом осмотре проверьте состояние семьи и запасы корма."
                              : "Особых действий не требуется. Продолжайте наблюдение.",
                        "График веса + температура + сезон. Вероятность роения — " + Math.round(risk * 100) + " %.",
                        Urgency.fromLevel(risk));
            }
        };
    }

    private List<RiskItem> risks(AgentRequest q) {
        Hive h = q.hive();
        double dw = weightDelta(h, 48);
        double dt = tempShift(h);
        double swarm = swarmRisk(h);
        double pest = 0.15 + (q.apiary().name().hashCode() & 3) * 0.12;
        double weather = 0.2;
        double migr = 0.3;
        return List.of(
                new RiskItem(Scenario.SWARMING, swarm, Urgency.fromLevel(swarm),
                        "Вес " + Fmt.signed(dw, 1) + " кг за 48 ч, температура " + Fmt.signed(dt, 1) + " °C"),
                new RiskItem(Scenario.PESTICIDES, pest, Urgency.fromLevel(pest), "Демо-оценка: подключите данные об обработке полей"),
                new RiskItem(Scenario.WEATHER, weather, Urgency.fromLevel(weather), "Демо-оценка: подключите прогноз погоды"),
                new RiskItem(Scenario.MIGRATION, migr, Urgency.fromLevel(migr), "Демо-оценка: подключите карту медоносов"));
    }

    private static Scenario detect(String text) {
        String t = text.toLowerCase();
        if (t.contains("рое") || t.contains("рой")) return Scenario.SWARMING;
        if (t.contains("пестиц") || t.contains("обработ") || t.contains("хими")) return Scenario.PESTICIDES;
        if (t.contains("погод") || t.contains("медосбор") || t.contains("ветер") || t.contains("дожд")) return Scenario.WEATHER;
        if (t.contains("кочев") || t.contains("вывез")) return Scenario.MIGRATION;
        return Scenario.GENERAL;
    }

    private static double swarmRisk(Hive h) {
        double dw = weightDelta(h, 48), dt = tempShift(h);
        double v = 0.12 + Math.max(0, -dw) / 4 * 0.55 + Math.max(0, dt) / 1.5 * 0.25;
        return Math.min(0.97, Math.max(0, v));
    }

    private static double weightDelta(Hive h, int hours) {
        List<Reading> r = h.lastHours(hours);
        return r.size() < 2 ? 0 : r.get(r.size() - 1).weightKg() - r.get(0).weightKg();
    }

    private static double tempShift(Hive h) {
        List<Reading> recent = h.lastHours(6), base = h.lastHours(7 * 24);
        if (recent.isEmpty() || base.isEmpty()) return 0;
        return avg(recent, Reading::tempC) - avg(base, Reading::tempC);
    }

    private static double avg(List<Reading> rs, ToDoubleFunction<Reading> f) {
        return rs.stream().mapToDouble(f).average().orElse(0);
    }

    private static void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

