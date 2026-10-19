package br.edu.cardio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class EventLog implements AutoCloseable {
    private final PrintWriter file;
    private final String process;
    private boolean closed;

    public EventLog(Path directory, String process) throws IOException {
        Files.createDirectories(directory);
        this.process = process;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        file = new PrintWriter(Files.newBufferedWriter(directory.resolve(process + "-" + stamp + "-"
                + ProcessHandle.current().pid() + ".log"), StandardCharsets.UTF_8), true);
    }

    public synchronized void event(String action, String detail) {
        if (closed) return;
        String safe = detail.replace("\r", "\\r").replace("\n", "\\n");
        String line = OffsetDateTime.now() + " [" + process + "] ["
                + Thread.currentThread().getName() + "] " + action + " " + safe;
        System.out.println(line);
        file.println(line);
    }

    @Override public synchronized void close() {
        if (!closed) { closed = true; file.close(); }
    }
}
