package br.edu.cardio;

/** Educational scales from the assignment. Never rounds intermediate values. */
public final class Evidence {
    private Evidence() { }

    public static double evaluate(Factor factor, String data) {
        return switch (factor) {
            case TABAGISMO -> questionnaire(data, new int[][]{
                    {0,1,2,4}, {0,1,2,3}, {0,1,2,3}, {0,1}, {0,1,2}}, 13);
            case SEDENTARISMO -> questionnaire(data, new int[][]{
                    {0,1,2,3}, {0,1,2}, {0,1}, {0,1,2}, {0,1}}, 9);
            case PRESSAO -> pressure(data);
            case IMC -> bmi(data);
        };
    }

    private static double questionnaire(String data, int[][] allowed, int maximum) {
        String[] fields = split(data, 5);
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            int point;
            try { point = Integer.parseInt(fields[i].trim()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("alternativa nao inteira na pergunta " + (i+1)); }
            boolean valid = false;
            for (int option : allowed[i]) if (point == option) valid = true;
            if (!valid) throw new IllegalArgumentException("alternativa inexistente na pergunta " + (i+1));
            sum += point;
        }
        return clamp((double) sum / maximum);
    }

    private static double pressure(String data) {
        String[] fields = split(data, 2);
        double systolic = positive(fields[0], "pressao sistolica");
        double diastolic = positive(fields[1], "pressao diastolica");
        return Math.max(clamp((systolic - 120) / 60), clamp((diastolic - 80) / 30));
    }

    private static double bmi(String data) {
        String[] fields = split(data, 2);
        double weight = positive(fields[0], "peso");
        double height = positive(fields[1], "altura");
        if (height < 0.5 || height > 2.5)
            throw new IllegalArgumentException("altura invalida: faixa didatica de 0.50 a 2.50 m");
        return clamp((weight / (height * height) - 25) / 15);
    }

    private static double positive(String value, String label) {
        double n;
        try { n = Double.parseDouble(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(label + " deve ser numero"); }
        if (!Double.isFinite(n) || n <= 0) throw new IllegalArgumentException(label + " deve ser finita e positiva");
        return n;
    }

    private static String[] split(String data, int length) {
        String[] fields = data.split(",", -1);
        if (fields.length != length) throw new IllegalArgumentException("esperados " + length + " valores");
        return fields;
    }

    public static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
