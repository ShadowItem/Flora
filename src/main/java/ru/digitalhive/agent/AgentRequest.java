package ru.digitalhive.agent;

import ru.digitalhive.model.Apiary;
import ru.digitalhive.model.Hive;
import ru.digitalhive.model.Period;
import ru.digitalhive.model.Scenario;

public record AgentRequest(String question, Apiary apiary, Hive hive, Period period, Scenario scenario) {}

