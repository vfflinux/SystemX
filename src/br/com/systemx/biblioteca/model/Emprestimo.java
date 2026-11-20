package br.com.systemx.biblioteca.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Emprestimo {
    private long id;
    private String codigo;
    private long leitorId;
    private long livroId;
    private String dataEmprestimo;
    private String dataPrevistaDevolucao;
    private String dataEfetivaDevolucao;
    private StatusEmprestimo status;
    private boolean prorrogado;
    private boolean retirado;
    private CondicaoExemplar condicaoDevolucao;
    private String observacaoCondicao;
    private String funcionarioResponsavel;

    public Emprestimo() {
    }

    public Emprestimo(long id, String codigo, long leitorId, long livroId,
                      String dataEmprestimo, String dataPrevistaDevolucao, String dataEfetivaDevolucao,
                      StatusEmprestimo status, boolean prorrogado, boolean retirado,
                      CondicaoExemplar condicaoDevolucao, String observacaoCondicao,
                      String funcionarioResponsavel) {
        this.id = id;
        this.codigo = codigo;
        this.leitorId = leitorId;
        this.livroId = livroId;
        this.dataEmprestimo = dataEmprestimo;
        this.dataPrevistaDevolucao = dataPrevistaDevolucao;
        this.dataEfetivaDevolucao = dataEfetivaDevolucao;
        this.status = status;
        this.prorrogado = prorrogado;
        this.retirado = retirado;
        this.condicaoDevolucao = condicaoDevolucao;
        this.observacaoCondicao = observacaoCondicao;
        this.funcionarioResponsavel = funcionarioResponsavel;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public long getLeitorId() { return leitorId; }
    public void setLeitorId(long leitorId) { this.leitorId = leitorId; }

    public long getLivroId() { return livroId; }
    public void setLivroId(long livroId) { this.livroId = livroId; }

    public String getDataEmprestimo() { return dataEmprestimo; }
    public void setDataEmprestimo(String dataEmprestimo) { this.dataEmprestimo = dataEmprestimo; }

    public String getDataPrevistaDevolucao() { return dataPrevistaDevolucao; }
    public void setDataPrevistaDevolucao(String dataPrevistaDevolucao) { this.dataPrevistaDevolucao = dataPrevistaDevolucao; }

    public String getDataEfetivaDevolucao() { return dataEfetivaDevolucao; }
    public void setDataEfetivaDevolucao(String dataEfetivaDevolucao) { this.dataEfetivaDevolucao = dataEfetivaDevolucao; }

    public StatusEmprestimo getStatus() { return status; }
    public void setStatus(StatusEmprestimo status) { this.status = status; }

    public boolean isProrrogado() { return prorrogado; }
    public void setProrrogado(boolean prorrogado) { this.prorrogado = prorrogado; }

    public boolean isRetirado() { return retirado; }
    public void setRetirado(boolean retirado) { this.retirado = retirado; }

    public CondicaoExemplar getCondicaoDevolucao() { return condicaoDevolucao; }
    public void setCondicaoDevolucao(CondicaoExemplar condicaoDevolucao) { this.condicaoDevolucao = condicaoDevolucao; }

    public String getObservacaoCondicao() { return observacaoCondicao; }
    public void setObservacaoCondicao(String observacaoCondicao) { this.observacaoCondicao = observacaoCondicao; }

    public String getFuncionarioResponsavel() { return funcionarioResponsavel; }
    public void setFuncionarioResponsavel(String funcionarioResponsavel) { this.funcionarioResponsavel = funcionarioResponsavel; }

    public Map<String, Object> toStorageMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("codigo", codigo);
        map.put("leitorId", leitorId);
        map.put("livroId", livroId);
        map.put("dataEmprestimo", dataEmprestimo);
        map.put("dataPrevistaDevolucao", dataPrevistaDevolucao);
        map.put("dataEfetivaDevolucao", dataEfetivaDevolucao);
        map.put("status", status.name());
        map.put("prorrogado", prorrogado);
        map.put("retirado", retirado);
        map.put("condicaoDevolucao", condicaoDevolucao != null ? condicaoDevolucao.name() : null);
        map.put("observacaoCondicao", observacaoCondicao);
        map.put("funcionarioResponsavel", funcionarioResponsavel);
        return map;
    }
}
