package br.edu.cardio;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Agent implements AutoCloseable {
    private final Factor factor;
    private final Config config;
    private final EventLog log;
    private final MulticastChannel channel;
    private final ThreadPoolExecutor workers;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Thread receiver;

    private Agent(Factor factor, Config config) throws IOException {
        this.factor = factor;
        this.config = config;
        log = new EventLog(config.logDirectory(), factor.name());
        try { channel = new MulticastChannel(config, log); }
        catch (IOException | RuntimeException e) { log.close(); throw e; }
        workers = new ThreadPoolExecutor(config.threads(), config.threads(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(128), new ThreadPoolExecutor.AbortPolicy());
    }

    private void listen() {
        while (!closed.get()) {
            try {
                Message message = channel.receive();
                if (message == null) continue;
                if (message.origin().equals(factor.name()) || message.type() != Message.Type.REQ
                        || !message.destination().equals(factor.name())
                        || !message.origin().startsWith("COORD-")) {
                    log.event("DESCARTADA", "origem/tipo/destino: " + message.encode());
                    continue;
                }
                try { workers.execute(() -> process(message)); }
                catch (RejectedExecutionException e) {
                    log.event("FILA_CHEIA", message.evaluation());
                    reply(message, Message.Type.ERRO, "agente ocupado: fila cheia");
                }
            } catch (SocketTimeoutException ignored) {
                // Periodic wake-up to inspect the lifecycle flag.
            } catch (IOException e) {
                if (!closed.get()) log.event("FALHA_RECEPCAO", e.toString());
            }
        }
    }

    private void process(Message request) {
        log.event("PROCESSANDO", request.evaluation());
        try {
            if (config.delayMs() > 0) Thread.sleep(config.delayMs());
            double mu = Evidence.evaluate(factor, request.data());
            reply(request, Message.Type.RESP, Double.toString(mu));
        } catch (IllegalArgumentException e) {
            reply(request, Message.Type.ERRO, e.getMessage());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void reply(Message request, Message.Type type, String data) {
        try { channel.send(new Message(type, request.evaluation(), factor.name(), request.origin(), data)); }
        catch (IOException e) { if (!closed.get()) log.event("FALHA_ENVIO", e.toString()); }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        workers.shutdownNow();
        channel.close();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
            if (receiver != null && receiver != Thread.currentThread()) receiver.join(1000);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log.event("ENCERRADO", "agente finalizado");
        log.close();
    }

    public static void run(Factor factor, String[] args) throws Exception {
        if (Config.showHelp(args)) return;
        try (Agent agent = new Agent(factor, Config.parse(args))) {
            Thread hook = new Thread(agent::close, "shutdown-" + factor);
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                agent.receiver = new Thread(agent::listen, "recepcao-" + factor);
                agent.receiver.start();
                System.out.println("Agente " + factor + " pronto. Digite sair para encerrar.");
                BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                String command;
                while ((command = input.readLine()) != null) {
                    if (command.trim().equalsIgnoreCase("sair")) break;
                    System.out.println("Comando disponivel: sair");
                }
            } finally { Runtime.getRuntime().removeShutdownHook(hook); }
        }
    }
}
