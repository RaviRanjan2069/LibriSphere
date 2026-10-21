// Thin wrapper around fetch for the Spring Boot JSON API.

export class ApiError extends Error {
    constructor(status, message, field = null) {
        super(message);
        this.status = status;
        this.field = field; // form field the server says the error belongs to, if any
    }
}

/**
 * Calls /api/<path>. `body` may be a plain object (sent as JSON) or FormData (sent as multipart).
 * Throws ApiError carrying the server's {"error", "field"} on failure.
 */
export async function api(path, { method = 'GET', body } = {}) {
    // The server rejects state-changing requests without this header (CSRF protection)
    const options = { method, headers: { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' }, credentials: 'same-origin' };
    if (body instanceof FormData) {
        options.body = body; // the browser sets the multipart boundary
    } else if (body !== undefined) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }
    let response;
    try {
        response = await fetch('/api/' + path.replace(/^\//, ''), options);
    } catch {
        throw new ApiError(0, 'Could not reach the server. Check your connection and try again.');
    }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new ApiError(response.status, data.error || `Request failed (${response.status})`, data.field || null);
    }
    return data;
}
