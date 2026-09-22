import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { api } from './api';
import { agendaState, formatDate, remainingTime } from './time';
import type { Agenda, Result, VoteChoice } from './types';
import { ErrorNotice, Icon, StatusBadge } from './ui';

const resultLabels: Record<Result['resultado'], string> = {
  AGUARDANDO: 'A decisão começa com o primeiro voto.',
  EM_ANDAMENTO: 'A decisão está em suas mãos.',
  APROVADA: 'Pauta aprovada',
  REJEITADA: 'Pauta rejeitada',
  EMPATE: 'A votação terminou empatada',
  SEM_VOTOS: 'Sessão encerrada sem votos',
};

export function AgendaDetail({
  id,
  now,
  onChanged,
}: {
  id: number;
  now: number;
  onChanged: () => void;
}) {
  const [agenda, setAgenda] = useState<Agenda | null>(null);
  const [result, setResult] = useState<Result | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [revision, setRevision] = useState(0);
  const [actionError, setActionError] = useState<unknown>(null);
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [duration, setDuration] = useState('');
  const [associate, setAssociate] = useState('');
  const [choice, setChoice] = useState<VoteChoice | ''>('');
  const [cpf, setCpf] = useState('');
  const [checkCpf, setCheckCpf] = useState(false);
  const actionAbort = useRef<AbortController | null>(null);
  const boundaryRef = useRef<string | null>(null);
  const refresh = useCallback(() => setRevision((value) => value + 1), []);

  // Each selection mounts its own detail. Polls are serialized and canceled on
  // selection/mutation, so a slow old response cannot replace the latest data.
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function load() {
      try {
        const [nextAgenda, nextResult] = await Promise.all([
          api.agenda(id, controller.signal),
          api.result(id, controller.signal),
        ]);
        if (!active) return;
        setAgenda(nextAgenda);
        setResult(nextResult);
        setError(null);
      } catch (failure) {
        if (active) setError(failure);
      } finally {
        if (active) {
          setLoading(false);
          timer = setTimeout(load, 5000);
        }
      }
    }
    void load();
    return () => {
      active = false;
      controller.abort();
      clearTimeout(timer);
    };
  }, [id, revision]);
  useEffect(() => () => actionAbort.current?.abort(), []);

  const status = agenda ? agendaState(agenda) : null;
  useEffect(() => {
    if (
      agenda?.sessao?.status === 'ABERTA' &&
      now >= Date.parse(agenda.sessao.encerraEm) &&
      boundaryRef.current !== agenda.sessao.encerraEm
    ) {
      boundaryRef.current = agenda.sessao.encerraEm;
      refresh();
      onChanged();
    }
  }, [agenda, now, refresh, onChanged]);

  async function perform(action: (signal: AbortSignal) => Promise<unknown>, message: string) {
    if (busy) return;
    setBusy(true);
    setActionError(null);
    setSuccess('');
    const controller = new AbortController();
    actionAbort.current = controller;
    try {
      await action(controller.signal);
      if (controller.signal.aborted) return;
      setSuccess(message);
      refresh();
      onChanged();
    } catch (failure) {
      if (!controller.signal.aborted) {
        setActionError(failure);
        refresh();
      }
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  function openSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const minutes = duration ? Number(duration) : undefined;
    if (minutes !== undefined && (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440))
      return;
    void perform(
      (signal) => api.open(id, minutes, signal),
      'Sessão aberta. Agora os associados já podem votar.',
    );
  }

  function vote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!choice || !associate.trim() || status !== 'ABERTA') return;
    void perform(
      (signal) =>
        api.vote(
          id,
          { associadoId: associate.trim(), voto: choice, ...(checkCpf && cpf ? { cpf } : {}) },
          signal,
        ),
      `Voto de ${associate.trim()} registrado com sucesso.`,
    );
  }

  if (loading)
    return (
      <section className="detail-loading card" aria-label="Carregando pauta">
        <div className="skeleton skeleton-title" />
        <div className="skeleton" />
        <div className="skeleton skeleton-block" />
        <p className="muted" role="status">
          Carregando detalhes…
        </p>
      </section>
    );
  if (!agenda || !result || !status)
    return (
      <section className="card detail-loading">
        <ErrorNotice error={error} retry={refresh} />
      </section>
    );
  const percent = result.total > 0 ? Math.round((result.sim / result.total) * 100) : 0;
  const noPercent = result.total > 0 ? 100 - percent : 0;
  const resultPending = status === 'ENCERRADA' && result.status !== 'ENCERRADA';

  return (
    <section className="agenda-detail" aria-label="Detalhes da pauta">
      <div className="card detail-top">
        <div className="detail-meta">
          <span className="eyebrow">PAUTA #{String(agenda.id).padStart(3, '0')}</span>
          <StatusBadge status={status} />
        </div>
        <h2>{agenda.titulo}</h2>
        {agenda.descricao ? (
          <p className="agenda-description">{agenda.descricao}</p>
        ) : (
          <p className="agenda-description muted">Esta pauta não possui descrição.</p>
        )}
        <div className="created-date">Criada em {formatDate(agenda.criadaEm)}</div>
      </div>
      <ErrorNotice error={error} retry={refresh} />
      <div className={`session-card ${status === 'ABERTA' ? 'session-live' : ''}`}>
        <div className="session-heading">
          <div className="icon-tile">
            <Icon name="clock" size={22} />
          </div>
          <div>
            <p className="eyebrow">SESSÃO DE VOTAÇÃO</p>
            <h3>
              {status === 'AGUARDANDO'
                ? 'Pronta para começar'
                : status === 'ABERTA'
                  ? 'É hora de participar'
                  : 'Votação concluída'}
            </h3>
          </div>
          {status === 'ABERTA' && agenda.sessao && (
            <div className="countdown">
              <span aria-label="Tempo restante estimado">
                {remainingTime(agenda.sessao.encerraEm, now)}
              </span>
              <small>estimados para encerrar</small>
            </div>
          )}
        </div>
        {status === 'AGUARDANDO' ? (
          <>
            <p className="muted">
              Abra a sessão para receber votos. Cada associado poderá votar uma única vez nesta
              pauta.
            </p>
            <form className="open-session-form" onSubmit={openSession}>
              <div>
                <label htmlFor="duration">Duração em minutos</label>
                <input
                  id="duration"
                  type="number"
                  min={1}
                  max={1440}
                  step={1}
                  value={duration}
                  onChange={(event) => setDuration(event.target.value)}
                  placeholder="1"
                  aria-describedby="duration-hint"
                  disabled={busy}
                />
                <small id="duration-hint">Se não informar, a sessão dura 1 minuto.</small>
              </div>
              <button className="button button-primary" type="submit" disabled={busy}>
                {busy ? 'Abrindo…' : 'Abrir votação'}
                <Icon name="arrow" size={17} />
              </button>
            </form>
          </>
        ) : (
          <p className="session-time">
            {status === 'ABERTA' ? 'Aberta' : 'Iniciada'} em {formatDate(agenda.sessao!.abertaEm)}{' '}
            <span>·</span> {status === 'ABERTA' ? 'Encerra' : 'Encerrada'} em{' '}
            {formatDate(agenda.sessao!.encerraEm)}
          </p>
        )}
      </div>
      <ErrorNotice error={actionError} />
      {success && (
        <div className="notice notice-success" role="status">
          <Icon name="check" size={18} />
          <p>{success}</p>
        </div>
      )}
      {status === 'ABERTA' && (
        <div className="card vote-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">SUA PARTICIPAÇÃO IMPORTA</p>
              <h3>Registre seu voto</h3>
            </div>
            <Icon name="ballot" size={26} />
          </div>
          <form onSubmit={vote}>
            <label htmlFor="associate">Identificação do associado</label>
            <input
              id="associate"
              value={associate}
              onChange={(event) => {
                setAssociate(event.target.value);
                setSuccess('');
                setActionError(null);
              }}
              maxLength={64}
              placeholder="Informe seu código de associado"
              required
              disabled={busy}
              aria-describedby="associate-hint"
            />
            <small id="associate-hint">
              Use sempre a mesma identificação. O voto não pode ser alterado.
            </small>
            <fieldset className="vote-options" disabled={busy}>
              <legend>Você é a favor desta pauta?</legend>
              {(['SIM', 'NAO'] as const).map((option) => (
                <label
                  key={option}
                  className={`vote-option ${choice === option ? 'selected' : ''}`}
                >
                  <input
                    type="radio"
                    name="vote"
                    value={option}
                    checked={choice === option}
                    onChange={() => setChoice(option)}
                    required
                  />
                  <span className="option-icon">
                    <Icon name={option === 'SIM' ? 'check' : 'close'} size={20} />
                  </span>
                  <span>
                    <strong>{option === 'SIM' ? 'Sim' : 'Não'}</strong>
                    <small>{option === 'SIM' ? 'Sou a favor' : 'Sou contra'}</small>
                  </span>
                  <span className="radio-indicator" />
                </label>
              ))}
            </fieldset>
            <details className="cpf-details">
              <summary>
                Simulação de elegibilidade <span>opcional</span>
              </summary>
              <p>
                Uma demonstração com resposta aleatória de aptidão. Não consulta dados reais. Use
                somente um CPF fictício com 11 dígitos.
              </p>
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={checkCpf}
                  onChange={(event) => setCheckCpf(event.target.checked)}
                  disabled={busy}
                />
                Incluir a simulação neste voto
              </label>
              {checkCpf && (
                <>
                  <label htmlFor="cpf">CPF fictício</label>
                  <input
                    id="cpf"
                    value={cpf}
                    onChange={(event) => setCpf(event.target.value.replace(/\D/g, '').slice(0, 11))}
                    inputMode="numeric"
                    pattern="[0-9]{11}"
                    minLength={11}
                    maxLength={11}
                    placeholder="00000000000"
                    required
                    disabled={busy}
                  />
                </>
              )}
            </details>
            <div className="vote-submit">
              <span>
                <Icon name="users" size={16} /> Um associado, um voto.
              </span>
              <button
                className="button button-primary"
                type="submit"
                disabled={busy || !associate.trim() || !choice}
              >
                {busy ? 'Registrando…' : 'Confirmar voto'}
                <Icon name="check" size={17} />
              </button>
            </div>
          </form>
        </div>
      )}
      <div className="card results-card">
        <div className="section-heading">
          <div>
            <p className="eyebrow">
              {status === 'ENCERRADA' ? 'RESULTADO DA SESSÃO' : 'ACOMPANHE A DECISÃO'}
            </p>
            <h3>{status === 'ENCERRADA' ? 'Resultado final' : 'Resultado parcial'}</h3>
          </div>
          <button
            className="icon-button"
            onClick={refresh}
            aria-label="Atualizar resultado"
            title="Atualizar resultado"
          >
            <Icon name="refresh" size={18} />
          </button>
        </div>
        <div className="result-summary">
          <div>
            <strong>{result.total.toLocaleString('pt-BR')}</strong>
            <span>{result.total === 1 ? 'voto registrado' : 'votos registrados'}</span>
          </div>
          <p>{resultPending ? 'Conferindo o resultado final…' : resultLabels[result.resultado]}</p>
        </div>
        <div className="result-row">
          <div>
            <span>
              <i className="legend-dot yes" />
              Sim
            </span>
            <strong>
              {result.sim.toLocaleString('pt-BR')} <small>({percent}%)</small>
            </strong>
          </div>
          <div
            className="bar-track"
            role="meter"
            aria-label="Percentual de votos Sim"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={percent}
          >
            <div className="bar-fill yes" style={{ width: `${percent}%` }} />
          </div>
        </div>
        <div className="result-row">
          <div>
            <span>
              <i className="legend-dot no" />
              Não
            </span>
            <strong>
              {result.nao.toLocaleString('pt-BR')} <small>({noPercent}%)</small>
            </strong>
          </div>
          <div
            className="bar-track"
            role="meter"
            aria-label="Percentual de votos Não"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={noPercent}
          >
            <div className="bar-fill no" style={{ width: `${noPercent}%` }} />
          </div>
        </div>
        <p className="result-footnote">
          <span className="live-dot" /> Atualização automática a cada 5 segundos
          {status === 'ABERTA' ? ' · O resultado pode mudar até o encerramento.' : '.'}
        </p>
      </div>
    </section>
  );
}
