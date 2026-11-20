package ru.digitalhive.agent;

import java.util.List;
import java.util.concurrent.CompletableFuture;


public interface AgentService extends AutoCloseable {
    CompletableFuture<AgentResponse> ask(AgentRequest request);

    CompletableFuture<List<RiskItem>> assess(AgentRequest request);

    @Override default void close() { }
}

