package br.edu.cardio;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Coordenador implements AutoCloseable {
    private final String identity = "COORD-" + UUID.randomUUID();
    private final Config config;
    private final EventLog log;
    private final MulticastChannel channel;
    private final AtomicBoolean closed = new AtomicBoolean();
    // Keep at most 256 recent evaluations to recognize duplicates after completion.
    private final Map<String, PendingEvaluation> pending = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override protected boolean removeEldestEntry(Map.Entry<String, PendingEvaluation> e) {
                    return size() > 256;
                }
            });
    private final Thread receiver;

    private Coordenador(Config config) throws IOException {
        this.config = config;
        log = new EventLog(config.logDirectory(), identity);
        try { channel = new MulticastChannel(config, log); }
        catch (IOException | RuntimeException e) { log.close(); throw e; }
        receiver = new Thread(this::listen, "recepcao-coordenador");
        receiver.start();
    }

    private void listen() {
        while (!closed.get()) {
            try {
                Message message = channel.receive();
                if (message == null) continue;
                if (message.origin().equals(identity) || !message.destination().equals(identity)
                        || message.type() == Message.Type.REQ) {
                    log.event("DESCARTADA", "origem/tipo/destino: " + message.encode());
                    continue;
                }
                Factor factor;
                try { factor = Factor.valueOf(message.origin()); }
                catch (IllegalArgumentException e) { log.event("DESCARTADA", "origem desconhecida"); continue; }
                PendingEvaluation evaluation = pending.get(message.evaluation());
                if (evaluation == null) { log.event("DESCARTADA", "avaliacao desconhecida: " + message.evaluation()); continue; }
                String action = evaluation.accept(factor, message);
                log.event(action.equals("ACEITA") ? action : "DESCARTADA_" + action, message.encode());
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) { if (!closed.get()) log.event("FALHA_RECEPCAO", e.toString()); }
        }
    }

    private boolean evaluate(PatientData patient, String label) throws IOException, InterruptedException {
        String id = UUID.randomUUID().toString();
        PendingEvaluation evaluation = new PendingEvaluation(config.timeoutMs());
        pending.put(id, evaluation);
        log.event("AVALIACAO", label + " id=" + id + " prazo-ms=" + config.timeoutMs());
        Map<Factor, String> requests = patient.requests();
        for (Factor factor : Factor.values())
            channel.send(new Message(Message.Type.REQ, id, identity, factor.name(), requests.get(factor)));
        PendingEvaluation.Snapshot snapshot = evaluation.await();
        for (Factor factor : Factor.values()) {
            if (snapshot.values().containsKey(factor))
                log.event("EVIDENCIA", String.format(Locale.US, "%s %s mu=%.3f", id, factor, snapshot.values().get(factor)));
        }
        if (!snapshot.complete()) {
            for (Factor factor : Factor.values()) {
                if (snapshot.errors().containsKey(factor)) log.event("ERRO_AGENTE", factor + ": " + snapshot.errors().get(factor));
                if (snapshot.missing().contains(factor)) log.event("AUSENTE", factor + " nao respondeu no prazo");
            }
            log.event("INCOMPLETA", label + " id=" + id + "; nao e possivel consolidar a LPA2v");
            return false;
        }
        Lpa.Result result = Lpa.consolidate(snapshot.values());
        display("NAP1", result.nap1());
        display("NAP2", result.nap2());
        display("NAP3", result.nap3());
        log.event("RESULTADO", String.format(Locale.US,
                "%s id=%s estado=%s Gc=%.3f Gct=%.3f muE=%.3f | %s", label, id,
                result.state(), result.nap3().gc(), result.nap3().gct(), result.nap3().muE(), result.classification()));
        return true;
    }

    private void display(String name, Lpa.Node n) {
        log.event(name, String.format(Locale.US, "mu=%.3f lambda=%.3f Gc=%.3f Gct=%.3f muE=%.3f",
                n.mu(), n.lambda(), n.gc(), n.gct(), n.muE()));
    }

    private void interactive() throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("Uso didatico: nao avaliar pessoas reais. Comandos: novo | caso 1..6 | sair");
        String command;
        while ((command = reader.readLine()) != null) {
            command = command.trim();
            if (command.equalsIgnoreCase("sair")) break;
            try {
                if (command.equalsIgnoreCase("novo")) evaluate(PatientData.interview(reader), "INTERATIVO");
                else if (command.matches("(?i)caso\\s+[1-6]")) {
                    int number = Integer.parseInt(command.split("\\s+")[1]);
                    evaluate(PatientData.example(number), "CASO " + number);
                } else System.out.println("Comandos: novo | caso 1..6 | sair");
            } catch (EOFException e) { break; }
            catch (IllegalArgumentException e) { System.out.println("Entrada invalida: " + e.getMessage()); }
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        channel.close();
        try { receiver.join(1000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log.event("ENCERRADO", "coordenador finalizado");
        log.close();
    }

    public static void main(String[] args) throws Exception {
        if (Config.showHelp(args)) return;
        Config config = Config.parse(args);
        Map<String, String> opts = config.options();
        long modes = List.of("caso", "casos", "dados").stream().filter(opts::containsKey).count();
        if (modes > 1) throw new IllegalArgumentException("escolha apenas --caso, --casos ou --dados");
        try (Coordenador coordinator = new Coordenador(config)) {
            Thread hook = new Thread(coordinator::close, "shutdown-coordenador");
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                System.out.println("SIMULACAO DIDATICA. Nao utilizar para avaliar pessoas reais.");
                if (opts.containsKey("caso") || opts.containsKey("casos")) {
                    String cases = opts.getOrDefault("casos", opts.get("caso"));
                    for (String number : cases.split(",")) {
                        int n = Integer.parseInt(number.trim());
                        coordinator.evaluate(PatientData.example(n), "CASO " + n);
                    }
                } else if (opts.containsKey("dados")) coordinator.evaluate(PatientData.load(Path.of(opts.get("dados"))), "ARQUIVO");
                else coordinator.interactive();
            } finally { Runtime.getRuntime().removeShutdownHook(hook); }
        }
    }
}
