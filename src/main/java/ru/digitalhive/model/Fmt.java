package ru.digitalhive.model;

import java.util.Locale;

public final class Fmt {
    private static final Locale RU = Locale.forLanguageTag("ru-RU");
    private Fmt() {}

    public static String num(double v, int decimals) {
        return String.format(RU, "%." + decimals + "f", v);
    }

    public static String signed(double v, int decimals) {
        return String.format(RU, "%+." + decimals + "f", v);
    }
}

