import React, { useState } from 'react';
import { Check, X, FileText, AlertCircle, Sparkles, AlertTriangle, ShieldAlert, Cpu, Percent } from 'lucide-react';
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

  const getSeverityBadge = () => {
    const sev = (suggestion.severity || 'MEDIUM').toUpperCase();
    let bg = 'rgba(245, 158, 11, 0.15)';
    let color = '#f59e0b';
    let icon = <AlertTriangle size={13} />;

    if (sev === 'CRITICAL') {
      bg = 'rgba(239, 68, 68, 0.2)';
      color = '#ef4444';
      icon = <ShieldAlert size={13} />;
    } else if (sev === 'HIGH') {
      bg = 'rgba(249, 115, 22, 0.2)';
      color = '#f97316';
      icon = <AlertTriangle size={13} />;
    } else if (sev === 'LOW') {
      bg = 'rgba(56, 189, 248, 0.15)';
      color = '#38bdf8';
      icon = <AlertCircle size={13} />;
    }

    return (
      <span style={{
        background: bg,
        color: color,
        border: `1px solid ${color}40`,
        padding: '3px 10px',
        borderRadius: '6px',
        fontSize: '0.72rem',
        fontWeight: 700,
        letterSpacing: '0.04em',
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px'
      }}>
        {icon} {sev} SEVERITY
      </span>
    );
  };

  const getDriftTypeLabel = () => {
    const type = suggestion.driftType || 'BEHAVIORAL_LOGIC_DRIFT';
    return type.replace(/_/g, ' ');
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

  const getMethodColor = (method) => {
    switch ((method || '').toUpperCase()) {
      case 'GET': return { bg: 'rgba(59, 130, 246, 0.15)', text: '#60a5fa', border: 'rgba(59, 130, 246, 0.3)' };
      case 'POST': return { bg: 'rgba(16, 185, 129, 0.15)', text: '#34d399', border: 'rgba(16, 185, 129, 0.3)' };
      case 'PUT': return { bg: 'rgba(245, 158, 11, 0.15)', text: '#fbbf24', border: 'rgba(245, 158, 11, 0.3)' };
      case 'DELETE': return { bg: 'rgba(239, 68, 68, 0.15)', text: '#f87171', border: 'rgba(239, 68, 68, 0.3)' };
      case 'PATCH': return { bg: 'rgba(139, 92, 246, 0.15)', text: '#a78bfa', border: 'rgba(139, 92, 246, 0.3)' };
      default: return { bg: 'rgba(255, 255, 255, 0.1)', text: '#e5e7eb', border: 'rgba(255, 255, 255, 0.2)' };
    }
  };

  const confidencePct = Math.round((suggestion.confidenceScore || 0.9) * 100);

  return (
    <div className="glass-panel animate-fade-in" style={{ padding: '24px', marginBottom: '20px' }}>
      {/* Breaking Change Critical Banner */}
      {suggestion.isBreakingChange && (
        <div style={{
          background: 'linear-gradient(90deg, rgba(239, 68, 68, 0.2) 0%, rgba(239, 68, 68, 0.05) 100%)',
          border: '1px solid rgba(239, 68, 68, 0.4)',
          borderRadius: '8px',
          padding: '10px 14px',
          marginBottom: '16px',
          display: 'flex',
          alignItems: 'center',
          gap: '10px'
        }}>
          <ShieldAlert size={18} color="#ef4444" style={{ flexShrink: 0 }} />
          <div>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#fca5a5', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Breaking API Contract Change
            </span>
            <p style={{ fontSize: '0.78rem', color: '#fecaca', margin: 0 }}>
              This change alters the contract for consumers. Ensure documentation & client SDKs are updated prior to merging.
            </p>
          </div>
        </div>
      )}

      {/* Top Header Row */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px', flexWrap: 'wrap', gap: '10px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <FileText size={18} color="var(--accent-primary)" />
            <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, fontSize: '0.9rem', color: 'var(--text-main)' }}>
              {suggestion.filePath}
            </span>
          </div>

          {getSeverityBadge()}

          {/* HTTP Method & Route if API Drift */}
          {suggestion.httpMethod && (
            <span style={{
              background: getMethodColor(suggestion.httpMethod).bg,
              color: getMethodColor(suggestion.httpMethod).text,
              border: `1px solid ${getMethodColor(suggestion.httpMethod).border}`,
              padding: '2px 8px',
              borderRadius: '4px',
              fontFamily: 'var(--font-mono)',
              fontSize: '0.72rem',
              fontWeight: 700
            }}>
              {suggestion.httpMethod}
            </span>
          )}

          {suggestion.endpointPath && (
            <code style={{
              background: 'rgba(0, 0, 0, 0.35)',
              padding: '2px 8px',
              borderRadius: '4px',
              fontFamily: 'var(--font-mono)',
              fontSize: '0.75rem',
              color: '#e2e8f0',
              border: '1px solid var(--border-glass)'
            }}>
              {suggestion.endpointPath}
            </code>
          )}

          <span style={{
            background: 'rgba(99, 102, 241, 0.12)',
            color: '#a5b4fc',
            border: '1px solid rgba(99, 102, 241, 0.3)',
            padding: '3px 10px',
            borderRadius: '6px',
            fontSize: '0.72rem',
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: '0.03em'
          }}>
            ⚡ {getDriftTypeLabel()}
          </span>

          {suggestion.schemaFormat && suggestion.schemaFormat !== 'GENERAL_PROSE' && (
            <span style={{
              background: 'rgba(168, 85, 247, 0.12)',
              color: '#d8b4fe',
              border: '1px solid rgba(168, 85, 247, 0.3)',
              padding: '2px 8px',
              borderRadius: '4px',
              fontSize: '0.72rem',
              fontWeight: 600
            }}>
              📄 {suggestion.schemaFormat.replace(/_/g, ' ')}
            </span>
          )}

          <span style={{
            background: 'rgba(255, 255, 255, 0.05)',
            color: 'var(--text-muted)',
            padding: '3px 8px',
            borderRadius: '6px',
            fontSize: '0.72rem',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px'
          }}>
            <Percent size={11} /> {confidencePct}% Confidence
          </span>
        </div>

        {getStatusBadge()}
      </div>

      {/* Impacted Symbol Tag if available */}
      {suggestion.impactedSymbol && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
          marginBottom: '14px',
          fontSize: '0.8rem',
          color: 'var(--text-muted)'
        }}>
          <Cpu size={14} color="var(--accent-primary)" />
          <span>Impacted Code Symbol:</span>
          <code style={{
            background: 'rgba(0, 0, 0, 0.4)',
            padding: '2px 8px',
            borderRadius: '4px',
            color: '#cbd5e1',
            fontFamily: 'var(--font-mono)',
            fontSize: '0.78rem',
            border: '1px solid var(--border-glass)'
          }}>
            {suggestion.impactedSymbol}
          </code>
        </div>
      )}

      {/* AI Semantic Reasoning Box */}
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
          <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 600 }}>
            API Contract & Semantic Drift Analysis
          </span>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-main)', marginTop: '4px', margin: 0, lineHeight: 1.6 }}>
            {suggestion.reason || 'No reasoning provided.'}
          </p>
        </div>
      </div>

      {/* Side by Side Diff Viewer */}
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

      {/* Actions */}
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
