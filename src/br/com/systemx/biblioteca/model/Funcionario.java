package br.com.systemx.biblioteca.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Funcionario {
    private long id;
    private String nome;
    private String email;
    private String matricula;
    private String senhaHash;
    private Cargo cargo;
    private boolean ativo;
    private String criadoEm;

    public Funcionario() {
    }

    public Funcionario(long id, String nome, String email, String matricula, String senhaHash,
                       Cargo cargo, boolean ativo, String criadoEm) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.matricula = matricula;
        this.senhaHash = senhaHash;
        this.cargo = cargo;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }

    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }

    public Cargo getCargo() { return cargo; }
    public void setCargo(Cargo cargo) { this.cargo = cargo; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }

    public boolean isAdministrador() {
        return cargo == Cargo.ADMINISTRADOR;
    }

    public boolean isVisualizador() {
        return cargo == Cargo.VISUALIZADOR;
    }

    public boolean podeEscrever() {
        return cargo != null && cargo.podeEscrever();
    }

    public String getIniciais() {
        if (nome == null || nome.isBlank()) return "SX";
        String[] parts = nome.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    public Map<String, Object> toPublicMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("nome", nome);
        map.put("email", email);
        map.put("matricula", matricula);
        map.put("cargo", cargo.name());
        map.put("cargoDescricao", cargo.getDescricao());
        map.put("podeEscrever", podeEscrever());
        map.put("somenteVisualizacao", isVisualizador());
        map.put("iniciais", getIniciais());
        map.put("ativo", ativo);
        map.put("criadoEm", criadoEm);
        return map;
    }

    public Map<String, Object> toStorageMap() {
        Map<String, Object> map = toPublicMap();
        map.put("senhaHash", senhaHash);
        return map;
    }
}
