const BASE_URL = 'http://localhost:8080/api';

export async function fetchReports() {
    try {
        const response = await fetch(`${BASE_URL}/reports`);
        if (!response.ok) {
            throw new Error('Failed to fetch reports');
        }
        return await response.json();
    } catch (error) {
        console.error("API Error:", error);
        throw error;
    }
}

export async function approveSuggestion(id) {
    const response = await fetch(`${BASE_URL}/suggestions/${id}/approve`, {
        method: 'POST'
    });
    if (!response.ok) {
        throw new Error('Failed to approve suggestion');
    }
    return await response.json();
}

export async function rejectSuggestion(id) {
    const response = await fetch(`${BASE_URL}/suggestions/${id}/reject`, {
        method: 'POST'
    });
    if (!response.ok) {
        throw new Error('Failed to reject suggestion');
    }
    return await response.json();
}

export async function registerRepo(url, name) {
    const response = await fetch(`${BASE_URL}/repos`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ url, name })
    });
    if (!response.ok) {
        throw new Error('Failed to register repository');
    }
    return await response.json();
}
