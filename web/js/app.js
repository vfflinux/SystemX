(() => {
  const state = {
    token: localStorage.getItem('systemx_token') || null,
    user: null,
    activeTab: 'dashboard',
    activeConfigSub: 'funcionarios',
    simulateNetworkError: false,

    dashFilter: 'todos',
    empFilter: 'todos',
    empSearch: '',
    bookFilter: 'todos',
    bookCategory: 'todas',
    bookSearch: '',
    readerFilter: 'todos',
    readerSearch: '',

    cache: {
      dashboard: null,
      emprestimos: [],
      livros: [],
      leitores: [],
      funcionarios: [],
      condicoes: [],
    },
  };

  // ==================== HELPERS ====================

  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => Array.from(document.querySelectorAll(sel));

  function escapeHtml(val) {
    return String(val ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatDateBR(isoDate) {
    if (!isoDate) return '—';
    const parts = String(isoDate).split('-');
    if (parts.length !== 3) return isoDate;
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }

  function formatShortDateBR(isoDate) {
    if (!isoDate) return '—';
    const parts = String(isoDate).split('-');
    if (parts.length !== 3) return isoDate;
    return `${parts[2]}/${parts[1]}`;
  }

  function miniCoverText(title) {
    if (!title) return 'LIVRO';
    const clean = title.replace(/^(O|A|Os|As)\s+/i, '').trim();
    return clean.slice(0, 10);
  }

  function showToast(message, type = 'info') {
    const container = $('#toast-container');
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.textContent = message;
    container.appendChild(el);
    setTimeout(() => {
      el.style.opacity = '0';
      el.style.transition = 'opacity 0.2s ease';
      setTimeout(() => el.remove(), 220);
    }, 3600);
  }

  // Se houver URL externa configurada em web/config.js (ex: front na Vercel + back Java no Render), usa ela.
  // Se aberto via file:// ou Live Server local (:5500 / :5501), aponta para localhost:8080.
  // Caso contrário (rodando direto no servidor Java ou Render), usa o mesmo domínio ('').
  const configuredApi = (window.SYSTEMX_API_URL || '').trim().replace(/\/+$/, '');
  const isLiveServer = (window.location.hostname === '127.0.0.1' || window.location.hostname === 'localhost')
    && (window.location.port === '5500' || window.location.port === '5501');
  const API_BASE = configuredApi || ((window.location.protocol === 'file:' || isLiveServer)
    ? 'http://localhost:8080'
    : '');

  async function api(path, options = {}) {
    if (state.simulateNetworkError && !path.startsWith('/api/auth/')) {
      throw new Error('Falha de conexão simulada com o servidor SystemX (HTTP 503).');
    }

    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (state.token) {
      headers.Authorization = `Bearer ${state.token}`;
    }

    let resp;
    try {
      resp = await fetch(API_BASE + path, {
        ...options,
        headers,
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      });
    } catch {
      throw new Error('Não foi possível conectar ao servidor Java (http://localhost:8080). Inicie o backend com ./run.sh.');
    }

    const data = await resp.json().catch(() => ({}));
    if (!resp.ok) {
      if (resp.status === 401 && !path.startsWith('/api/auth/login')) {
        handleForceLogout();
      }
      if (resp.status === 404 && !data.erro && path.startsWith('/api/')) {
        throw new Error('Backend Java não encontrado neste domínio. Configure a URL do servidor Java (Render/Railway) em web/config.js ou acesse diretamente pela URL do Render.');
      }
      throw new Error(data.erro || `Erro na requisição (${resp.status}).`);
    }
    return data;
  }

  function statusBadgeHtml(status, label) {
    const map = {
      EM_ANDAMENTO: 'status-em-dia',
      ATRASADO: 'status-atrasado',
      DEVOLVIDO: 'status-devolvido',
      AGUARDANDO_RETIRADA: 'status-aguardando',
      CANCELADO: 'status-cancelado',
    };
    const cls = map[status] || 'status-cancelado';
    return `<span class="status-badge ${cls}">${escapeHtml(label || status)}</span>`;
  }

  // ==================== AUTENTICAÇÃO (UC01) ====================

  function handleForceLogout() {
    state.token = null;
    state.user = null;
    localStorage.removeItem('systemx_token');
    $('#app-shell').classList.add('hidden');
    $('#login-screen').classList.remove('hidden');
  }

  function canWrite() {
    return Boolean(state.user && state.user.podeEscrever);
  }

  function syncUserUI() {
    if (!state.user) return;
    const u = state.user;
    const isAdmin = u.cargo === 'ADMINISTRADOR';
    const isReadOnly = !u.podeEscrever;

    $('#sidebar-user-avatar').textContent = u.iniciais || 'SX';
    $('#sidebar-user-name').textContent = u.nome;
    $('#sidebar-user-role').textContent = u.cargoDescricao;

    $('#topbar-user-avatar').textContent = u.iniciais || 'SX';
    $('#topbar-user-name').textContent = u.cargoDescricao;
    const roleShort = isAdmin ? 'Admin' : (isReadOnly ? 'Visualização' : 'Operacional');
    $('#topbar-user-role').textContent = `${roleShort} ▾`;

    $('#profile-pop-name').textContent = u.nome;
    $('#profile-pop-email').textContent = u.email;

    const rbacSel = $('#rbac-select');
    rbacSel.value = u.cargo;
    rbacSel.disabled = !u.podeAlternarPerfil;
    rbacSel.title = u.podeAlternarPerfil
      ? 'Alterne entre perfis para testar permissões RBAC'
      : 'Perfil bloqueado conforme suas permissões de acesso';

    // Banner e bloqueios do modo Somente Visualização
    $('#readonly-banner').classList.toggle('hidden', !isReadOnly);
    $('#new-loan-btn').classList.toggle('hidden', isReadOnly);
    $('#emp-new-btn').classList.toggle('hidden', isReadOnly);
    $('#book-new-btn').classList.toggle('hidden', isReadOnly);
    $('#reader-new-btn').classList.toggle('hidden', isReadOnly);

    // Controles exclusivos de Administrador (UC16)
    $('#staff-new-btn').classList.toggle('hidden', !(isAdmin && state.activeTab === 'configuracoes' && state.activeConfigSub === 'funcionarios'));
    $('#staff-rbac-lock').classList.toggle('hidden', isAdmin);
    $('#staff-panel-card').classList.toggle('hidden', !isAdmin);
    $('#admin-reset-box').classList.toggle('hidden', !isAdmin);
  }

  async function tryRestoreSession() {
    if (!state.token) {
      $('#login-screen').classList.remove('hidden');
      $('#app-shell').classList.add('hidden');
      return;
    }
    try {
      const data = await api('/api/auth/me');
      state.user = data.usuario;
      enterApp();
    } catch {
      handleForceLogout();
    }
  }

  async function enterApp() {
    $('#login-screen').classList.add('hidden');
    $('#app-shell').classList.remove('hidden');
    syncUserUI();
    await refreshActiveTab();
  }

  // ==================== NAVEGAÇÃO ENTRE ABAS ====================

  async function switchTab(tabName) {
    state.activeTab = tabName;
    $$('.nav-item').forEach((btn) => btn.classList.toggle('active', btn.dataset.tab === tabName));
    $$('.tab-view').forEach((sec) => sec.classList.toggle('active', sec.id === `tab-${tabName}`));
    $('.sidebar').classList.remove('open');
    syncUserUI();
    await refreshActiveTab();
  }

  async function refreshActiveTab() {
    try {
      switch (state.activeTab) {
        case 'dashboard':
          await loadDashboard();
          break;
        case 'emprestimos':
          await loadEmprestimos();
          break;
        case 'livros':
          await loadLivros();
          break;
        case 'usuarios':
          await loadLeitores();
          break;
        case 'configuracoes':
          await loadConfiguracoes();
          break;
      }
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  // ==================== ABA 1: DASHBOARD ====================

  async function loadDashboard() {
    const data = await api(`/api/dashboard?filtro=${encodeURIComponent(state.dashFilter)}`);
    state.cache.dashboard = data;

    const k = data.kpis;
    $('#kpi-total-livros').textContent = k.totalLivros;
    $('#kpi-livros-sub').textContent = `${k.totalDisponiveis} disponíveis • ${k.totalTitulos} títulos`;
    $('#kpi-usuarios').textContent = k.usuariosCadastrados;
    $('#kpi-usuarios-sub').textContent = 'Leitores ativos no acervo';
    $('#kpi-ativos').textContent = k.emprestimosAtivos;
    $('#kpi-ativos-sub').textContent = 'Prazo padrão de 14 dias';
    $('#kpi-atrasados').textContent = k.livrosAtrasados;
    $('#kpi-atrasados-sub').textContent = k.livrosAtrasados > 0
      ? `${k.livrosAtrasados} pendência(s) bloqueando novos empréstimos`
      : 'Nenhuma pendência em aberto';
    $('#kpi-atrasados-sub').className = `kpi-trend ${k.livrosAtrasados > 0 ? 'negative' : 'positive'}`;

    renderDashRecentLoans(data.emprestimosRecentes || []);
    renderTopBooksChart(data.livrosMaisEmprestados || []);
    renderNotifications(data.notificacoes || []);
  }

  function renderDashRecentLoans(items) {
    const tbody = $('#dash-recent-tbody');
    if (!items.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty-state">Nenhum empréstimo encontrado para este filtro.</td></tr>';
      return;
    }

    tbody.innerHTML = items.map((e) => {
      let subDate = `Vence: ${formatShortDateBR(e.dataPrevistaDevolucao)}`;
      let subDateClass = 'cell-secondary';
      if (e.status === 'ATRASADO') {
        subDate = `Venceu: ${formatShortDateBR(e.dataPrevistaDevolucao)}`;
        subDateClass = 'cell-secondary alert-text';
      } else if (e.status === 'DEVOLVIDO') {
        subDate = `Entregue: ${formatShortDateBR(e.dataEfetivaDevolucao)}`;
      } else if (e.status === 'AGUARDANDO_RETIRADA') {
        subDate = 'Aguardando balcão';
      }

      const readerSub = e.status === 'ATRASADO'
        ? `${escapeHtml(e.leitorPerfil)} • ${e.diasAtraso} dia(s) de atraso`
        : `${escapeHtml(e.leitorPerfil)} • ${escapeHtml(e.codigo)}`;

      let actionBtn = '';
      if (canWrite()) {
        if (e.podeDevolver) {
          actionBtn = `<button type="button" class="btn btn-outline-accent" data-action="devolver" data-id="${e.id}">Devolver</button>`;
        } else if (e.podeConfirmarRetirada) {
          actionBtn = `<button type="button" class="btn btn-outline-accent" data-action="confirmar-retirada" data-id="${e.id}">Retirar</button>`;
        } else {
          actionBtn = `<button type="button" class="btn btn-outline-slate" data-action="recibo" data-id="${e.id}">Recibo</button>`;
        }
      } else if (e.status === 'DEVOLVIDO') {
        actionBtn = `<button type="button" class="btn btn-outline-slate" data-action="recibo" data-id="${e.id}">Recibo</button>`;
      } else {
        actionBtn = '<span class="readonly-pill">Visualização</span>';
      }

      return `
        <tr>
          <td>
            <div class="book-cell">
              <div class="book-thumb" style="background:${escapeHtml(e.livroCorCapa)}">${escapeHtml(miniCoverText(e.livroTitulo))}</div>
              <div>
                <div class="cell-primary">${escapeHtml(e.livroTitulo)}</div>
                <div class="cell-secondary">${escapeHtml(e.livroAutor)}</div>
              </div>
            </div>
          </td>
          <td>
            <div class="cell-primary">${escapeHtml(e.leitorNome)}</div>
            <div class="${e.status === 'ATRASADO' ? 'cell-secondary alert-text' : 'cell-secondary'}">${readerSub}</div>
          </td>
          <td>
            <div class="cell-primary">${formatDateBR(e.dataEmprestimo)}</div>
            <div class="${subDateClass}">${subDate}</div>
          </td>
          <td>${statusBadgeHtml(e.status, e.statusLabel)}</td>
          <td class="align-right">${actionBtn}</td>
        </tr>
      `;
    }).join('');
  }

  function renderTopBooksChart(items) {
    const container = $('#top-books-chart');
    if (!items.length) {
      container.innerHTML = '<div class="empty-state">Sem dados de empréstimos.</div>';
      return;
    }
    const maxVal = Math.max(20, ...items.map((i) => Number(i.quantidade || 0)));
    const ceilMax = Math.ceil(maxVal / 5) * 5;
    const ticks = [ceilMax, Math.round(ceilMax * 0.75), Math.round(ceilMax * 0.5), Math.round(ceilMax * 0.25), 0];

    const barsHtml = items.map((item) => {
      const pct = Math.max(8, Math.round((Number(item.quantidade || 0) / ceilMax) * 100));
      return `
        <div class="chart-bar-col">
          <div class="chart-bar" style="height:${pct}%" data-count="${item.quantidade} empr." title="${escapeHtml(item.titulo)}: ${item.quantidade} empréstimos"></div>
        </div>
      `;
    }).join('');

    const labelsHtml = items.map((item) => `
      <div class="chart-x-label" title="${escapeHtml(item.titulo)}">${escapeHtml(item.rotuloCurto)}</div>
    `).join('');

    container.innerHTML = `
      <div class="chart-canvas">
        <div class="chart-y-axis">
          ${ticks.map((t) => `<span>${t}</span>`).join('')}
        </div>
        <div class="chart-plot">${barsHtml}</div>
      </div>
      <div class="chart-x-labels">
        <div></div>
        <div class="chart-x-row">${labelsHtml}</div>
      </div>
    `;
  }

  function renderNotifications(notifs) {
    $('#notif-count-badge').textContent = notifs.length;
    $('#notif-dot').classList.toggle('hidden', notifs.length === 0);
    const list = $('#notif-list');
    if (!notifs.length) {
      list.innerHTML = '<div class="empty-state">Tudo em dia! Nenhuma pendência ativa.</div>';
      return;
    }
    list.innerHTML = notifs.map((n) => `
      <div class="notif-item ${escapeHtml(String(n.tipo || '').toLowerCase())}">
        <strong>${escapeHtml(n.titulo)}</strong>
        <span>${escapeHtml(n.mensagem)}</span>
      </div>
    `).join('');
  }

  // ==================== ABA 2: EMPRÉSTIMOS (UC10–UC14) ====================

  async function loadEmprestimos() {
    const params = new URLSearchParams({ status: state.empFilter, q: state.empSearch });
    const list = await api(`/api/emprestimos?${params.toString()}`);
    state.cache.emprestimos = list;
    const tbody = $('#emp-tbody');

    if (!list.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="empty-state">Nenhum empréstimo encontrado para os filtros aplicados.</td></tr>';
      return;
    }

    tbody.innerHTML = list.map((e) => {
      const prorrogaHtml = e.prorrogado
        ? '<span class="stock-chip empr">Utilizada (+14d)</span>'
        : '<span class="stock-chip">Original (14d)</span>';

      const dateDetails = e.status === 'DEVOLVIDO'
        ? `Devolvido em ${formatDateBR(e.dataEfetivaDevolucao)}`
        : `Prevista: ${formatDateBR(e.dataPrevistaDevolucao)}${e.status === 'ATRASADO' ? ` (${e.diasAtraso}d atraso)` : ''}`;

      const btns = [];
      if (canWrite()) {
        if (e.podeConfirmarRetirada) {
          btns.push(`<button type="button" class="btn btn-outline-accent" data-action="confirmar-retirada" data-id="${e.id}">Confirmar Retirada</button>`);
        }
        if (e.podeCancelar) {
          btns.push(`<button type="button" class="btn btn-outline-danger" data-action="cancelar-emprestimo" data-id="${e.id}">Cancelar</button>`);
        }
        if (e.podeProrrogar) {
          btns.push(`<button type="button" class="btn btn-outline-slate" data-action="prorrogar" data-id="${e.id}">Prorrogar +14d</button>`);
        }
        if (e.podeDevolver) {
          btns.push(`<button type="button" class="btn btn-outline-accent" data-action="devolver" data-id="${e.id}">Devolver</button>`);
        }
      }
      if (e.status === 'DEVOLVIDO') {
        btns.push(`<button type="button" class="btn btn-outline-slate" data-action="recibo" data-id="${e.id}">Recibo</button>`);
      }
      if (!btns.length) {
        btns.push('<span class="readonly-pill">Somente leitura</span>');
      }

      return `
        <tr>
          <td>
            <div class="book-cell">
              <div class="book-thumb" style="background:${escapeHtml(e.livroCorCapa)}">${escapeHtml(miniCoverText(e.livroTitulo))}</div>
              <div>
                <div class="cell-primary">${escapeHtml(e.livroTitulo)}</div>
                <div class="cell-secondary">${escapeHtml(e.codigo)} • ${escapeHtml(e.livroAutor)}</div>
              </div>
            </div>
          </td>
          <td>
            <div class="cell-primary">${escapeHtml(e.leitorNome)}</div>
            <div class="cell-secondary">${escapeHtml(e.leitorPerfil)} • ${escapeHtml(e.leitorCpf)}</div>
          </td>
          <td>
            <div class="cell-primary">${formatDateBR(e.dataEmprestimo)}</div>
            <div class="${e.status === 'ATRASADO' ? 'cell-secondary alert-text' : 'cell-secondary'}">${dateDetails}</div>
          </td>
          <td>${prorrogaHtml}</td>
          <td>${statusBadgeHtml(e.status, e.statusLabel)}</td>
          <td class="align-right"><div class="actions-group">${btns.join('')}</div></td>
        </tr>
      `;
    }).join('');
  }

  // ==================== ABA 3: LIVROS & ACERVO (UC06–UC09, UC15) ====================

  async function loadLivros() {
    const params = new URLSearchParams({
      status: state.bookFilter,
      categoria: state.bookCategory,
      q: state.bookSearch,
    });
    const list = await api(`/api/livros?${params.toString()}`);
    state.cache.livros = list;

    // Popular filtro de categorias com base na lista completa quando sem filtro
    if (state.bookFilter === 'todos' && state.bookCategory === 'todas' && !state.bookSearch) {
      const cats = [...new Set(list.map((l) => l.categoria).filter(Boolean))].sort();
      const sel = $('#book-category-filter');
      const current = sel.value;
      sel.innerHTML = '<option value="todas">Todas as categorias</option>'
        + cats.map((c) => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
      sel.value = cats.includes(current) ? current : 'todas';
    }

    const totalEx = list.reduce((acc, b) => acc + b.quantidadeTotal, 0);
    const totalDisp = list.reduce((acc, b) => acc + b.quantidadeDisponivel, 0);
    const totalEmpr = list.reduce((acc, b) => acc + b.quantidadeEmprestada, 0);
    const totalDanif = list.reduce((acc, b) => acc + b.quantidadeDanificada, 0);

    $('#livros-summary-strip').innerHTML = `
      <div class="mini-kpi"><span>Títulos listados</span><strong>${list.length}</strong></div>
      <div class="mini-kpi"><span>Exemplares disponíveis</span><strong>${totalDisp} / ${totalEx}</strong></div>
      <div class="mini-kpi"><span>Emprestados agora</span><strong>${totalEmpr}</strong></div>
      <div class="mini-kpi"><span>Danificados / Manutenção</span><strong>${totalDanif}</strong></div>
    `;

    const tbody = $('#books-tbody');
    if (!list.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="empty-state">Nenhum livro encontrado no acervo.</td></tr>';
      return;
    }

    tbody.innerHTML = list.map((b) => `
      <tr>
        <td>
          <div class="book-cell">
            <div class="book-thumb" style="background:${escapeHtml(b.corCapa)}">${escapeHtml(miniCoverText(b.titulo))}</div>
            <div>
              <div class="cell-primary">${escapeHtml(b.titulo)}</div>
              <div class="cell-secondary">${escapeHtml(b.autor)} • ISBN ${escapeHtml(b.isbn)}</div>
            </div>
          </div>
        </td>
        <td>
          <div class="cell-primary">${escapeHtml(b.editora)}</div>
          <div class="cell-secondary">Ano ${escapeHtml(b.anoPublicacao)}</div>
        </td>
        <td><span class="stock-chip">${escapeHtml(b.categoria)}</span></td>
        <td>
          <div class="stock-grid">
            <span class="stock-chip">Total: ${b.quantidadeTotal}</span>
            <span class="stock-chip disp">Disp: ${b.quantidadeDisponivel}</span>
            <span class="stock-chip empr">Empr: ${b.quantidadeEmprestada}</span>
            ${b.quantidadeDanificada > 0 ? `<span class="stock-chip danif">Danif: ${b.quantidadeDanificada}</span>` : ''}
          </div>
        </td>
        <td>
          ${b.ativo
            ? '<span class="status-badge status-em-dia">Ativo</span>'
            : '<span class="status-badge status-cancelado">Desativado</span>'}
        </td>
        <td class="align-right">
          <div class="actions-group">
            ${canWrite()
              ? `<button type="button" class="btn btn-outline-slate" data-action="edit-book" data-id="${b.id}">Editar</button>
                 <button type="button" class="btn btn-outline-accent" data-action="cond-book" data-id="${b.id}">Condição</button>
                 ${b.ativo
                   ? `<button type="button" class="btn btn-outline-danger" data-action="deactivate-book" data-id="${b.id}">Desativar</button>`
                   : `<button type="button" class="btn btn-outline-slate" data-action="reactivate-book" data-id="${b.id}">Reativar</button>`}`
              : '<span class="readonly-pill">Somente leitura</span>'}
          </div>
        </td>
      </tr>
    `).join('');
  }

  // ==================== ABA 4: USUÁRIOS / LEITORES (UC02–UC05) ====================

  async function loadLeitores() {
    const params = new URLSearchParams({ status: state.readerFilter, q: state.readerSearch });
    const list = await api(`/api/leitores?${params.toString()}`);
    state.cache.leitores = list;

    const ativos = list.filter((r) => r.ativo).length;
    const comEmprestimo = list.filter((r) => r.emprestimosAtivos > 0).length;
    const comPendencia = list.filter((r) => r.possuiPendencia).length;
    const desativados = list.filter((r) => !r.ativo).length;

    $('#readers-summary-strip').innerHTML = `
      <div class="mini-kpi"><span>Leitores ativos</span><strong>${ativos}</strong></div>
      <div class="mini-kpi"><span>Com livros emprestados</span><strong>${comEmprestimo}</strong></div>
      <div class="mini-kpi"><span>Com pendência / atraso</span><strong>${comPendencia}</strong></div>
      <div class="mini-kpi"><span>Cadastros desativados</span><strong>${desativados}</strong></div>
    `;

    const tbody = $('#readers-tbody');
    if (!list.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty-state">Nenhum leitor encontrado.</td></tr>';
      return;
    }

    tbody.innerHTML = list.map((r) => {
      let badge = '<span class="status-badge status-em-dia">Regular</span>';
      if (!r.ativo) {
        badge = '<span class="status-badge status-cancelado">Desativado</span>';
      } else if (r.possuiPendencia) {
        badge = '<span class="status-badge status-atrasado">Pendência (Atraso)</span>';
      } else if (r.emprestimosAtivos >= r.limiteEmprestimos) {
        badge = '<span class="status-badge status-aguardando">Limite 3/3 atingido</span>';
      }

      return `
        <tr>
          <td>
            <div class="cell-primary">${escapeHtml(r.nome)}</div>
            <div class="cell-secondary">${escapeHtml(r.perfil)} • ${escapeHtml(r.endereco)}</div>
          </td>
          <td>
            <div class="cell-primary">${escapeHtml(r.cpf)}</div>
            <div class="cell-secondary">${escapeHtml(r.telefone)} • ${escapeHtml(r.email)}</div>
          </td>
          <td>
            <span class="stock-chip ${r.emprestimosAtivos >= 3 ? 'danif' : 'empr'}">${r.emprestimosAtivos} / ${r.limiteEmprestimos} ativos</span>
            <div class="cell-secondary">${r.totalHistorico} no histórico</div>
          </td>
          <td>${badge}</td>
          <td class="align-right">
            <div class="actions-group">
              <button type="button" class="btn btn-outline-slate" data-action="history-reader" data-id="${r.id}">Histórico</button>
              ${canWrite()
                ? `<button type="button" class="btn btn-outline-accent" data-action="edit-reader" data-id="${r.id}">Editar</button>
                   ${r.ativo
                     ? `<button type="button" class="btn btn-outline-danger" data-action="deactivate-reader" data-id="${r.id}">Desativar</button>`
                     : `<button type="button" class="btn btn-outline-slate" data-action="reactivate-reader" data-id="${r.id}">Reativar</button>`}`
                : ''}
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  // ==================== ABA 5: CONFIGURAÇÕES (UC16 + UC15) ====================

  async function loadConfiguracoes() {
    syncUserUI();
    const isAdmin = state.user && state.user.cargo === 'ADMINISTRADOR';

    if (state.activeConfigSub === 'funcionarios' && isAdmin) {
      const staff = await api('/api/funcionarios');
      state.cache.funcionarios = staff;
      const tbody = $('#staff-tbody');
      tbody.innerHTML = staff.map((f) => `
        <tr>
          <td>
            <div class="cell-primary">${escapeHtml(f.nome)}</div>
            <div class="cell-secondary">Desde ${formatDateBR(f.criadoEm)}</div>
          </td>
          <td><span class="stock-chip">${escapeHtml(f.matricula)}</span></td>
          <td>${escapeHtml(f.email)}</td>
          <td>
            <span class="stock-chip ${f.cargo === 'ADMINISTRADOR' ? 'empr' : ''}">${escapeHtml(f.cargoDescricao)}</span>
          </td>
          <td>
            ${f.ativo
              ? '<span class="status-badge status-em-dia">Ativo</span>'
              : '<span class="status-badge status-cancelado">Desativado</span>'}
          </td>
          <td class="align-right">
            <div class="actions-group">
              <button type="button" class="btn btn-outline-slate" data-action="edit-staff" data-id="${f.id}">Editar</button>
              ${f.ativo
                ? `<button type="button" class="btn btn-outline-danger" data-action="deactivate-staff" data-id="${f.id}">Desativar</button>`
                : `<button type="button" class="btn btn-outline-accent" data-action="reactivate-staff" data-id="${f.id}">Reativar</button>`}
            </div>
          </td>
        </tr>
      `).join('');
    }

    if (state.activeConfigSub === 'supervisao') {
      const logs = await api('/api/condicoes');
      state.cache.condicoes = logs;
      const tbody = $('#condition-log-tbody');
      if (!logs.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="empty-state">Nenhuma ocorrência de condição registrada.</td></tr>';
        return;
      }
      const actionLabels = {
        DEVOLUCAO_DANIFICADA: 'Devolução Danificada',
        MARCAR_DANIFICADO: 'Separado p/ Manutenção',
        REPARAR_DISPONIBILIZAR: 'Reparado e Disponibilizado',
      };
      tbody.innerHTML = logs.map((r) => `
        <tr>
          <td>${escapeHtml(r.dataHora)}</td>
          <td>
            <div class="cell-primary">${escapeHtml(r.livroTitulo)}</div>
            <div class="cell-secondary">${escapeHtml(r.livroAutor)} • Qtd: ${r.quantidade}</div>
          </td>
          <td>
            <span class="stock-chip ${r.acao === 'REPARAR_DISPONIBILIZAR' ? 'disp' : 'danif'}">${escapeHtml(actionLabels[r.acao] || r.acao)}</span>
          </td>
          <td>
            <div class="cell-primary">${escapeHtml(r.observacao)}</div>
            <div class="cell-secondary">Registrado por ${escapeHtml(r.responsavel)}</div>
          </td>
          <td>
            ${r.supervisionado
              ? `<span class="status-badge status-em-dia">Aprovado</span><div class="cell-secondary">${escapeHtml(r.supervisorNome)}</div>`
              : '<span class="status-badge status-aguardando">Pendente</span>'}
          </td>
          <td class="align-right">
            ${!r.supervisionado && isAdmin
              ? `<button type="button" class="btn btn-outline-accent" data-action="supervise-cond" data-id="${r.id}">Supervisionar</button>`
              : '<span class="cell-secondary">—</span>'}
          </td>
        </tr>
      `).join('');
    }
  }

  // ==================== MODAIS E OPERAÇÕES ====================

  async function openNewLoanModal() {
    try {
      const [leitores, livros] = await Promise.all([
        api('/api/leitores?status=ativos'),
        api('/api/livros?status=ativos'),
      ]);
      state.cache.leitores = leitores;

      const leitorSel = $('#loan-leitor-select');
      leitorSel.innerHTML = leitores.map((r) => {
        const tag = r.possuiPendencia
          ? ' [BLOQUEADO: ATRASO]'
          : (r.emprestimosAtivos >= 3 ? ' [LIMITE 3/3]' : ` (${r.emprestimosAtivos}/3)`);
        return `<option value="${r.id}" data-elegivel="${r.elegivelEmprestimo}" data-pendencia="${r.possuiPendencia}" data-ativos="${r.emprestimosAtivos}">${escapeHtml(r.nome)} — ${escapeHtml(r.cpf)}${tag}</option>`;
      }).join('');

      const livroSel = $('#loan-livro-select');
      livroSel.innerHTML = livros.map((b) => `
        <option value="${b.id}" ${b.quantidadeDisponivel <= 0 ? 'disabled' : ''}>
          ${escapeHtml(b.titulo)} (${escapeHtml(b.autor)}) — ${b.quantidadeDisponivel} disponível(is)
        </option>
      `).join('');

      updateLoanReaderHint();
      $('#modal-new-loan').showModal();
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  function updateLoanReaderHint() {
    const sel = $('#loan-leitor-select');
    const opt = sel.options[sel.selectedIndex];
    const hint = $('#loan-leitor-hint');
    if (!opt) {
      hint.textContent = '';
      return;
    }
    if (opt.dataset.pendencia === 'true') {
      hint.textContent = 'Atenção: este leitor possui empréstimo atrasado e está impedido de retirar novos livros.';
      hint.className = 'field-hint danger';
    } else if (Number(opt.dataset.ativos) >= 3) {
      hint.textContent = 'Atenção: este leitor atingiu o limite máximo de 3 empréstimos simultâneos.';
      hint.className = 'field-hint danger';
    } else {
      hint.textContent = `Leitor elegível (${opt.dataset.ativos}/3 empréstimos ativos).`;
      hint.className = 'field-hint';
    }
  }

  function findLoanInCache(id) {
    const numId = Number(id);
    const inDash = (state.cache.dashboard?.emprestimosRecentes || []).find((e) => e.id === numId);
    if (inDash) return inDash;
    return state.cache.emprestimos.find((e) => e.id === numId);
  }

  function openReturnLoanModal(id) {
    const loan = findLoanInCache(id);
    if (!loan) return;
    $('#return-loan-id').value = loan.id;
    $('#return-observacao').value = '';
    document.querySelector('input[name="condicaoDevolucao"][value="BOAS_CONDICOES"]').checked = true;
    $('#return-loan-summary').innerHTML = `
      <strong>${escapeHtml(loan.codigo)} — ${escapeHtml(loan.livroTitulo)}</strong><br>
      Leitor: ${escapeHtml(loan.leitorNome)} (${escapeHtml(loan.leitorCpf)})<br>
      Empréstimo: ${formatDateBR(loan.dataEmprestimo)} • Previsto: ${formatDateBR(loan.dataPrevistaDevolucao)}
      ${loan.status === 'ATRASADO' ? `<br><strong style="color:#B84239">Empréstimo em atraso (${loan.diasAtraso} dia(s)).</strong>` : ''}
    `;
    $('#modal-return-loan').showModal();
  }

  function openReceiptModal(id) {
    const loan = findLoanInCache(id);
    if (!loan) return;
    $('#receipt-content').innerHTML = `
      <div class="receipt-sheet">
        <h4>Comprovante de Movimentação • ${escapeHtml(loan.codigo)}</h4>
        <div class="info-kv-list">
          <div><span>Livro</span><strong>${escapeHtml(loan.livroTitulo)} (${escapeHtml(loan.livroAutor)})</strong></div>
          <div><span>ISBN</span><strong>${escapeHtml(loan.livroIsbn)}</strong></div>
          <div><span>Leitor</span><strong>${escapeHtml(loan.leitorNome)} (${escapeHtml(loan.leitorCpf)})</strong></div>
          <div><span>Data do Empréstimo</span><strong>${formatDateBR(loan.dataEmprestimo)}</strong></div>
          <div><span>Data Prevista</span><strong>${formatDateBR(loan.dataPrevistaDevolucao)}</strong></div>
          <div><span>Data de Devolução</span><strong>${formatDateBR(loan.dataEfetivaDevolucao)}</strong></div>
          <div><span>Condição Avaliada</span><strong>${escapeHtml(loan.condicaoDevolucaoLabel || '—')}</strong></div>
          <div><span>Observação</span><strong>${escapeHtml(loan.observacaoCondicao || '—')}</strong></div>
          <div><span>Atendente</span><strong>${escapeHtml(loan.funcionarioResponsavel || '—')}</strong></div>
        </div>
      </div>
    `;
    $('#modal-receipt').showModal();
  }

  function openBookModal(book = null) {
    $('#modal-book-title').textContent = book ? 'Editar Livro (UC07)' : 'Cadastrar Livro (UC06)';
    $('#book-id').value = book ? book.id : '';
    $('#book-titulo').value = book ? book.titulo : '';
    $('#book-autor').value = book ? book.autor : '';
    $('#book-editora').value = book ? book.editora : '';
    $('#book-isbn').value = book ? book.isbn : '';
    $('#book-ano').value = book ? book.anoPublicacao : new Date().getFullYear();
    $('#book-categoria').value = book ? book.categoria : '';
    $('#book-qtd').value = book ? book.quantidadeTotal : 5;
    const color = book ? book.corCapa : '#2C3639';
    $('#book-cor').value = color;
    $$('#book-color-swatches .swatch').forEach((s) => s.classList.toggle('active', s.dataset.color === color));
    $('#modal-book').showModal();
  }

  function openConditionModal(book) {
    $('#cond-book-id').value = book.id;
    $('#cond-qtd').value = 1;
    $('#cond-obs').value = '';
    $('#cond-acao').value = book.quantidadeDanificada > 0 ? 'REPARAR_DISPONIBILIZAR' : 'MARCAR_DANIFICADO';
    $('#cond-book-summary').innerHTML = `
      <strong>${escapeHtml(book.titulo)}</strong> — ${escapeHtml(book.autor)}<br>
      Total: <strong>${book.quantidadeTotal}</strong> •
      Disponíveis: <strong>${book.quantidadeDisponivel}</strong> •
      Emprestados: <strong>${book.quantidadeEmprestada}</strong> •
      Danificados: <strong>${book.quantidadeDanificada}</strong>
    `;
    $('#modal-condition').showModal();
  }

  function openReaderModal(reader = null) {
    $('#modal-reader-title').textContent = reader ? 'Editar Dados do Leitor (UC03)' : 'Cadastrar Leitor (UC02)';
    $('#reader-id').value = reader ? reader.id : '';
    $('#reader-nome').value = reader ? reader.nome : '';
    $('#reader-cpf').value = reader ? reader.cpf : '';
    $('#reader-perfil').value = reader ? reader.perfil : 'Aluno';
    $('#reader-telefone').value = reader ? reader.telefone : '';
    $('#reader-email').value = reader ? reader.email : '';
    $('#reader-endereco').value = reader ? reader.endereco : '';
    $('#modal-reader').showModal();
  }

  async function openReaderHistoryModal(reader) {
    try {
      const loans = await api(`/api/emprestimos?leitorId=${reader.id}`);
      $('#history-reader-name').textContent = `Histórico — ${reader.nome}`;
      $('#history-reader-meta').innerHTML = `
        CPF: <strong>${escapeHtml(reader.cpf)}</strong> • Perfil: <strong>${escapeHtml(reader.perfil)}</strong> •
        Empréstimos ativos: <strong>${reader.emprestimosAtivos}/${reader.limiteEmprestimos}</strong>
      `;
      const tbody = $('#history-tbody');
      if (!loans.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="empty-state">Este leitor ainda não realizou empréstimos.</td></tr>';
      } else {
        tbody.innerHTML = loans.map((e) => `
          <tr>
            <td><div class="cell-primary">${escapeHtml(e.livroTitulo)}</div><div class="cell-secondary">${escapeHtml(e.codigo)}</div></td>
            <td>${formatDateBR(e.dataEmprestimo)}</td>
            <td>${formatDateBR(e.dataPrevistaDevolucao)}${e.dataEfetivaDevolucao ? `<div class="cell-secondary">Devolvido: ${formatDateBR(e.dataEfetivaDevolucao)}</div>` : ''}</td>
            <td>${escapeHtml(e.condicaoDevolucaoLabel || '—')}</td>
            <td>${statusBadgeHtml(e.status, e.statusLabel)}</td>
          </tr>
        `).join('');
      }
      $('#modal-reader-history').showModal();
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  function openStaffModal(staff = null) {
    $('#modal-staff-title').textContent = staff ? 'Editar Funcionário (UC16)' : 'Cadastrar Funcionário (UC16)';
    $('#staff-id').value = staff ? staff.id : '';
    $('#staff-nome').value = staff ? staff.nome : '';
    $('#staff-matricula').value = staff ? staff.matricula : '';
    $('#staff-cargo').value = staff ? staff.cargo : 'FUNCIONARIO';
    $('#staff-email').value = staff ? staff.email : '';
    $('#staff-senha').value = '';
    $('#staff-senha').required = !staff;
    $('#staff-senha-hint').textContent = staff ? '(deixe em branco para manter a atual)' : '(mínimo 6 caracteres)';
    $('#modal-staff').showModal();
  }

  // ==================== BUSCA GLOBAL (CTRL+K) ====================

  let searchDebounce = null;
  async function handleGlobalSearch(q) {
    const box = $('#global-search-results');
    if (!q.trim()) {
      box.classList.add('hidden');
      return;
    }
    try {
      const res = await api(`/api/busca?q=${encodeURIComponent(q)}`);
      const chunks = [];
      if (res.livros.length) {
        chunks.push('<div class="search-group-title">Livros &amp; Acervo</div>');
        res.livros.forEach((b) => {
          chunks.push(`
            <button type="button" class="search-result-item" data-jump-tab="livros" data-jump-q="${escapeHtml(b.titulo)}">
              <div><strong>${escapeHtml(b.titulo)}</strong><small>${escapeHtml(b.autor)} • Disp: ${b.quantidadeDisponivel}/${b.quantidadeTotal}</small></div>
              <span class="stock-chip">Livro</span>
            </button>
          `);
        });
      }
      if (res.leitores.length) {
        chunks.push('<div class="search-group-title">Leitores</div>');
        res.leitores.forEach((r) => {
          chunks.push(`
            <button type="button" class="search-result-item" data-jump-tab="usuarios" data-jump-q="${escapeHtml(r.nome)}">
              <div><strong>${escapeHtml(r.nome)}</strong><small>${escapeHtml(r.cpf)} • ${escapeHtml(r.perfil)}</small></div>
              <span class="stock-chip">Leitor</span>
            </button>
          `);
        });
      }
      if (res.emprestimos.length) {
        chunks.push('<div class="search-group-title">Empréstimos</div>');
        res.emprestimos.forEach((e) => {
          chunks.push(`
            <button type="button" class="search-result-item" data-jump-tab="emprestimos" data-jump-q="${escapeHtml(e.codigo)}">
              <div><strong>${escapeHtml(e.codigo)} — ${escapeHtml(e.livroTitulo)}</strong><small>${escapeHtml(e.leitorNome)}</small></div>
              ${statusBadgeHtml(e.status, e.statusLabel)}
            </button>
          `);
        });
      }
      box.innerHTML = chunks.length
        ? chunks.join('')
        : '<div class="empty-state">Nenhum resultado encontrado para esta busca.</div>';
      box.classList.remove('hidden');
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  // ==================== EVENT LISTENERS ====================

  function bindEvents() {
    // Login (UC01) — acesso rápido apenas para a conta Somente Visualização (sem credenciais no front-end)
    $('#fill-viewer-btn').addEventListener('click', async () => {
      const errBox = $('#login-error');
      errBox.classList.add('hidden');
      try {
        const res = await api('/api/auth/viewer', { method: 'POST' });
        state.token = res.token;
        state.user = res.usuario;
        localStorage.setItem('systemx_token', res.token);
        await enterApp();
        showToast('Acesso em modo Somente Visualização ativado.', 'info');
      } catch (err) {
        errBox.textContent = err.message;
        errBox.classList.remove('hidden');
      }
    });

    $('#login-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const errBox = $('#login-error');
      errBox.classList.add('hidden');
      try {
        const res = await api('/api/auth/login', {
          method: 'POST',
          body: {
            email: $('#login-email').value.trim(),
            senha: $('#login-password').value,
          },
        });
        state.token = res.token;
        state.user = res.usuario;
        localStorage.setItem('systemx_token', res.token);
        await enterApp();
        showToast(`Bem-vindo(a), ${res.usuario.nome}!`, 'success');
      } catch (err) {
        errBox.textContent = err.message;
        errBox.classList.remove('hidden');
      }
    });

    // Logout
    $('#logout-btn').addEventListener('click', async () => {
      try { await api('/api/auth/logout', { method: 'POST' }); } catch {}
      $('#profile-popover').classList.add('hidden');
      handleForceLogout();
    });

    $('#go-config-profile-btn').addEventListener('click', () => {
      $('#profile-popover').classList.add('hidden');
      switchTab('configuracoes');
    });

    // Sidebar Navigation
    $$('.nav-item').forEach((btn) => {
      btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });
    $('#mobile-menu-btn').addEventListener('click', () => {
      $('.sidebar').classList.toggle('open');
    });

    // RBAC Switcher na sidebar
    $('#rbac-select').addEventListener('change', async (e) => {
      try {
        const res = await api('/api/auth/switch-role', {
          method: 'POST',
          body: { cargo: e.target.value },
        });
        state.user = res.usuario;
        syncUserUI();
        await refreshActiveTab();
        showToast(`Perfil ativo alterado para ${res.usuario.cargoDescricao} (${res.usuario.nome}).`, 'success');
      } catch (err) {
        showToast(err.message, 'error');
        syncUserUI();
      }
    });

    // Simular Erro de Rede
    const toggleSimError = (forced) => {
      state.simulateNetworkError = typeof forced === 'boolean' ? forced : !state.simulateNetworkError;
      $('#simulate-error-btn').classList.toggle('active', state.simulateNetworkError);
      $('#network-error-banner').classList.toggle('hidden', !state.simulateNetworkError);
      if (state.simulateNetworkError) {
        showToast('Modo de simulação de erro de rede ativado.', 'error');
      } else {
        showToast('Conexão com o servidor Java restaurada.', 'success');
        refreshActiveTab();
      }
    };
    $('#simulate-error-btn').addEventListener('click', () => toggleSimError());
    $('#disable-error-btn').addEventListener('click', () => toggleSimError(false));

    // Popovers (Notificações e Perfil)
    $('#notif-btn').addEventListener('click', (e) => {
      e.stopPropagation();
      $('#profile-popover').classList.add('hidden');
      $('#notif-popover').classList.toggle('hidden');
    });
    $('#profile-menu-btn').addEventListener('click', (e) => {
      e.stopPropagation();
      $('#notif-popover').classList.add('hidden');
      $('#profile-popover').classList.toggle('hidden');
    });
    document.addEventListener('click', (e) => {
      if (!e.target.closest('.popover-anchor')) {
        $('#notif-popover').classList.add('hidden');
        $('#profile-popover').classList.add('hidden');
      }
      if (!e.target.closest('.global-search-wrap')) {
        $('#global-search-results').classList.add('hidden');
      }
    });

    // Busca global
    $('#global-search-input').addEventListener('input', (e) => {
      clearTimeout(searchDebounce);
      const val = e.target.value;
      searchDebounce = setTimeout(() => handleGlobalSearch(val), 180);
    });
    $('#global-search-results').addEventListener('click', (e) => {
      const btn = e.target.closest('[data-jump-tab]');
      if (!btn) return;
      const tab = btn.dataset.jumpTab;
      const q = btn.dataset.jumpQ || '';
      $('#global-search-results').classList.add('hidden');
      $('#global-search-input').value = '';
      if (tab === 'livros') { state.bookSearch = q; $('#book-search-input').value = q; }
      if (tab === 'usuarios') { state.readerSearch = q; $('#reader-search-input').value = q; }
      if (tab === 'emprestimos') { state.empSearch = q; $('#emp-search-input').value = q; }
      switchTab(tab);
    });

    // Atalhos globais: Ctrl+K e Alt+N
    document.addEventListener('keydown', (e) => {
      if (!state.user) return;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        $('#global-search-input').focus();
      } else if (e.altKey && e.key.toLowerCase() === 'n') {
        e.preventDefault();
        if (!canWrite()) {
          showToast('Conta em modo Somente Visualização: não é possível registrar empréstimos.', 'error');
          return;
        }
        openNewLoanModal();
      }
    });

    // KPIs clicáveis no Dashboard
    $$('[data-kpi-nav]').forEach((card) => {
      card.addEventListener('click', () => {
        const target = card.dataset.kpiNav;
        if (target === 'atrasados') {
          state.empFilter = 'atrasados';
          $$('[data-emp-filter]').forEach((b) => b.classList.toggle('active', b.dataset.empFilter === 'atrasados'));
          switchTab('emprestimos');
        } else {
          switchTab(target);
        }
      });
    });

    // Filtros do Dashboard
    $$('[data-dash-filter]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.dashFilter = btn.dataset.dashFilter;
        $$('[data-dash-filter]').forEach((b) => b.classList.toggle('active', b === btn));
        loadDashboard().catch((err) => showToast(err.message, 'error'));
      });
    });
    $('#dash-see-all-btn').addEventListener('click', () => switchTab('emprestimos'));

    // Filtros da aba Empréstimos
    $$('[data-emp-filter]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.empFilter = btn.dataset.empFilter;
        $$('[data-emp-filter]').forEach((b) => b.classList.toggle('active', b === btn));
        loadEmprestimos().catch((err) => showToast(err.message, 'error'));
      });
    });
    $('#emp-search-input').addEventListener('input', (e) => {
      state.empSearch = e.target.value;
      loadEmprestimos().catch((err) => showToast(err.message, 'error'));
    });

    // Filtros da aba Livros
    $$('[data-book-filter]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.bookFilter = btn.dataset.bookFilter;
        $$('[data-book-filter]').forEach((b) => b.classList.toggle('active', b === btn));
        loadLivros().catch((err) => showToast(err.message, 'error'));
      });
    });
    $('#book-category-filter').addEventListener('change', (e) => {
      state.bookCategory = e.target.value;
      loadLivros().catch((err) => showToast(err.message, 'error'));
    });
    $('#book-search-input').addEventListener('input', (e) => {
      state.bookSearch = e.target.value;
      loadLivros().catch((err) => showToast(err.message, 'error'));
    });

    // Filtros da aba Usuários
    $$('[data-reader-filter]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.readerFilter = btn.dataset.readerFilter;
        $$('[data-reader-filter]').forEach((b) => b.classList.toggle('active', b === btn));
        loadLeitores().catch((err) => showToast(err.message, 'error'));
      });
    });
    $('#reader-search-input').addEventListener('input', (e) => {
      state.readerSearch = e.target.value;
      loadLeitores().catch((err) => showToast(err.message, 'error'));
    });

    // Sub-abas Configurações
    $$('[data-config-sub]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.activeConfigSub = btn.dataset.configSub;
        $$('[data-config-sub]').forEach((b) => b.classList.toggle('active', b === btn));
        $$('.config-subpanel').forEach((p) => p.classList.toggle('active', p.id === `config-panel-${state.activeConfigSub}`));
        loadConfiguracoes().catch((err) => showToast(err.message, 'error'));
      });
    });

    // Botões de criação
    $('#new-loan-btn').addEventListener('click', openNewLoanModal);
    $('#emp-new-btn').addEventListener('click', openNewLoanModal);
    $('#book-new-btn').addEventListener('click', () => openBookModal(null));
    $('#reader-new-btn').addEventListener('click', () => openReaderModal(null));
    $('#staff-new-btn').addEventListener('click', () => openStaffModal(null));

    // Fechar modais
    $$('[data-close-dialog]').forEach((btn) => {
      btn.addEventListener('click', () => btn.closest('dialog').close());
    });
    $('#loan-leitor-select').addEventListener('change', updateLoanReaderHint);
    $('#print-receipt-btn').addEventListener('click', () => window.print());

    // Swatches de cor da capa
    $$('#book-color-swatches .swatch').forEach((sw) => {
      sw.addEventListener('click', () => {
        $$('#book-color-swatches .swatch').forEach((s) => s.classList.remove('active'));
        sw.classList.add('active');
        $('#book-cor').value = sw.dataset.color;
      });
    });

    // Submits de formulários
    $('#form-new-loan').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const retirada = document.querySelector('input[name="retiradaImediata"]:checked').value === 'true';
        const res = await api('/api/emprestimos', {
          method: 'POST',
          body: {
            leitorId: Number($('#loan-leitor-select').value),
            livroId: Number($('#loan-livro-select').value),
            retiradaImediata: retirada,
          },
        });
        $('#modal-new-loan').close();
        showToast(`Empréstimo ${res.codigo} registrado com sucesso!`, 'success');
        await refreshActiveTab();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('#form-return-loan').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const id = $('#return-loan-id').value;
        const condicao = document.querySelector('input[name="condicaoDevolucao"]:checked').value;
        await api(`/api/emprestimos/${id}/devolver`, {
          method: 'POST',
          body: {
            condicao,
            observacao: $('#return-observacao').value.trim(),
          },
        });
        $('#modal-return-loan').close();
        showToast(
          condicao === 'DANIFICADO'
            ? 'Devolução registrada. Exemplar danificado separado para manutenção (UC15).'
            : 'Devolução concluída e exemplar retornado ao estoque disponível!',
          'success'
        );
        await refreshActiveTab();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('#form-book').addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = $('#book-id').value;
      const body = {
        titulo: $('#book-titulo').value.trim(),
        autor: $('#book-autor').value.trim(),
        editora: $('#book-editora').value.trim(),
        isbn: $('#book-isbn').value.trim(),
        anoPublicacao: Number($('#book-ano').value),
        categoria: $('#book-categoria').value.trim(),
        quantidadeTotal: Number($('#book-qtd').value),
        corCapa: $('#book-cor').value,
      };
      try {
        if (id) {
          await api(`/api/livros/${id}`, { method: 'PUT', body });
          showToast('Livro atualizado com sucesso!', 'success');
        } else {
          await api('/api/livros', { method: 'POST', body });
          showToast('Livro cadastrado no acervo!', 'success');
        }
        $('#modal-book').close();
        await refreshActiveTab();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('#form-condition').addEventListener('submit', async (e) => {
      e.preventDefault();
      const bookId = $('#cond-book-id').value;
      try {
        await api(`/api/livros/${bookId}/condicao`, {
          method: 'POST',
          body: {
            acao: $('#cond-acao').value,
            quantidade: Number($('#cond-qtd').value),
            observacao: $('#cond-obs').value.trim(),
          },
        });
        $('#modal-condition').close();
        showToast('Condição de exemplar atualizada e registrada para supervisão.', 'success');
        await refreshActiveTab();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('#form-reader').addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = $('#reader-id').value;
      const body = {
        nome: $('#reader-nome').value.trim(),
        cpf: $('#reader-cpf').value.trim(),
        perfil: $('#reader-perfil').value,
        telefone: $('#reader-telefone').value.trim(),
        email: $('#reader-email').value.trim(),
        endereco: $('#reader-endereco').value.trim(),
      };
      try {
        if (id) {
          await api(`/api/leitores/${id}`, { method: 'PUT', body });
          showToast('Dados do leitor atualizados!', 'success');
        } else {
          await api('/api/leitores', { method: 'POST', body });
          showToast('Leitor cadastrado com sucesso!', 'success');
        }
        $('#modal-reader').close();
        await refreshActiveTab();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('#form-staff').addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = $('#staff-id').value;
      const body = {
        nome: $('#staff-nome').value.trim(),
        matricula: $('#staff-matricula').value.trim(),
        cargo: $('#staff-cargo').value,
        email: $('#staff-email').value.trim(),
        senha: $('#staff-senha').value,
      };
      try {
        if (id) {
          await api(`/api/funcionarios/${id}`, { method: 'PUT', body });
          showToast('Conta de funcionário atualizada!', 'success');
        } else {
          await api('/api/funcionarios', { method: 'POST', body });
          showToast('Funcionário cadastrado com sucesso!', 'success');
        }
        $('#modal-staff').close();
        await refreshActiveTab();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    // Resetar base demo (Admin)
    $('#reset-demo-btn').addEventListener('click', async () => {
      if (!confirm('Restaurar todos os dados iniciais de demonstração do SystemX?')) return;
      try {
        await api('/api/admin/reset-demo', { method: 'POST' });
        showToast('Base de demonstração restaurada!', 'success');
        await refreshActiveTab();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    // Delegação global de ações em tabelas
    document.addEventListener('click', async (e) => {
      const btn = e.target.closest('[data-action]');
      if (!btn) return;
      const action = btn.dataset.action;
      const id = Number(btn.dataset.id);

      try {
        if (action === 'devolver') {
          openReturnLoanModal(id);
        } else if (action === 'recibo') {
          openReceiptModal(id);
        } else if (action === 'prorrogar') {
          const res = await api(`/api/emprestimos/${id}/prorrogar`, { method: 'POST' });
          showToast(`Empréstimo ${res.codigo} prorrogado até ${formatDateBR(res.dataPrevistaDevolucao)}!`, 'success');
          await refreshActiveTab();
        } else if (action === 'confirmar-retirada') {
          const res = await api(`/api/emprestimos/${id}/confirmar-retirada`, { method: 'POST' });
          showToast(`Retirada do empréstimo ${res.codigo} confirmada!`, 'success');
          await refreshActiveTab();
        } else if (action === 'cancelar-emprestimo') {
          if (!confirm('Confirmar cancelamento deste empréstimo antes da retirada (UC11)?')) return;
          const res = await api(`/api/emprestimos/${id}/cancelar`, { method: 'POST' });
          showToast(`Empréstimo ${res.codigo} cancelado e exemplar liberado.`, 'success');
          await refreshActiveTab();
        } else if (action === 'edit-book') {
          const book = state.cache.livros.find((b) => b.id === id);
          if (book) openBookModal(book);
        } else if (action === 'cond-book') {
          const book = state.cache.livros.find((b) => b.id === id);
          if (book) openConditionModal(book);
        } else if (action === 'deactivate-book') {
          await api(`/api/livros/${id}/desativar`, { method: 'POST' });
          showToast('Livro desativado no acervo (histórico preservado).', 'success');
          await refreshActiveTab();
        } else if (action === 'reactivate-book') {
          await api(`/api/livros/${id}/reativar`, { method: 'POST' });
          showToast('Livro reativado no acervo.', 'success');
          await refreshActiveTab();
        } else if (action === 'edit-reader') {
          const reader = state.cache.leitores.find((r) => r.id === id);
          if (reader) openReaderModal(reader);
        } else if (action === 'history-reader') {
          const reader = state.cache.leitores.find((r) => r.id === id);
          if (reader) await openReaderHistoryModal(reader);
        } else if (action === 'deactivate-reader') {
          await api(`/api/leitores/${id}/desativar`, { method: 'POST' });
          showToast('Leitor desativado (histórico de empréstimos preservado).', 'success');
          await refreshActiveTab();
        } else if (action === 'reactivate-reader') {
          await api(`/api/leitores/${id}/reativar`, { method: 'POST' });
          showToast('Leitor reativado com sucesso.', 'success');
          await refreshActiveTab();
        } else if (action === 'edit-staff') {
          const st = state.cache.funcionarios.find((f) => f.id === id);
          if (st) openStaffModal(st);
        } else if (action === 'deactivate-staff') {
          await api(`/api/funcionarios/${id}/desativar`, { method: 'POST' });
          showToast('Conta de funcionário desativada.', 'success');
          await refreshActiveTab();
        } else if (action === 'reactivate-staff') {
          await api(`/api/funcionarios/${id}/reativar`, { method: 'POST' });
          showToast('Conta de funcionário reativada.', 'success');
          await refreshActiveTab();
        } else if (action === 'supervise-cond') {
          await api(`/api/condicoes/${id}/supervisionar`, { method: 'POST' });
          showToast('Ocorrência supervisionada e aprovada pelo Administrador.', 'success');
          await refreshActiveTab();
        }
      } catch (err) {
        showToast(err.message, 'error');
      }
    });
  }

  bindEvents();
  tryRestoreSession();
})();
