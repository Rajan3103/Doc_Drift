import React, { useState, useEffect } from 'react';
import { RefreshCw, Plus, Layers, GitFork, AlertTriangle } from 'lucide-react';
import Header from './components/Header';
import ReportList from './components/ReportList';
import RepoList from './components/RepoList';
import { fetchReports, fetchRepos, registerRepo } from './api';

export default function App() {
  const [activeTab, setActiveTab] = useState('reports'); // 'reports' | 'repos'
  const [reports, setReports] = useState([]);
  const [repos, setRepos] = useState([]);
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
      const [reportsData, reposData] = await Promise.all([
        fetchReports().catch(() => []),
        fetchRepos().catch(() => [])
      ]);
      setReports(reportsData);
      setRepos(reposData);
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
      const newRepo = await registerRepo(repoUrl, repoName);
      setRepos((prev) => [...prev, newRepo]);
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

  const handleRepoUpdated = (updatedRepo) => {
    setRepos((prev) => prev.map((r) => (r.id === updatedRepo.id ? updatedRepo : r)));
    fetchReports().then(setReports).catch(() => {});
  };

  const handleRepoDeleted = (deletedId) => {
    setRepos((prev) => prev.filter((r) => r.id !== deletedId));
  };

  // Count pending reviews
  const pendingCount = reports.reduce((acc, r) => {
    return acc + (r.suggestions?.filter(s => s.status === 'PENDING').length || 0);
  }, 0);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header />

      <main className="container" style={{ flex: 1, padding: '32px 24px' }}>
        {/* Top Title & Actions Bar */}
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
              title="Refresh Reports & Repos"
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

        {/* Tab Navigation */}
        <div style={{
          display: 'flex',
          gap: '12px',
          borderBottom: '1px solid var(--border-glass)',
          marginBottom: '24px'
        }}>
          <button
            onClick={() => setActiveTab('reports')}
            style={{
              padding: '12px 20px',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'reports' ? '2px solid var(--accent-primary)' : '2px solid transparent',
              color: activeTab === 'reports' ? 'white' : 'var(--text-muted)',
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              transition: 'all 0.2s ease'
            }}
          >
            <AlertTriangle size={16} color={activeTab === 'reports' ? 'var(--accent-primary)' : 'currentColor'} />
            <span>Drift Reports ({reports.length})</span>
          </button>

          <button
            onClick={() => setActiveTab('repos')}
            style={{
              padding: '12px 20px',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'repos' ? '2px solid var(--accent-primary)' : '2px solid transparent',
              color: activeTab === 'repos' ? 'white' : 'var(--text-muted)',
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              transition: 'all 0.2s ease'
            }}
          >
            <GitFork size={16} color={activeTab === 'repos' ? 'var(--accent-primary)' : 'currentColor'} />
            <span>Tracked Repositories ({repos.length})</span>
          </button>
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
            <p>Loading dashboard data...</p>
          </div>
        ) : activeTab === 'reports' ? (
          <ReportList reports={reports} onStatusChange={handleStatusChange} />
        ) : (
          <RepoList
            repos={repos}
            onRepoUpdated={handleRepoUpdated}
            onRepoDeleted={handleRepoDeleted}
          />
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
                  placeholder="Rajan3103/Doc_Drift"
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
                  placeholder="https://github.com/Rajan3103/Doc_Drift"
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
