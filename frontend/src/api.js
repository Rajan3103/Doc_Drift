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

export async function fetchReports() {
    return request('/reports', 'Failed to fetch reports');
}

export async function approveSuggestion(id) {
    return request(`/suggestions/${id}/approve`, 'Failed to approve suggestion', { method: 'POST' });
}

export async function rejectSuggestion(id) {
    return request(`/suggestions/${id}/reject`, 'Failed to reject suggestion', { method: 'POST' });
}

export async function fetchRepos() {
    return request('/repos', 'Failed to fetch repositories');
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
