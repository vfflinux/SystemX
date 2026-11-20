package br.com.systemx.biblioteca.service;

import br.com.systemx.biblioteca.model.Cargo;
import br.com.systemx.biblioteca.model.Funcionario;
import br.com.systemx.biblioteca.repository.BibliotecaRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
    private static final class SessionInfo {
        long activeUserId;
        final long originalUserId;
        final boolean canSwitchRole;

        SessionInfo(long userId, boolean canSwitchRole) {
            this.activeUserId = userId;
            this.originalUserId = userId;
            this.canSwitchRole = canSwitchRole;
        }
    }

    private final BibliotecaRepository repo;
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public AuthService(BibliotecaRepository repo) {
        this.repo = repo;
    }

    public Map<String, Object> login(String email, String senha) {
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            throw new BusinessException(400, "Informe e-mail e senha para entrar no SystemX.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        String expectedHash = BibliotecaRepository.hashPassword(senha);

        synchronized (repo) {
            for (Funcionario f : repo.getFuncionarios()) {
                if (f.getEmail() != null && f.getEmail().trim().toLowerCase().equals(normalizedEmail)) {
                    if (!f.isAtivo()) {
                        throw new BusinessException(403, "Conta desativada. Contate um administrador.");
                    }
                    if (!expectedHash.equals(f.getSenhaHash())) {
                        throw new BusinessException(401, "Credenciais inválidas. Verifique e-mail e senha.");
                    }
                    String token = UUID.randomUUID().toString();
                    boolean allowSwitch = f.isAdministrador();
                    sessions.put(token, new SessionInfo(f.getId(), allowSwitch));
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("token", token);
                    result.put("usuario", buildSessionUserMap(f, allowSwitch));
                    return result;
                }
            }
        }
        throw new BusinessException(401, "Credenciais inválidas. Verifique e-mail e senha.");
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public Map<String, Object> loginViewer() {
        synchronized (repo) {
            for (Funcionario f : repo.getFuncionarios()) {
                if (f.isAtivo() && f.isVisualizador()) {
                    String token = UUID.randomUUID().toString();
                    sessions.put(token, new SessionInfo(f.getId(), false));
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("token", token);
                    result.put("usuario", buildSessionUserMap(f, false));
                    return result;
                }
            }
        }
        throw new BusinessException(404, "Nenhuma conta Somente Visualização ativa encontrada.");
    }

    public Funcionario authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "Sessão não autenticada. Faça login para continuar.");
        }
        SessionInfo session = sessions.get(token);
        if (session == null) {
            throw new BusinessException(401, "Sessão expirada ou inválida. Faça login novamente.");
        }
        synchronized (repo) {
            for (Funcionario f : repo.getFuncionarios()) {
                if (f.getId() == session.activeUserId) {
                    if (!f.isAtivo()) {
                        sessions.remove(token);
                        throw new BusinessException(403, "Sua conta foi desativada.");
                    }
                    return f;
                }
            }
        }
        throw new BusinessException(401, "Usuário não encontrado.");
    }

    public Map<String, Object> getSessionUser(String token) {
        Funcionario f = authenticate(token);
        SessionInfo session = sessions.get(token);
        return buildSessionUserMap(f, session != null && session.canSwitchRole);
    }

    /**
     * Permite que um Administrador autenticado simule outros perfis (Funcionário ou Somente Visualização)
     * pelo seletor RBAC da barra lateral e volte ao perfil Administrador.
     * Contas de Funcionário ou Somente Visualização NÃO podem elevar privilégios.
     */
    public Map<String, Object> switchRoleSession(String currentToken, String targetCargo) {
        authenticate(currentToken);
        SessionInfo session = sessions.get(currentToken);
        if (session == null || !session.canSwitchRole) {
            throw new BusinessException(403, "Apenas Administradores podem alternar a visualização de perfis RBAC.");
        }

        Cargo cargo = Cargo.fromString(targetCargo);
        synchronized (repo) {
            Funcionario match = null;
            if (cargo == Cargo.ADMINISTRADOR) {
                for (Funcionario f : repo.getFuncionarios()) {
                    if (f.getId() == session.originalUserId && f.isAtivo()) {
                        match = f;
                        break;
                    }
                }
            }
            if (match == null) {
                for (Funcionario f : repo.getFuncionarios()) {
                    if (f.isAtivo() && f.getCargo() == cargo) {
                        match = f;
                        break;
                    }
                }
            }
            if (match == null) {
                throw new BusinessException(404, "Nenhum usuário ativo encontrado para o perfil " + cargo.getDescricao() + ".");
            }
            session.activeUserId = match.getId();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", currentToken);
            result.put("usuario", buildSessionUserMap(match, session.canSwitchRole));
            return result;
        }
    }

    public void requireWriteAccess(Funcionario user) {
        if (user == null || !user.podeEscrever()) {
            throw new BusinessException(403,
                    "Acesso somente leitura: sua conta não tem permissão para criar, editar ou alterar dados no SystemX.");
        }
    }

    public void requireAdmin(Funcionario user) {
        if (user == null || !user.isAdministrador()) {
            throw new BusinessException(403, "Ação restrita a Administradores do SystemX.");
        }
    }

    private Map<String, Object> buildSessionUserMap(Funcionario f, boolean canSwitchRole) {
        Map<String, Object> map = f.toPublicMap();
        map.put("podeAlternarPerfil", canSwitchRole);
        return map;
    }
}
