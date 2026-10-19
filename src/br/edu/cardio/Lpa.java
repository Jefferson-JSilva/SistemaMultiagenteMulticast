package br.edu.cardio;

import java.util.Map;

public final class Lpa {
    private Lpa() { }
    public record Node(double mu, double lambda, double gc, double gct, double muE) { }
    public record Result(Node nap1, Node nap2, Node nap3, String state, String classification) { }

    public static Node node(double first, double second) {
        for (double value : new double[]{first, second})
            if (!Double.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("evidencia fora de [0,1]");
        double lambda = 1 - second;
        double gc = first - lambda;
        double gct = first + lambda - 1;
        return new Node(first, lambda, gc, gct, (gc + 1) / 2);
    }

    public static Result consolidate(Map<Factor, Double> evidence) {
        if (evidence.size() != 4) throw new IllegalArgumentException("quatro evidencias obrigatorias");
        Node n1 = node(evidence.get(Factor.TABAGISMO), evidence.get(Factor.SEDENTARISMO));
        Node n2 = node(evidence.get(Factor.PRESSAO), evidence.get(Factor.IMC));
        Node n3 = node(n1.muE(), n2.muE());
        String state, classification;
        if (n3.gc() >= 0.5) { state = "V"; classification = "Risco cardiologico elevado"; }
        else if (n3.gc() <= -0.5) { state = "F"; classification = "Risco cardiologico baixo"; }
        else if (n3.gct() >= 0.5) { state = "T"; classification = "Inconclusivo: evidencias contraditorias, reavaliar o paciente"; }
        else if (n3.gct() <= -0.5) { state = "PARACOMPLETO"; classification = "Inconclusivo: evidencias contraditorias, reavaliar o paciente"; }
        else {
            state = "NAO_EXTREMO";
            classification = "Risco indefinido" + (n3.gc() > 0 ? ", tendencia a elevado"
                    : n3.gc() < 0 ? ", tendencia a baixo" : ", sem tendencia");
        }
        return new Result(n1, n2, n3, state, classification);
    }
}
