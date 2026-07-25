import React, { useState } from 'react';
import { ExternalLink, Play, Trash2, ShieldCheck, ShieldAlert, RefreshCw, Clock, GitFork } from 'lucide-react';
import { deleteRepo, scanRepo } from '../api';

export default function RepoList({ repos, onRepoUpdated, onRepoDeleted }) {
  const [loadingId, setLoadingId] = useState(null);

  const handleScan = async (id) => {
    setLoadingId(id);
    try {
      const updated = await scanRepo(id);
      if (onRepoUpdated) onRepoUpdated(updated);
    } catch (e) {
      alert('Failed to trigger manual scan');
    } finally {
      setLoadingId(null);
    }
  };

  const handleDelete = async (id) => {
    if (!confirm('Are you sure you want to stop tracking this repository?')) return;
    setLoadingId(id);
    try {
      await deleteRepo(id);
      if (onRepoDeleted) onRepoDeleted(id);
    } catch (e) {
      alert('Failed to untrack repository');
    } finally {
      setLoadingId(null);
    }
  };

  if (!repos || repos.length === 0) {
    return (
      <div className="glass-panel" style={{
        padding: '64px 24px',
        textAlign: 'center',
        marginTop: '24px'
      }}>
        <GitFork size={48} color="var(--accent-primary)" style={{ margin: '0 auto 16px' }} />
        <h3 style={{ fontSize: '1.25rem', marginBottom: '8px' }}>No Repositories Tracked</h3>
        <p style={{ color: 'var(--text-muted)', maxWidth: '400px', margin: '0 auto 24px' }}>
          Add your software repositories to start monitoring documentation-code drift in real time.
        </p>
      </div>
    );
  }

  const getStatusBadge = (status) => {
    if (status === 'SCANNING') {
      return (
        <span style={{
          background: 'rgba(99, 102, 241, 0.15)',
          color: 'var(--accent-primary)',
          padding: '4px 12px',
          borderRadius: '12px',
          fontSize: '0.75rem',
          fontWeight: 600,
          display: 'flex',
          alignItems: 'center',
          gap: '4px'
        }}>
          <RefreshCw size={14} className="animate-spin" /> Scanning...
        </span>
      );
    }
    if (status === 'DRIFTED') {
      return (
        <span style={{
          background: 'var(--warning-bg)',
          color: 'var(--warning)',
          padding: '4px 12px',
          borderRadius: '12px',
          fontSize: '0.75rem',
          fontWeight: 600,
          display: 'flex',
          alignItems: 'center',
          gap: '4px'
        }}>
          <ShieldAlert size={14} /> Drift Flagged
        </span>
      );
    }
    return (
      <span style={{
        background: 'var(--success-bg)',
        color: 'var(--success)',
        padding: '4px 12px',
        borderRadius: '12px',
        fontSize: '0.75rem',
        fontWeight: 600,
        display: 'flex',
        alignItems: 'center',
        gap: '4px'
      }}>
        <ShieldCheck size={14} /> Healthy (In Sync)
      </span>
    );
  };

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))',
      gap: '20px',
      marginTop: '24px'
    }}>
      {repos.map((repo) => (
        <div key={repo.id} className="glass-panel animate-fade-in" style={{ padding: '24px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <GitFork size={20} color="var(--accent-primary)" />
                <h3 style={{ fontSize: '1.1rem', margin: 0, fontWeight: 700 }}>{repo.name}</h3>
              </div>
              {getStatusBadge(repo.status)}
            </div>

            <div style={{ marginBottom: '20px' }}>
              <a
                href={repo.url}
                target="_blank"
                rel="noreferrer"
                style={{
                  color: 'var(--text-muted)',
                  fontSize: '0.85rem',
                  textDecoration: 'none',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px'
                }}
              >
                <span>{repo.url}</span>
                <ExternalLink size={12} />
              </a>
            </div>

            <div style={{
              background: 'rgba(255, 255, 255, 0.02)',
              borderRadius: '8px',
              padding: '12px',
              fontSize: '0.8rem',
              color: 'var(--text-dim)',
              display: 'flex',
              flexDirection: 'column',
              gap: '6px',
              marginBottom: '20px'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Clock size={12} />
                <span>Last Scan: {repo.lastScannedAt ? new Date(repo.lastScannedAt).toLocaleString() : 'Never scanned'}</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Clock size={12} />
                <span>Added On: {repo.createdAt ? new Date(repo.createdAt).toLocaleDateString() : 'N/A'}</span>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--border-glass)', paddingTop: '16px' }}>
            <button
              className="btn btn-outline"
              onClick={() => handleDelete(repo.id)}
              disabled={loadingId === repo.id}
              style={{ color: 'var(--danger)', borderColor: 'rgba(239, 68, 68, 0.2)' }}
              title="Untrack Repository"
            >
              <Trash2 size={15} />
              <span>Untrack</span>
            </button>

            <button
              className="btn btn-primary"
              onClick={() => handleScan(repo.id)}
              disabled={loadingId === repo.id}
            >
              <Play size={15} />
              <span>{loadingId === repo.id ? 'Scanning...' : 'Manual Drift Scan'}</span>
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
