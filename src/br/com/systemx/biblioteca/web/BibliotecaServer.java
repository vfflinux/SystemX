package br.com.systemx.biblioteca.web;

import br.com.systemx.biblioteca.model.Funcionario;
import br.com.systemx.biblioteca.service.AuthService;
import br.com.systemx.biblioteca.service.BibliotecaService;
import br.com.systemx.biblioteca.service.BusinessException;
import br.com.systemx.biblioteca.util.JsonUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class BibliotecaServer {
    private final int port;
    private final Path webRoot;
    private final AuthService authService;
    private final BibliotecaService service;
    private HttpServer server;

    public BibliotecaServer(int port, Path webRoot, AuthService authService, BibliotecaService service) {
        this.port = port;
        this.webRoot = webRoot.toAbsolutePath().normalize();
        this.authService = authService;
        this.service = service;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());

            // Healthcheck público para Render / Railway / Docker
            if ("GET".equals(method) && "/api/health".equals(path)) {
                sendJson(exchange, 200, Map.of("status", "ok", "service", "SystemX"));
                return;
            }

            // Endpoints públicos de autenticação (UC01)
            if ("POST".equals(method) && "/api/auth/login".equals(path)) {
                Map<String, Object> body = readJsonBody(exchange);
                Map<String, Object> res = authService.login(
                        JsonUtil.getString(body, "email"),
                        JsonUtil.getString(body, "senha"));
                sendJson(exchange, 200, res);
                return;
            }
            if ("POST".equals(method) && "/api/auth/viewer".equals(path)) {
                sendJson(exchange, 200, authService.loginViewer());
                return;
            }
            if ("POST".equals(method) && "/api/auth/logout".equals(path)) {
                authService.logout(extractToken(exchange));
                sendJson(exchange, 200, Map.of("ok", true));
                return;
            }

            // Todos os demais endpoints exigem sessão autenticada (UC01)
            String token = extractToken(exchange);
            Funcionario usuario = authService.authenticate(token);

            if ("GET".equals(method) && "/api/auth/me".equals(path)) {
                sendJson(exchange, 200, Map.of("usuario", authService.getSessionUser(token)));
                return;
            }
            if ("POST".equals(method) && "/api/auth/switch-role".equals(path)) {
                Map<String, Object> body = readJsonBody(exchange);
                sendJson(exchange, 200, authService.switchRoleSession(token, JsonUtil.getString(body, "cargo")));
                return;
            }

            // Bloqueio global no backend: contas Somente Visualização não podem executar escrita (POST/PUT/DELETE)
            if (!"GET".equals(method)) {
                authService.requireWriteAccess(usuario);
            }

            // Dashboard & Busca Global
            if ("GET".equals(method) && "/api/dashboard".equals(path)) {
                sendJson(exchange, 200, service.getDashboard(query.get("filtro")));
                return;
            }
            if ("GET".equals(method) && "/api/busca".equals(path)) {
                sendJson(exchange, 200, service.buscaGlobal(query.get("q")));
                return;
            }

            // Leitores (UC02-UC05)
            if ("/api/leitores".equals(path)) {
                if ("GET".equals(method)) {
                    sendJson(exchange, 200, service.listarLeitores(query.get("q"), query.get("status")));
                    return;
                }
                if ("POST".equals(method)) {
                    sendJson(exchange, 201, service.cadastrarLeitor(readJsonBody(exchange)));
                    return;
                }
            }
            if (path.startsWith("/api/leitores/")) {
                String tail = path.substring("/api/leitores/".length());
                String[] parts = tail.split("/");
                long id = parseId(parts[0]);
                if (parts.length == 1 && "PUT".equals(method)) {
                    sendJson(exchange, 200, service.editarLeitor(id, readJsonBody(exchange)));
                    return;
                }
                if (parts.length == 2 && "POST".equals(method)) {
                    if ("desativar".equals(parts[1])) {
                        sendJson(exchange, 200, service.alterarStatusLeitor(id, false));
                        return;
                    }
                    if ("reativar".equals(parts[1])) {
                        sendJson(exchange, 200, service.alterarStatusLeitor(id, true));
                        return;
                    }
                }
            }

            // Livros & Condição de Exemplares (UC06-UC09, UC15)
            if ("/api/livros".equals(path)) {
                if ("GET".equals(method)) {
                    sendJson(exchange, 200, service.listarLivros(
                            query.get("q"), query.get("categoria"), query.get("status")));
                    return;
                }
                if ("POST".equals(method)) {
                    sendJson(exchange, 201, service.cadastrarLivro(readJsonBody(exchange)));
                    return;
                }
            }
            if (path.startsWith("/api/livros/")) {
                String tail = path.substring("/api/livros/".length());
                String[] parts = tail.split("/");
                long id = parseId(parts[0]);
                if (parts.length == 1 && "PUT".equals(method)) {
                    sendJson(exchange, 200, service.editarLivro(id, readJsonBody(exchange)));
                    return;
                }
                if (parts.length == 2 && "POST".equals(method)) {
                    if ("desativar".equals(parts[1])) {
                        sendJson(exchange, 200, service.alterarStatusLivro(id, false));
                        return;
                    }
                    if ("reativar".equals(parts[1])) {
                        sendJson(exchange, 200, service.alterarStatusLivro(id, true));
                        return;
                    }
                    if ("condicao".equals(parts[1])) {
                        sendJson(exchange, 200, service.atualizarCondicaoExemplar(id, readJsonBody(exchange), usuario));
                        return;
                    }
                }
            }

            // Registros de condição / Supervisão (UC15)
            if ("GET".equals(method) && "/api/condicoes".equals(path)) {
                sendJson(exchange, 200, service.listarRegistrosCondicao());
                return;
            }
            if (path.startsWith("/api/condicoes/") && path.endsWith("/supervisionar") && "POST".equals(method)) {
                String idStr = path.substring("/api/condicoes/".length(), path.length() - "/supervisionar".length());
                sendJson(exchange, 200, service.supervisionarRegistroCondicao(parseId(idStr), usuario));
                return;
            }

            // Empréstimos (UC10-UC14)
            if ("/api/emprestimos".equals(path)) {
                if ("GET".equals(method)) {
                    Long leitorId = query.containsKey("leitorId") && !query.get("leitorId").isBlank()
                            ? parseId(query.get("leitorId")) : null;
                    sendJson(exchange, 200, service.listarEmprestimos(query.get("q"), query.get("status"), leitorId));
                    return;
                }
                if ("POST".equals(method)) {
                    sendJson(exchange, 201, service.registrarEmprestimo(readJsonBody(exchange), usuario));
                    return;
                }
            }
            if (path.startsWith("/api/emprestimos/")) {
                String tail = path.substring("/api/emprestimos/".length());
                String[] parts = tail.split("/");
                long id = parseId(parts[0]);
                if (parts.length == 2 && "POST".equals(method)) {
                    if ("devolver".equals(parts[1])) {
                        sendJson(exchange, 200, service.registrarDevolucao(id, readJsonBody(exchange), usuario));
                        return;
                    }
                    if ("prorrogar".equals(parts[1])) {
                        sendJson(exchange, 200, service.prorrogarEmprestimo(id));
                        return;
                    }
                    if ("cancelar".equals(parts[1])) {
                        sendJson(exchange, 200, service.cancelarEmprestimo(id));
                        return;
                    }
                    if ("confirmar-retirada".equals(parts[1])) {
                        sendJson(exchange, 200, service.confirmarRetirada(id));
                        return;
                    }
                }
            }

            // Funcionários (UC16 — Exclusivo Administrador)
            if ("/api/funcionarios".equals(path)) {
                if ("GET".equals(method)) {
                    sendJson(exchange, 200, service.listarFuncionarios(usuario));
                    return;
                }
                if ("POST".equals(method)) {
                    sendJson(exchange, 201, service.cadastrarFuncionario(readJsonBody(exchange), usuario));
                    return;
                }
            }
            if (path.startsWith("/api/funcionarios/")) {
                String tail = path.substring("/api/funcionarios/".length());
                String[] parts = tail.split("/");
                long id = parseId(parts[0]);
                if (parts.length == 1 && "PUT".equals(method)) {
                    sendJson(exchange, 200, service.editarFuncionario(id, readJsonBody(exchange), usuario));
                    return;
                }
                if (parts.length == 2 && "POST".equals(method)) {
                    if ("desativar".equals(parts[1])) {
                        sendJson(exchange, 200, service.alterarStatusFuncionario(id, false, usuario));
                        return;
                    }
                    if ("reativar".equals(parts[1])) {
                        sendJson(exchange, 200, service.alterarStatusFuncionario(id, true, usuario));
                        return;
                    }
                }
            }

            // Resetar dados de demonstração (Admin)
            if ("POST".equals(method) && "/api/admin/reset-demo".equals(path)) {
                service.resetarBaseDemo(usuario);
                sendJson(exchange, 200, Map.of("ok", true, "mensagem", "Base de demonstração restaurada com sucesso."));
                return;
            }

            sendJson(exchange, 404, Map.of("erro", "Endpoint não encontrado: " + method + " " + path));
        } catch (BusinessException be) {
            sendJson(exchange, be.getStatusCode(), Map.of("erro", be.getMessage()));
        } catch (IllegalArgumentException iae) {
            sendJson(exchange, 400, Map.of("erro", iae.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, Map.of("erro", "Erro interno do servidor: " + e.getMessage()));
        }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String rawPath = exchange.getRequestURI().getPath();
        if (rawPath == null || "/".equals(rawPath)) {
            rawPath = "/index.html";
        }
        Path resolved = webRoot.resolve(rawPath.substring(1)).normalize();
        if (!resolved.startsWith(webRoot) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
            resolved = webRoot.resolve("index.html");
        }

        byte[] bytes = Files.readAllBytes(resolved);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", detectContentType(resolved.getFileName().toString()));
        headers.set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String detectContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (raw.isBlank()) return new LinkedHashMap<>();
            return JsonUtil.parseObject(raw);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = JsonUtil.stringify(payload).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String extractToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private long parseId(String str) {
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "Identificador inválido: " + str);
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return map;
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }
}
