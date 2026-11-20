package br.com.systemx.biblioteca.model;

public enum CondicaoExemplar {
    BOAS_CONDICOES("Boas condições"),
    DANIFICADO("Danificado");

    private final String label;

    CondicaoExemplar(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static CondicaoExemplar fromString(String value) {
        if (value == null || value.isBlank()) {
            return BOAS_CONDICOES;
        }
        String normalized = value.trim().toUpperCase();
        for (CondicaoExemplar condicao : values()) {
            if (condicao.name().equals(normalized)) {
                return condicao;
            }
        }
        return BOAS_CONDICOES;
    }
}
