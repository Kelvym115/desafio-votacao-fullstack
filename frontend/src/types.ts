export type VoteChoice = 'SIM' | 'NAO';
export type SessionStatus = 'ABERTA' | 'ENCERRADA';
export interface Session {
  id: number;
  abertaEm: string;
  encerraEm: string;
  status: SessionStatus;
}
export interface Agenda {
  id: number;
  titulo: string;
  descricao: string | null;
  criadaEm: string;
  sessao: Session | null;
}
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Result {
  pautaId: number;
  sim: number;
  nao: number;
  total: number;
  status: 'NAO_INICIADA' | SessionStatus;
  resultado: 'AGUARDANDO' | 'EM_ANDAMENTO' | 'APROVADA' | 'REJEITADA' | 'EMPATE' | 'SEM_VOTOS';
}
export interface VoteInput {
  associadoId: string;
  voto: VoteChoice;
  cpf?: string;
}
export interface Vote extends VoteInput {
  id: number;
  pautaId: number;
  registradoEm: string;
}
