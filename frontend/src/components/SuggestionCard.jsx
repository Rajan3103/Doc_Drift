import React, { useState } from 'react';
import { Check, X, FileText, AlertCircle, Sparkles } from 'lucide-react';
import { approveSuggestion, rejectSuggestion } from '../api';

export default function SuggestionCard({ suggestion, onStatusChange }) {
  const [status, setStatus] = useState(suggestion.status);
  const [loading, setLoading] = useState(false);

  const handleApprove = async () => {
    setLoading(true);
    try {
      await approveSuggestion(suggestion.id);
      setStatus('APPROVED');
      if (onStatusChange) onStatusChange(suggestion.id, 'APPROVED');
    } catch (e) {
      alert('Failed to approve suggestion');
    } finally {
      setLoading(false);
    }
  };

  const handleReject = async () => {
    setLoading(true);
    try {
      await rejectSuggestion(suggestion.id);
      setStatus('REJECTED');
      if (onStatusChange) onStatusChange(suggestion.id, 'REJECTED');
    } catch (e) {
      alert('Failed to reject suggestion');
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadge = () => {
    if (status === 'APPROVED') {
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
          <Check size={14} /> Approved
        </span>
      );
    }
    if (status === 'REJECTED') {
      return (
        <span style={{
          background: 'var(--danger-bg)',
          color: 'var(--danger)',
          padding: '4px 12px',
          borderRadius: '12px',
          fontSize: '0.75rem',
          fontWeight: 600,
          display: 'flex',
          alignItems: 'center',
          gap: '4px'
        }}>
          <X size={14} /> Rejected
        </span>
      );
    }
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
        <AlertCircle size={14} /> Pending Review
      </span>
    );
  };

  return (
    <div className="glass-panel animate-fade-in" style={{ padding: '24px', marginBottom: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FileText size={18} color="var(--accent-primary)" />
          <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, fontSize: '0.9rem' }}>
            {suggestion.filePath}
          </span>
        </div>
        {getStatusBadge()}
      </div>

      <div style={{
        background: 'rgba(255, 255, 255, 0.02)',
        borderLeft: '3px solid var(--accent-primary)',
        padding: '12px 16px',
        borderRadius: '0 8px 8px 0',
        marginBottom: '20px',
        display: 'flex',
        gap: '10px',
        alignItems: 'flex-start'
      }}>
        <Sparkles size={18} color="var(--accent-primary)" style={{ flexShrink: 0, marginTop: '2px' }} />
        <div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 600 }}>
            AI Reasoning
          </span>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-main)', marginTop: '4px', margin: 0 }}>
            {suggestion.reason || 'No reasoning provided.'}
          </p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '20px' }}>
        <div style={{
          background: 'rgba(239, 68, 68, 0.05)',
          border: '1px solid rgba(239, 68, 68, 0.15)',
          borderRadius: '8px',
          padding: '12px',
          overflowX: 'auto'
        }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--danger)', fontWeight: 600, marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span>- Current Documentation</span>
          </div>
          <pre style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: '#fca5a5', whiteSpace: 'pre-wrap', margin: 0 }}>
            {suggestion.oldText}
          </pre>
        </div>

        <div style={{
          background: 'rgba(16, 185, 129, 0.05)',
          border: '1px solid rgba(16, 185, 129, 0.15)',
          borderRadius: '8px',
          padding: '12px',
          overflowX: 'auto'
        }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--success)', fontWeight: 600, marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span>+ Suggested Fix</span>
          </div>
          <pre style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: '#6ee7b7', whiteSpace: 'pre-wrap', margin: 0 }}>
            {suggestion.suggestedText}
          </pre>
        </div>
      </div>

      {status === 'PENDING' && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', borderTop: '1px solid var(--border-glass)', paddingTop: '16px' }}>
          <button
            className="btn btn-outline"
            onClick={handleReject}
            disabled={loading}
            style={{ color: 'var(--danger)', borderColor: 'rgba(239, 68, 68, 0.3)' }}
          >
            <X size={16} /> Reject
          </button>
          <button
            className="btn btn-primary"
            onClick={handleApprove}
            disabled={loading}
          >
            <Check size={16} /> Approve Fix & Create PR
          </button>
        </div>
      )}
    </div>
  );
}
