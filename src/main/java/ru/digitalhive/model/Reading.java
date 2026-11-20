package ru.digitalhive.model;

import java.time.LocalDateTime;

/** Одно измерение датчиков улья; null/NaN означают отсутствие измерения, а не нулевое значение. */
public record Reading(LocalDateTime time, double weightKg, double tempC, double humidity, double co2,
                      Double extTempC, Double pesticideRisk) {
    /** Совместимость с прежними вызовами/демо-данными без внешней погоды и отметки пестицидов. */
    public Reading(LocalDateTime time, double weightKg, double tempC, double humidity, double co2) {
        this(time, weightKg, tempC, humidity, co2, null, null);
    }
}
