import type { ReactNode } from 'react';
import { ApiError } from './api';

type IconName =
  | 'plus'
  | 'search'
  | 'arrow'
  | 'check'
  | 'close'
  | 'clock'
  | 'ballot'
  | 'users'
  | 'refresh'
  | 'alert';
const paths: Record<IconName, ReactNode> = {
  plus: <path d="M12 5v14M5 12h14" />,
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m16 16 4.5 4.5" />
    </>
  ),
  arrow: <path d="M5 12h14m-5-5 5 5-5 5" />,
  check: <path d="m5 12 4 4L19 6" />,
  close: <path d="m6 6 12 12M6 18 18 6" />,
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  ballot: (
    <>
      <path d="M5 11H3v10h18V11h-2M8 14h8M7 3h10v8H7z" />
      <path d="m10 7 1.5 1.5L14 6" />
    </>
  ),
  users: (
    <>
      <circle cx="9" cy="8" r="3" />
      <path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6m2 4a6 6 0 0 1 3 5" />
    </>
  ),
  refresh: (
    <>
      <path d="M20 7v5h-5M4 17v-5h5" />
      <path d="M6 7a7 7 0 0 1 12-1l2 3M4 15l2 3a7 7 0 0 0 12-1" />
    </>
  ),
  alert: (
    <>
      <path d="m12 3 10 18H2L12 3zM12 9v4m0 4h.01" />
    </>
  ),
};

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}

export function ErrorNotice({ error, retry }: { error: unknown; retry?: () => void }) {
  if (!error) return null;
  const message = error instanceof Error ? error.message : String(error);
  const fields = error instanceof ApiError ? Object.entries(error.errors) : [];
  return (
    <div className="notice notice-error" role="alert">
      <Icon name="alert" size={18} />
      <div>
        <p>{message}</p>
        {fields.length > 0 && (
          <ul>
            {fields.map(([field, detail]) => (
              <li key={field}>{detail}</li>
            ))}
          </ul>
        )}
        {retry && (
          <button className="text-button" onClick={retry}>
            Tentar novamente <Icon name="refresh" size={14} />
          </button>
        )}
      </div>
    </div>
  );
}

export function StatusBadge({ status }: { status: 'AGUARDANDO' | 'ABERTA' | 'ENCERRADA' }) {
  return (
    <span className={`badge badge-${status.toLowerCase()}`}>
      <span className="badge-dot" />
      {status === 'ABERTA'
        ? 'Em votação'
        : status === 'ENCERRADA'
          ? 'Encerrada'
          : 'Aguardando sessão'}
    </span>
  );
}
