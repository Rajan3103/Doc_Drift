import React, { useEffect, useState } from 'react';
import { CheckCircle2, XCircle, MinusCircle, RefreshCw, GitPullRequest, GitCommit, Play } from 'lucide-react';
import Pagination from './Pagination';
import { fetchEvents } from '../api';

// How often the Activity tab refreshes itself, in milliseconds
const REFRESH_MS = 10000;
const PAGE_SIZE = 20;

const STATUS = {
  PROCESSED: { label: 'Done', color: 'var(--success)', Icon: CheckCircle2 },
  ERROR: { label: 'Failed', color: 'var(--danger)', Icon: XCircle },
  IGNORED: { label: 'Ignored', color: 'var(--text-muted)', Icon: MinusCircle },
  PENDING: { label: 'Running', color: 'var(--accent-primary)', Icon: RefreshCw }
};

const TYPE = {
  push: { label: 'Push', Icon: GitCommit },
  pull_request: { label: 'Pull request', Icon: GitPullRequest },
  manual_scan: { label: 'Manual scan', Icon: Play }
};

// Live list of webhook deliveries, pull request checks and manual scans, newest first
export default function ActivityList({ repoNames }) {
  const [page, setPage] = useState(0);
  const [repository, setRepository] = useState('');
  const [events, setEvents] = useState({ content: [], page: 0, totalPages: 0, totalElements: 0 });
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    const load = () => fetchEvents({ page, size: PAGE_SIZE, repository })
      .then((data) => { if (active) { setEvents(data); setError(null); } })
      .catch((e) => { if (active) setError(e.message); });
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => { active = false; clearInterval(timer); };
  }, [page, repository]);

  return (
    <div style={{ marginTop: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', flexWrap: 'wrap', gap: '12px' }}>
        <select value={repository} onChange={(e) => { setRepository(e.target.value); setPage(0); }} aria-label="Repository"
          style={{ padding: '8px 10px', borderRadius: '8px', background: 'rgba(0, 0, 0, 0.3)', border: '1px solid var(--border-glass)', color: 'white' }}>
          <option value="">All repositories</option>
          {repoNames.map((name) => <option key={name} value={name}>{name}</option>)}
        </select>
        <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>Refreshes every {REFRESH_MS / 1000} seconds</span>
      </div>

      {error && <p style={{ color: '#fca5a5' }}>{error}</p>}
      {events.content.length === 0 && !error && (
        <div className="glass-panel" style={{ padding: '48px 24px', textAlign: 'center', color: 'var(--text-muted)' }}>
          No activity yet. Pushes, pull requests and manual scans will appear here.
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {events.content.map((event) => {
          const status = STATUS[event.status] || STATUS.PENDING;
          const type = TYPE[event.eventType] || { label: event.eventType, Icon: GitCommit };
          return (
            <div key={event.id} className="glass-panel" style={{ padding: '12px 16px', display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
              <type.Icon size={18} color="var(--accent-primary)" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap', fontSize: '0.85rem' }}>
                  <strong>{event.repositoryName}</strong>
                  <span style={{ color: 'var(--text-muted)' }}>
                    {type.label}{event.pullRequestNumber ? ` #${event.pullRequestNumber}` : ''}
                  </span>
                  {event.commitSha && (
                    <code style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{event.commitSha.substring(0, 7)}</code>
                  )}
                </div>
                {event.payload && (
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {event.payload}
                  </div>
                )}
                {event.errorMessage && (
                  <div style={{ fontSize: '0.78rem', color: event.status === 'ERROR' ? '#fca5a5' : 'var(--text-dim)', marginTop: '4px', whiteSpace: 'pre-wrap' }}>
                    {event.errorMessage}
                  </div>
                )}
              </div>
              <div style={{ textAlign: 'right', flexShrink: 0 }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', fontWeight: 600, color: status.color }}>
                  <status.Icon size={14} /> {status.label}
                </span>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginTop: '2px' }}>
                  {event.receivedAt ? new Date(event.receivedAt).toLocaleString() : ''}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <Pagination {...events} onPageChange={setPage} />
    </div>
  );
}
