-- ============================================================================
-- SystemX — Sistema de Gestão de Biblioteca
-- Modelagem Relacional MySQL (Projeto e Engenharia de Software — A3)
-- ============================================================================

CREATE DATABASE IF NOT EXISTS systemx_biblioteca
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE systemx_biblioteca;

-- UC01 / UC16: Funcionários e Administradores (RBAC)
CREATE TABLE IF NOT EXISTS funcionarios (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nome VARCHAR(120) NOT NULL,
  email VARCHAR(160) NOT NULL UNIQUE,
  matricula VARCHAR(32) NOT NULL UNIQUE,
  senha_hash VARCHAR(128) NOT NULL,
  cargo ENUM('VISUALIZADOR', 'FUNCIONARIO', 'ADMINISTRADOR') NOT NULL DEFAULT 'FUNCIONARIO',
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- UC02–UC05: Leitores (não acessam diretamente o sistema)
CREATE TABLE IF NOT EXISTS leitores (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nome VARCHAR(120) NOT NULL,
  endereco VARCHAR(255) NOT NULL,
  cpf VARCHAR(14) NOT NULL UNIQUE,
  telefone VARCHAR(24) NOT NULL,
  email VARCHAR(160) NOT NULL,
  perfil VARCHAR(40) NOT NULL DEFAULT 'Aluno',
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- UC06–UC09 / UC15: Livros com controle quantitativo de exemplares
CREATE TABLE IF NOT EXISTS livros (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  titulo VARCHAR(180) NOT NULL,
  autor VARCHAR(140) NOT NULL,
  editora VARCHAR(120) NOT NULL,
  isbn VARCHAR(32) NOT NULL UNIQUE,
  ano_publicacao INT NOT NULL,
  categoria VARCHAR(80) NOT NULL,
  quantidade_total INT NOT NULL CHECK (quantidade_total >= 1),
  quantidade_emprestada INT NOT NULL DEFAULT 0 CHECK (quantidade_emprestada >= 0),
  quantidade_danificada INT NOT NULL DEFAULT 0 CHECK (quantidade_danificada >= 0),
  cor_capa VARCHAR(16) NOT NULL DEFAULT '#3F4E4F',
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_exemplares_coerentes CHECK (quantidade_emprestada + quantidade_danificada <= quantidade_total)
);

-- UC10–UC14: Empréstimos, devoluções, cancelamentos e prorrogação única
CREATE TABLE IF NOT EXISTS emprestimos (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  codigo VARCHAR(20) NOT NULL UNIQUE,
  leitor_id BIGINT NOT NULL,
  livro_id BIGINT NOT NULL,
  funcionario_id BIGINT NULL,
  data_emprestimo DATE NOT NULL,
  data_prevista_devolucao DATE NOT NULL,
  data_efetiva_devolucao DATE NULL,
  status ENUM('AGUARDANDO_RETIRADA', 'EM_ANDAMENTO', 'ATRASADO', 'DEVOLVIDO', 'CANCELADO') NOT NULL DEFAULT 'EM_ANDAMENTO',
  prorrogado BOOLEAN NOT NULL DEFAULT FALSE,
  retirado BOOLEAN NOT NULL DEFAULT TRUE,
  condicao_devolucao ENUM('BOAS_CONDICOES', 'DANIFICADO') NULL,
  observacao_condicao TEXT NULL,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_emp_leitor FOREIGN KEY (leitor_id) REFERENCES leitores(id),
  CONSTRAINT fk_emp_livro FOREIGN KEY (livro_id) REFERENCES livros(id),
  CONSTRAINT fk_emp_func FOREIGN KEY (funcionario_id) REFERENCES funcionarios(id)
);

-- UC15: Histórico e supervisão administrativa da condição dos exemplares
CREATE TABLE IF NOT EXISTS registros_condicao (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  livro_id BIGINT NOT NULL,
  acao ENUM('MARCAR_DANIFICADO', 'REPARAR_DISPONIBILIZAR', 'DEVOLUCAO_DANIFICADA') NOT NULL,
  quantidade INT NOT NULL DEFAULT 1 CHECK (quantidade >= 1),
  observacao TEXT NOT NULL,
  responsavel VARCHAR(120) NOT NULL,
  data_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  supervisionado BOOLEAN NOT NULL DEFAULT FALSE,
  supervisor_nome VARCHAR(120) NULL,
  data_supervisao DATETIME NULL,
  CONSTRAINT fk_cond_livro FOREIGN KEY (livro_id) REFERENCES livros(id)
);

CREATE INDEX idx_emp_status ON emprestimos(status);
CREATE INDEX idx_emp_leitor ON emprestimos(leitor_id);
CREATE INDEX idx_emp_livro ON emprestimos(livro_id);
CREATE INDEX idx_livro_categoria ON livros(categoria);
