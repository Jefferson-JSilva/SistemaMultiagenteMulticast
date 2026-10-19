package br.edu.cardio;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** No unicast destinations: every send uses the configured multicast endpoint. */
public final class MulticastChannel implements AutoCloseable {
    private final MulticastSocket socket;
    private final InetSocketAddress endpoint;
    private final NetworkInterface nic;
    private final EventLog log;
    private final AtomicBoolean closed = new AtomicBoolean();

    public MulticastChannel(Config config, EventLog log) throws IOException {
        this.log = log;
        endpoint = new InetSocketAddress(config.group(), config.port());
        nic = config.nic();
        socket = new MulticastSocket(null);
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(config.port()));
            socket.setNetworkInterface(nic);
            socket.setTimeToLive(1);
            socket.setSoTimeout(200);
            socket.joinGroup(endpoint, nic);
            log.event("JOIN", endpoint + " interface=" + nic.getName() + " TTL=1");
        } catch (IOException | RuntimeException e) { socket.close(); throw e; }
    }

    public synchronized void send(Message message) throws IOException {
        byte[] bytes = message.encode().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 4096) throw new IOException("datagrama excede 4096 bytes");
        socket.send(new DatagramPacket(bytes, bytes.length, endpoint));
        log.event("ENVIADA", message.encode());
    }

    public Message receive() throws IOException {
        byte[] bytes = new byte[8193];
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);
        String raw = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
        log.event("RECEBIDA", raw);
        try {
            if (packet.getLength() > 4096) throw new IllegalArgumentException("datagrama muito longo");
            return Message.parse(raw);
        } catch (IllegalArgumentException e) {
            log.event("DESCARTADA", "protocolo invalido: " + e.getMessage());
            return null;
        }
    }

    @Override public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.leaveGroup(endpoint, nic); log.event("LEAVE", endpoint.toString()); }
        catch (IOException e) { log.event("FALHA_LEAVE", e.toString()); }
        finally { socket.close(); log.event("CLOSE", "socket fechado"); }
    }
}
