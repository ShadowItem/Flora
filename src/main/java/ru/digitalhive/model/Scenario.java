package ru.digitalhive.model;

/** Четыре сценария из задания + общий вопрос. prompt — готовая фраза для быстрого запроса. */
public enum Scenario {
    SWARMING("Предупреждение о роении", "Есть ли риск роения у этого улья?"),
    PESTICIDES("Защита от пестицидов", "Планируется ли обработка полей рядом с пасекой?"),
    WEATHER("Погода и медосбор", "Подходит ли погода для лёта пчёл и медосбора?"),
    MIGRATION("Выбор места для кочёвки", "Куда лучше вывезти пасеку на кочёвку?"),
    GENERAL("Общий вопрос", "Что происходит с этим ульем?");

    public final String title;
    public final String prompt;

    Scenario(String title, String prompt) {
        this.title = title;
        this.prompt = prompt;
    }
}

