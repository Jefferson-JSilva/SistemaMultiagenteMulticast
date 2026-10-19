package br.edu.cardio;

/** Wire format: UTF-8, exactly five semicolon-separated fields. */
public record Message(Type type, String evaluation, String origin, String destination, String data) {
    public enum Type { REQ, RESP, ERRO }

    public Message {
        if (type == null) throw new IllegalArgumentException("tipo ausente");
        for (String field : new String[]{evaluation, origin, destination, data}) {
            if (field == null || field.isBlank() || field.length() > 1000
                    || field.indexOf(';') >= 0 || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0)
                throw new IllegalArgumentException("campo vazio, longo ou delimitador invalido");
        }
    }

    public static Message parse(String text) {
        String[] parts = text.split(";", -1);
        if (parts.length != 5) throw new IllegalArgumentException("esperados cinco campos");
        return new Message(Type.valueOf(parts[0]), parts[1], parts[2], parts[3], parts[4]);
    }

    public String encode() {
        return type + ";" + evaluation + ";" + origin + ";" + destination + ";" + data;
    }
}
