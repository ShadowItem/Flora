package ru.digitalhive.model;

public enum Urgency {
    LOW("Плановое наблюдение", "urgency-low"),
    MEDIUM("Срок указан в рекомендации", "urgency-mid"),
    HIGH("Срок указан в рекомендации", "urgency-high");

    public final String text;
    public final String css;

    Urgency(String text, String css) {
        this.text = text;
        this.css = css;
    }

    public static Urgency fromLevel(double level) {
        if (level >= 0.7) return HIGH;
        if (level >= 0.4) return MEDIUM;
        return LOW;
    }
}

