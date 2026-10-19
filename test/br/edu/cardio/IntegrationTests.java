package br.edu.cardio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Launches actual independent JVMs and verifies their multicast logs. */
public final class IntegrationTests {
    private record Child(Process process, Path output) {
        String text() throws IOException { return Files.readString(output, StandardCharsets.UTF_8); }
        void command(String command) throws IOException {
            process.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
        }
    }
    private static final List<Child> children = new ArrayList<>();
    private static final List<String> results = new ArrayList<>();
    private static List<String> common;
    private static Path directory;
    private static int sequence;

    private static Child start(String main, String... options) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"),"br.edu.cardio." + main));
        cmd.addAll(common); cmd.addAll(Arrays.asList(options));
        Path output = directory.resolve(String.format("%02d-%s-console.log", ++sequence,main));
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        Child child = new Child(process,output); children.add(child); return child;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void until(Child child, String fragment) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (child.text().contains(fragment)) return;
            if (!child.process().isAlive()) break;
            Thread.sleep(30);
        }
        throw new AssertionError("Nao encontrou '" + fragment + "' em " + child.output() + "\n" + child.text());
    }
    private static String done(Child child) throws Exception {
        require(child.process().waitFor(20,TimeUnit.SECONDS),"processo excedeu prazo: " + child.output());
        String text = child.text();
        require(child.process().exitValue()==0,"processo falhou: " + text);
        require(text.contains(" LEAVE ") && text.contains(" CLOSE "),"encerramento incompleto: " + child.output());
        return text;
    }
    private static void stop(Child child) throws Exception {
        if (child.process().isAlive()) child.command("sair");
        done(child);
    }
    private static String resultLine(String text, String label) {
        return text.lines().filter(l -> l.contains(" RESULTADO " + label + " ")).findFirst()
                .orElseThrow(() -> new AssertionError("resultado ausente: " + label + "\n" + text));
    }
    private static void passed(String name, String detail) {
        String line = OffsetDateTime.now() + " PASS " + name + " | " + detail;
        results.add(line); System.out.println(line);
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        directory = Path.of("evidencias/local/multicast-" + System.currentTimeMillis());
        Files.createDirectories(directory);
        common = List.of("--interface",config.nic().getName(),"--endereco",config.group().getHostAddress(),
                "--porta","" + config.port(),"--timeout-ms","3000","--logs",directory.resolve("logs").toString());
        results.add("Teste LOCAL: JVMs independentes, UMA maquina. Nao comprova distribuicao em tres maquinas.");
        results.add("Java=" + System.getProperty("java.version") + " OS=" + System.getProperty("os.name")
                + " interface=" + config.nic().getName() + " IPv4=" + config.group().getHostAddress() + ":" + config.port());
        try {
            start("Tabagismo"); start("Sedentarismo"); start("Pressao"); Child imc = start("Imc");
            for (Child child : List.copyOf(children)) until(child,"pronto.");
            Child normal = start("Coordenador","--casos","1,2,3,4,5,6");
            String text = done(normal);
            String[] states = {"F","V","NAO_EXTREMO","T","PARACOMPLETO","NAO_EXTREMO"};
            String[] gcs = {"-1.000","1.000","0.115","0.000","0.000","-0.465"};
            String[] gcts = {"0.000","0.000","0.279","1.000","-1.000","0.106"};
            for (int n = 1; n <= 6; n++) {
                String line = resultLine(text,"CASO " + n);
                require(line.contains("estado=" + states[n-1] + " ") && line.contains("Gc=" + gcs[n-1])
                        && line.contains("Gct=" + gcts[n-1]),"resultado incorreto: " + line);
                passed("caso-" + n,line.substring(line.indexOf(" RESULTADO ")+11));
            }
            stop(imc);
            long before = System.nanoTime();
            Child failure = start("Coordenador","--caso","3");
            String failureText = done(failure);
            require(failureText.contains("AUSENTE IMC") && failureText.contains("INCOMPLETA")
                    && !failureText.contains(" RESULTADO "),"falha do agente nao detectada");
            require(System.nanoTime()-before >= TimeUnit.MILLISECONDS.toNanos(2900),"timeout encerrou prematuramente");
            passed("falha-agente","IMC ausente; prazo de 3000 ms; sem consolidacao");
            imc = start("Imc"); until(imc,"pronto.");
            require(resultLine(done(start("Coordenador","--caso","3")),"CASO 3").contains("Gc=0.115"),"reingresso falhou");
            passed("reingresso","IMC reiniciado sem reiniciar os outros agentes");
            Child one = start("Coordenador"), two = start("Coordenador");
            until(one,"Comandos:"); until(two,"Comandos:");
            one.command("caso 1"); two.command("caso 2");
            until(one," RESULTADO "); until(two," RESULTADO ");
            require(resultLine(one.text(),"CASO 1").contains("estado=F "),"coordenador 1 contaminado");
            require(resultLine(two.text(),"CASO 2").contains("estado=V "),"coordenador 2 contaminado");
            require(!one.text().contains(" RESULTADO CASO 2 ") && !two.text().contains(" RESULTADO CASO 1 "),"mistura de avaliacoes");
            stop(one); stop(two);
            passed("coordenadores-simultaneos","casos 1 e 2 correlacionados por UUID e destino proprio");
            Child duplicate = start("Tabagismo","--delay-ms","150"); until(duplicate,"pronto.");
            Child interactive = start("Coordenador"); until(interactive,"Comandos:"); interactive.command("caso 3");
            until(interactive,"DESCARTADA_DUPLICADA"); until(interactive," RESULTADO ");
            require(interactive.text().lines().filter(l->l.contains(" RESULTADO ")).count()==1,"duplicata consolidada");
            stop(interactive); stop(duplicate);
            passed("agente-duplicado","segunda resposta TABAGISMO ignorada e registrada");
            String bad = done(start("Coordenador","--dados","config/paciente-invalido.properties"));
            require(bad.contains("ERRO_AGENTE IMC") && bad.contains("INCOMPLETA") && !bad.contains(" RESULTADO "),"altura zero nao rejeitada");
            passed("entrada-invalida","altura zero devolve ERRO do IMC; avaliacao incompleta");
            // Two queued requests must begin on separate workers before either delayed reply.
            stop(imc);
            Child slow = start("Imc","--delay-ms","600","--threads","2"); until(slow,"pronto.");
            Child three = start("Coordenador"), four = start("Coordenador");
            until(three,"Comandos:"); until(four,"Comandos:");
            three.command("caso 1"); four.command("caso 2");
            until(three," RESULTADO "); until(four," RESULTADO ");
            List<String> processing = slow.text().lines().filter(l->l.contains(" PROCESSANDO ") || l.contains(" ENVIADA RESP;")).toList();
            require(processing.size()>=4 && processing.get(0).contains("PROCESSANDO")
                    && processing.get(1).contains("PROCESSANDO"),"processamento bloqueou recepcao ou nao houve paralelismo");
            stop(three); stop(four); stop(slow);
            passed("pool-concorrente","duas requisicoes IMC iniciadas antes da primeira resposta com atraso de 600 ms");
            for (Child child : children) if (child.process().isAlive()) stop(child);
            passed("encerramento","todos os processos encerrados com sair e registros LEAVE/CLOSE");
            Files.write(directory.resolve("resumo.txt"),results,StandardCharsets.UTF_8);
            Files.writeString(Path.of("evidencias/local/ultimo-teste.txt"),directory.toString(),StandardCharsets.UTF_8);
            System.out.println("PASS: todos os cenarios multicast. Evidencias: " + directory);
        } finally {
            for (Child child : children) if (child.process().isAlive()) {
                try { child.command("sair"); child.process().waitFor(5,TimeUnit.SECONDS); }
                catch (Exception ignored) { }
                if (child.process().isAlive()) child.process().destroyForcibly();
            }
            Files.write(directory.resolve("resumo.txt"),results,StandardCharsets.UTF_8);
        }
    }
}
