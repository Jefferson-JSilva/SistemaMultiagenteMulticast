package br.edu.cardio;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public record Config(InetAddress group, int port, NetworkInterface nic,
                     int timeoutMs, int threads, int delayMs, Path logDirectory,
                     Map<String, String> options) {
    public static Config parse(String[] args) throws IOException {
        Map<String, String> cli = new LinkedHashMap<>();
        Set<String> allowed = Set.of("config", "grupo", "endereco", "porta", "interface",
                "timeout-ms", "threads", "delay-ms", "logs", "caso", "casos", "dados");
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 == args.length)
                throw new IllegalArgumentException("use --opcao valor (ou --ajuda)");
            String key = args[i].substring(2);
            if (!allowed.contains(key)) throw new IllegalArgumentException("opcao desconhecida: " + key);
            cli.put(key, args[i + 1]);
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (cli.containsKey("config")) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(Path.of(cli.get("config")))) {
                properties.load(reader);
            }
            for (String key : properties.stringPropertyNames()) {
                if (!allowed.contains(key) || key.equals("config"))
                    throw new IllegalArgumentException("propriedade desconhecida: " + key);
                values.put(key, properties.getProperty(key).trim());
            }
        }
        values.putAll(cli);
        int groupNumber = integer(values.getOrDefault("grupo", "7"), 1, 254, "grupo");
        String address = values.getOrDefault("endereco", "239.0.0." + groupNumber);
        if (!address.matches("[0-9.]+")) throw new IllegalArgumentException("endereco deve ser IPv4 numerico");
        InetAddress group = InetAddress.getByName(address);
        if (!(group instanceof Inet4Address) || !group.isMulticastAddress())
            throw new IllegalArgumentException("endereco deve ser IPv4 multicast");
        int port = integer(values.getOrDefault("porta", "" + (5000 + groupNumber)), 1, 65535, "porta");
        String name = values.get("interface");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("informe --interface nome-ou-IP; consulte --listar-interfaces");
        NetworkInterface nic = NetworkInterface.getByName(name);
        if (nic == null && name.matches("[0-9.]+"))
            nic = NetworkInterface.getByInetAddress(InetAddress.getByName(name));
        if (nic == null || !nic.isUp()) throw new IllegalArgumentException("interface inexistente ou inativa: " + name);
        if (!nic.supportsMulticast()) throw new IllegalArgumentException("interface sem suporte multicast: " + name);
        return new Config(group, port, nic,
                integer(values.getOrDefault("timeout-ms", "3000"), 1, 600000, "timeout-ms"),
                integer(values.getOrDefault("threads", "4"), 1, 64, "threads"),
                integer(values.getOrDefault("delay-ms", "0"), 0, 60000, "delay-ms"),
                Path.of(values.getOrDefault("logs", "logs")), Map.copyOf(values));
    }

    private static int integer(String value, int min, int max, String label) {
        int n;
        try { n = Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(label + " deve ser inteiro"); }
        if (n < min || n > max) throw new IllegalArgumentException(label + " fora de " + min + ".." + max);
        return n;
    }

    public static boolean showHelp(String[] args) throws SocketException {
        if (Arrays.asList(args).contains("--listar-interfaces")) {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                System.out.printf("%s | %s | ativa=%s multicast=%s | %s%n", nic.getName(),
                        nic.getDisplayName(), nic.isUp(), nic.supportsMulticast(),
                        Collections.list(nic.getInetAddresses()));
            }
            return true;
        }
        if (Arrays.asList(args).contains("--ajuda")) {
            System.out.println("Java 17+ | --interface nome-ou-IP (obrigatorio) | --grupo G (padrao 7)");
            System.out.println("--endereco IPv4 --porta N --timeout-ms 3000 --threads 4 --logs logs");
            System.out.println("--config arquivo.properties | --listar-interfaces | --delay-ms 0 (teste)");
            System.out.println("Coordenador: --caso 1..6 | --casos 1,2,3,4,5,6 | --dados paciente.properties");
            System.out.println("Sem caso/dados: questionarios interativos; digite sair para encerrar.");
            return true;
        }
        return false;
    }
}
