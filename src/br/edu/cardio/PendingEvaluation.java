package br.edu.cardio;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** All shared state is protected by this object's monitor. First valid response/error wins. */
public final class PendingEvaluation {
    private final Map<Factor, Double> values = new EnumMap<>(Factor.class);
    private final Map<Factor, String> errors = new EnumMap<>(Factor.class);
    private final long deadline;
    private boolean finished;
    public record Snapshot(Map<Factor, Double> values, Map<Factor, String> errors, Set<Factor> missing) {
        public boolean complete() { return values.size() == Factor.values().length && errors.isEmpty(); }
    }

    public PendingEvaluation(int timeoutMs) {
        deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    }

    public synchronized String accept(Factor factor, Message message) {
        if (values.containsKey(factor) || errors.containsKey(factor)) return "DUPLICADA";
        if (finished || System.nanoTime() >= deadline) return "TARDIA";
        if (message.type() == Message.Type.ERRO) errors.put(factor, message.data());
        else {
            double value;
            try { value = Double.parseDouble(message.data()); }
            catch (NumberFormatException e) { return "EVIDENCIA_INVALIDA"; }
            if (!Double.isFinite(value) || value < 0 || value > 1) return "EVIDENCIA_INVALIDA";
            values.put(factor, value);
        }
        notifyAll();
        return "ACEITA";
    }

    public synchronized Snapshot await() throws InterruptedException {
        while (values.size() + errors.size() < Factor.values().length) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
        finished = true;
        Set<Factor> missing = EnumSet.allOf(Factor.class);
        missing.removeAll(values.keySet());
        missing.removeAll(errors.keySet());
        return new Snapshot(Map.copyOf(values), Map.copyOf(errors), Set.copyOf(missing));
    }
}
