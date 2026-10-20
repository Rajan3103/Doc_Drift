import React, { useEffect, useState } from 'react';
import Modal from './Modal';
import { fetchRepoHistory } from '../api';

// Severity is ordered, so it uses one blue ramp (validated for the dark surface with the
// dataviz palette checker in --ordinal mode). Critical is brightest so it stands out most.
const SERIES = [
  { key: 'critical', label: 'Critical', color: '#9ec5f4' },
  { key: 'high', label: 'High', color: '#5598e7' },
  { key: 'medium', label: 'Medium', color: '#2a78d6' },
  { key: 'low', label: 'Low', color: '#184f95' }
];

// Chart size in SVG units (it scales to the container width)
const WIDTH = 640;
const HEIGHT = 220;
const MARGIN = { top: 10, right: 8, bottom: 26, left: 34 };
const GAP = 2; // surface-colored gap between stacked segments
const RADIUS = 4; // rounded top of each bar

const total = (day) => SERIES.reduce((sum, s) => sum + day[s.key], 0);

// Rounds the axis maximum up to 1, 2 or 5 x 10^n so gridlines land on round numbers
function niceMax(value) {
  if (value <= 4) return 4;
  const power = 10 ** Math.floor(Math.log10(value));
  const step = [1, 2, 5, 10].find((s) => s * power * 4 >= value) * power;
  return step * 4;
}

// Rectangle with only the top corners rounded, so bars stay anchored flat on the baseline
function topRoundedRect(x, y, w, h, r) {
  const radius = Math.min(r, w / 2, h);
  return `M${x},${y + h} V${y + radius} Q${x},${y} ${x + radius},${y} H${x + w - radius} Q${x + w},${y} ${x + w},${y + radius} V${y + h} Z`;
}

const shortDate = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

