package br.com.systemx.biblioteca.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Leitor {
    private long id;
    private String nome;
    private String endereco;
    private String cpf;
    private String telefone;
    private String email;
    private String perfil;
    private boolean ativo;
    private String criadoEm;

    public Leitor() {
    }

    public Leitor(long id, String nome, String endereco, String cpf, String telefone,
                  String email, String perfil, boolean ativo, String criadoEm) {
        this.id = id;
        this.nome = nome;
        this.endereco = endereco;
        this.cpf = cpf;
        this.telefone = telefone;
        this.email = email;
        this.perfil = perfil;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("nome", nome);
        map.put("endereco", endereco);
        map.put("cpf", cpf);
        map.put("telefone", telefone);
        map.put("email", email);
        map.put("perfil", perfil != null && !perfil.isBlank() ? perfil : "Leitor");
        map.put("ativo", ativo);
        map.put("criadoEm", criadoEm);
        return map;
    }
}
