package br.com.systemx.biblioteca.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Livro {
    private long id;
    private String titulo;
    private String autor;
    private String editora;
    private String isbn;
    private int anoPublicacao;
    private String categoria;
    private int quantidadeTotal;
    private int quantidadeEmprestada;
    private int quantidadeDanificada;
    private String corCapa;
    private boolean ativo;
    private String criadoEm;

    public Livro() {
    }

    public Livro(long id, String titulo, String autor, String editora, String isbn,
                 int anoPublicacao, String categoria, int quantidadeTotal,
                 int quantidadeEmprestada, int quantidadeDanificada,
                 String corCapa, boolean ativo, String criadoEm) {
        this.id = id;
        this.titulo = titulo;
        this.autor = autor;
        this.editora = editora;
        this.isbn = isbn;
        this.anoPublicacao = anoPublicacao;
        this.categoria = categoria;
        this.quantidadeTotal = quantidadeTotal;
        this.quantidadeEmprestada = quantidadeEmprestada;
        this.quantidadeDanificada = quantidadeDanificada;
        this.corCapa = corCapa;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getAutor() { return autor; }
    public void setAutor(String autor) { this.autor = autor; }

    public String getEditora() { return editora; }
    public void setEditora(String editora) { this.editora = editora; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public int getAnoPublicacao() { return anoPublicacao; }
    public void setAnoPublicacao(int anoPublicacao) { this.anoPublicacao = anoPublicacao; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public int getQuantidadeTotal() { return quantidadeTotal; }
    public void setQuantidadeTotal(int quantidadeTotal) { this.quantidadeTotal = quantidadeTotal; }

    public int getQuantidadeEmprestada() { return quantidadeEmprestada; }
    public void setQuantidadeEmprestada(int quantidadeEmprestada) { this.quantidadeEmprestada = quantidadeEmprestada; }

    public int getQuantidadeDanificada() { return quantidadeDanificada; }
    public void setQuantidadeDanificada(int quantidadeDanificada) { this.quantidadeDanificada = quantidadeDanificada; }

    public int getQuantidadeDisponivel() {
        return Math.max(0, quantidadeTotal - quantidadeEmprestada - quantidadeDanificada);
    }

    public String getCorCapa() { return corCapa; }
    public void setCorCapa(String corCapa) { this.corCapa = corCapa; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("titulo", titulo);
        map.put("autor", autor);
        map.put("editora", editora);
        map.put("isbn", isbn);
        map.put("anoPublicacao", anoPublicacao);
        map.put("categoria", categoria);
        map.put("quantidadeTotal", quantidadeTotal);
        map.put("quantidadeEmprestada", quantidadeEmprestada);
        map.put("quantidadeDanificada", quantidadeDanificada);
        map.put("quantidadeDisponivel", getQuantidadeDisponivel());
        map.put("corCapa", corCapa != null && !corCapa.isBlank() ? corCapa : "#3F4E4F");
        map.put("ativo", ativo);
        map.put("criadoEm", criadoEm);
        return map;
    }
}
