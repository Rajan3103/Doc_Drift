import React from 'react';
import { Filter } from 'lucide-react';

const selectStyle = {
  padding: '8px 10px',
  borderRadius: '8px',
  background: 'rgba(0, 0, 0, 0.3)',
  border: '1px solid var(--border-glass)',
  color: 'white',
  fontFamily: 'var(--font-sans)',
  fontSize: '0.85rem',
  outline: 'none'
};

// Repository / severity / review-status filters for the drift reports list
export default function ReportFilters({ filters, repoNames, onChange }) {
  const update = (key) => (e) => onChange({ ...filters, [key]: e.target.value });

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap', marginBottom: '8px' }}>
      <Filter size={16} color="var(--text-muted)" />
      <select value={filters.repository} onChange={update('repository')} style={selectStyle} aria-label="Repository">
        <option value="">All repositories</option>
        {repoNames.map((name) => <option key={name} value={name}>{name}</option>)}
      </select>
      <select value={filters.severity} onChange={update('severity')} style={selectStyle} aria-label="Severity">
        <option value="">All severities</option>
        <option value="CRITICAL">Critical</option>
        <option value="HIGH">High</option>
        <option value="MEDIUM">Medium</option>
        <option value="LOW">Low</option>
      </select>
      <select value={filters.status} onChange={update('status')} style={selectStyle} aria-label="Review status">
        <option value="">All statuses</option>
        <option value="PENDING">Pending review</option>
        <option value="APPROVED">Approved</option>
        <option value="REJECTED">Rejected</option>
      </select>
    </div>
  );
}
