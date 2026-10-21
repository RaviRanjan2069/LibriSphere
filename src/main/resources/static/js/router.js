// Client-side routing with the History API.
// Same-site link clicks are handled here instead of reloading the page; the server answers deep links
// with index.html (keep the route list in sync with SPA_ROUTES in WebConfig).

const routes = [];
let onRoute = null;
let navId = 0;

/** Registers a page for a path such as "/book/:id"; "*" matches anything (register it last). */
export function addRoute(path, page, options = {}) {
    const keys = [];
    const source = path === '*' ? '.*' : path.replace(/:(\w+)/g, (_, key) => { keys.push(key); return '([^/]+)'; });
    routes.push({ pattern: new RegExp(`^${source}$`), keys, page, options });
}

/**
 * Goes to `to` ("/orders?tab=all"). `flash` is a { type, text } message for the next page,
 * kept in the history entry like a redirect message.
 */
export function navigate(to, { replace = false, flash = null } = {}) {
    history[replace ? 'replaceState' : 'pushState']({ flash }, '', to);
    dispatch();
}

/** Updates the query string in place (for tabs) without re-rendering the page. */
export function replaceQuery(params) {
    const qs = new URLSearchParams(params).toString();
    history.replaceState(null, '', location.pathname + (qs ? `?${qs}` : ''));
}

function dispatch() {
    const id = ++navId;
    const url = new URL(location.href);
    const path = url.pathname.length > 1 ? url.pathname.replace(/\/+$/, '') : url.pathname;
    for (const route of routes) {
        const match = route.pattern.exec(path);
        if (!match) continue;
        const params = Object.fromEntries(route.keys.map((key, i) => [key, decodeURIComponent(match[i + 1])]));
        onRoute(route, {
            params,
            query: url.searchParams,
            flash: history.state?.flash || null,
            /** False once the user has moved on to another page; check it before using async results. */
            alive: () => id === navId,
        });
        return;
    }
}

/** Starts routing: renders the current URL and handles back/forward and link clicks. */
export function startRouter(handler) {
    onRoute = handler;
    window.addEventListener('popstate', dispatch);
    document.addEventListener('click', e => {
        if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        const link = e.target.closest('a[href]');
        if (!link || link.target || link.hasAttribute('download')) return;
        const url = new URL(link.href, location.href);
        if (url.origin !== location.origin || /^\/(api|media)\//.test(url.pathname)) return;
        // In-page anchors such as "#catalog" scroll natively
        if (url.hash && url.pathname === location.pathname && url.search === location.search) return;
        e.preventDefault();
        navigate(url.pathname + url.search + url.hash);
    });
    dispatch();
}
