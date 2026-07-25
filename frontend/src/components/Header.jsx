import React from 'react';
import { GitPullRequest, ShieldAlert, Activity } from 'lucide-react';

export default function Header() {
  return (
    <header style={{
      borderBottom: '1px solid var(--border-glass)',
      background: 'rgba(10, 12, 16, 0.8)',
      backdropFilter: 'blur(16px)',
      position: 'sticky',
      top: 0,
      zIndex: 50,
      padding: '16px 0'
    }}>
      <div className="container" style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            background: 'var(--accent-gradient)',
            padding: '8px',
            borderRadius: '10px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <ShieldAlert size={24} color="white" />
          </div>
          <div>
            <h1 style={{ fontSize: '1.25rem', margin: 0, fontWeight: 700 }}>
              Doc<span className="gradient-text">Drift</span>
            </h1>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-dim)', margin: 0 }}>
              AI-Assisted Documentation Drift Detector
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: 'rgba(16, 185, 129, 0.1)',
            border: '1px solid rgba(16, 185, 129, 0.2)',
            padding: '6px 12px',
            borderRadius: '20px',
            fontSize: '0.75rem',
            color: 'var(--success)'
          }}>
            <Activity size={14} />
            <span>System Active</span>
          </div>
        </div>
      </div>
    </header>
  );
}
