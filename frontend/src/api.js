const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

// The API key is entered by the user in the dashboard and kept in this browser only,
// so it is never baked into the public JavaScript bundle.
const API_KEY_STORAGE = 'docdrift_api_key';

export function getApiKey() {
    try {
        return localStorage.getItem(API_KEY_STORAGE) || '';
    } catch {
        return '';
    }
}

export function setApiKey(key) {
    try {
        localStorage.setItem(API_KEY_STORAGE, key);
    } catch {
        // Storage unavailable (private mode); the key will need re-entering next visit
    }
}

export function clearApiKey() {
    try {
        localStorage.removeItem(API_KEY_STORAGE);
    } catch {
        // ignore
    }
}

export class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

async function request(path, fallbackMessage, options = {}) {
    const response = await fetch(`${BASE_URL}${path}`, {
        ...options,
        headers: {
            ...options.headers,
            'X-API-Key': getApiKey()
        }
    });
    if (!response.ok) {
        // The backend returns {"error": "..."} for failures it can explain
        const body = await response.json().catch(() => null);
        throw new ApiError(body?.error || fallbackMessage, response.status);
    }
    return response.status === 204 ? null : await response.json();
}

// Builds "?a=1&b=2" from an object, leaving out empty values
function query(params) {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') search.set(key, value);
    });
    const text = search.toString();
    return text ? `?${text}` : '';
}

// Returns a page: { content, page, size, totalElements, totalPages }
export async function fetchReports({ page = 0, size = 20, repository, severity, status } = {}) {
    return request(`/reports${query({ page, size, repository, severity, status })}`, 'Failed to fetch reports');
}

// Returns { pending, critical, breaking } counts of suggestions awaiting review
export async function fetchSummary() {
    return request('/reports/summary', 'Failed to fetch review summary');
}

export async function approveSuggestion(id) {
    return request(`/suggestions/${id}/approve`, 'Failed to approve suggestion', { method: 'POST' });
}

export async function rejectSuggestion(id) {
    return request(`/suggestions/${id}/reject`, 'Failed to reject suggestion', { method: 'POST' });
}

// Returns a page: { content, page, size, totalElements, totalPages }
export async function fetchRepos({ page = 0, size = 20 } = {}) {
    return request(`/repos${query({ page, size })}`, 'Failed to fetch repositories');
}

export async function registerRepo(url, name) {
    return request('/repos', 'Failed to register repository', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ url, name })
    });
}

export async function deleteRepo(id) {
    await request(`/repos/${id}`, 'Failed to untrack repository', { method: 'DELETE' });
    return true;
}

export async function scanRepo(id) {
    return request(`/repos/${id}/scan`, 'Failed to trigger scan', { method: 'POST' });
}

// Saves a reviewer's edit of the suggested text (only while the suggestion is pending)
export async function editSuggestion(id, suggestedText) {
    return request(`/suggestions/${id}`, 'Failed to save the edit', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ suggestedText })
    });
}

// Approves several suggestions at once; their fixes go into one pull request. Returns { approved, prUrl }
export async function approveSuggestions(ids) {
    return request('/suggestions/approve', 'Failed to approve the selected suggestions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ids })
    });
}

// Saves a repository's settings (doc paths, branches, thresholds, alerts)
export async function updateRepoSettings(id, settings) {
    return request(`/repos/${id}/settings`, 'Failed to save settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(settings)
    });
}

// Returns [{ date, critical, high, medium, low }] for the last `days` days
export async function fetchRepoHistory(id, days = 30) {
    return request(`/repos/${id}/history${query({ days })}`, 'Failed to load drift history');
}

// Returns a page of webhook deliveries and scans, newest first
export async function fetchEvents({ page = 0, size = 20, repository } = {}) {
    return request(`/events${query({ page, size, repository })}`, 'Failed to load activity');
}
