package br.com.systemx.biblioteca.service;

import br.com.systemx.biblioteca.model.*;
import br.com.systemx.biblioteca.repository.BibliotecaRepository;
import br.com.systemx.biblioteca.util.JsonUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class BibliotecaService {
    public static final int LIMITE_EMPRESTIMOS_SIMULTANEOS = 3;
    public static final int PRAZO_PADRAO_DIAS = 14;
    public static final int DIAS_PRORROGACAO = 14;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BibliotecaRepository repo;
    private final AuthService authService;

    public BibliotecaService(BibliotecaRepository repo, AuthService authService) {
        this.repo = repo;
        this.authService = authService;
    }

    /**
     * Atualiza automaticamente o status de empréstimos vencidos para ATRASADO.
     */
    public void atualizarAtrasos() {
        LocalDate hoje = LocalDate.now();
        boolean changed = false;
        synchronized (repo) {
            for (Emprestimo e : repo.getEmprestimos()) {
                if (e.getStatus() == StatusEmprestimo.EM_ANDAMENTO && e.getDataPrevistaDevolucao() != null) {
                    LocalDate prevista = LocalDate.parse(e.getDataPrevistaDevolucao(), DATE_FMT);
                    if (hoje.isAfter(prevista)) {
                        e.setStatus(StatusEmprestimo.ATRASADO);
                        changed = true;
                    }
                }
            }
            if (changed) {
                repo.save();
            }
        }
    }

    // ==================== DASHBOARD ====================

    public Map<String, Object> getDashboard(String filtro) {
        atualizarAtrasos();
        synchronized (repo) {
            LocalDate hoje = LocalDate.now();

            int totalExemplares = 0;
            int totalTitulosAtivos = 0;
            int totalDisponiveis = 0;
            int totalDanificados = 0;
            for (Livro l : repo.getLivros()) {
                if (l.isAtivo()) {
                    totalTitulosAtivos++;
                    totalExemplares += l.getQuantidadeTotal();
                    totalDisponiveis += l.getQuantidadeDisponivel();
                    totalDanificados += l.getQuantidadeDanificada();
                }
            }

            long usuariosCadastrados = repo.getLeitores().stream().filter(Leitor::isAtivo).count();
            long emprestimosAtivos = repo.getEmprestimos().stream()
                    .filter(e -> e.getStatus() == StatusEmprestimo.EM_ANDAMENTO
                            || e.getStatus() == StatusEmprestimo.AGUARDANDO_RETIRADA
                            || e.getStatus() == StatusEmprestimo.ATRASADO)
                    .count();
            long livrosAtrasados = repo.getEmprestimos().stream()
                    .filter(e -> e.getStatus() == StatusEmprestimo.ATRASADO)
                    .count();

            // Empréstimos recentes ordenados por mais recente (maior ID primeiro)
            List<Emprestimo> sorted = new ArrayList<>(repo.getEmprestimos());
            sorted.sort(Comparator.comparingLong(Emprestimo::getId).reversed());

            List<Map<String, Object>> recentes = new ArrayList<>();
            String f = filtro == null ? "todos" : filtro.trim().toLowerCase();
            for (Emprestimo e : sorted) {
                if (e.getCodigo() != null && e.getCodigo().startsWith("EMP-0") && recentes.size() >= 6) {
                    continue;
                }
                if (!matchesDashboardFilter(e, f, hoje)) continue;
                recentes.add(enrichEmprestimo(e, hoje));
                if (recentes.size() >= 6) break;
            }

            // Livros mais emprestados (Top 5)
            Map<Long, Integer> loanCounts = new HashMap<>();
            for (Emprestimo e : repo.getEmprestimos()) {
                if (e.getStatus() != StatusEmprestimo.CANCELADO) {
                    loanCounts.merge(e.getLivroId(), 1, Integer::sum);
                }
            }
            List<Map<String, Object>> topLivros = new ArrayList<>();
            List<Livro> sortedLivros = new ArrayList<>(repo.getLivros());
            sortedLivros.sort((a, b) -> Integer.compare(
                    loanCounts.getOrDefault(b.getId(), 0),
                    loanCounts.getOrDefault(a.getId(), 0)));

            for (int i = 0; i < Math.min(5, sortedLivros.size()); i++) {
                Livro livro = sortedLivros.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", livro.getId());
                item.put("titulo", livro.getTitulo());
                item.put("rotuloCurto", encurtarTitulo(livro.getTitulo()));
                item.put("quantidade", loanCounts.getOrDefault(livro.getId(), 0));
                topLivros.add(item);
            }

            // Notificações (atrasos, vencimento próximo, exemplares sem supervisão)
            List<Map<String, Object>> notificacoes = new ArrayList<>();
            for (Emprestimo e : sorted) {
                if (e.getStatus() == StatusEmprestimo.ATRASADO) {
                    Livro lv = findLivroById(e.getLivroId());
                    Leitor lt = findLeitorById(e.getLeitorId());
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("tipo", "ATRASO");
                    n.put("titulo", "Empréstimo atrasado (" + e.getCodigo() + ")");
                    n.put("mensagem", (lv != null ? lv.getTitulo() : "Livro") + " — " + (lt != null ? lt.getNome() : "Leitor"));
                    n.put("referenciaId", e.getId());
                    notificacoes.add(n);
                } else if (e.getStatus() == StatusEmprestimo.EM_ANDAMENTO && e.getDataPrevistaDevolucao() != null) {
                    LocalDate prev = LocalDate.parse(e.getDataPrevistaDevolucao(), DATE_FMT);
                    long dias = ChronoUnit.DAYS.between(hoje, prev);
                    if (dias >= 0 && dias <= 3) {
                        Livro lv = findLivroById(e.getLivroId());
                        Leitor lt = findLeitorById(e.getLeitorId());
                        Map<String, Object> n = new LinkedHashMap<>();
                        n.put("tipo", "VENCIMENTO");
                        n.put("titulo", "Vence em " + dias + " dia(s) (" + e.getCodigo() + ")");
                        n.put("mensagem", (lv != null ? lv.getTitulo() : "Livro") + " — " + (lt != null ? lt.getNome() : "Leitor"));
                        n.put("referenciaId", e.getId());
                        notificacoes.add(n);
                    }
                }
            }
            long pendentesSupervisao = repo.getRegistrosCondicao().stream()
                    .filter(r -> !r.isSupervisionado()).count();
            if (pendentesSupervisao > 0) {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("tipo", "CONDICAO");
                n.put("titulo", pendentesSupervisao + " ocorrência(s) de exemplar");
                n.put("mensagem", "Aguardando supervisão administrativa no módulo de acervo/configurações.");
                notificacoes.add(n);
            }

            Map<String, Object> kpis = new LinkedHashMap<>();
            kpis.put("totalLivros", totalExemplares);
            kpis.put("totalTitulos", totalTitulosAtivos);
            kpis.put("totalDisponiveis", totalDisponiveis);
            kpis.put("totalDanificados", totalDanificados);
            kpis.put("usuariosCadastrados", usuariosCadastrados);
            kpis.put("emprestimosAtivos", emprestimosAtivos);
            kpis.put("livrosAtrasados", livrosAtrasados);
            kpis.put("pendentesSupervisao", pendentesSupervisao);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("kpis", kpis);
            response.put("emprestimosRecentes", recentes);
            response.put("livrosMaisEmprestados", topLivros);
            response.put("notificacoes", notificacoes);
            return response;
        }
    }

    private boolean matchesDashboardFilter(Emprestimo e, String filtro, LocalDate hoje) {
        return switch (filtro) {
            case "no_prazo" -> e.getStatus() == StatusEmprestimo.EM_ANDAMENTO
                    || e.getStatus() == StatusEmprestimo.AGUARDANDO_RETIRADA;
            case "prox_vencimento" -> {
                if (e.getStatus() != StatusEmprestimo.EM_ANDAMENTO || e.getDataPrevistaDevolucao() == null) yield false;
                LocalDate prev = LocalDate.parse(e.getDataPrevistaDevolucao(), DATE_FMT);
                long dias = ChronoUnit.DAYS.between(hoje, prev);
                yield dias >= 0 && dias <= 4;
            }
            case "atrasados" -> e.getStatus() == StatusEmprestimo.ATRASADO;
            default -> true;
        };
    }

    private String encurtarTitulo(String titulo) {
        if (titulo == null) return "";
        if (titulo.length() <= 18) return titulo;
        return titulo.substring(0, 16) + "…";
    }

    // ==================== LEITORES (UC02, UC03, UC04, UC05) ====================

    public List<Map<String, Object>> listarLeitores(String busca, String statusFiltro) {
        atualizarAtrasos();
        synchronized (repo) {
            String q = busca == null ? "" : busca.trim().toLowerCase();
            String st = statusFiltro == null ? "todos" : statusFiltro.trim().toLowerCase();
            List<Map<String, Object>> result = new ArrayList<>();

            for (Leitor l : repo.getLeitores()) {
                Map<String, Object> item = enrichLeitor(l);
                boolean ativo = l.isAtivo();
                boolean temPendencia = Boolean.TRUE.equals(item.get("possuiPendencia"));

                if ("ativos".equals(st) && !ativo) continue;
                if ("desativados".equals(st) && ativo) continue;
                if ("pendencia".equals(st) && !temPendencia) continue;

                if (!q.isBlank()) {
                    boolean match = contains(l.getNome(), q)
                            || contains(l.getCpf(), q)
                            || contains(l.getEmail(), q)
                            || contains(l.getTelefone(), q)
                            || contains(l.getPerfil(), q);
                    if (!match) continue;
                }
                result.add(item);
            }
            result.sort((a, b) -> String.valueOf(a.get("nome")).compareToIgnoreCase(String.valueOf(b.get("nome"))));
            return result;
        }
    }

    public Map<String, Object> cadastrarLeitor(Map<String, Object> body) {
        String nome = requireText(body, "nome", "Nome do leitor é obrigatório.");
        String endereco = requireText(body, "endereco", "Endereço é obrigatório.");
        String cpf = requireText(body, "cpf", "CPF é obrigatório.");
        String telefone = requireText(body, "telefone", "Telefone é obrigatório.");
        String email = requireText(body, "email", "E-mail é obrigatório.");
        String perfil = JsonUtil.getStringOrDefault(body, "perfil", "Aluno");

        String cpfDigits = cpf.replaceAll("\\D", "");
        if (cpfDigits.length() != 11) {
            throw new BusinessException(400, "CPF inválido. Informe os 11 dígitos do CPF.");
        }

        synchronized (repo) {
            for (Leitor existente : repo.getLeitores()) {
                if (existente.getCpf() != null && existente.getCpf().replaceAll("\\D", "").equals(cpfDigits)) {
                    throw new BusinessException(409, "Já existe um leitor cadastrado com este CPF.");
                }
            }
            Leitor novo = new Leitor(
                    repo.nextLeitorId(),
                    nome,
                    endereco,
                    formatarCpf(cpfDigits),
                    telefone,
                    email,
                    perfil,
                    true,
                    LocalDate.now().format(DATE_FMT)
            );
            repo.getLeitores().add(novo);
            repo.save();
            return enrichLeitor(novo);
        }
    }

    public Map<String, Object> editarLeitor(long id, Map<String, Object> body) {
        String nome = requireText(body, "nome", "Nome do leitor é obrigatório.");
        String endereco = requireText(body, "endereco", "Endereço é obrigatório.");
        String cpf = requireText(body, "cpf", "CPF é obrigatório.");
        String telefone = requireText(body, "telefone", "Telefone é obrigatório.");
        String email = requireText(body, "email", "E-mail é obrigatório.");
        String perfil = JsonUtil.getStringOrDefault(body, "perfil", "Aluno");

        String cpfDigits = cpf.replaceAll("\\D", "");
        if (cpfDigits.length() != 11) {
            throw new BusinessException(400, "CPF inválido. Informe os 11 dígitos do CPF.");
        }

        synchronized (repo) {
            Leitor leitor = findLeitorById(id);
            if (leitor == null) throw new BusinessException(404, "Leitor não encontrado.");

            for (Leitor outro : repo.getLeitores()) {
                if (outro.getId() != id && outro.getCpf() != null
                        && outro.getCpf().replaceAll("\\D", "").equals(cpfDigits)) {
                    throw new BusinessException(409, "Outro leitor já utiliza este CPF.");
                }
            }

            leitor.setNome(nome);
            leitor.setEndereco(endereco);
            leitor.setCpf(formatarCpf(cpfDigits));
            leitor.setTelefone(telefone);
            leitor.setEmail(email);
            leitor.setPerfil(perfil);
            repo.save();
            return enrichLeitor(leitor);
        }
    }

    public Map<String, Object> alterarStatusLeitor(long id, boolean ativar) {
        atualizarAtrasos();
        synchronized (repo) {
            Leitor leitor = findLeitorById(id);
            if (leitor == null) throw new BusinessException(404, "Leitor não encontrado.");

            if (!ativar) {
                long ativos = countEmprestimosAtivosLeitor(id);
                boolean pendencias = hasPendenciaLeitor(id);
                if (ativos > 0 || pendencias) {
                    throw new BusinessException(409,
                            "Não é possível desativar o leitor: existem empréstimos ativos (" + ativos + ") ou pendências em aberto.");
                }
            }
            leitor.setAtivo(ativar);
            repo.save();
            return enrichLeitor(leitor);
        }
    }

    // ==================== LIVROS & CONDIÇÃO (UC06, UC07, UC08, UC09, UC15) ====================

    public List<Map<String, Object>> listarLivros(String busca, String categoria, String statusFiltro) {
        atualizarAtrasos();
        synchronized (repo) {
            String q = busca == null ? "" : busca.trim().toLowerCase();
            String cat = categoria == null ? "" : categoria.trim().toLowerCase();
            String st = statusFiltro == null ? "todos" : statusFiltro.trim().toLowerCase();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Livro l : repo.getLivros()) {
                if ("ativos".equals(st) && !l.isAtivo()) continue;
                if ("desativados".equals(st) && l.isAtivo()) continue;
                if ("disponiveis".equals(st) && (!l.isAtivo() || l.getQuantidadeDisponivel() <= 0)) continue;
                if ("danificados".equals(st) && l.getQuantidadeDanificada() <= 0) continue;

                if (!cat.isBlank() && !"todas".equals(cat)) {
                    if (l.getCategoria() == null || !l.getCategoria().equalsIgnoreCase(cat)) continue;
                }
                if (!q.isBlank()) {
                    boolean match = contains(l.getTitulo(), q)
                            || contains(l.getAutor(), q)
                            || contains(l.getEditora(), q)
                            || contains(l.getIsbn(), q)
                            || contains(l.getCategoria(), q);
                    if (!match) continue;
                }
                Map<String, Object> m = l.toMap();
                long totalEmprestimos = repo.getEmprestimos().stream()
                        .filter(e -> e.getLivroId() == l.getId() && e.getStatus() != StatusEmprestimo.CANCELADO)
                        .count();
                m.put("totalEmprestimosHistorico", totalEmprestimos);
                result.add(m);
            }
            result.sort((a, b) -> String.valueOf(a.get("titulo")).compareToIgnoreCase(String.valueOf(b.get("titulo"))));
            return result;
        }
    }

    public Map<String, Object> cadastrarLivro(Map<String, Object> body) {
        String titulo = requireText(body, "titulo", "Título do livro é obrigatório.");
        String autor = requireText(body, "autor", "Autor é obrigatório.");
        String editora = requireText(body, "editora", "Editora é obrigatória.");
        String isbn = requireText(body, "isbn", "ISBN é obrigatório.");
        int ano = JsonUtil.getInt(body, "anoPublicacao", 0);
        String categoria = requireText(body, "categoria", "Categoria é obrigatória.");
        int qtdTotal = JsonUtil.getInt(body, "quantidadeTotal", 0);
        String corCapa = JsonUtil.getStringOrDefault(body, "corCapa", "#3F4E4F");

        if (ano < 1450 || ano > LocalDate.now().getYear() + 1) {
            throw new BusinessException(400, "Informe um ano de publicação válido.");
        }
        if (qtdTotal < 1) {
            throw new BusinessException(400, "A quantidade de exemplares deve ser pelo menos 1.");
        }

        synchronized (repo) {
            for (Livro existente : repo.getLivros()) {
                if (existente.getIsbn() != null && existente.getIsbn().equalsIgnoreCase(isbn.trim())) {
                    throw new BusinessException(409, "Já existe um livro cadastrado com o ISBN " + isbn + ".");
                }
            }
            Livro novo = new Livro(
                    repo.nextLivroId(), titulo, autor, editora, isbn, ano, categoria,
                    qtdTotal, 0, 0, corCapa, true, LocalDate.now().format(DATE_FMT)
            );
            repo.getLivros().add(novo);
            repo.save();
            return novo.toMap();
        }
    }

    public Map<String, Object> editarLivro(long id, Map<String, Object> body) {
        String titulo = requireText(body, "titulo", "Título do livro é obrigatório.");
        String autor = requireText(body, "autor", "Autor é obrigatório.");
        String editora = requireText(body, "editora", "Editora é obrigatória.");
        String isbn = requireText(body, "isbn", "ISBN é obrigatório.");
        int ano = JsonUtil.getInt(body, "anoPublicacao", 0);
        String categoria = requireText(body, "categoria", "Categoria é obrigatória.");
        int qtdTotal = JsonUtil.getInt(body, "quantidadeTotal", 0);
        String corCapa = JsonUtil.getStringOrDefault(body, "corCapa", "#3F4E4F");

        if (ano < 1450 || ano > LocalDate.now().getYear() + 1) {
            throw new BusinessException(400, "Informe um ano de publicação válido.");
        }

        synchronized (repo) {
            Livro livro = findLivroById(id);
            if (livro == null) throw new BusinessException(404, "Livro não encontrado.");

            int minimoPermitido = livro.getQuantidadeEmprestada() + livro.getQuantidadeDanificada();
            if (qtdTotal < minimoPermitido) {
                throw new BusinessException(400,
                        "A quantidade total (" + qtdTotal + ") não pode ser menor que a soma de exemplares emprestados ("
                                + livro.getQuantidadeEmprestada() + ") e danificados (" + livro.getQuantidadeDanificada() + ").");
            }
            for (Livro outro : repo.getLivros()) {
                if (outro.getId() != id && outro.getIsbn() != null && outro.getIsbn().equalsIgnoreCase(isbn.trim())) {
                    throw new BusinessException(409, "Outro livro já está cadastrado com este ISBN.");
                }
            }

            livro.setTitulo(titulo);
            livro.setAutor(autor);
            livro.setEditora(editora);
            livro.setIsbn(isbn);
            livro.setAnoPublicacao(ano);
            livro.setCategoria(categoria);
            livro.setQuantidadeTotal(qtdTotal);
            livro.setCorCapa(corCapa);
            repo.save();
            return livro.toMap();
        }
    }

    public Map<String, Object> alterarStatusLivro(long id, boolean ativar) {
        synchronized (repo) {
            Livro livro = findLivroById(id);
            if (livro == null) throw new BusinessException(404, "Livro não encontrado.");

            if (!ativar && livro.getQuantidadeEmprestada() > 0) {
                throw new BusinessException(409,
                        "Não é possível desativar o livro enquanto houver " + livro.getQuantidadeEmprestada()
                                + " empréstimo(s) ativo(s) vinculado(s).");
            }
            livro.setAtivo(ativar);
            repo.save();
            return livro.toMap();
        }
    }

    /**
     * UC15 — Gerenciar condição de exemplares (marcar danificado ou reparar e disponibilizar).
     */
    public Map<String, Object> atualizarCondicaoExemplar(long livroId, Map<String, Object> body, Funcionario ator) {
        String acao = requireText(body, "acao", "Informe a ação (MARCAR_DANIFICADO ou REPARAR_DISPONIBILIZAR).").toUpperCase();
        int quantidade = JsonUtil.getInt(body, "quantidade", 1);
        String observacao = JsonUtil.getStringOrDefault(body, "observacao", "Atualização registrada no balcão.");

        if (quantidade < 1) {
            throw new BusinessException(400, "A quantidade deve ser maior ou igual a 1.");
        }

        synchronized (repo) {
            Livro livro = findLivroById(livroId);
            if (livro == null) throw new BusinessException(404, "Livro não encontrado.");

            if ("MARCAR_DANIFICADO".equals(acao)) {
                if (livro.getQuantidadeDisponivel() < quantidade) {
                    throw new BusinessException(409,
                            "Há apenas " + livro.getQuantidadeDisponivel() + " exemplar(es) disponível(is) para separar como danificado.");
                }
                livro.setQuantidadeDanificada(livro.getQuantidadeDanificada() + quantidade);
            } else if ("REPARAR_DISPONIBILIZAR".equals(acao)) {
                if (livro.getQuantidadeDanificada() < quantidade) {
                    throw new BusinessException(409,
                            "Há apenas " + livro.getQuantidadeDanificada() + " exemplar(es) danificado(s) para recolocar como disponível.");
                }
                livro.setQuantidadeDanificada(livro.getQuantidadeDanificada() - quantidade);
            } else {
                throw new BusinessException(400, "Ação inválida. Use MARCAR_DANIFICADO ou REPARAR_DISPONIBILIZAR.");
            }

            boolean autoSupervisionado = ator.isAdministrador();
            String nowStr = LocalDateTime.now().format(DATETIME_FMT);
            RegistroCondicao reg = new RegistroCondicao(
                    repo.nextCondicaoId(),
                    livroId,
                    acao,
                    quantidade,
                    observacao,
                    ator.getNome(),
                    nowStr,
                    autoSupervisionado,
                    autoSupervisionado ? ator.getNome() : null,
                    autoSupervisionado ? nowStr : null
            );
            repo.getRegistrosCondicao().add(reg);
            repo.save();

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("livro", livro.toMap());
            resp.put("registro", enrichRegistroCondicao(reg));
            return resp;
        }
    }

    public List<Map<String, Object>> listarRegistrosCondicao() {
        synchronized (repo) {
            List<RegistroCondicao> copy = new ArrayList<>(repo.getRegistrosCondicao());
            copy.sort(Comparator.comparingLong(RegistroCondicao::getId).reversed());
            List<Map<String, Object>> list = new ArrayList<>();
            for (RegistroCondicao r : copy) {
                list.add(enrichRegistroCondicao(r));
            }
            return list;
        }
    }

    public Map<String, Object> supervisionarRegistroCondicao(long registroId, Funcionario admin) {
        authService.requireAdmin(admin);
        synchronized (repo) {
            for (RegistroCondicao r : repo.getRegistrosCondicao()) {
                if (r.getId() == registroId) {
                    r.setSupervisionado(true);
                    r.setSupervisorNome(admin.getNome());
                    r.setDataSupervisao(LocalDateTime.now().format(DATETIME_FMT));
                    repo.save();
                    return enrichRegistroCondicao(r);
                }
            }
            throw new BusinessException(404, "Registro de condição não encontrado.");
        }
    }

    // ==================== EMPRÉSTIMOS (UC10, UC11, UC12, UC13, UC14) ====================

    public List<Map<String, Object>> listarEmprestimos(String busca, String statusFiltro, Long leitorId) {
        atualizarAtrasos();
        synchronized (repo) {
            LocalDate hoje = LocalDate.now();
            String q = busca == null ? "" : busca.trim().toLowerCase();
            String st = statusFiltro == null ? "todos" : statusFiltro.trim().toLowerCase();

            List<Emprestimo> sorted = new ArrayList<>(repo.getEmprestimos());
            sorted.sort(Comparator.comparingLong(Emprestimo::getId).reversed());

            List<Map<String, Object>> result = new ArrayList<>();
            for (Emprestimo e : sorted) {
                if (leitorId != null && e.getLeitorId() != leitorId) continue;

                boolean statusOk = switch (st) {
                    case "todos" -> true;
                    case "em_andamento", "no_prazo" -> e.getStatus() == StatusEmprestimo.EM_ANDAMENTO;
                    case "aguardando" -> e.getStatus() == StatusEmprestimo.AGUARDANDO_RETIRADA;
                    case "prox_vencimento" -> {
                        if (e.getStatus() != StatusEmprestimo.EM_ANDAMENTO || e.getDataPrevistaDevolucao() == null) yield false;
                        long dias = ChronoUnit.DAYS.between(hoje, LocalDate.parse(e.getDataPrevistaDevolucao(), DATE_FMT));
                        yield dias >= 0 && dias <= 4;
                    }
                    case "atrasados" -> e.getStatus() == StatusEmprestimo.ATRASADO;
                    case "devolvidos" -> e.getStatus() == StatusEmprestimo.DEVOLVIDO;
                    case "cancelados" -> e.getStatus() == StatusEmprestimo.CANCELADO;
                    case "ativos" -> e.getStatus().isAtivo();
                    default -> true;
                };
                if (!statusOk) continue;

                Map<String, Object> enriched = enrichEmprestimo(e, hoje);
                if (!q.isBlank()) {
                    boolean match = contains(e.getCodigo(), q)
                            || contains(String.valueOf(enriched.get("livroTitulo")), q)
                            || contains(String.valueOf(enriched.get("livroAutor")), q)
                            || contains(String.valueOf(enriched.get("leitorNome")), q)
                            || contains(String.valueOf(enriched.get("leitorCpf")), q);
                    if (!match) continue;
                }
                result.add(enriched);
            }
            return result;
        }
    }

    /**
     * UC10 — Registrar empréstimo.
     */
    public Map<String, Object> registrarEmprestimo(Map<String, Object> body, Funcionario ator) {
        atualizarAtrasos();
        long leitorId = JsonUtil.getLong(body, "leitorId", 0);
        long livroId = JsonUtil.getLong(body, "livroId", 0);
        boolean retiradaImediata = JsonUtil.getBoolean(body, "retiradaImediata", true);

        if (leitorId <= 0 || livroId <= 0) {
            throw new BusinessException(400, "Selecione um leitor e um livro válidos.");
        }

        synchronized (repo) {
            Leitor leitor = findLeitorById(leitorId);
            if (leitor == null) throw new BusinessException(404, "Leitor não encontrado.");
            if (!leitor.isAtivo()) {
                throw new BusinessException(409, "Este leitor está desativado e não pode realizar empréstimos.");
            }

            Livro livro = findLivroById(livroId);
            if (livro == null) throw new BusinessException(404, "Livro não encontrado.");
            if (!livro.isAtivo()) {
                throw new BusinessException(409, "Este livro está desativado no acervo.");
            }

            // Regra: impedido enquanto houver qualquer pendência (empréstimo atrasado)
            if (hasPendenciaLeitor(leitorId)) {
                throw new BusinessException(409,
                        "Empréstimo bloqueado: " + leitor.getNome()
                                + " possui empréstimo(s) em atraso. Regularize a devolução pendente primeiro.");
            }

            // Regra: máximo de 3 empréstimos simultâneos
            long ativos = countEmprestimosAtivosLeitor(leitorId);
            if (ativos >= LIMITE_EMPRESTIMOS_SIMULTANEOS) {
                throw new BusinessException(409,
                        "Limite atingido: " + leitor.getNome() + " já possui " + ativos
                                + " empréstimos simultâneos (máximo permitido: " + LIMITE_EMPRESTIMOS_SIMULTANEOS + ").");
            }

            // Regra: verificar disponibilidade
            if (livro.getQuantidadeDisponivel() <= 0) {
                throw new BusinessException(409,
                        "Sem exemplares disponíveis de \"" + livro.getTitulo()
                                + "\" no momento (disponíveis: 0, danificados: " + livro.getQuantidadeDanificada() + ").");
            }

            LocalDate hoje = LocalDate.now();
            LocalDate prevista = hoje.plusDays(PRAZO_PADRAO_DIAS);

            Emprestimo emp = new Emprestimo(
                    repo.nextEmprestimoId(),
                    repo.nextCodigoEmprestimo(),
                    leitorId,
                    livroId,
                    hoje.format(DATE_FMT),
                    prevista.format(DATE_FMT),
                    null,
                    retiradaImediata ? StatusEmprestimo.EM_ANDAMENTO : StatusEmprestimo.AGUARDANDO_RETIRADA,
                    false,
                    retiradaImediata,
                    null,
                    null,
                    ator.getNome()
            );

            repo.getEmprestimos().add(emp);
            livro.setQuantidadeEmprestada(livro.getQuantidadeEmprestada() + 1);
            repo.save();
            return enrichEmprestimo(emp, hoje);
        }
    }

    /**
     * Confirma a retirada presencial de um empréstimo reservado (AGUARDANDO_RETIRADA -> EM_ANDAMENTO).
     */
    public Map<String, Object> confirmarRetirada(long emprestimoId) {
        synchronized (repo) {
            Emprestimo emp = findEmprestimoById(emprestimoId);
            if (emp == null) throw new BusinessException(404, "Empréstimo não encontrado.");
            if (emp.getStatus() != StatusEmprestimo.AGUARDANDO_RETIRADA) {
                throw new BusinessException(409, "Apenas empréstimos aguardando retirada podem ser confirmados.");
            }
            LocalDate hoje = LocalDate.now();
            emp.setRetirado(true);
            emp.setStatus(StatusEmprestimo.EM_ANDAMENTO);
            emp.setDataEmprestimo(hoje.format(DATE_FMT));
            emp.setDataPrevistaDevolucao(hoje.plusDays(PRAZO_PADRAO_DIAS).format(DATE_FMT));
            repo.save();
            return enrichEmprestimo(emp, hoje);
        }
    }

    /**
     * UC11 — Cancelar empréstimo (somente antes de o livro ser retirado pelo leitor).
     */
    public Map<String, Object> cancelarEmprestimo(long emprestimoId) {
        synchronized (repo) {
            Emprestimo emp = findEmprestimoById(emprestimoId);
            if (emp == null) throw new BusinessException(404, "Empréstimo não encontrado.");

            if (emp.isRetirado() || emp.getStatus() != StatusEmprestimo.AGUARDANDO_RETIRADA) {
                throw new BusinessException(409,
                        "Regra de negócio (UC11): o empréstimo só pode ser cancelado antes de o livro ser retirado pelo leitor.");
            }

            emp.setStatus(StatusEmprestimo.CANCELADO);
            Livro livro = findLivroById(emp.getLivroId());
            if (livro != null && livro.getQuantidadeEmprestada() > 0) {
                livro.setQuantidadeEmprestada(livro.getQuantidadeEmprestada() - 1);
            }
            repo.save();
            return enrichEmprestimo(emp, LocalDate.now());
        }
    }

    /**
     * UC12 — Registrar devolução + avaliação obrigatória da condição do exemplar.
     */
    public Map<String, Object> registrarDevolucao(long emprestimoId, Map<String, Object> body, Funcionario ator) {
        atualizarAtrasos();
        String condicaoStr = requireText(body, "condicao", "Informe a condição do exemplar na devolução.");
        CondicaoExemplar condicao = CondicaoExemplar.fromString(condicaoStr);
        String observacao = JsonUtil.getStringOrDefault(body, "observacao",
                condicao == CondicaoExemplar.BOAS_CONDICOES
                        ? "Exemplar devolvido em boas condições."
                        : "Avaria identificada durante a devolução.");

        synchronized (repo) {
            Emprestimo emp = findEmprestimoById(emprestimoId);
            if (emp == null) throw new BusinessException(404, "Empréstimo não encontrado.");

            if (emp.getStatus() == StatusEmprestimo.DEVOLVIDO || emp.getStatus() == StatusEmprestimo.CANCELADO) {
                throw new BusinessException(409, "Este empréstimo já foi encerrado.");
            }
            if (emp.getStatus() == StatusEmprestimo.AGUARDANDO_RETIRADA) {
                throw new BusinessException(409, "O livro ainda não foi retirado. Utilize a opção Cancelar empréstimo.");
            }

            LocalDate hoje = LocalDate.now();
            emp.setStatus(StatusEmprestimo.DEVOLVIDO);
            emp.setDataEfetivaDevolucao(hoje.format(DATE_FMT));
            emp.setCondicaoDevolucao(condicao);
            emp.setObservacaoCondicao(observacao);

            Livro livro = findLivroById(emp.getLivroId());
            if (livro != null) {
                if (livro.getQuantidadeEmprestada() > 0) {
                    livro.setQuantidadeEmprestada(livro.getQuantidadeEmprestada() - 1);
                }
                if (condicao == CondicaoExemplar.DANIFICADO) {
                    livro.setQuantidadeDanificada(livro.getQuantidadeDanificada() + 1);
                    String nowStr = LocalDateTime.now().format(DATETIME_FMT);
                    boolean autoSup = ator.isAdministrador();
                    repo.getRegistrosCondicao().add(new RegistroCondicao(
                            repo.nextCondicaoId(),
                            livro.getId(),
                            "DEVOLUCAO_DANIFICADA",
                            1,
                            "Empréstimo " + emp.getCodigo() + ": " + observacao,
                            ator.getNome(),
                            nowStr,
                            autoSup,
                            autoSup ? ator.getNome() : null,
                            autoSup ? nowStr : null
                    ));
                }
            }

            repo.save();
            return enrichEmprestimo(emp, hoje);
        }
    }

    /**
     * UC13 — Prorrogar empréstimo (única vez, +14 dias, solicitada presencialmente antes do vencimento).
     */
    public Map<String, Object> prorrogarEmprestimo(long emprestimoId) {
        atualizarAtrasos();
        synchronized (repo) {
            Emprestimo emp = findEmprestimoById(emprestimoId);
            if (emp == null) throw new BusinessException(404, "Empréstimo não encontrado.");

            if (emp.getStatus() == StatusEmprestimo.ATRASADO) {
                throw new BusinessException(409,
                        "Prorrogação negada (UC13): o empréstimo já está vencido. Só é permitida prorrogação antes do vencimento.");
            }
            if (emp.getStatus() != StatusEmprestimo.EM_ANDAMENTO) {
                throw new BusinessException(409,
                        "Apenas empréstimos em andamento (já retirados e no prazo) podem ser prorrogados.");
            }
            if (emp.isProrrogado()) {
                throw new BusinessException(409,
                        "Prorrogação negada (UC13): este empréstimo já utilizou a prorrogação única permitida de 14 dias.");
            }

            LocalDate hoje = LocalDate.now();
            LocalDate previstaAtual = LocalDate.parse(emp.getDataPrevistaDevolucao(), DATE_FMT);
            if (hoje.isAfter(previstaAtual)) {
                emp.setStatus(StatusEmprestimo.ATRASADO);
                repo.save();
                throw new BusinessException(409, "Prorrogação negada: o prazo do empréstimo já venceu.");
            }

            LocalDate novaPrevista = previstaAtual.plusDays(DIAS_PRORROGACAO);
            emp.setDataPrevistaDevolucao(novaPrevista.format(DATE_FMT));
            emp.setProrrogado(true);
            repo.save();
            return enrichEmprestimo(emp, hoje);
        }
    }

    // ==================== FUNCIONÁRIOS (UC16 — EXCLUSIVO ADMINISTRADOR) ====================

    public List<Map<String, Object>> listarFuncionarios(Funcionario ator) {
        authService.requireAdmin(ator);
        synchronized (repo) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Funcionario f : repo.getFuncionarios()) {
                list.add(f.toPublicMap());
            }
            list.sort((a, b) -> String.valueOf(a.get("nome")).compareToIgnoreCase(String.valueOf(b.get("nome"))));
            return list;
        }
    }

    public Map<String, Object> cadastrarFuncionario(Map<String, Object> body, Funcionario ator) {
        authService.requireAdmin(ator);
        String nome = requireText(body, "nome", "Nome do funcionário é obrigatório.");
        String email = requireText(body, "email", "E-mail é obrigatório.").toLowerCase();
        String matricula = requireText(body, "matricula", "Matrícula é obrigatória.");
        String senha = requireText(body, "senha", "Senha inicial é obrigatória.");
        Cargo cargo = Cargo.fromString(JsonUtil.getStringOrDefault(body, "cargo", "FUNCIONARIO"));

        if (senha.length() < 6) {
            throw new BusinessException(400, "A senha deve conter no mínimo 6 caracteres.");
        }

        synchronized (repo) {
            for (Funcionario f : repo.getFuncionarios()) {
                if (f.getEmail() != null && f.getEmail().equalsIgnoreCase(email)) {
                    throw new BusinessException(409, "Já existe um funcionário cadastrado com este e-mail.");
                }
            }
            Funcionario novo = new Funcionario(
                    repo.nextFuncionarioId(), nome, email, matricula,
                    BibliotecaRepository.hashPassword(senha), cargo, true,
                    LocalDate.now().format(DATE_FMT)
            );
            repo.getFuncionarios().add(novo);
            repo.save();
            return novo.toPublicMap();
        }
    }

    public Map<String, Object> editarFuncionario(long id, Map<String, Object> body, Funcionario ator) {
        authService.requireAdmin(ator);
        String nome = requireText(body, "nome", "Nome do funcionário é obrigatório.");
        String email = requireText(body, "email", "E-mail é obrigatório.").toLowerCase();
        String matricula = requireText(body, "matricula", "Matrícula é obrigatória.");
        Cargo cargo = Cargo.fromString(JsonUtil.getStringOrDefault(body, "cargo", "FUNCIONARIO"));
        String novaSenha = JsonUtil.getString(body, "senha");

        synchronized (repo) {
            Funcionario alvo = findFuncionarioById(id);
            if (alvo == null) throw new BusinessException(404, "Funcionário não encontrado.");

            for (Funcionario f : repo.getFuncionarios()) {
                if (f.getId() != id && f.getEmail() != null && f.getEmail().equalsIgnoreCase(email)) {
                    throw new BusinessException(409, "Outro funcionário já utiliza este e-mail.");
                }
            }
            if (alvo.isAdministrador() && cargo != Cargo.ADMINISTRADOR && countAdminsAtivos() <= 1) {
                throw new BusinessException(409, "Deve permanecer ao menos um Administrador ativo no sistema.");
            }

            alvo.setNome(nome);
            alvo.setEmail(email);
            alvo.setMatricula(matricula);
            alvo.setCargo(cargo);
            if (novaSenha != null && !novaSenha.isBlank()) {
                if (novaSenha.length() < 6) {
                    throw new BusinessException(400, "A nova senha deve conter no mínimo 6 caracteres.");
                }
                alvo.setSenhaHash(BibliotecaRepository.hashPassword(novaSenha));
            }
            repo.save();
            return alvo.toPublicMap();
        }
    }

    public Map<String, Object> alterarStatusFuncionario(long id, boolean ativar, Funcionario ator) {
        authService.requireAdmin(ator);
        synchronized (repo) {
            Funcionario alvo = findFuncionarioById(id);
            if (alvo == null) throw new BusinessException(404, "Funcionário não encontrado.");

            if (!ativar) {
                if (alvo.getId() == ator.getId()) {
                    throw new BusinessException(409, "Você não pode desativar sua própria conta enquanto está autenticado.");
                }
                if (alvo.isAdministrador() && countAdminsAtivos() <= 1) {
                    throw new BusinessException(409, "Não é possível desativar o único Administrador ativo.");
                }
            }
            alvo.setAtivo(ativar);
            repo.save();
            return alvo.toPublicMap();
        }
    }

    public void resetarBaseDemo(Funcionario ator) {
        authService.requireAdmin(ator);
        repo.resetToSeed();
    }

    // ==================== BUSCA GLOBAL ====================

    public Map<String, Object> buscaGlobal(String query) {
        atualizarAtrasos();
        String q = query == null ? "" : query.trim().toLowerCase();
        Map<String, Object> res = new LinkedHashMap<>();
        if (q.isBlank()) {
            res.put("livros", List.of());
            res.put("leitores", List.of());
            res.put("emprestimos", List.of());
            return res;
        }
        res.put("livros", listarLivros(q, null, "todos").stream().limit(5).toList());
        res.put("leitores", listarLeitores(q, "todos").stream().limit(5).toList());
        res.put("emprestimos", listarEmprestimos(q, "todos", null).stream().limit(5).toList());
        return res;
    }

    // ==================== HELPERS ====================

    private Map<String, Object> enrichLeitor(Leitor l) {
        Map<String, Object> map = l.toMap();
        long ativos = countEmprestimosAtivosLeitor(l.getId());
        long atrasados = countEmprestimosAtrasadosLeitor(l.getId());
        long totalHistorico = repo.getEmprestimos().stream()
                .filter(e -> e.getLeitorId() == l.getId())
                .count();
        map.put("emprestimosAtivos", ativos);
        map.put("limiteEmprestimos", LIMITE_EMPRESTIMOS_SIMULTANEOS);
        map.put("emprestimosAtrasados", atrasados);
        map.put("possuiPendencia", atrasados > 0);
        map.put("totalHistorico", totalHistorico);
        map.put("elegivelEmprestimo", l.isAtivo() && atrasados == 0 && ativos < LIMITE_EMPRESTIMOS_SIMULTANEOS);
        return map;
    }

    private Map<String, Object> enrichEmprestimo(Emprestimo e, LocalDate hoje) {
        Map<String, Object> map = e.toStorageMap();
        Livro livro = findLivroById(e.getLivroId());
        Leitor leitor = findLeitorById(e.getLeitorId());

        map.put("statusLabel", e.getStatus().getLabel());
        map.put("livroTitulo", livro != null ? livro.getTitulo() : "Livro #" + e.getLivroId());
        map.put("livroAutor", livro != null ? livro.getAutor() : "—");
        map.put("livroCorCapa", livro != null ? livro.getCorCapa() : "#3F4E4F");
        map.put("livroIsbn", livro != null ? livro.getIsbn() : "—");

        map.put("leitorNome", leitor != null ? leitor.getNome() : "Leitor #" + e.getLeitorId());
        map.put("leitorPerfil", leitor != null ? leitor.getPerfil() : "Leitor");
        map.put("leitorCpf", leitor != null ? leitor.getCpf() : "—");
        map.put("leitorEmail", leitor != null ? leitor.getEmail() : "—");

        long diasAtraso = 0;
        long diasRestantes = 0;
        if (e.getDataPrevistaDevolucao() != null) {
            LocalDate prev = LocalDate.parse(e.getDataPrevistaDevolucao(), DATE_FMT);
            if (e.getStatus() == StatusEmprestimo.ATRASADO) {
                diasAtraso = Math.max(1, ChronoUnit.DAYS.between(prev, hoje));
            } else if (e.getStatus() == StatusEmprestimo.EM_ANDAMENTO) {
                diasRestantes = ChronoUnit.DAYS.between(hoje, prev);
            }
        }
        map.put("diasAtraso", diasAtraso);
        map.put("diasRestantes", diasRestantes);
        map.put("podeProrrogar", e.getStatus() == StatusEmprestimo.EM_ANDAMENTO && !e.isProrrogado());
        map.put("podeCancelar", e.getStatus() == StatusEmprestimo.AGUARDANDO_RETIRADA && !e.isRetirado());
        map.put("podeConfirmarRetirada", e.getStatus() == StatusEmprestimo.AGUARDANDO_RETIRADA);
        map.put("podeDevolver", e.getStatus() == StatusEmprestimo.EM_ANDAMENTO || e.getStatus() == StatusEmprestimo.ATRASADO);
        map.put("condicaoDevolucaoLabel", e.getCondicaoDevolucao() != null ? e.getCondicaoDevolucao().getLabel() : null);
        return map;
    }

    private Map<String, Object> enrichRegistroCondicao(RegistroCondicao r) {
        Map<String, Object> map = r.toMap();
        Livro l = findLivroById(r.getLivroId());
        map.put("livroTitulo", l != null ? l.getTitulo() : "Livro #" + r.getLivroId());
        map.put("livroAutor", l != null ? l.getAutor() : "—");
        return map;
    }

    private long countEmprestimosAtivosLeitor(long leitorId) {
        return repo.getEmprestimos().stream()
                .filter(e -> e.getLeitorId() == leitorId && e.getStatus().isAtivo())
                .count();
    }

    private long countEmprestimosAtrasadosLeitor(long leitorId) {
        return repo.getEmprestimos().stream()
                .filter(e -> e.getLeitorId() == leitorId && e.getStatus() == StatusEmprestimo.ATRASADO)
                .count();
    }

    private boolean hasPendenciaLeitor(long leitorId) {
        return countEmprestimosAtrasadosLeitor(leitorId) > 0;
    }

    private long countAdminsAtivos() {
        return repo.getFuncionarios().stream()
                .filter(f -> f.isAtivo() && f.isAdministrador())
                .count();
    }

    private Leitor findLeitorById(long id) {
        for (Leitor l : repo.getLeitores()) {
            if (l.getId() == id) return l;
        }
        return null;
    }

    private Livro findLivroById(long id) {
        for (Livro l : repo.getLivros()) {
            if (l.getId() == id) return l;
        }
        return null;
    }

    private Emprestimo findEmprestimoById(long id) {
        for (Emprestimo e : repo.getEmprestimos()) {
            if (e.getId() == id) return e;
        }
        return null;
    }

    private Funcionario findFuncionarioById(long id) {
        for (Funcionario f : repo.getFuncionarios()) {
            if (f.getId() == id) return f;
        }
        return null;
    }

    private static String requireText(Map<String, Object> body, String key, String errorMsg) {
        String val = JsonUtil.getString(body, key);
        if (val == null || val.isBlank()) {
            throw new BusinessException(400, errorMsg);
        }
        return val.trim();
    }

    private static boolean contains(String source, String query) {
        return source != null && source.toLowerCase().contains(query);
    }

    private static String formatarCpf(String digits) {
        return digits.substring(0, 3) + "." + digits.substring(3, 6) + "."
                + digits.substring(6, 9) + "-" + digits.substring(9, 11);
    }
}
