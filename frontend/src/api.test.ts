import { describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';

describe('cliente da API', () => {
  it('preserva mensagens e erros por campo no ProblemDetail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            detail: 'Dados inválidos.',
            errors: { titulo: 'O título é obrigatório.' },
          }),
          { status: 400 },
        ),
      ),
    );
    await expect(api.create({ titulo: '' })).rejects.toMatchObject({
      message: 'Dados inválidos.',
      status: 400,
      errors: { titulo: 'O título é obrigatório.' },
    });
  });
  it('traduz falha de rede em um erro recuperável para o usuário', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(api.list(0, '')).rejects.toBeInstanceOf(ApiError);
    await expect(api.list(0, '')).rejects.toMatchObject({
      status: 0,
      message: expect.stringContaining('backend está em execução'),
    });
  });
  it('mantém cancelamentos como AbortError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new DOMException('The operation was aborted', 'AbortError')),
    );
    await expect(api.agenda(1, new AbortController().signal)).rejects.toMatchObject({
      name: 'AbortError',
    });
  });
  it('trata respostas de erro sem JSON sem ocultar o status HTTP', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('Bad gateway', { status: 502 })));
    await expect(api.result(1)).rejects.toMatchObject({
      status: 502,
      message: expect.stringContaining('HTTP 502'),
    });
  });
  it('codifica corretamente o texto de busca', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [] })));
    vi.stubGlobal('fetch', fetch);
    await api.list(2, 'pauta & orçamento');
    const url = new URL(fetch.mock.calls[0][0], 'http://localhost');
    expect(url.searchParams.get('busca')).toBe('pauta & orçamento');
    expect(url.searchParams.get('page')).toBe('2');
  });
});
