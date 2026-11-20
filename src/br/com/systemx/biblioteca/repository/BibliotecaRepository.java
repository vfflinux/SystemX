package br.com.systemx.biblioteca.repository;

import br.com.systemx.biblioteca.model.*;
import br.com.systemx.biblioteca.util.EnvConfig;
import br.com.systemx.biblioteca.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BibliotecaRepository {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Path storagePath;
    private final EnvConfig env;

    private long nextFuncionarioId = 1;
    private long nextLeitorId = 1;
    private long nextLivroId = 1;
    private long nextEmprestimoId = 1;
    private long nextCondicaoId = 1;
    private int nextCodigoEmprestimo = 1001;

    private final List<Funcionario> funcionarios = new ArrayList<>();
    private final List<Leitor> leitores = new ArrayList<>();
    private final List<Livro> livros = new ArrayList<>();
    private final List<Emprestimo> emprestimos = new ArrayList<>();
    private final List<RegistroCondicao> registrosCondicao = new ArrayList<>();

    public BibliotecaRepository(Path storagePath, EnvConfig env) {
        this.storagePath = storagePath;
        this.env = env;
        loadOrSeed();
    }

    public synchronized List<Funcionario> getFuncionarios() { return funcionarios; }
    public synchronized List<Leitor> getLeitores() { return leitores; }
    public synchronized List<Livro> getLivros() { return livros; }
    public synchronized List<Emprestimo> getEmprestimos() { return emprestimos; }
    public synchronized List<RegistroCondicao> getRegistrosCondicao() { return registrosCondicao; }

    public synchronized long nextFuncionarioId() { return nextFuncionarioId++; }
    public synchronized long nextLeitorId() { return nextLeitorId++; }
    public synchronized long nextLivroId() { return nextLivroId++; }
    public synchronized long nextEmprestimoId() { return nextEmprestimoId++; }
    public synchronized long nextCondicaoId() { return nextCondicaoId++; }
    public synchronized String nextCodigoEmprestimo() { return "EMP-" + (nextCodigoEmprestimo++); }

    public static String hashPassword(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(("systemx-salt::" + raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar hash de senha", e);
        }
    }

    public synchronized void resetToSeed() {
        funcionarios.clear();
        leitores.clear();
        livros.clear();
        emprestimos.clear();
        registrosCondicao.clear();
        nextFuncionarioId = 1;
        nextLeitorId = 1;
        nextLivroId = 1;
        nextEmprestimoId = 1;
        nextCondicaoId = 1;
        nextCodigoEmprestimo = 1001;
        seedInitialData();
        save();
    }

    private void loadOrSeed() {
        if (Files.exists(storagePath)) {
            try {
                String content = Files.readString(storagePath, StandardCharsets.UTF_8);
                if (!content.isBlank()) {
                    loadFromJson(content);
                    if (syncEnvAccounts()) {
                        save();
                    }
                    return;
                }
            } catch (Exception e) {
                System.err.println("[SystemX] Aviso ao ler banco local, recriando seed: " + e.getMessage());
            }
        }
        seedInitialData();
        save();
    }

    private boolean syncEnvAccounts() {
        boolean changed = false;
        String criado = LocalDate.now().format(DATE_FMT);

        changed |= upsertEnvAccount(
                env.getOrDefault("ADMIN_NOME", "Administrador"),
                env.require("ADMIN_EMAIL"),
                "ADM-001",
                env.require("ADMIN_SENHA"),
                Cargo.ADMINISTRADOR,
                criado);

        changed |= upsertEnvAccount(
                env.getOrDefault("FUNCIONARIO_NOME", "Funcionário"),
                env.require("FUNCIONARIO_EMAIL"),
                "FUN-101",
                env.require("FUNCIONARIO_SENHA"),
                Cargo.FUNCIONARIO,
                criado);

        changed |= upsertEnvAccount(
                env.getOrDefault("VISUALIZADOR_NOME", "Visitante (Visualização)"),
                env.getOrDefault("VISUALIZADOR_EMAIL", "visualizador@gmail.com"),
                "VIS-001",
                env.getOrDefault("VISUALIZADOR_SENHA", "visualizar123"),
                Cargo.VISUALIZADOR,
                criado);

        return changed;
    }

    private boolean upsertEnvAccount(String nome, String email, String matricula,
                                     String senhaPlana, Cargo cargo, String criado) {
        String normalizedEmail = email.trim().toLowerCase();
        String expectedHash = hashPassword(senhaPlana);
        for (Funcionario f : funcionarios) {
            if (f.getEmail() != null && f.getEmail().trim().toLowerCase().equals(normalizedEmail)) {
                boolean updated = false;
                if (!expectedHash.equals(f.getSenhaHash())) {
                    f.setSenhaHash(expectedHash);
                    updated = true;
                }
                if (f.getCargo() != cargo) {
                    f.setCargo(cargo);
                    updated = true;
                }
                if (!f.isAtivo()) {
                    f.setAtivo(true);
                    updated = true;
                }
                return updated;
            }
        }
        funcionarios.add(new Funcionario(
                nextFuncionarioId(), nome, normalizedEmail, matricula,
                expectedHash, cargo, true, criado));
        return true;
    }

    @SuppressWarnings("unchecked")
    private void loadFromJson(String json) {
        Map<String, Object> root = JsonUtil.parseObject(json);
        nextFuncionarioId = JsonUtil.getLong(root, "nextFuncionarioId", 1);
        nextLeitorId = JsonUtil.getLong(root, "nextLeitorId", 1);
        nextLivroId = JsonUtil.getLong(root, "nextLivroId", 1);
        nextEmprestimoId = JsonUtil.getLong(root, "nextEmprestimoId", 1);
        nextCondicaoId = JsonUtil.getLong(root, "nextCondicaoId", 1);
        nextCodigoEmprestimo = JsonUtil.getInt(root, "nextCodigoEmprestimo", 1001);

        funcionarios.clear();
        Object fList = root.get("funcionarios");
        if (fList instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> m = (Map<String, Object>) item;
                funcionarios.add(new Funcionario(
                        JsonUtil.getLong(m, "id", 0),
                        JsonUtil.getString(m, "nome"),
                        JsonUtil.getString(m, "email"),
                        JsonUtil.getString(m, "matricula"),
                        JsonUtil.getString(m, "senhaHash"),
                        Cargo.fromString(JsonUtil.getString(m, "cargo")),
                        JsonUtil.getBoolean(m, "ativo", true),
                        JsonUtil.getString(m, "criadoEm")
                ));
            }
        }

        leitores.clear();
        Object lList = root.get("leitores");
        if (lList instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> m = (Map<String, Object>) item;
                leitores.add(new Leitor(
                        JsonUtil.getLong(m, "id", 0),
                        JsonUtil.getString(m, "nome"),
                        JsonUtil.getString(m, "endereco"),
                        JsonUtil.getString(m, "cpf"),
                        JsonUtil.getString(m, "telefone"),
                        JsonUtil.getString(m, "email"),
                        JsonUtil.getStringOrDefault(m, "perfil", "Aluno"),
                        JsonUtil.getBoolean(m, "ativo", true),
                        JsonUtil.getString(m, "criadoEm")
                ));
            }
        }

        livros.clear();
        Object bList = root.get("livros");
        if (bList instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> m = (Map<String, Object>) item;
                livros.add(new Livro(
                        JsonUtil.getLong(m, "id", 0),
                        JsonUtil.getString(m, "titulo"),
                        JsonUtil.getString(m, "autor"),
                        JsonUtil.getString(m, "editora"),
                        JsonUtil.getString(m, "isbn"),
                        JsonUtil.getInt(m, "anoPublicacao", 2000),
                        JsonUtil.getString(m, "categoria"),
                        JsonUtil.getInt(m, "quantidadeTotal", 1),
                        JsonUtil.getInt(m, "quantidadeEmprestada", 0),
                        JsonUtil.getInt(m, "quantidadeDanificada", 0),
                        JsonUtil.getStringOrDefault(m, "corCapa", "#3F4E4F"),
                        JsonUtil.getBoolean(m, "ativo", true),
                        JsonUtil.getString(m, "criadoEm")
                ));
            }
        }

        emprestimos.clear();
        Object eList = root.get("emprestimos");
        if (eList instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> m = (Map<String, Object>) item;
                String condStr = JsonUtil.getString(m, "condicaoDevolucao");
                emprestimos.add(new Emprestimo(
                        JsonUtil.getLong(m, "id", 0),
                        JsonUtil.getString(m, "codigo"),
                        JsonUtil.getLong(m, "leitorId", 0),
                        JsonUtil.getLong(m, "livroId", 0),
                        JsonUtil.getString(m, "dataEmprestimo"),
                        JsonUtil.getString(m, "dataPrevistaDevolucao"),
                        JsonUtil.getString(m, "dataEfetivaDevolucao"),
                        StatusEmprestimo.fromString(JsonUtil.getString(m, "status")),
                        JsonUtil.getBoolean(m, "prorrogado", false),
                        JsonUtil.getBoolean(m, "retirado", true),
                        condStr != null ? CondicaoExemplar.fromString(condStr) : null,
                        JsonUtil.getString(m, "observacaoCondicao"),
                        JsonUtil.getString(m, "funcionarioResponsavel")
                ));
            }
        }

        registrosCondicao.clear();
        Object cList = root.get("registrosCondicao");
        if (cList instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> m = (Map<String, Object>) item;
                registrosCondicao.add(new RegistroCondicao(
                        JsonUtil.getLong(m, "id", 0),
                        JsonUtil.getLong(m, "livroId", 0),
                        JsonUtil.getString(m, "acao"),
                        JsonUtil.getInt(m, "quantidade", 1),
                        JsonUtil.getString(m, "observacao"),
                        JsonUtil.getString(m, "responsavel"),
                        JsonUtil.getString(m, "dataHora"),
                        JsonUtil.getBoolean(m, "supervisionado", false),
                        JsonUtil.getString(m, "supervisorNome"),
                        JsonUtil.getString(m, "dataSupervisao")
                ));
            }
        }
    }

    public synchronized void save() {
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("nextFuncionarioId", nextFuncionarioId);
            root.put("nextLeitorId", nextLeitorId);
            root.put("nextLivroId", nextLivroId);
            root.put("nextEmprestimoId", nextEmprestimoId);
            root.put("nextCondicaoId", nextCondicaoId);
            root.put("nextCodigoEmprestimo", nextCodigoEmprestimo);

            List<Map<String, Object>> fMaps = new ArrayList<>();
            for (Funcionario f : funcionarios) fMaps.add(f.toStorageMap());
            root.put("funcionarios", fMaps);

            List<Map<String, Object>> lMaps = new ArrayList<>();
            for (Leitor l : leitores) lMaps.add(l.toMap());
            root.put("leitores", lMaps);

            List<Map<String, Object>> bMaps = new ArrayList<>();
            for (Livro b : livros) bMaps.add(b.toMap());
            root.put("livros", bMaps);

            List<Map<String, Object>> eMaps = new ArrayList<>();
            for (Emprestimo e : emprestimos) eMaps.add(e.toStorageMap());
            root.put("emprestimos", eMaps);

            List<Map<String, Object>> cMaps = new ArrayList<>();
            for (RegistroCondicao r : registrosCondicao) cMaps.add(r.toMap());
            root.put("registrosCondicao", cMaps);

            Path tmp = storagePath.resolveSibling(storagePath.getFileName().toString() + ".tmp");
            Files.writeString(tmp, JsonUtil.stringifyPretty(root), StandardCharsets.UTF_8);
            Files.move(tmp, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao persistir dados em disco: " + e.getMessage(), e);
        }
    }

    private void seedInitialData() {
        LocalDate hoje = LocalDate.now();
        String criado = hoje.minusDays(45).format(DATE_FMT);

        // Contas carregadas a partir do arquivo .env (Administrador, Funcionário e Somente Visualização)
        syncEnvAccounts();

        // Leitores (UC02-UC05)
        Leitor mariana = new Leitor(nextLeitorId(), "Mariana Silva", "Rua das Acácias, 142 - Centro",
                "123.456.789-01", "(11) 98765-4321", "mariana.silva@edu.br", "Aluno", true, criado);
        Leitor carlos = new Leitor(nextLeitorId(), "Prof. Carlos Eduardo", "Av. Paulista, 980 - Apto 54",
                "234.567.890-12", "(11) 99123-4567", "carlos.eduardo@edu.br", "Professor", true, criado);
        Leitor lucas = new Leitor(nextLeitorId(), "Lucas Ferreira", "Rua Vergueiro, 510 - Vila Mariana",
                "345.678.901-23", "(11) 97654-3210", "lucas.ferreira@edu.br", "Aluno", true, criado);
        Leitor beatriz = new Leitor(nextLeitorId(), "Beatriz Vasconcelos", "Alameda Santos, 320 - Jardins",
                "456.789.012-34", "(11) 98234-5678", "beatriz.vasconcelos@pesquisa.br", "Pesquisador", true, criado);
        Leitor rafael = new Leitor(nextLeitorId(), "Rafael Mendes", "Rua Augusta, 1205 - Consolação",
                "567.890.123-45", "(11) 96543-2109", "rafael.mendes@edu.br", "Aluno", true, criado);
        Leitor fernanda = new Leitor(nextLeitorId(), "Fernanda Oliveira", "Rua Domingos de Morais, 780",
                "678.901.234-56", "(11) 97111-2233", "fernanda.oliveira@edu.br", "Professor", true, criado);
        Leitor gabriel = new Leitor(nextLeitorId(), "Gabriel Costa", "Rua Voluntários da Pátria, 455",
                "789.012.345-67", "(11) 99888-7766", "gabriel.costa@edu.br", "Aluno", true, criado);
        Leitor camila = new Leitor(nextLeitorId(), "Camila Nogueira", "Av. Brasil, 210 - Jardim América",
                "890.123.456-78", "(11) 98444-5566", "camila.nogueira@pesquisa.br", "Pesquisador", true, criado);

        leitores.addAll(List.of(mariana, carlos, lucas, beatriz, rafael, fernanda, gabriel, camila));

        // Livros (UC06-UC09, UC15) - controle quantitativo
        Livro pequenoPrincipe = new Livro(nextLivroId(), "O Pequeno Príncipe", "Antoine de Saint-Exupéry",
                "HarperCollins", "978-8595081512", 1943, "Literatura Clássica", 14, 0, 0, "#3F4E4F", true, criado);
        Livro book1984 = new Livro(nextLivroId(), "1984", "George Orwell",
                "Companhia das Letras", "978-8535914849", 1949, "Ficção Científica", 16, 0, 1, "#7B3F3F", true, criado);
        Livro domCasmurro = new Livro(nextLivroId(), "Dom Casmurro", "Machado de Assis",
                "Penguin Companhia", "978-8582850350", 1899, "Literatura Brasileira", 12, 0, 1, "#A27B5C", true, criado);
        Livro metamorfose = new Livro(nextLivroId(), "A Metamorfose", "Franz Kafka",
                "Companhia das Letras", "978-8571646858", 1915, "Ficção Clássica", 10, 0, 0, "#6E3B3B", true, criado);
        Livro sapiens = new Livro(nextLivroId(), "Sapiens", "Yuval Noah Harari",
                "L&PM Editores", "978-8525432186", 2014, "História", 18, 0, 0, "#355E4B", true, criado);
        Livro cleanCode = new Livro(nextLivroId(), "Código Limpo", "Robert C. Martin",
                "Alta Books", "978-8576082675", 2009, "Engenharia de Software", 15, 0, 0, "#2C3639", true, criado);
        Livro engSoftware = new Livro(nextLivroId(), "Engenharia de Software", "Ian Sommerville",
                "Pearson", "978-8543024974", 2019, "Engenharia de Software", 12, 0, 0, "#4F5D5E", true, criado);
        Livro grandeSertao = new Livro(nextLivroId(), "Grande Sertão: Veredas", "João Guimarães Rosa",
                "Companhia das Letras", "978-8535931983", 1956, "Literatura Brasileira", 9, 0, 0, "#8C6749", true, criado);
        Livro arquiteturaLimpa = new Livro(nextLivroId(), "Arquitetura Limpa", "Robert C. Martin",
                "Alta Books", "978-8550804606", 2019, "Tecnologia", 10, 0, 0, "#3B4A4D", true, criado);
        Livro capitaesAreia = new Livro(nextLivroId(), "Capitães da Areia", "Jorge Amado",
                "Companhia das Letras", "978-8535914061", 1937, "Literatura Brasileira", 8, 0, 0, "#7C5E44", true, criado);

        livros.addAll(List.of(pequenoPrincipe, book1984, domCasmurro, metamorfose, sapiens,
                cleanCode, engSoftware, grandeSertao, arquiteturaLimpa, capitaesAreia));

        // Empréstimos históricos devolvidos (alimentam o gráfico "Livros mais emprestados" idêntico ao PNG!)
        // Sapiens (maior), 1984 (2º), O Pequeno Príncipe (3º), Dom Casmurro (4º), A Metamorfose (5º)
        seedReturnedBatch(sapiens, 15, List.of(mariana, carlos, beatriz, fernanda, camila), hoje);
        seedReturnedBatch(book1984, 11, List.of(carlos, rafael, fernanda, gabriel), hoje);
        seedReturnedBatch(pequenoPrincipe, 10, List.of(mariana, beatriz, camila, gabriel), hoje);
        seedReturnedBatch(domCasmurro, 7, List.of(lucas, fernanda, rafael), hoje);
        seedReturnedBatch(metamorfose, 5, List.of(beatriz, camila, mariana), hoje);

        // Empréstimos recentes (os 5 principais espelham diretamente a tabela do Dashboard PNG!)
        // Resetamos a numeração visível dos mais recentes para EMP-1001..EMP-1008 ficarem no topo
        // Primeiro, renumeramos os históricos como EMP-09xx para deixar EMP-1001..EMP-1008 como recentes
        int histNum = 901;
        for (Emprestimo e : emprestimos) {
            e.setCodigo("EMP-0" + (histNum++));
        }
        nextCodigoEmprestimo = 1001;

        // EMP-1005: Sapiens - Mariana Silva - Devolvido
        emprestimos.add(new Emprestimo(
                nextEmprestimoId(), nextCodigoEmprestimo(), mariana.getId(), sapiens.getId(),
                hoje.minusDays(18).format(DATE_FMT),
                hoje.minusDays(4).format(DATE_FMT),
                hoje.minusDays(6).format(DATE_FMT),
                StatusEmprestimo.DEVOLVIDO, false, true,
                CondicaoExemplar.BOAS_CONDICOES, "Exemplar devolvido em perfeito estado.",
                "João Caldeira"));

        // EMP-1004: A Metamorfose - Beatriz Vasconcelos - Em dia
        emprestimos.add(new Emprestimo(
                nextEmprestimoId(), nextCodigoEmprestimo(), beatriz.getId(), metamorfose.getId(),
                hoje.minusDays(5).format(DATE_FMT),
                hoje.plusDays(9).format(DATE_FMT),
                null,
                StatusEmprestimo.EM_ANDAMENTO, false, true,
                null, null, "Equipe Biblioteca"));

        // EMP-1003: Dom Casmurro - Lucas Ferreira - Atrasado
        emprestimos.add(new Emprestimo(
                nextEmprestimoId(), nextCodigoEmprestimo(), lucas.getId(), domCasmurro.getId(),
                hoje.minusDays(20).format(DATE_FMT),
                hoje.minusDays(6).format(DATE_FMT),
                null,
                StatusEmprestimo.ATRASADO, false, true,
                null, null, "João Caldeira"));

        // EMP-1002: 1984 - Prof. Carlos Eduardo - Em dia (próximo do vencimento: 2 dias!)
        emprestimos.add(new Emprestimo(
                nextEmprestimoId(), nextCodigoEmprestimo(), carlos.getId(), book1984.getId(),
                hoje.minusDays(12).format(DATE_FMT),
                hoje.plusDays(2).format(DATE_FMT),
                null,
                StatusEmprestimo.EM_ANDAMENTO, false, true,
                null, null, "Equipe Biblioteca"));

        // EMP-1001: O Pequeno Príncipe - Mariana Silva - Em dia
        emprestimos.add(new Emprestimo(
                nextEmprestimoId(), nextCodigoEmprestimo(), mariana.getId(), pequenoPrincipe.getId(),
                hoje.minusDays(4).format(DATE_FMT),
                hoje.plusDays(10).format(DATE_FMT),
                null,
                StatusEmprestimo.EM_ANDAMENTO, false, true,
                null, null, "Equipe Biblioteca"));

        // Empréstimos adicionais ativos para enriquecer filtros e testar UC11 (Aguardando Retirada) e atrasos
        emprestimos.add(new Emprestimo(
                nextEmprestimoId(), nextCodigoEmprestimo(), rafael.getId(), cleanCode.getId(),
                hoje.minusDays(17).format(DATE_FMT),
                hoje.minusDays(3).format(DATE_FMT),
                null,
                StatusEmprestimo.ATRASADO, false, true,
                null, null, "João Caldeira"));

        emprestimos.add(new Emprestimo(
                nextEmprestimoId(), nextCodigoEmprestimo(), gabriel.getId(), engSoftware.getId(),
                hoje.minusDays(16).format(DATE_FMT),
                hoje.minusDays(2).format(DATE_FMT),
                null,
                StatusEmprestimo.ATRASADO, false, true,
                null, null, "Vinícius Felix"));

        emprestimos.add(new Emprestimo(
                nextEmprestimoId(), nextCodigoEmprestimo(), fernanda.getId(), arquiteturaLimpa.getId(),
                hoje.format(DATE_FMT),
                hoje.plusDays(14).format(DATE_FMT),
                null,
                StatusEmprestimo.AGUARDANDO_RETIRADA, false, false,
                null, null, "Equipe Biblioteca"));

        emprestimos.add(new Emprestimo(
                nextEmprestimoId(), nextCodigoEmprestimo(), camila.getId(), grandeSertao.getId(),
                hoje.minusDays(3).format(DATE_FMT),
                hoje.plusDays(11).format(DATE_FMT),
                null,
                StatusEmprestimo.EM_ANDAMENTO, false, true,
                null, null, "João Caldeira"));

        // Recalcula quantidadeEmprestada dos livros a partir dos empréstimos ativos
        recalcularQuantidadesEmprestadas();

        // Registros de condição de exemplares (UC15)
        registrosCondicao.add(new RegistroCondicao(
                nextCondicaoId(), book1984.getId(), "DEVOLUCAO_DANIFICADA", 1,
                "Lombada descolada identificada na devolução; encaminhado para restauração.",
                "João Caldeira", LocalDateTime.now().minusDays(3).format(DATETIME_FMT),
                false, null, null));
        registrosCondicao.add(new RegistroCondicao(
                nextCondicaoId(), domCasmurro.getId(), "MARCAR_DANIFICADO", 1,
                "Páginas finais com umidade; separado do acervo disponível.",
                "Vinícius Felix", LocalDateTime.now().minusDays(1).format(DATETIME_FMT),
                false, null, null));
    }

    private void seedReturnedBatch(Livro livro, int count, List<Leitor> pool, LocalDate hoje) {
        for (int i = 0; i < count; i++) {
            Leitor leitor = pool.get(i % pool.size());
            int daysAgo = 25 + (i * 4);
            emprestimos.add(new Emprestimo(
                    nextEmprestimoId(), "EMP-HIST", leitor.getId(), livro.getId(),
                    hoje.minusDays(daysAgo + 14).format(DATE_FMT),
                    hoje.minusDays(daysAgo).format(DATE_FMT),
                    hoje.minusDays(daysAgo + 2).format(DATE_FMT),
                    StatusEmprestimo.DEVOLVIDO, false, true,
                    CondicaoExemplar.BOAS_CONDICOES, "Devolvido sem avarias.",
                    "Equipe Biblioteca"));
        }
    }

    public synchronized void recalcularQuantidadesEmprestadas() {
        Map<Long, Integer> counts = new HashMap<>();
        for (Emprestimo e : emprestimos) {
            if (e.getStatus().isAtivo()) {
                counts.merge(e.getLivroId(), 1, Integer::sum);
            }
        }
        for (Livro l : livros) {
            l.setQuantidadeEmprestada(counts.getOrDefault(l.getId(), 0));
        }
    }
}
