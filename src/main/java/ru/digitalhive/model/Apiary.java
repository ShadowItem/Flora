package ru.digitalhive.model;

import java.util.List;

public final class Apiary {
    private final String name;
    private final List<Hive> hives;

    public Apiary(String name, List<Hive> hives) {
        this.name = name;
        this.hives = List.copyOf(hives);
    }

    public String name() { return name; }
    public List<Hive> hives() { return hives; }

    @Override public String toString() { return name; }
}

