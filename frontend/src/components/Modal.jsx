import React from 'react';
import { X } from 'lucide-react';

// Centered dialog with a dimmed backdrop; clicking the backdrop or the X closes it
export default function Modal({ title, onClose, children, maxWidth = '560px' }) {
  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0, 0, 0, 0.7)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100, padding: '24px'
      }}
    >
      <div
        className="glass-panel animate-fade-in"
        role="dialog"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%', maxWidth, maxHeight: '90vh', overflowY: 'auto', padding: '24px',
          background: 'var(--bg-secondary)', border: '1px solid var(--border-focus)'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <h2 style={{ fontSize: '1.2rem', margin: 0 }}>{title}</h2>
          <button className="btn btn-outline" onClick={onClose} aria-label="Close" style={{ padding: '6px' }}>
            <X size={16} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
