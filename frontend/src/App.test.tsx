import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import App from './App';
import { AgendaDetail } from './AgendaDetail';
import type { Agenda, Result } from './types';

function agenda(id = 1, title = 'Novo horário de atendimento', open = false): Agenda {
  return {
    id,
    titulo: title,
    descricao: 'Avaliar a proposta com a cooperativa.',
    criadaEm: '2026-09-22T12:00:00Z',
    sessao: open
      ? {
          id,
          abertaEm: new Date(Date.now()).toISOString(),
          encerraEm: new Date(Date.now() + 60_000).toISOString(),
          status: 'ABERTA',
        }
      : null,
  };
}
function result(item: Agenda, votes = 0): Result {
  return {
    pautaId: item.id,
    sim: votes,
    nao: 0,
    total: votes,
    status: item.sessao ? 'ABERTA' : 'NAO_INICIADA',
    resultado: item.sessao ? 'EM_ANDAMENTO' : 'AGUARDANDO',
  };
}
function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
type Router = (url: URL, init: RequestInit) => Response | Promise<Response>;
function mockServer(route: Router) {
  const fetch = vi.fn((input: string, init: RequestInit = {}) =>
    Promise.resolve(route(new URL(input, 'http://localhost'), init)),
  );
  vi.stubGlobal('fetch', fetch);
  return fetch;
}
function list(items: Agenda[], overrides: Record<string, unknown> = {}) {
  return json({
    content: items,
    page: 0,
    size: 8,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
    ...overrides,
  });
}

