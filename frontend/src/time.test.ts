import { describe, expect, it } from 'vitest';
import { agendaState, remainingTime } from './time';
import type { Agenda } from './types';

const start = Date.parse('2026-09-22T15:00:00Z');
const agenda: Agenda = {
  id: 1,
  titulo: 'Teste',
  descricao: null,
  criadaEm: new Date(start).toISOString(),
  sessao: {
    id: 1,
    abertaEm: new Date(start).toISOString(),
    encerraEm: new Date(start + 60_000).toISOString(),
    status: 'ABERTA',
  },
};

describe('janela de votação exibida', () => {
  it('aguarda enquanto não há sessão', () => {
    expect(agendaState({ ...agenda, sessao: null })).toBe('AGUARDANDO');
  });
  it('o contador zerado não substitui o estado aberto informado pelo servidor', () => {
    expect(remainingTime(agenda.sessao!.encerraEm, start + 120_000)).toBe('00:00');
    expect(agendaState(agenda)).toBe('ABERTA');
  });
  it('respeita o encerramento informado pelo backend mesmo com relógio local atrasado', () => {
    expect(agendaState({ ...agenda, sessao: { ...agenda.sessao!, status: 'ENCERRADA' } })).toBe(
      'ENCERRADA',
    );
  });
  it('arredonda segundos para cima e nunca apresenta duração negativa', () => {
    expect(remainingTime(agenda.sessao!.encerraEm, start + 59_999)).toBe('00:01');
    expect(remainingTime(agenda.sessao!.encerraEm, start + 61_000)).toBe('00:00');
  });
  it('apresenta horas para sessões com duração longa', () => {
    expect(remainingTime(new Date(start + 3_661_000).toISOString(), start)).toBe('01:01:01');
  });
});
