package br.com.systemx.biblioteca.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor simples de arquivo .env (com suporte a variáveis de ambiente do sistema operacional).
 */
public final class EnvConfig {
    private final Map<String, String> values = new LinkedHashMap<>();

    private EnvConfig() {
    }

    public static EnvConfig load(Path envFile) {
        EnvConfig config = new EnvConfig();
        if (envFile != null && Files.exists(envFile)) {
            try {
                List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\""))
                            || (val.startsWith("'") && val.endsWith("'"))) {
                        if (val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                    }
                    config.values.put(key, val);
                }
            } catch (IOException e) {
                throw new RuntimeException("Erro ao ler arquivo .env: " + e.getMessage(), e);
            }
        }
        return config;
    }

    public String get(String key) {
        String sys = System.getenv(key);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String v = values.get(key);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    public String getOrDefault(String key, String defaultValue) {
        String val = get(key);
        return val != null ? val : defaultValue;
    }

    public String require(String key) {
        String val = get(key);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException(
                    "Variável de ambiente obrigatória ausente no .env: " + key);
        }
        return val;
    }
}
