import type { Agenda, Page, Result, Session, Vote, VoteInput } from './types';

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public errors: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    });
  } catch (error) {
    if (
      typeof error === 'object' &&
      error !== null &&
      'name' in error &&
      error.name === 'AbortError'
    ) {
      throw error;
    }
    throw new ApiError(
      'Não foi possível conectar ao servidor. Verifique se o backend está em execução e tente novamente.',
      0,
    );
  }
  const data: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const problem = data as { detail?: string; errors?: Record<string, string> } | null;
    throw new ApiError(
      problem?.detail || `Não foi possível concluir a operação (HTTP ${response.status}).`,
      response.status,
      problem?.errors,
    );
  }
  if (data === null)
    throw new ApiError(
      'O servidor retornou uma resposta inesperada. Tente novamente.',
      response.status,
    );
  return data as T;
}

export const api = {
  list: (page: number, busca: string, signal?: AbortSignal) =>
    request<Page<Agenda>>(
      `/pautas?${new URLSearchParams({ page: String(page), size: '8', ...(busca ? { busca } : {}) })}`,
      { signal },
    ),
  agenda: (id: number, signal?: AbortSignal) => request<Agenda>(`/pautas/${id}`, { signal }),
  result: (id: number, signal?: AbortSignal) =>
    request<Result>(`/pautas/${id}/resultado`, { signal }),
  create: (input: { titulo: string; descricao?: string }, signal?: AbortSignal) =>
    request<Agenda>('/pautas', { method: 'POST', body: JSON.stringify(input), signal }),
  open: (id: number, duracaoMinutos?: number, signal?: AbortSignal) =>
    request<Session>(`/pautas/${id}/sessoes`, {
      method: 'POST',
      body: JSON.stringify({ duracaoMinutos }),
      signal,
    }),
  vote: (id: number, input: VoteInput, signal?: AbortSignal) =>
    request<Vote>(`/pautas/${id}/votos`, { method: 'POST', body: JSON.stringify(input), signal }),
};

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Ocorreu um erro inesperado. Tente novamente.';
}
