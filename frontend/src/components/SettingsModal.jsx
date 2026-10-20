import React, { useState } from 'react';
import Modal from './Modal';
import { updateRepoSettings } from '../api';

const inputStyle = {
  width: '100%', padding: '10px 12px', borderRadius: '8px', background: 'rgba(0, 0, 0, 0.3)',
  border: '1px solid var(--border-glass)', color: 'white', fontFamily: 'var(--font-sans)', outline: 'none'
};
const labelStyle = { display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '6px' };
const hintStyle = { fontSize: '0.72rem', color: 'var(--text-dim)', marginTop: '4px' };

// Turns a textarea (one entry per line) into a list, and back
const toLines = (list) => (list || []).join('\n');
const fromLines = (text) => text.split('\n').map((s) => s.trim()).filter(Boolean);

// Per-repository settings: which docs and branches to check, noise thresholds, PR checks and alerts
export default function SettingsModal({ repo, onClose, onSaved }) {
  const [docPaths, setDocPaths] = useState(toLines(repo.docPaths));
  const [branches, setBranches] = useState(toLines(repo.watchedBranches));
  const [minSeverity, setMinSeverity] = useState(repo.minSeverity || 'LOW');
  const [minConfidence, setMinConfidence] = useState(Math.round((repo.minConfidence || 0) * 100));
  const [analyzePullRequests, setAnalyzePullRequests] = useState(repo.analyzePullRequests !== false);
  // The Slack URL is secret and never sent back; empty means "keep the current one"
  const [slackWebhookUrl, setSlackWebhookUrl] = useState('');
  const [removeSlack, setRemoveSlack] = useState(false);
  const [alertEmail, setAlertEmail] = useState(repo.alertEmail || '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const handleSave = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const saved = await updateRepoSettings(repo.id, {
        docPaths: fromLines(docPaths),
        watchedBranches: fromLines(branches),
        minSeverity,
        minConfidence: Number(minConfidence) / 100,
        analyzePullRequests,
        slackWebhookUrl: removeSlack ? '' : (slackWebhookUrl.trim() || null),
        alertEmail: alertEmail.trim() || null
      });
      onSaved(saved);
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={`Settings: ${repo.name}`} onClose={onClose}>
      <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div>
          <label style={labelStyle} htmlFor="docPaths">Documentation to check (one per line)</label>
          <textarea id="docPaths" rows={3} value={docPaths} onChange={(e) => setDocPaths(e.target.value)}
            placeholder={'README.md\ndocs/\napi/**.yaml'} style={{ ...inputStyle, fontFamily: 'var(--font-mono)' }} />
          <p style={hintStyle}>A file, a folder ending in /, or a pattern. Leave empty to find docs automatically.</p>
        </div>

        <div>
          <label style={labelStyle} htmlFor="branches">Branches to watch (one per line)</label>
          <textarea id="branches" rows={2} value={branches} onChange={(e) => setBranches(e.target.value)}
            placeholder={'main\nrelease/*'} style={{ ...inputStyle, fontFamily: 'var(--font-mono)' }} />
          <p style={hintStyle}>Leave empty to watch only the default branch.</p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
          <div>
            <label style={labelStyle} htmlFor="minSeverity">Minimum severity</label>
            <select id="minSeverity" value={minSeverity} onChange={(e) => setMinSeverity(e.target.value)} style={inputStyle}>
              <option value="LOW">Low (keep everything)</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical only</option>
            </select>
          </div>
          <div>
            <label style={labelStyle} htmlFor="minConfidence">Minimum confidence: {minConfidence}%</label>
            <input id="minConfidence" type="range" min="0" max="100" step="5" value={minConfidence}
              onChange={(e) => setMinConfidence(e.target.value)} style={{ width: '100%' }} />
          </div>
        </div>

        <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.85rem' }}>
          <input type="checkbox" checked={analyzePullRequests} onChange={(e) => setAnalyzePullRequests(e.target.checked)} />
          Check pull requests and comment on them
        </label>

        <div>
          <label style={labelStyle} htmlFor="slack">
            Slack webhook for breaking/critical alerts {repo.slackAlertsConfigured && !removeSlack ? '(configured)' : ''}
          </label>
          <input id="slack" type="url" value={slackWebhookUrl} disabled={removeSlack}
            onChange={(e) => setSlackWebhookUrl(e.target.value)}
            placeholder={repo.slackAlertsConfigured ? 'Leave empty to keep the current webhook' : 'https://hooks.slack.com/services/...'}
            style={inputStyle} />
          {repo.slackAlertsConfigured && (
            <label style={{ ...hintStyle, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <input type="checkbox" checked={removeSlack} onChange={(e) => setRemoveSlack(e.target.checked)} />
              Remove the Slack webhook
            </label>
          )}
        </div>

        <div>
          <label style={labelStyle} htmlFor="alertEmail">Email for breaking/critical alerts</label>
          <input id="alertEmail" type="email" value={alertEmail} onChange={(e) => setAlertEmail(e.target.value)}
            placeholder="team@example.com" style={inputStyle} />
          <p style={hintStyle}>Needs a mail server configured on the backend.</p>
        </div>

        {error && <p style={{ color: '#fca5a5', fontSize: '0.85rem', margin: 0 }}>{error}</p>}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
          <button type="button" className="btn btn-outline" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving...' : 'Save Settings'}</button>
        </div>
      </form>
    </Modal>
  );
}
