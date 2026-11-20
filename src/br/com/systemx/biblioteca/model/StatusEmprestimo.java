package br.com.systemx.biblioteca.model;

public enum StatusEmprestimo {
    AGUARDANDO_RETIRADA("Aguardando Retirada"),
    EM_ANDAMENTO("Em dia"),
    ATRASADO("Atrasado"),
    DEVOLVIDO("Devolvido"),
    CANCELADO("Cancelado");

    private final String label;

    StatusEmprestimo(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isAtivo() {
        return this == AGUARDANDO_RETIRADA || this == EM_ANDAMENTO || this == ATRASADO;
    }

    public static StatusEmprestimo fromString(String value) {
        if (value == null || value.isBlank()) {
            return EM_ANDAMENTO;
        }
        String normalized = value.trim().toUpperCase();
        for (StatusEmprestimo status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return EM_ANDAMENTO;
    }
}
