package br.edu.cardio;

import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Dependency-free tests: no assertions flag, JUnit, framework or network needed. */
public final class UnitTests {
    private static int checks;
    private static final double[][] EXPECTED = {
        {0,0,0,0,-1,0}, {1,1,1,1,1,0}, {.615,.778,.5,.336,.115,.279},
        {1,1,0,0,0,1}, {0,0,1,1,0,-1}, {.308,.333,.25,.179,-.465,.106}};
    private static final String[] STATES = {"F","V","NAO_EXTREMO","T","PARACOMPLETO","NAO_EXTREMO"};

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static void near(double actual, double expected, String label) {
        check(Math.abs(actual - expected) <= .001, label + ": " + actual + " != " + expected);
    }
    private static void invalid(Runnable operation) {
        try { operation.run(); throw new AssertionError("entrada deveria ser rejeitada"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    public static void main(String[] args) throws Exception {
        List<String> rows = new ArrayList<>();
        rows.add("caso,muT,muS,muPA,muIMC,Gc,Gct,muE,estado");
        for (int n = 1; n <= 6; n++) {
            Map<Factor, Double> evidence = new EnumMap<>(Factor.class);
            Map<Factor, String> input = PatientData.example(n).requests();
            for (Factor f : Factor.values()) {
                double value = Evidence.evaluate(f, input.get(f));
                evidence.put(f, value);
                near(value, EXPECTED[n-1][f.ordinal()], "caso " + n + " " + f);
            }
            Lpa.Result result = Lpa.consolidate(evidence);
            near(result.nap3().gc(), EXPECTED[n-1][4], "Gc caso " + n);
            near(result.nap3().gct(), EXPECTED[n-1][5], "Gct caso " + n);
            check(result.state().equals(STATES[n-1]), "estado caso " + n);
            rows.add(String.format(Locale.US,"%d,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%s", n,
                    evidence.get(Factor.TABAGISMO), evidence.get(Factor.SEDENTARISMO),
                    evidence.get(Factor.PRESSAO), evidence.get(Factor.IMC), result.nap3().gc(),
                    result.nap3().gct(), result.nap3().muE(), result.state()));
        }
        for (String data : List.of("3,0,0,0,0", "4,4,0,0,0", "0,0,0,0", "0,0,0,0,0,0", "NaN,0,0,0,0", "0,0,0,0,"))
            invalid(() -> Evidence.evaluate(Factor.TABAGISMO, data));
        for (String data : List.of("4,0,0,0,0", "0,0,2,0,0", "0,0,0,3,0", "0,0,0,0,2"))
            invalid(() -> Evidence.evaluate(Factor.SEDENTARISMO, data));
        for (String data : List.of("0,80", "120,-1", "NaN,80", "Infinity,80", "120", "120,80,90"))
            invalid(() -> Evidence.evaluate(Factor.PRESSAO, data));
        for (String data : List.of("80,0", "-1,1.7", "80,0.49", "80,2.51", "80,NaN", "NaN,1.7", "80,Infinity"))
            invalid(() -> Evidence.evaluate(Factor.IMC, data));
        near(Evidence.evaluate(Factor.PRESSAO,"120,80"),0,"limite PA baixo");
        near(Evidence.evaluate(Factor.PRESSAO,"180,80"),1,"PAS isolada");
        near(Evidence.evaluate(Factor.PRESSAO,"120,110"),1,"PAD isolada");
        near(Evidence.evaluate(Factor.IMC,"100,2"),0,"IMC 25");
        near(Evidence.evaluate(Factor.IMC,"160,2"),1,"IMC 40");
        near(Evidence.evaluate(Factor.IMC,"10,1.7"),0,"baixo peso");
        near(Evidence.evaluate(Factor.IMC,"1000,1.7"),1,"clamp IMC");
        invalid(() -> Lpa.node(Double.NaN,0));
        invalid(() -> Lpa.node(-.01,0));
        for (int a = 0; a <= 10; a++) for (int b = 0; b <= 10; b++) {
            Lpa.Node node = Lpa.node(a/10.0,b/10.0);
            check(Math.abs(node.muE() - (a/10.0 + b/10.0)/2) < 1e-14, "prova numerica da media");
        }
        Map<Factor,Double> threshold = new EnumMap<>(Factor.class);
        threshold.put(Factor.TABAGISMO,1.0); threshold.put(Factor.SEDENTARISMO,1.0);
        threshold.put(Factor.PRESSAO,.5); threshold.put(Factor.IMC,.5);
        check(Lpa.consolidate(threshold).state().equals("V"),"prioridade Gc=0.5 sobre Gct=0.5");
        threshold.replaceAll((f,v)->1-v);
        check(Lpa.consolidate(threshold).state().equals("F"),"prioridade Gc=-0.5 sobre Gct=-0.5");
        threshold.replaceAll((f,v)->.5);
        check(Lpa.consolidate(threshold).classification().endsWith("sem tendencia"),"certeza zero");
        Message message = new Message(Message.Type.REQ,"abc","COORD-x","IMC","80,1.7");
        check(Message.parse(message.encode()).equals(message),"roundtrip protocolo");
        for (String raw : List.of("REQ;abc;x;y", "REQ;abc;x;y;z;extra", "OTHER;a;b;c;d", "REQ;;a;b;c", "REQ;a;b;c;d\n"))
            invalid(() -> Message.parse(raw));
        PendingEvaluation pending = new PendingEvaluation(1000);
        Message response = new Message(Message.Type.RESP,"a","IMC","COORD-x","0.2");
        check(pending.accept(Factor.IMC,response).equals("ACEITA"),"primeira aceita");
        check(pending.accept(Factor.IMC,response).equals("DUPLICADA"),"duplicada ignorada");
        check(pending.accept(Factor.PRESSAO,new Message(Message.Type.RESP,"a","PRESSAO","COORD-x","NaN"))
                .equals("EVIDENCIA_INVALIDA"),"NaN rejeitado pelo coordenador");
        for (Factor f : List.of(Factor.PRESSAO,Factor.TABAGISMO,Factor.SEDENTARISMO))
            pending.accept(f,new Message(Message.Type.ERRO,"a",f.name(),"COORD-x","entrada invalida"));
        PendingEvaluation.Snapshot snapshot = pending.await();
        check(!snapshot.complete() && snapshot.errors().size()==3 && snapshot.missing().isEmpty(),"erro sem consolidacao");
        PendingEvaluation timeout = new PendingEvaluation(10);
        check(timeout.await().missing().size()==4,"timeout identifica ausentes");
        check(timeout.accept(Factor.IMC,response).equals("TARDIA"),"resposta tardia ignorada");
        Files.createDirectories(Path.of("evidencias/local"));
        Files.write(Path.of("evidencias/local/calculos.csv"),rows,StandardCharsets.UTF_8);
        String summary = "PASS: " + checks + " verificacoes unitarias; seis casos dentro da tolerancia 0.001.";
        Files.writeString(Path.of("evidencias/local/unitarios.txt"),summary + System.lineSeparator());
        System.out.println(summary);
    }
}