// Daily drift found in a repository over the last 30 days, stacked by severity
export default function HistoryChart({ repo, onClose }) {
  const [days, setDays] = useState(null);
  const [error, setError] = useState(null);
  const [hovered, setHovered] = useState(null);
  const [showTable, setShowTable] = useState(false);

  useEffect(() => {
    fetchRepoHistory(repo.id, 30).then(setDays).catch((e) => setError(e.message));
  }, [repo.id]);

  const plotW = WIDTH - MARGIN.left - MARGIN.right;
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom;
  const maxY = days ? niceMax(Math.max(...days.map(total))) : 4;
  const band = days ? plotW / days.length : 0;
  const barW = Math.max(4, band * 0.62);
  const y = (value) => MARGIN.top + plotH - (value / maxY) * plotH;
  const grandTotal = days ? days.reduce((sum, d) => sum + total(d), 0) : 0;

  return (
    <Modal title={`Drift history: ${repo.name}`} onClose={onClose} maxWidth="720px">
      {error && <p style={{ color: '#fca5a5' }}>{error}</p>}
      {!days && !error && <p style={{ color: 'var(--text-muted)' }}>Loading...</p>}

      {days && (
        <>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '8px' }}>
            <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Suggestions found per day, last 30 days: <strong style={{ color: 'var(--text-main)' }}>{grandTotal}</strong> total
            </p>
            {/* Legend: color is never the only way to tell severities apart (tooltip and table name them too) */}
            <div style={{ display: 'flex', gap: '14px', fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              {SERIES.map((s) => (
                <span key={s.key} style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                  <span style={{ width: 10, height: 10, borderRadius: 2, background: s.color, display: 'inline-block' }} />
                  {s.label}
                </span>
              ))}
            </div>
          </div>

          {!showTable ? (
            <div style={{ position: 'relative' }} onMouseLeave={() => setHovered(null)}>
              <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} width="100%" role="img"
                aria-label={`Stacked bar chart of drift suggestions per day by severity, ${grandTotal} in total`}>
                {/* Recessive gridlines and y-axis labels */}
                {[0, 0.25, 0.5, 0.75, 1].map((f) => (
                  <g key={f}>
                    <line x1={MARGIN.left} x2={WIDTH - MARGIN.right} y1={y(maxY * f)} y2={y(maxY * f)}
                      stroke="rgba(255,255,255,0.08)" strokeWidth="1" />
                    <text x={MARGIN.left - 6} y={y(maxY * f) + 4} textAnchor="end" fontSize="10" fill="var(--text-dim)">
                      {maxY * f}
                    </text>
                  </g>
                ))}

                {days.map((day, i) => {
                  const x = MARGIN.left + i * band + (band - barW) / 2;
                  let running = 0;
                  const segments = SERIES.filter((s) => day[s.key] > 0);
                  return (
                    <g key={day.date} opacity={hovered === null || hovered === i ? 1 : 0.55}>
                      {segments.map((s, idx) => {
                        const top = y(running + day[s.key]);
                        const bottom = y(running);
                        running += day[s.key];
                        const h = Math.max(1, bottom - top - (idx > 0 ? GAP : 0));
                        const isTop = idx === segments.length - 1;
                        return isTop
                          ? <path key={s.key} d={topRoundedRect(x, top, barW, h, RADIUS)} fill={s.color} />
                          : <rect key={s.key} x={x} y={top} width={barW} height={h} fill={s.color} />;
                      })}
                      {/* Hit target covers the whole day column, larger than the bar itself */}
                      <rect x={MARGIN.left + i * band} y={MARGIN.top} width={band} height={plotH}
                        fill="transparent" onMouseEnter={() => setHovered(i)} />
                    </g>
                  );
                })}

                {/* Baseline and sparse date labels */}
                <line x1={MARGIN.left} x2={WIDTH - MARGIN.right} y1={y(0)} y2={y(0)} stroke="rgba(255,255,255,0.25)" />
                {/* A label every 7 days, plus the last day unless it would collide with the previous label */}
                {days.map((day, i) => (i % 7 === 0 || (i === days.length - 1 && i % 7 >= 4)) && (
                  <text key={day.date} x={MARGIN.left + i * band + band / 2} y={HEIGHT - 8} textAnchor="middle"
                    fontSize="10" fill="var(--text-dim)">
                    {shortDate(day.date)}
                  </text>
                ))}
              </svg>

              {hovered !== null && (
                <div style={{
                  position: 'absolute', top: 0, pointerEvents: 'none',
                  left: `${((MARGIN.left + hovered * band + band / 2) / WIDTH) * 100}%`,
                  transform: hovered > days.length / 2 ? 'translateX(-105%)' : 'translateX(5%)',
                  background: 'var(--bg-secondary)', border: '1px solid var(--border-focus)', borderRadius: '8px',
                  padding: '8px 10px', fontSize: '0.78rem', color: 'var(--text-main)', whiteSpace: 'nowrap'
                }}>
                  <div style={{ fontWeight: 700, marginBottom: '4px' }}>{shortDate(days[hovered].date)}</div>
                  {SERIES.map((s) => (
                    <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-muted)' }}>
                      <span style={{ width: 8, height: 8, borderRadius: 2, background: s.color, display: 'inline-block' }} />
                      {s.label}: <span style={{ color: 'var(--text-main)' }}>{days[hovered][s.key]}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div style={{ maxHeight: '280px', overflowY: 'auto' }}>
              <table style={{ width: '100%', fontSize: '0.8rem', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ color: 'var(--text-muted)', textAlign: 'right' }}>
                    <th style={{ textAlign: 'left', padding: '4px' }}>Date</th>
                    {SERIES.map((s) => <th key={s.key} style={{ padding: '4px' }}>{s.label}</th>)}
                    <th style={{ padding: '4px' }}>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {days.filter((d) => total(d) > 0).map((d) => (
                    <tr key={d.date} style={{ textAlign: 'right', borderTop: '1px solid var(--border-glass)' }}>
                      <td style={{ textAlign: 'left', padding: '4px' }}>{shortDate(d.date)}</td>
                      {SERIES.map((s) => <td key={s.key} style={{ padding: '4px' }}>{d[s.key]}</td>)}
                      <td style={{ padding: '4px', fontWeight: 600 }}>{total(d)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {grandTotal === 0 && <p style={{ color: 'var(--text-muted)' }}>No drift found in the last 30 days.</p>}
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '12px' }}>
            <button className="btn btn-outline" onClick={() => setShowTable((v) => !v)}>
              {showTable ? 'Show chart' : 'Show table'}
            </button>
          </div>
        </>
      )}
    </Modal>
  );
}
