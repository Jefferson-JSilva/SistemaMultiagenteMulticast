package br.edu.cardio;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Raw inputs only: scoring always runs inside the corresponding agent JVM. */
public record PatientData(String smoking, String sedentary, String pressure, String bmi) {
    public Map<Factor, String> requests() {
        return Map.of(Factor.TABAGISMO, smoking, Factor.SEDENTARISMO, sedentary,
                Factor.PRESSAO, pressure, Factor.IMC, bmi);
    }

    public static PatientData example(int number) {
        return switch (number) {
            case 1 -> new PatientData("0,0,0,0,0", "0,0,0,0,0", "115,75", "65,1.70");
            case 2 -> new PatientData("4,3,3,1,2", "3,2,1,2,1", "185,115", "125,1.70");
            case 3 -> new PatientData("4,2,2,0,0", "2,2,1,2,0", "150,95", "92,1.75");
            case 4 -> new PatientData("4,3,3,1,2", "3,2,1,2,1", "115,75", "65,1.70");
            case 5 -> new PatientData("0,0,0,0,0", "0,0,0,0,0", "185,115", "125,1.70");
            case 6 -> new PatientData("2,1,1,0,0", "1,1,0,1,0", "135,85", "80,1.70");
            default -> throw new IllegalArgumentException("caso deve estar em 1..6");
        };
    }

    public static PatientData load(Path path) throws IOException {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
        for (String key : List.of("tabagismo", "sedentarismo", "pas", "pad", "peso", "altura"))
            if (!p.containsKey(key) || p.getProperty(key).isBlank())
                throw new IllegalArgumentException("campo ausente: " + key);
        return new PatientData(p.getProperty("tabagismo"), p.getProperty("sedentarismo"),
                p.getProperty("pas") + "," + p.getProperty("pad"),
                p.getProperty("peso") + "," + p.getProperty("altura"));
    }

    public static PatientData interview(BufferedReader reader) throws IOException {
        String[] t = {
            "T1 Situacao: nunca=0; parou >5 anos=1; parou <5 anos=2; fumante=4",
            "T2 Cigarros/dia: nenhum=0; 1..10=1; 11..20=2; >20=3",
            "T3 Tempo fumando: nenhum=0; <10 anos=1; 10..20=2; >20=3",
            "T4 Convive diariamente com fumantes: nao=0; sim=1",
            "T5 Eletronico/narguile: nao=0; ocasional=1; diario=2"};
        String[] s = {
            "S1 Atividade semanal: >=150 min=0; 75..149=1; 1..74=2; nenhum=3",
            "S2 Sentado/deitado fora sono: <4h=0; 4..8h=1; >8h=2",
            "S3 Anda/pedala >=10 min/dia: sim=0; nao=1",
            "S4 Forca por semana: >=2 vezes=0; 1 vez=1; nenhuma=2",
            "S5 Escadas/tarefas vigorosas regularmente: sim=0; nao=1"};
        List<String> tv = new ArrayList<>(), sv = new ArrayList<>();
        for (String question : t) tv.add(ask(reader, question));
        for (String question : s) sv.add(ask(reader, question));
        String pas = ask(reader, "Pressao sistolica (mmHg)");
        String pad = ask(reader, "Pressao diastolica (mmHg)");
        String weight = ask(reader, "Peso (kg; use ponto decimal)");
        String height = ask(reader, "Altura (m; use ponto decimal)");
        return new PatientData(String.join(",", tv), String.join(",", sv), pas + "," + pad, weight + "," + height);
    }

    private static String ask(BufferedReader reader, String question) throws IOException {
        System.out.println(question + " [sair cancela]");
        String value = reader.readLine();
        if (value == null || value.trim().equalsIgnoreCase("sair")) throw new EOFException("entrevista cancelada");
        value = value.trim();
        // Empty answers must reach agent validation without breaking the five-field envelope.
        return value.isEmpty() ? "?" : value;
    }
}
