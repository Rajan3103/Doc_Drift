import React, { useState, useEffect } from 'react';
import { RefreshCw, Plus, Layers } from 'lucide-react';
import Header from './components/Header';
import ReportList from './components/ReportList';
import { fetchReports, registerRepo } from './api';

export default function App() {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  // Repo registration modal state
  const [showModal, setShowModal] = useState(false);
  const [repoUrl, setRepoUrl] = useState('');
  const [repoName, setRepoName] = useState('');
  const [registering, setRegistering] = useState(false);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchReports();
      setReports(data);
    } catch (err) {
      setError('Could not connect to the backend server. Is Spring Boot running on port 8080?');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleStatusChange = (suggestionId, newStatus) => {
    setReports((prevReports) =>
      prevReports.map((report) => ({
        ...report,
        suggestions: report.suggestions?.map((s) =>
          s.id === suggestionId ? { ...s, status: newStatus } : s
        ) || [],
      }))
    );
  };

  const handleRegisterRepo = async (e) => {
    e.preventDefault();
    if (!repoUrl || !repoName) return;
    
    setRegistering(true);
    try {
      await registerRepo(repoUrl, repoName);
      setShowModal(false);
      setRepoUrl('');
      setRepoName('');
      alert('Repository registered successfully!');
    } catch (err) {
      alert('Failed to register repository: ' + err.message);
    } finally {
      setRegistering(false);
    }
  };

  // Count pending reviews
  const pendingCount = reports.reduce((acc, r) => {
    return acc + (r.suggestions?.filter(s => s.status === 'PENDING').length || 0);
  }, 0);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header />

      <main className="container" style={{ flex: 1, padding: '32px 24px' }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '24px',
          flexWrap: 'wrap',
          gap: '16px'
        }}>
          <div>
            <h1 style={{ fontSize: '1.75rem', marginBottom: '4px' }}>Review Dashboard</h1>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              Review and apply AI-suggested documentation fixes triggered by code changes.
            </p>
          </div>

          <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
            <div className="glass-panel" style={{ padding: '6px 14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Layers size={16} color="var(--accent-primary)" />
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Pending Reviews:</span>
              <span style={{ fontWeight: 700, color: pendingCount > 0 ? 'var(--warning)' : 'var(--success)' }}>
                {pendingCount}
              </span>
            </div>

            <button
              className="btn btn-outline"
              onClick={loadData}
              disabled={loading}
              title="Refresh Reports"
            >
              <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
              <span>Refresh</span>
            </button>

            <button
              className="btn btn-primary"
              onClick={() => setShowModal(true)}
            >
              <Plus size={16} />
              <span>Track Repository</span>
            </button>
          </div>
        </div>

        {error && (
          <div style={{
            background: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            color: '#fca5a5',
            padding: '16px',
            borderRadius: '8px',
            marginBottom: '24px'
          }}>
            <strong>Connection Error: </strong> {error}
          </div>
        )}

        {loading && !error ? (
          <div style={{ padding: '64px 0', textAlign: 'center', color: 'var(--text-muted)' }}>
            <RefreshCw size={32} style={{ margin: '0 auto 16px', animation: 'spin 1s linear infinite' }} />
            <p>Loading drift reports...</p>
          </div>
        ) : (
          <ReportList reports={reports} onStatusChange={handleStatusChange} />
        )}
      </main>

      {/* Register Repo Modal */}
      {showModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.7)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          padding: '24px'
        }}>
          <div className="glass-panel animate-fade-in" style={{
            width: '100%',
            maxWidth: '480px',
            padding: '24px',
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-focus)'
          }}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '16px' }}>Track New Repository</h2>
            <form onSubmit={handleRegisterRepo}>
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '6px' }}>
                  Repository Name (e.g., owner/repo)
                </label>
                <input
                  type="text"
                  placeholder="docdrift/example"
                  value={repoName}
                  onChange={(e) => setRepoName(e.target.value)}
                  required
                  style={{
                    width: '100%',
                    padding: '10px 12px',
                    borderRadius: '8px',
                    background: 'rgba(0, 0, 0, 0.3)',
                    border: '1px solid var(--border-glass)',
                    color: 'white',
                    fontFamily: 'var(--font-sans)',
                    outline: 'none'
                  }}
                />
              </div>

              <div style={{ marginBottom: '24px' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '6px' }}>
                  GitHub URL
                </label>
                <input
                  type="url"
                  placeholder="https://github.com/docdrift/example"
                  value={repoUrl}
                  onChange={(e) => setRepoUrl(e.target.value)}
                  required
                  style={{
                    width: '100%',
                    padding: '10px 12px',
                    borderRadius: '8px',
                    background: 'rgba(0, 0, 0, 0.3)',
                    border: '1px solid var(--border-glass)',
                    color: 'white',
                    fontFamily: 'var(--font-sans)',
                    outline: 'none'
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button
                  type="button"
                  className="btn btn-outline"
                  onClick={() => setShowModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={registering}
                >
                  {registering ? 'Registering...' : 'Start Tracking'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <style>{`
        @keyframes spin { 100% { transform: rotate(360deg); } }
        .animate-spin { animation: spin 1s linear infinite; }
      `}</style>
    </div>
  );
}
