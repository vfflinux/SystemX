# SystemX — Sistema de Gestão de Biblioteca

Aplicação web desenvolvida em **Java 21**, **HTML5**, **CSS3** e **JavaScript** puro (sem dependências externas), seguindo os casos de uso **UC01 a UC16**, o layout do Dashboard e a paleta oficial do projeto (`#2C3639`, `#3F4E4F`, `#A27B5C`, `#DCD7C9`).

## Como executar

```bash
chmod +x run.sh
./run.sh
```

Acesse no navegador: **http://localhost:8080**

*(Opcional: informe outra porta, por exemplo `./run.sh 9090`.)*

## Configuração de Credenciais (`.env`)

As contas iniciais (Administrador, Funcionário e Somente Visualização) são carregadas automaticamente do arquivo `.env` na raiz do projeto (veja o modelo em `.env.example`).

| Perfil | Variáveis no `.env` | Permissão |
|---|---|---|
| **Administrador** | `ADMIN_EMAIL` / `ADMIN_SENHA` | Acesso total (inclui gestão de funcionários e supervisão) |
| **Funcionário** | `FUNCIONARIO_EMAIL` / `FUNCIONARIO_SENHA` | Operações de livros, leitores e empréstimos |
| **Somente Visualização** | `VISUALIZADOR_EMAIL` / `VISUALIZADOR_SENHA` | Apenas consulta (sem permissão para criar, editar ou alterar) |

## Casos de Uso Implementados (UC01–UC16)

- **UC01 — Autenticar no sistema:** Login com sessão autenticada e controle de acesso por perfil (RBAC: Funcionário / Administrador).
- **UC02–UC05 — Leitores (`Usuários`):** Cadastro completo (nome, endereço, CPF, telefone, e-mail), edição, consulta e desativação segura (bloqueada se houver empréstimo ativo ou pendência, preservando o histórico).
- **UC06–UC09 — Livros (`Livros & Acervo`):** Cadastro (título, autor, editora, ISBN, ano, categoria, quantidade), edição, consulta com disponibilidade quantitativa e desativação segura (bloqueada se houver empréstimo ativo).
- **UC10 — Registrar empréstimo:** Verifica elegibilidade, pendências, limite de **3 empréstimos simultâneos**, disponibilidade e aplica prazo padrão de **14 dias**.
- **UC11 — Cancelar empréstimo:** Permitido somente enquanto o empréstimo estiver em `Aguardando Retirada` (antes da retirada física do livro).
- **UC12 — Registrar devolução:** Avaliação obrigatória da condição do exemplar (`Boas condições` retorna ao estoque disponível; `Danificado` separa para manutenção).
- **UC13 — Prorrogar empréstimo:** Prorrogação única de **+14 dias**, permitida apenas antes do vencimento.
- **UC14 — Consultar empréstimos:** Filtros por status (`Em dia`, `Aguardando Retirada`, `Próx. Vencimento`, `Atrasados`, `Devolvidos`, `Cancelados`) e histórico por leitor.
- **UC15 — Gerenciar condição de exemplares:** Separação de exemplares danificados, retorno ao acervo após reparo e trilha de supervisão pelo Administrador.
- **UC16 — Gerenciar funcionários (`Configurações`):** Área exclusiva do Administrador para cadastrar, editar e desativar contas internas.

## Deploy Online 100% em Java (Render ou Railway com Docker)

O projeto inclui `Dockerfile`, `.dockerignore` e `render.yaml` prontos para hospedar o sistema inteiro (backend Java 21 + frontend HTML/CSS/JS) na nuvem:

1. Faça `git push` para o seu repositório no GitHub.
2. No **Render** (https://render.com) → **New +** → **Web Service** → conecte o repositório `SystemX` → selecione **Runtime: Docker** e plano **Free**.
   - *(Ou no **Railway**: New Project → Deploy from GitHub Repo — ele detecta o `Dockerfile` automaticamente.)*
3. Em **Environment Variables**, cadastre as variáveis do seu `.env`:
   - `ADMIN_NOME`, `ADMIN_EMAIL`, `ADMIN_SENHA`
   - `FUNCIONARIO_NOME`, `FUNCIONARIO_EMAIL`, `FUNCIONARIO_SENHA`
   - `VISUALIZADOR_NOME`, `VISUALIZADOR_EMAIL`, `VISUALIZADOR_SENHA`
4. Clique em **Deploy**. O servidor lê automaticamente a porta da variável `PORT` e responde em `/api/health`.

## Estrutura do Projeto

- `src/br/com/systemx/biblioteca/` — Backend Java (modelos, repositório com persistência local em `data/systemx-db.json`, serviços com todas as regras de negócio e servidor HTTP REST).
- `web/` — Frontend em HTML5, CSS3 e JS (`index.html`, `css/styles.css`, `js/app.js`).
- `database/schema.sql` — Script DDL MySQL alinhado à arquitetura do Relatório Executivo (2ª Parcial).
# SystemX
