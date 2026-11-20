# ============================================================
# SystemX — Dockerfile (Multi-stage Java 21)
# Pronto para deploy no Render, Railway ou Docker local
# ============================================================

# Etapa 1: Compilação Java 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY src ./src
RUN mkdir -p build \
    && find src -name "*.java" > build/sources.txt \
    && javac -encoding UTF-8 -d build @build/sources.txt

# Etapa 2: Runtime leve (JRE 21)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build ./build
COPY web ./web
RUN mkdir -p /app/data

EXPOSE 8080

# Lê a porta automaticamente da variável de ambiente PORT (Render/Railway) ou usa 8080
CMD ["java", "-cp", "build", "br.com.systemx.biblioteca.Main"]
