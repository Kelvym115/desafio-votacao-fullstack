import { useCallback, useEffect, useRef, useState, type MouseEvent } from 'react';
import { api } from './api';
import { AgendaDetail } from './AgendaDetail';
import { CreateAgenda } from './CreateAgenda';
import { agendaState, formatDate } from './time';
import type { Agenda, Page } from './types';
import { ErrorNotice, Icon, StatusBadge } from './ui';

export default function App() {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Agenda> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  const [now, setNow] = useState(Date.now);
  const createTrigger = useRef<HTMLButtonElement | null>(null);
  const mainCreateButton = useRef<HTMLButtonElement | null>(null);
  const refreshList = useCallback(() => setRevision((value) => value + 1), []);

  useEffect(() => {
    if (!createOpen && createTrigger.current) {
      (createTrigger.current.isConnected
        ? createTrigger.current
        : mainCreateButton.current
      )?.focus();
    }
  }, [createOpen]);

  function openCreate(event: MouseEvent<HTMLButtonElement>) {
    createTrigger.current = event.currentTarget;
    setCreateOpen(true);
    setNotice('');
  }

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(search.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    setLoading(true);
    async function load() {
      try {
        const response = await api.list(page, query, controller.signal);
        if (!active) return;
        setData(response);
        setError(null);
        setSelected((current) =>
          current !== null && response.content.some((item) => item.id === current)
            ? current
            : (response.content[0]?.id ?? null),
        );
      } catch (failure) {
        if (active) setError(failure);
      } finally {
        if (active) {
          setLoading(false);
          timer = setTimeout(load, 10000);
        }
      }
    }
    void load();
    return () => {
      active = false;
      controller.abort();
      clearTimeout(timer);
    };
  }, [page, query, revision]);

  function created(agenda: Agenda) {
    setCreateOpen(false);
    setNotice(`Pauta “${agenda.titulo}” criada. Abra a sessão para começar a votação.`);
    setSearch('');
    setQuery('');
    setPage(0);
    setSelected(agenda.id);
    refreshList();
  }

  return (
    <>
      <a className="skip-link" href="#main">
        Ir para as pautas
      </a>
      <header className="site-header">
        <div className="header-inner">
          <a className="brand" href="#" aria-label="Pauta, início">
            <img src="/favicon.svg" alt="" width={34} height={34} />
            <span>
              pauta<span className="brand-period">.</span>
            </span>
          </a>
          <div className="header-divider" />
          <span className="header-subtitle">Decisões em conjunto</span>
          <span className="header-label">
            <Icon name="users" size={16} /> Assembleia da cooperativa
          </span>
        </div>
      </header>
      <main id="main" className="page-shell">
        <section className="page-intro">
          <div>
            <p className="eyebrow">
              <span className="tiny-line" /> ESPAÇO DE PARTICIPAÇÃO
            </p>
            <h1>
              Cada voz faz a diferença<span>.</span>
            </h1>
            <p>
              Organize as pautas, participe das votações e acompanhe
              <br className="desktop-break" /> as decisões da sua cooperativa.
            </p>
          </div>
          <button
            ref={mainCreateButton}
            className="button button-primary new-agenda"
            onClick={openCreate}
          >
            <Icon name="plus" size={19} />
            Nova pauta
          </button>
        </section>
        <div className="assembly-strip">
          <span>
            <Icon name="ballot" size={19} />
            <strong>Assembleia</strong>
            <span className="strip-separator">/</span>Pautas e votações
          </span>
          <span className="strip-note">
            <span className="live-dot" /> Decisões transparentes, participação de todos.
          </span>
        </div>
        {notice && (
          <div className="notice notice-success" role="status">
            <Icon name="check" size={18} />
            <p>{notice}</p>
            <button className="icon-button" aria-label="Fechar aviso" onClick={() => setNotice('')}>
              <Icon name="close" size={16} />
            </button>
          </div>
        )}
        <div className="workspace-grid">
          <aside className="agenda-panel" aria-label="Lista de pautas">
            <div className="list-heading">
              <h2>
                Pautas <span className="count-pill">{data?.totalElements ?? '—'}</span>
              </h2>
              <span>{loading ? 'Atualizando…' : 'Mais recentes'}</span>
            </div>
            <div className="search-box">
              <Icon name="search" size={18} />
              <label className="sr-only" htmlFor="search">
                Buscar pautas
              </label>
              <input
                id="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Buscar por título…"
                type="search"
                autoComplete="off"
              />
            </div>
            <ErrorNotice error={error} retry={refreshList} />
            {loading && !data && !error ? (
              <div aria-label="Carregando pautas" className="list-skeleton">
                {[0, 1, 2].map((value) => (
                  <div className="skeleton skeleton-list" key={value} />
                ))}
              </div>
            ) : data?.content.length ? (
              <>
                <ul className="agenda-list">
                  {data.content.map((agenda) => (
                    <li key={agenda.id}>
                      <button
                        className={`agenda-list-item ${selected === agenda.id ? 'active' : ''}`}
                        aria-pressed={selected === agenda.id}
                        onClick={() => {
                          setSelected(agenda.id);
                          setNotice('');
                        }}
                      >
                        <div className="list-item-top">
                          <span className="agenda-number">
                            #{String(agenda.id).padStart(3, '0')}
                          </span>
                          <StatusBadge status={agendaState(agenda)} />
                        </div>
                        <h3>{agenda.titulo}</h3>
                        <div className="list-item-bottom">
                          <span>{formatDate(agenda.criadaEm)}</span>
                          <Icon name="arrow" size={17} />
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
                {data.totalPages > 1 && (
                  <nav className="pagination" aria-label="Paginação de pautas">
                    <button
                      className="button button-secondary"
                      disabled={page === 0 || loading}
                      onClick={() => setPage((value) => value - 1)}
                      aria-label="Página anterior"
                    >
                      ←
                    </button>
                    <span>
                      {page + 1} de {data.totalPages}
                    </span>
                    <button
                      className="button button-secondary"
                      disabled={page >= data.totalPages - 1 || loading}
                      onClick={() => setPage((value) => value + 1)}
                      aria-label="Próxima página"
                    >
                      →
                    </button>
                  </nav>
                )}
              </>
            ) : !loading && !error ? (
              <div className="list-empty">
                <Icon name="search" size={25} />
                <h3>{query ? 'Nenhuma pauta encontrada' : 'Sua primeira decisão começa aqui'}</h3>
                <p>
                  {query
                    ? 'Tente buscar por outro título.'
                    : 'Crie uma pauta para iniciar a assembleia.'}
                </p>
                {query ? (
                  <button className="text-button" onClick={() => setSearch('')}>
                    Limpar busca
                  </button>
                ) : (
                  <button className="text-button" onClick={openCreate}>
                    Criar primeira pauta <Icon name="plus" size={15} />
                  </button>
                )}
              </div>
            ) : null}
            <div className="participation-note">
              <div className="icon-tile">
                <Icon name="users" size={20} />
              </div>
              <div>
                <strong>Uma decisão coletiva.</strong>
                <p>Cada associado tem direito a um voto por pauta. Toda participação conta.</p>
              </div>
            </div>
          </aside>
          {selected !== null ? (
            <AgendaDetail key={selected} id={selected} now={now} onChanged={refreshList} />
          ) : (
            <section className="card welcome-card">
              <div className="welcome-illustration" aria-hidden="true">
                <div className="paper-ballot">
                  <Icon name="check" size={38} />
                </div>
                <div className="ballot-box">
                  <div />
                  <span>pauta.</span>
                </div>
                <i className="decor-dot one" />
                <i className="decor-dot two" />
                <i className="decor-line" />
              </div>
              <p className="eyebrow">BOAS DECISÕES COMEÇAM COM DIÁLOGO</p>
              <h2>{query ? 'Encontre a próxima conversa.' : 'Vamos decidir juntos?'}</h2>
              <p>
                {query
                  ? 'Use a busca ao lado para encontrar uma pauta e acompanhar a votação.'
                  : 'Escolha uma pauta ao lado ou proponha um novo assunto para a sua cooperativa.'}
              </p>
              {!query && (
                <button className="button button-secondary" onClick={openCreate}>
                  <Icon name="plus" size={18} />
                  Criar uma pauta
                </button>
              )}
            </section>
          )}
        </div>
        <footer className="site-footer">
          <span>
            <strong>pauta.</strong> Mais vozes. Melhores decisões.
          </span>
          <span>Assembleia digital · Votação transparente</span>
        </footer>
      </main>
      {createOpen && <CreateAgenda onClose={() => setCreateOpen(false)} onCreated={created} />}
    </>
  );
}
