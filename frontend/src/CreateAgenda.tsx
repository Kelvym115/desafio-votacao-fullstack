import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api } from './api';
import type { Agenda } from './types';
import { ErrorNotice, Icon } from './ui';

export function CreateAgenda({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (agenda: Agenda) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!title.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      const created = await api.create({
        titulo: title.trim(),
        ...(description.trim() ? { descricao: description.trim() } : {}),
      });
      onCreated(created);
    } catch (failure) {
      setError(failure);
      setBusy(false);
    }
  }

  return (
    <dialog
      ref={dialog}
      aria-labelledby="create-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className="dialog-heading">
        <div className="icon-tile">
          <Icon name="ballot" size={24} />
        </div>
        <button
          type="button"
          className="icon-button"
          aria-label="Fechar nova pauta"
          onClick={onClose}
          disabled={busy}
        >
          <Icon name="close" />
        </button>
      </div>
      <p className="eyebrow">UMA NOVA CONVERSA</p>
      <h2 id="create-title">O que vamos decidir?</h2>
      <p className="muted">
        Cadastre o assunto. Você poderá abrir a votação quando estiver tudo pronto.
      </p>
      <form onSubmit={submit}>
        <label htmlFor="agenda-title">
          Título da pauta <span aria-hidden="true">*</span>
        </label>
        <input
          id="agenda-title"
          autoFocus
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          maxLength={140}
          required
          placeholder="Ex.: Aprovar o novo horário de atendimento"
          disabled={busy}
        />
        <div className="field-counter">{title.length}/140</div>
        <label htmlFor="agenda-description">
          Descrição <span className="optional">opcional</span>
        </label>
        <textarea
          id="agenda-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          maxLength={2000}
          rows={4}
          placeholder="Adicione o contexto que os associados precisam conhecer."
          disabled={busy}
        />
        <ErrorNotice error={error} />
        <div className="dialog-actions">
          <button
            className="button button-secondary"
            type="button"
            onClick={onClose}
            disabled={busy}
          >
            Cancelar
          </button>
          <button className="button button-primary" type="submit" disabled={!title.trim() || busy}>
            {busy ? 'Criando…' : 'Criar pauta'}
            <Icon name="arrow" size={17} />
          </button>
        </div>
      </form>
    </dialog>
  );
}
