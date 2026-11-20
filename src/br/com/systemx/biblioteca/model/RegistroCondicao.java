package br.com.systemx.biblioteca.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class RegistroCondicao {
    private long id;
    private long livroId;
    private String acao; // MARCAR_DANIFICADO, REPARAR_DISPONIBILIZAR, DEVOLUCAO_DANIFICADA
    private int quantidade;
    private String observacao;
    private String responsavel;
    private String dataHora;
    private boolean supervisionado;
    private String supervisorNome;
    private String dataSupervisao;

    public RegistroCondicao() {
    }

    public RegistroCondicao(long id, long livroId, String acao, int quantidade, String observacao,
                            String responsavel, String dataHora, boolean supervisionado,
                            String supervisorNome, String dataSupervisao) {
        this.id = id;
        this.livroId = livroId;
        this.acao = acao;
        this.quantidade = quantidade;
        this.observacao = observacao;
        this.responsavel = responsavel;
        this.dataHora = dataHora;
        this.supervisionado = supervisionado;
        this.supervisorNome = supervisorNome;
        this.dataSupervisao = dataSupervisao;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getLivroId() { return livroId; }
    public void setLivroId(long livroId) { this.livroId = livroId; }

    public String getAcao() { return acao; }
    public void setAcao(String acao) { this.acao = acao; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public String getResponsavel() { return responsavel; }
    public void setResponsavel(String responsavel) { this.responsavel = responsavel; }

    public String getDataHora() { return dataHora; }
    public void setDataHora(String dataHora) { this.dataHora = dataHora; }

    public boolean isSupervisionado() { return supervisionado; }
    public void setSupervisionado(boolean supervisionado) { this.supervisionado = supervisionado; }

    public String getSupervisorNome() { return supervisorNome; }
    public void setSupervisorNome(String supervisorNome) { this.supervisorNome = supervisorNome; }

    public String getDataSupervisao() { return dataSupervisao; }
    public void setDataSupervisao(String dataSupervisao) { this.dataSupervisao = dataSupervisao; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("livroId", livroId);
        map.put("acao", acao);
        map.put("quantidade", quantidade);
        map.put("observacao", observacao);
        map.put("responsavel", responsavel);
        map.put("dataHora", dataHora);
        map.put("supervisionado", supervisionado);
        map.put("supervisorNome", supervisorNome);
        map.put("dataSupervisao", dataSupervisao);
        return map;
    }
}
