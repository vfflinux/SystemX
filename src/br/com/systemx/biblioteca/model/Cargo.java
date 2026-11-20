package br.com.systemx.biblioteca.model;

public enum Cargo {
    VISUALIZADOR("Somente Visualização"),
    FUNCIONARIO("Funcionário"),
    ADMINISTRADOR("Administrador");

    private final String descricao;

    Cargo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean podeEscrever() {
        return this == FUNCIONARIO || this == ADMINISTRADOR;
    }

    public static Cargo fromString(String value) {
        if (value == null || value.isBlank()) {
            return FUNCIONARIO;
        }
        String normalized = value.trim().toUpperCase();
        for (Cargo cargo : values()) {
            if (cargo.name().equals(normalized) || cargo.descricao.equalsIgnoreCase(value.trim())) {
                return cargo;
            }
        }
        return FUNCIONARIO;
    }
}
