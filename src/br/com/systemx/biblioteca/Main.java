package br.com.systemx.biblioteca;

import br.com.systemx.biblioteca.repository.BibliotecaRepository;
import br.com.systemx.biblioteca.service.AuthService;
import br.com.systemx.biblioteca.service.BibliotecaService;
import br.com.systemx.biblioteca.util.EnvConfig;
import br.com.systemx.biblioteca.web.BibliotecaServer;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath();
        Path envFile = projectRoot.resolve(".env");
        EnvConfig env = EnvConfig.load(envFile);

        int port = 8080;
        String envPort = env.get("PORT");
        if (envPort != null) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {
            }
        }
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        Path dbFile = projectRoot.resolve("data").resolve("systemx-db.json");
        Path webRoot = projectRoot.resolve("web");

        BibliotecaRepository repository = new BibliotecaRepository(dbFile, env);
        AuthService authService = new AuthService(repository);
        BibliotecaService service = new BibliotecaService(repository, authService);

        BibliotecaServer server = new BibliotecaServer(port, webRoot, authService, service);
        server.start();

        System.out.println("============================================================");
        System.out.println("  SystemX — Sistema de Gestão de Biblioteca");
        System.out.println("  Servidor Java rodando em: http://localhost:" + port);
        System.out.println("  Configuração (.env): " + envFile);
        System.out.println("  Persistência local: " + dbFile);
        System.out.println("  Contas carregadas do .env:");
        System.out.println("    Administrador:        " + env.require("ADMIN_EMAIL"));
        System.out.println("    Funcionário:          " + env.require("FUNCIONARIO_EMAIL"));
        System.out.println("    Somente Visualização: " + env.getOrDefault("VISUALIZADOR_EMAIL", "visualizador@gmail.com"));
        System.out.println("============================================================");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SystemX] Encerrando servidor...");
            server.stop();
        }));
    }
}
