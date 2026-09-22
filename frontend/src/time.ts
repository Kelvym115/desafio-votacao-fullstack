import type { Agenda } from './types';

export function agendaState(agenda: Agenda) {
  // O relógio do dispositivo só estima a contagem regressiva. O servidor decide
  // se a sessão ainda aceita votos, mesmo quando a estimativa já chegou a zero.
  return agenda.sessao?.status ?? 'AGUARDANDO';
}

export function remainingTime(end: string, now: number) {
  const seconds = Math.max(0, Math.ceil((Date.parse(end) - now) / 1000));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  return hours > 0
    ? `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