describe('fluxos da assembleia', () => {
  it('cria uma pauta pelo formulário e apresenta o detalhe retornado pela API', async () => {
    const user = userEvent.setup();
    const items: Agenda[] = [];
    const fetch = mockServer((url, init) => {
      if (url.pathname === '/api/v1/pautas' && init.method === 'POST') {
        const input = JSON.parse(String(init.body));
        const created = { ...agenda(3, input.titulo), descricao: input.descricao };
        items.push(created);
        return json(created, 201);
      }
      if (url.pathname.endsWith('/resultado')) return json(result(items[0]));
      if (url.pathname === '/api/v1/pautas/3') return json(items[0]);
      return list(items);
    });
    render(<App />);
    await screen.findByText('Sua primeira decisão começa aqui');
    await user.click(screen.getByRole('button', { name: 'Nova pauta' }));
    const modal = screen.getByRole('dialog', { name: 'O que vamos decidir?' });
    expect(within(modal).getByRole('button', { name: 'Criar pauta' })).toBeDisabled();
    await user.type(within(modal).getByLabelText(/Título da pauta/), '  Ampliar a biblioteca  ');
    await user.type(within(modal).getByLabelText(/Descrição/), 'Novos livros para a cooperativa.');
    await user.click(within(modal).getByRole('button', { name: 'Criar pauta' }));
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Ampliar a biblioteca' }),
    ).toBeVisible();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      titulo: 'Ampliar a biblioteca',
      descricao: 'Novos livros para a cooperativa.',
    });
  });

  it('abre a sessão omitindo a duração para usar o padrão do servidor', async () => {
    const user = userEvent.setup();
    let item = agenda();
    const fetch = mockServer((url, init) => {
      if (url.pathname.endsWith('/sessoes') && init.method === 'POST') {
        item = agenda(1, item.titulo, true);
        return json(item.sessao, 201);
      }
      if (url.pathname.endsWith('/resultado')) return json(result(item));
      if (url.pathname === '/api/v1/pautas/1') return json(item);
      return list([item]);
    });
    render(<App />);
    await user.click(await screen.findByRole('button', { name: 'Abrir votação' }));
    expect(await screen.findByRole('heading', { name: 'Registre seu voto' })).toBeVisible();
    expect(
      JSON.parse(String(fetch.mock.calls.find(([, init]) => init?.method === 'POST')?.[1]?.body)),
    ).toEqual({});
    expect(screen.getByText('Sessão aberta. Agora os associados já podem votar.')).toBeVisible();
  });

  it('registra um voto e atualiza o resultado com a contagem confirmada pela API', async () => {
    const user = userEvent.setup();
    const item = agenda(1, 'Novo horário', true);
    let votes = 0;
    const fetch = mockServer((url, init) => {
      if (url.pathname.endsWith('/votos')) {
        votes++;
        return json(
          {
            id: 4,
            pautaId: 1,
            ...JSON.parse(String(init.body)),
            registradoEm: new Date().toISOString(),
          },
          201,
        );
      }
      if (url.pathname.endsWith('/resultado')) return json(result(item, votes));
      if (url.pathname === '/api/v1/pautas/1') return json(item);
      return list([item]);
    });
    render(<App />);
    await user.type(await screen.findByLabelText('Identificação do associado'), '  associado-22  ');
    await user.click(screen.getByRole('radio', { name: /Sim/ }));
    await user.click(screen.getByRole('button', { name: 'Confirmar voto' }));
    expect(await screen.findByText('Voto de associado-22 registrado com sucesso.')).toBeVisible();
    await waitFor(() =>
      expect(screen.getByRole('meter', { name: 'Percentual de votos Sim' })).toHaveAttribute(
        'aria-valuenow',
        '100',
      ),
    );
    expect(
      JSON.parse(String(fetch.mock.calls.find(([, init]) => init?.method === 'POST')?.[1]?.body)),
    ).toEqual({ associadoId: 'associado-22', voto: 'SIM' });
  });

  it('exibe o erro de voto duplicado sem inventar uma confirmação ou alterar a contagem', async () => {
    const user = userEvent.setup();
    const item = agenda(1, 'Novo horário', true);
    mockServer((url) => {
      if (url.pathname.endsWith('/votos'))
        return json({ detail: 'Este associado já votou nesta pauta.' }, 409);
      if (url.pathname.endsWith('/resultado')) return json(result(item, 1));
      if (url.pathname === '/api/v1/pautas/1') return json(item);
      return list([item]);
    });
    render(<App />);
    await user.type(await screen.findByLabelText('Identificação do associado'), 'associado-1');
    await user.click(screen.getByRole('radio', { name: /Não/ }));
    await user.click(screen.getByRole('button', { name: 'Confirmar voto' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este associado já votou nesta pauta.',
    );
    expect(screen.queryByText(/registrado com sucesso/)).not.toBeInTheDocument();
    expect(screen.getByLabelText('Identificação do associado')).toHaveValue('associado-1');
    expect(screen.getByText('voto registrado')).toBeVisible();
  });

  it('ignora a resposta atrasada de uma pauta quando a pessoa seleciona outra', async () => {
    const user = userEvent.setup();
    const first = agenda(1, 'Primeira pauta');
    const second = agenda(2, 'Segunda pauta');
    let resolveFirst!: (value: Response) => void;
    const deferred = new Promise<Response>((resolve) => {
      resolveFirst = resolve;
    });
    mockServer((url) => {
      if (url.pathname === '/api/v1/pautas/1') return deferred;
      if (url.pathname === '/api/v1/pautas/2') return json(second);
      if (url.pathname.endsWith('/resultado'))
        return json(result(url.pathname.includes('/1/') ? first : second));
      return list([first, second]);
    });
    render(<App />);
    await user.click(await screen.findByRole('button', { name: /Segunda pauta/ }));
    expect(await screen.findByRole('heading', { level: 2, name: 'Segunda pauta' })).toBeVisible();
    await act(async () => {
      resolveFirst(json(first));
      await deferred;
    });
    expect(screen.getByRole('heading', { level: 2, name: 'Segunda pauta' })).toBeVisible();
    expect(
      screen.queryByRole('heading', { level: 2, name: 'Primeira pauta' }),
    ).not.toBeInTheDocument();
  });

  it('busca por título e cancela visualmente a seleção quando não há resultados', async () => {
    const user = userEvent.setup();
    const item = agenda();
    const fetch = mockServer((url) => {
      if (url.pathname.endsWith('/resultado')) return json(result(item));
      if (url.pathname === '/api/v1/pautas/1') return json(item);
      return list(url.searchParams.has('busca') ? [] : [item]);
    });
    render(<App />);
    await screen.findByRole('heading', { level: 2, name: item.titulo });
    await user.type(screen.getByRole('searchbox', { name: 'Buscar pautas' }), 'inexistente');
    expect(await screen.findByText('Nenhuma pauta encontrada')).toBeVisible();
    expect(screen.queryByRole('heading', { level: 2, name: item.titulo })).not.toBeInTheDocument();
    expect(fetch.mock.calls.some(([url]) => url.includes('busca=inexistente'))).toBe(true);
    await user.click(screen.getByRole('button', { name: 'Limpar busca' }));
    expect(await screen.findByRole('heading', { level: 2, name: item.titulo })).toBeVisible();
  });

  it('permite recuperar a listagem depois de uma falha de rede', async () => {
    const user = userEvent.setup();
    let failed = true;
    mockServer(() => {
      if (failed) throw new TypeError('network failure');
      return list([]);
    });
    render(<App />);
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível conectar ao servidor',
    );
    failed = false;
    await user.click(screen.getByRole('button', { name: /Tentar novamente/ }));
    expect(await screen.findByText('Sua primeira decisão começa aqui')).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('consulta o estado ao zerar a estimativa e encerra após a confirmação do servidor', async () => {
    const start = Date.now();
    const item = agenda(1, 'Prazo da sessão', true);
    item.sessao!.encerraEm = new Date(start + 1000).toISOString();
    let closed = false;
    const onChanged = vi.fn();
    mockServer((url) => {
      if (url.pathname.endsWith('/resultado'))
        return json(
          closed ? { ...result(item), status: 'ENCERRADA', resultado: 'SEM_VOTOS' } : result(item),
        );
      return json(closed ? { ...item, sessao: { ...item.sessao, status: 'ENCERRADA' } } : item);
    });
    const { rerender } = render(<AgendaDetail id={1} now={start} onChanged={onChanged} />);
    expect(await screen.findByRole('heading', { name: 'Registre seu voto' })).toBeVisible();
    closed = true;
    rerender(<AgendaDetail id={1} now={start + 1000} onChanged={onChanged} />);
    expect(await screen.findByText('Sessão encerrada sem votos')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Confirmar voto' })).not.toBeInTheDocument();
    expect(onChanged).toHaveBeenCalledOnce();
  });

  it('permite votar com relógio local adiantado quando o servidor mantém a sessão aberta', async () => {
    const user = userEvent.setup();
    const start = Date.now();
    const item = agenda(1, 'Relógio adiantado', true);
    item.sessao!.encerraEm = new Date(start + 60_000).toISOString();
    const onChanged = vi.fn();
    let votes = 0;
    const fetch = mockServer((url, init) => {
      if (url.pathname.endsWith('/votos')) {
        votes++;
        return json(
          { id: 1, pautaId: 1, ...JSON.parse(String(init.body)), registradoEm: new Date(start) },
          201,
        );
      }
      if (url.pathname.endsWith('/resultado')) return json(result(item, votes));
      return json(item);
    });
    render(<AgendaDetail id={1} now={start + 120_000} onChanged={onChanged} />);

    expect(await screen.findByLabelText('Tempo restante estimado')).toHaveTextContent('00:00');
    await waitFor(() => {
      expect(fetch.mock.calls.filter(([url]) => url.endsWith('/pautas/1'))).toHaveLength(2);
    });
    expect(screen.getByText('Em votação')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Resultado final' })).not.toBeInTheDocument();
    expect(onChanged).toHaveBeenCalledOnce();

    await user.type(screen.getByLabelText('Identificação do associado'), 'associado-adiantado');
    await user.click(screen.getByRole('radio', { name: /Sim/ }));
    await user.click(screen.getByRole('button', { name: 'Confirmar voto' }));
    expect(
      await screen.findByText('Voto de associado-adiantado registrado com sucesso.'),
    ).toBeVisible();
    expect(votes).toBe(1);
  });

  it('impede votar com relógio local atrasado quando o servidor confirma o encerramento', async () => {
    const start = Date.now();
    const item = agenda(1, 'Relógio atrasado', true);
    item.sessao!.status = 'ENCERRADA';
    item.sessao!.encerraEm = new Date(start).toISOString();
    const fetch = mockServer((url) =>
      url.pathname.endsWith('/resultado')
        ? json({ ...result(item), status: 'ENCERRADA', resultado: 'SEM_VOTOS' })
        : json(item),
    );
    render(<AgendaDetail id={1} now={start - 120_000} onChanged={vi.fn()} />);

    expect(await screen.findByText('Sessão encerrada sem votos')).toBeVisible();
    expect(screen.getByText('Encerrada', { selector: '.badge' })).toBeVisible();
    expect(screen.queryByLabelText('Tempo restante estimado')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar voto' })).not.toBeInTheDocument();
    expect(fetch.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
  });

  it('apresenta validações detalhadas da API ao criar uma pauta', async () => {
    const user = userEvent.setup();
    mockServer((_url, init) =>
      init.method === 'POST'
        ? json(
            {
              detail: 'Revise os campos informados.',
              errors: { titulo: 'O título não pode conter apenas espaços.' },
            },
            400,
          )
        : list([]),
    );
    render(<App />);
    await user.click(screen.getByRole('button', { name: 'Nova pauta' }));
    fireEvent.change(screen.getByLabelText(/Título da pauta/), { target: { value: 'Título' } });
    await user.click(screen.getByRole('button', { name: 'Criar pauta' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Revise os campos informados.');
    expect(alert).toHaveTextContent('O título não pode conter apenas espaços.');
    expect(screen.getByRole('dialog')).toBeVisible();
  });

  it('envia CPF fictício somente após a opção explícita e mostra uma recusa de elegibilidade', async () => {
    const user = userEvent.setup();
    const item = agenda(1, 'Elegibilidade simulada', true);
    const fetch = mockServer((url) => {
      if (url.pathname.endsWith('/votos')) {
        return json({ detail: 'O associado não está apto a votar nesta simulação.' }, 404);
      }
      if (url.pathname.endsWith('/resultado')) return json(result(item));
      if (url.pathname === '/api/v1/pautas/1') return json(item);
      return list([item]);
    });
    render(<App />);
    await user.type(await screen.findByLabelText('Identificação do associado'), 'associado-10');
    await user.click(screen.getByRole('radio', { name: /Sim/ }));
    await user.click(screen.getByText('Simulação de elegibilidade'));
    await user.click(screen.getByLabelText('Incluir a simulação neste voto'));
    await user.type(screen.getByLabelText('CPF fictício'), '00000000000');
    await user.click(screen.getByRole('button', { name: 'Confirmar voto' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('O associado não está apto');
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      associadoId: 'associado-10',
      voto: 'SIM',
      cpf: '00000000000',
    });
    expect(screen.queryByText(/registrado com sucesso/)).not.toBeInTheDocument();
  });

  it('troca a página e retorna à primeira ao iniciar uma busca', async () => {
    const user = userEvent.setup();
    const first = agenda(1, 'Primeira página');
    const second = agenda(2, 'Segunda página');
    const fetch = mockServer((url) => {
      const item = url.pathname.includes('/2') ? second : first;
      if (url.pathname.endsWith('/resultado')) return json(result(item));
      if (/\/pautas\/\d+$/.test(url.pathname)) return json(item);
      const page = Number(url.searchParams.get('page'));
      return list([page === 1 ? second : first], { page, totalPages: 2, totalElements: 9 });
    });
    render(<App />);
    await screen.findByRole('heading', { name: 'Primeira página', level: 2 });
    expect(screen.getByRole('button', { name: 'Página anterior' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Próxima página' }));
    expect(await screen.findByRole('heading', { name: 'Segunda página', level: 2 })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Próxima página' })).toBeDisabled();
    await user.type(screen.getByRole('searchbox', { name: 'Buscar pautas' }), 'horário');
    await waitFor(() => {
      const request = fetch.mock.calls.find(([url]) => url.includes('busca='));
      expect(request).toBeDefined();
      expect(new URL(request![0], 'http://localhost').searchParams.get('page')).toBe('0');
    });
    expect(await screen.findByRole('heading', { name: 'Primeira página', level: 2 })).toBeVisible();
  });
});
