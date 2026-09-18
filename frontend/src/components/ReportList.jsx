import React from 'react';
import { GitCommit, Clock, CheckCircle2 } from 'lucide-react';
import SuggestionCard from './SuggestionCard';

export default function ReportList({ reports, onStatusChange }) {
  if (!reports || reports.length === 0) {
    return (
      <div className="glass-panel" style={{
        padding: '64px 24px',
        textAlign: 'center',
        marginTop: '24px'
      }}>
        <CheckCircle2 size={48} color="var(--success)" style={{ margin: '0 auto 16px' }} />
        <h3 style={{ fontSize: '1.25rem', marginBottom: '8px' }}>No Drift Detected</h3>
        <p style={{ color: 'var(--text-muted)', maxWidth: '400px', margin: '0 auto' }}>
          All tracked repositories are synchronized. No documentation drift has been flagged by the AI engine.
        </p>
      </div>
    );
  }

  return (
    <div style={{ marginTop: '24px' }}>
      {reports.map((report) => (
        <div key={report.id} style={{ marginBottom: '40px' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '16px',
            paddingBottom: '12px',
            borderBottom: '1px solid var(--border-glass)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{
                background: 'rgba(255, 255, 255, 0.05)',
                padding: '8px',
                borderRadius: '8px'
              }}>
                <GitCommit size={20} color="var(--accent-primary)" />
              </div>
              <div>
                <h2 style={{ fontSize: '1.1rem', margin: 0 }}>
                  Commit <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent-primary)' }}>
                    {report.commitSha?.substring(0, 7) || 'Unknown'}
                  </span>
                </h2>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  {report.repositoryName}
                </span>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              {/* Semantic summary pills */}
              <div style={{ display: 'flex', gap: '6px' }}>
                {report.suggestions?.some(s => (s.severity || '').toUpperCase() === 'CRITICAL') && (
                  <span style={{
                    background: 'rgba(239, 68, 68, 0.15)',
                    color: '#ef4444',
                    padding: '2px 8px',
                    borderRadius: '4px',
                    fontSize: '0.72rem',
                    fontWeight: 700
                  }}>
                    {report.suggestions.filter(s => (s.severity || '').toUpperCase() === 'CRITICAL').length} Critical
                  </span>
                )}
                <span style={{
                  background: 'rgba(99, 102, 241, 0.1)',
                  color: 'var(--accent-primary)',
                  padding: '2px 8px',
                  borderRadius: '4px',
                  fontSize: '0.72rem',
                  fontWeight: 600
                }}>
                  {report.suggestions?.length || 0} Drifts
                </span>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                <Clock size={14} />
                <span>{new Date(report.analyzedAt).toLocaleString()}</span>
              </div>
            </div>
          </div>

          <div>
            {report.suggestions && report.suggestions.length > 0 ? (
              report.suggestions.map((suggestion) => (
                <SuggestionCard
                  key={suggestion.id}
                  suggestion={suggestion}
                  onStatusChange={onStatusChange}
                />
              ))
            ) : (
              <p style={{ color: 'var(--text-dim)', fontSize: '0.875rem' }}>No suggestions for this report.</p>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
