import React, { useState, useEffect } from 'react';
import { RefreshCw, Plus, Layers, GitFork, AlertTriangle, KeyRound, Activity, Check, X } from 'lucide-react';
import Header from './components/Header';
import ReportList from './components/ReportList';
import RepoList from './components/RepoList';
import Pagination from './components/Pagination';
import ReportFilters from './components/ReportFilters';
import ActivityList from './components/ActivityList';
import { fetchReports, fetchRepos, fetchSummary, registerRepo, approveSuggestions, getApiKey, setApiKey, clearApiKey } from './api';

// Page sizes for the two lists; change these to show more or fewer items per page
const REPORTS_PAGE_SIZE = 10;
const REPOS_PAGE_SIZE = 12;
const EMPTY_PAGE = { content: [], page: 0, totalPages: 0, totalElements: 0 };

export default function App() {
  const [activeTab, setActiveTab] = useState('reports'); // 'reports' | 'repos' | 'activity'
  const [reportsPage, setReportsPage] = useState(EMPTY_PAGE);
  const [reposPage, setReposPage] = useState(EMPTY_PAGE);
  const [repoNames, setRepoNames] = useState([]);
  const [summary, setSummary] = useState({ pending: 0, critical: 0, breaking: 0 });
  const [reportPageNumber, setReportPageNumber] = useState(0);
  const [repoPageNumber, setRepoPageNumber] = useState(0);
  const [filters, setFilters] = useState({ repository: '', severity: '', status: '' });
  const reports = reportsPage.content;

  // Suggestions selected for one combined fix; they must share a repository (and pull request, if any)
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [selectionGroup, setSelectionGroup] = useState(null);
  const [approvingBatch, setApprovingBatch] = useState(false);
  const [notice, setNotice] = useState(null); // { text, url }
  const repos = reposPage.content;
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // API key prompt state (shown when the backend answers 401)
  const [needsApiKey, setNeedsApiKey] = useState(!getApiKey());
  const [apiKeyInput, setApiKeyInput] = useState('');
  
  // Repo registration modal state
  const [showModal, setShowModal] = useState(false);
  const [repoUrl, setRepoUrl] = useState('');
  const [repoName, setRepoName] = useState('');
  const [registering, setRegistering] = useState(false);

  // Fetches the current pages, the review counts and the repository names for the filter
  const fetchAll = async () => {
    const [reportsData, reposData, summaryData, allRepos] = await Promise.all([
      fetchReports({ page: reportPageNumber, size: REPORTS_PAGE_SIZE, ...filters }),
      fetchRepos({ page: repoPageNumber, size: REPOS_PAGE_SIZE }),
      fetchSummary(),
      fetchRepos({ page: 0, size: 100 })
    ]);
    setReportsPage(reportsData);
    setReposPage(reposData);
    setSummary(summaryData);
    setRepoNames(allRepos.content.map((r) => r.name));
  };

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      await fetchAll();
    } catch (err) {
      if (err.status === 401) {
        clearApiKey();
        setNeedsApiKey(true);
      } else {
        setError('Could not connect to the backend server. Is Spring Boot running on port 8080?');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!needsApiKey) loadData();
  }, [needsApiKey, reportPageNumber, repoPageNumber, filters]);

  const handleToggleSelect = (suggestion, report) => {
    const group = `${report.repositoryName.toLowerCase()}#${report.pullRequestNumber ?? ''}`;
    const next = new Set(selectedIds);
    if (next.has(suggestion.id)) {
      next.delete(suggestion.id);
    } else {
      if (next.size > 0 && group !== selectionGroup) {
        alert('Fixes can only be combined for the same repository (and the same pull request).');
        return;
      }
      next.add(suggestion.id);
    }
    setSelectedIds(next);
    setSelectionGroup(next.size > 0 ? group : null);
  };

  const clearSelection = () => {
    setSelectedIds(new Set());
    setSelectionGroup(null);
  };

  const handleApproveSelected = async () => {
    setApprovingBatch(true);
    try {
      const result = await approveSuggestions([...selectedIds]);
      setNotice({
        text: `${result.approved.length} suggestion(s) approved${result.prUrl ? ' and applied in one change' : ''}.`,
        url: result.prUrl
      });
      clearSelection();
      await fetchAll();
    } catch (err) {
      alert(err.message);
    } finally {
      setApprovingBatch(false);
    }
  };

  const handleFiltersChange = (newFilters) => {
    setFilters(newFilters);
    setReportPageNumber(0);
  };

  // Scans run in the background on the server; refresh every few seconds until they finish
  const anyScanning = repos.some((r) => r.status === 'SCANNING');
  useEffect(() => {
    if (!anyScanning || needsApiKey) return undefined;
    const timer = setInterval(async () => {
      try {
        await fetchAll();
      } catch {
        // Keep the current data; the next tick or a manual refresh will retry
      }
    }, 5000);
    return () => clearInterval(timer);
  }, [anyScanning, needsApiKey, reportPageNumber, repoPageNumber, filters]);

  const handleSaveApiKey = (e) => {
    e.preventDefault();
    if (!apiKeyInput.trim()) return;
    setApiKey(apiKeyInput.trim());
    setApiKeyInput('');
    setNeedsApiKey(false);
  };

  const handleStatusChange = (suggestionId, newStatus) => {
    setReportsPage((prev) => ({
      ...prev,
      content: prev.content.map((report) => ({
        ...report,
        suggestions: report.suggestions?.map((s) =>
          s.id === suggestionId ? { ...s, status: newStatus } : s
        ) || [],
      }))
    }));
    fetchSummary().then(setSummary).catch(() => {});
  };

  const handleRegisterRepo = async (e) => {
    e.preventDefault();
    if (!repoUrl || !repoName) return;
    
    setRegistering(true);
    try {
      await registerRepo(repoUrl, repoName);
      await fetchAll();
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
    setReposPage((prev) => ({
      ...prev,
      content: prev.content.map((r) => (r.id === updatedRepo.id ? updatedRepo : r))
    }));
  };

  const handleRepoDeleted = (deletedId) => {
    setReposPage((prev) => ({ ...prev, content: prev.content.filter((r) => r.id !== deletedId) }));
    fetchAll().catch(() => {});
  };

  // Counts of suggestions awaiting review, across all pages
  const pendingCount = summary.pending;
  const criticalCount = summary.critical;
  const breakingCount = summary.breaking;

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header />

      <main className="container" style={{ flex: 1, width: '100%', padding: '32px 16px' }}>
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
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px', flexWrap: 'wrap' }}>
              <h1 style={{ fontSize: '1.75rem', margin: 0 }}>Review Dashboard</h1>
              <span style={{
                background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.2), rgba(168, 85, 247, 0.2))',
                border: '1px solid rgba(99, 102, 241, 0.4)',
                color: '#c084fc',
                fontSize: '0.72rem',
                fontWeight: 700,
                padding: '2px 8px',
                borderRadius: '12px',
                letterSpacing: '0.04em'
              }}>
                SEMANTIC & CONTRACT AI
              </span>
            </div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              Review and apply AI-suggested documentation & API contract fixes triggered by code changes.
            </p>
          </div>

          <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
            <div className="glass-panel" style={{ padding: '6px 14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Layers size={16} color="var(--accent-primary)" />
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Pending Reviews:</span>
              <span style={{ fontWeight: 700, color: pendingCount > 0 ? 'var(--warning)' : 'var(--success)' }}>
                {pendingCount}
              </span>
            </div>

            {breakingCount > 0 && (
              <div className="glass-panel" style={{ padding: '6px 14px', display: 'flex', alignItems: 'center', gap: '8px', borderColor: 'rgba(239, 68, 68, 0.4)', background: 'rgba(239, 68, 68, 0.1)' }}>
                <span style={{ fontSize: '0.8rem', color: '#fca5a5', fontWeight: 600 }}>⚠️ Breaking Changes:</span>
                <span style={{ fontWeight: 700, color: '#ef4444' }}>
                  {breakingCount}
                </span>
              </div>
            )}

            {criticalCount > 0 && breakingCount === 0 && (
              <div className="glass-panel" style={{ padding: '6px 14px', display: 'flex', alignItems: 'center', gap: '8px', borderColor: 'rgba(239, 68, 68, 0.3)' }}>
                <AlertTriangle size={16} color="#ef4444" />
                <span style={{ fontSize: '0.8rem', color: '#fca5a5' }}>Critical:</span>
                <span style={{ fontWeight: 700, color: '#ef4444' }}>
                  {criticalCount}
                </span>
              </div>
            )}

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
          marginBottom: '24px',
          overflowX: 'auto'
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
            <span>Drift Reports ({reportsPage.totalElements})</span>
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
            <span>Tracked Repositories ({reposPage.totalElements})</span>
          </button>

          <button
            onClick={() => setActiveTab('activity')}
            style={{
              padding: '12px 20px',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'activity' ? '2px solid var(--accent-primary)' : '2px solid transparent',
              color: activeTab === 'activity' ? 'white' : 'var(--text-muted)',
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              transition: 'all 0.2s ease'
            }}
          >
            <Activity size={16} color={activeTab === 'activity' ? 'var(--accent-primary)' : 'currentColor'} />
            <span>Activity</span>
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

        {notice && (
          <div style={{
            background: 'var(--success-bg)', border: '1px solid rgba(16, 185, 129, 0.3)', color: 'var(--success)',
            padding: '12px 16px', borderRadius: '8px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '12px'
          }}>
            <Check size={16} />
            <span style={{ flex: 1 }}>
              {notice.text}{' '}
              {notice.url && <a href={notice.url} target="_blank" rel="noopener noreferrer" style={{ color: 'inherit', fontWeight: 600 }}>View on GitHub</a>}
            </span>
            <button className="btn btn-outline" onClick={() => setNotice(null)} aria-label="Dismiss" style={{ padding: '4px' }}>
              <X size={14} />
            </button>
          </div>
        )}

        {needsApiKey ? (
          <div className="glass-panel animate-fade-in" style={{ maxWidth: '480px', margin: '48px auto', padding: '24px' }}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <KeyRound size={20} color="var(--accent-primary)" /> Enter API Key
            </h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '16px' }}>
              The dashboard needs the DOCDRIFT_API_KEY configured on the backend. It is stored only in this browser.
            </p>
            <form onSubmit={handleSaveApiKey} style={{ display: 'flex', gap: '12px' }}>
              <input
                type="password"
                placeholder="API key"
                value={apiKeyInput}
                onChange={(e) => setApiKeyInput(e.target.value)}
                required
                style={{
                  flex: 1,
                  padding: '10px 12px',
                  borderRadius: '8px',
                  background: 'rgba(0, 0, 0, 0.3)',
                  border: '1px solid var(--border-glass)',
                  color: 'white',
                  fontFamily: 'var(--font-sans)',
                  outline: 'none'
                }}
              />
              <button type="submit" className="btn btn-primary">Connect</button>
            </form>
          </div>
        ) : loading && !error ? (
          <div style={{ padding: '64px 0', textAlign: 'center', color: 'var(--text-muted)' }}>
            <RefreshCw size={32} style={{ margin: '0 auto 16px', animation: 'spin 1s linear infinite' }} />
            <p>Loading dashboard data...</p>
          </div>
        ) : activeTab === 'reports' ? (
          <>
            <ReportFilters filters={filters} repoNames={repoNames} onChange={handleFiltersChange} />
            <ReportList
              reports={reports}
              onStatusChange={handleStatusChange}
              filtered={Boolean(filters.repository || filters.severity || filters.status)}
              selectedIds={selectedIds}
              onToggleSelect={handleToggleSelect}
            />
            <Pagination {...reportsPage} onPageChange={setReportPageNumber} />
          </>
        ) : activeTab === 'activity' ? (
          <ActivityList repoNames={repoNames} />
        ) : (
          <>
            <RepoList
              repos={repos}
              onRepoUpdated={handleRepoUpdated}
              onRepoDeleted={handleRepoDeleted}
            />
            <Pagination {...reposPage} onPageChange={setRepoPageNumber} />
          </>
        )}
      </main>

      {/* Combined-fix bar, shown while suggestions are selected */}
      {selectedIds.size > 0 && activeTab === 'reports' && (
        <div className="glass-panel" style={{
          position: 'fixed', bottom: '24px', left: '50%', transform: 'translateX(-50%)', zIndex: 50,
          padding: '12px 16px', display: 'flex', alignItems: 'center', gap: '12px',
          background: 'var(--bg-secondary)', border: '1px solid var(--border-focus)', maxWidth: 'calc(100vw - 32px)'
        }}>
          <span style={{ fontSize: '0.85rem' }}>{selectedIds.size} selected</span>
          <button className="btn btn-outline" onClick={clearSelection}>Clear</button>
          <button className="btn btn-primary" onClick={handleApproveSelected} disabled={approvingBatch}>
            <Check size={16} /> {approvingBatch ? 'Applying...' : 'Approve selected as one change'}
          </button>
        </div>
      )}

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
