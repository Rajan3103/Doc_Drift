import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

// Previous / Next controls for a paged API result ({ page, totalPages, totalElements })
export default function Pagination({ page, totalPages, totalElements, onPageChange }) {
  if (!totalPages || totalPages <= 1) return null;

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '16px', marginTop: '24px' }}>
      <button className="btn btn-outline" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
        <ChevronLeft size={16} /> Previous
      </button>
      <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
        Page {page + 1} of {totalPages} ({totalElements} total)
      </span>
      <button className="btn btn-outline" onClick={() => onPageChange(page + 1)} disabled={page >= totalPages - 1}>
        Next <ChevronRight size={16} />
      </button>
    </div>
  );
}
