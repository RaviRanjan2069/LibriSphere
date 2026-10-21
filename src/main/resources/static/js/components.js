// Small shared building blocks used across pages. Each returns an html`` fragment.
import { html, render } from './dom.js';
import { alertIcon, boxIcon, checkIcon, infoIcon } from './icons.js';

/** Book cover with a typeset fallback when the image is missing, broken, or a 1x1 placeholder (see watchImages). */
export function cover(book, size = '') {
    return html`
        <div class="cover ${size} ${book.coverUrl ? '' : 'is-missing'}">
            ${book.coverUrl ? html`<img src="${book.coverUrl}" alt="Cover of ${book.title}" loading="lazy" decoding="async" data-cover>` : ''}
            <div class="cover-fallback" aria-hidden="true">
                <span class="cover-fallback-title">${book.title || 'Book title'}</span>
                <span class="cover-fallback-author">${book.author || 'Author'}</span>
            </div>
        </div>`;
}

const initials = name => {
    const parts = (name || '?').trim().split(/[\s_.-]+/).filter(Boolean);
    return ((parts[0]?.[0] || '?') + (parts.length > 1 ? parts[1][0] : '')).toUpperCase();
};
// A stable colour per name, so default avatars are easy to tell apart
const hue = name => [...(name || '')].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 150);

/** A round avatar. Shows `url` if it loads, otherwise initials on a colour derived from `name`. */
export function avatar({ url, name, size = 'md', cls = '' }) {
    if (url) {
        return html`<span class="avatar avatar-${size} ${cls}" aria-hidden="true" data-initials="${initials(name)}" data-hue="${hue(name)}"><img src="${url}" alt="" loading="lazy" decoding="async" data-avatar></span>`;
    }
    return html`<span class="avatar avatar-${size} avatar-default ${cls}" style="--avatar-hue: ${hue(name)}" aria-hidden="true">${initials(name)}</span>`;
}

/** Swaps broken covers and avatars for their fallbacks. Load/error events don't bubble, so listen in the capture phase. */
export function watchImages() {
    document.addEventListener('error', e => {
        const img = e.target;
        if (!(img instanceof HTMLImageElement)) return;
        if (img.matches('[data-cover]')) img.parentElement.classList.add('is-missing');
        if (img.matches('[data-avatar]')) {
            const span = img.parentElement;
            span.classList.add('avatar-default');
            span.style.setProperty('--avatar-hue', span.dataset.hue);
            span.textContent = span.dataset.initials;
        }
    }, true);
    document.addEventListener('load', e => {
        const img = e.target;
        if (img instanceof HTMLImageElement && img.matches('[data-cover]')) {
            img.parentElement.classList.toggle('is-missing', img.naturalWidth < 10);
        }
    }, true);
}

export const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const AVATAR_MAX_BYTES = 2 * 1024 * 1024;

/** Checks type and size in the browser for quick feedback; the server re-checks the actual file content. */
export function avatarProblem(file) {
    if (!AVATAR_TYPES.includes(file.type)) return 'Choose a JPG, PNG or WebP image.';
    if (file.size > AVATAR_MAX_BYTES) return 'Profile pictures must be 2 MB or smaller.';
    return null;
}

/** Object URLs for previewing a chosen File; set(file) revokes the previous one. */
export function previewUrl() {
    let url = null;
    return {
        get url() { return url; },
        set(file) {
            if (url) URL.revokeObjectURL(url);
            url = file ? URL.createObjectURL(file) : null;
        },
    };
}

export const statusBadge = status => {
    const s = (status || '').toLowerCase();
    return html`<span class="status-badge status-${s}">${s}</span>`;
};

export function alert(type, text) {
    const icon = type === 'success' ? checkIcon() : type === 'info' ? infoIcon() : alertIcon();
    const cls = type === 'success' ? 'alert-success' : type === 'info' ? 'alert-info' : 'alert-danger';
    return html`<div class="alert ${cls}" role="${type === 'error' ? 'alert' : 'status'}">${icon}<span>${text}</span></div>`;
}

/** Renders a { type, text } flash message, if any. */
export const flash = message => (message ? alert(message.type, message.text) : '');

/** Shows a flash message in the page's [data-flash] slot (or clears it with null). */
export function showFlash(root, message) {
    render(root.querySelector('[data-flash]'), flash(message));
}

/** An inline validation message under a form field, hidden until setFieldError fills it. */
export const fieldError = id => html`<p class="field-error" id="${id}" role="alert" hidden></p>`;

/**
 * Shows or clears the error for field `name`: fills <p id="name-error">, links it to <input id="name">,
 * and hides the field's hint (<p id="name-hint">) while an error is shown.
 */
export function setFieldError(root, name, message) {
    const p = root.querySelector(`#${name}-error`);
    const input = root.querySelector(`#${name}`);
    const hint = root.querySelector(`#${name}-hint`);
    if (p) { p.textContent = message || ''; p.hidden = !message; }
    if (hint) hint.hidden = Boolean(message);
    if (input) {
        if (message) {
            input.setAttribute('aria-invalid', 'true');
            input.setAttribute('aria-describedby', `${name}-error`);
        } else {
            input.removeAttribute('aria-invalid');
            input.removeAttribute('aria-describedby');
        }
    }
}

/** A password input with a Show/Hide button inside it; call bindPasswordToggles(root) once to make the buttons work. */
export const passwordInput = (id, autocomplete) => html`
    <div class="password-field">
        <input type="password" class="form-input" id="${id}" autocomplete="${autocomplete}">
        <button type="button" class="password-toggle" data-toggle-password="${id}" aria-controls="${id}" aria-pressed="false">Show</button>
    </div>`;

export function bindPasswordToggles(root) {
    root.addEventListener('click', e => {
        const toggle = e.target.closest('[data-toggle-password]');
        if (!toggle) return;
        const input = root.querySelector(`#${toggle.dataset.togglePassword}`);
        const show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        toggle.textContent = show ? 'Hide' : 'Show';
        toggle.setAttribute('aria-pressed', String(show));
        input.focus();
    });
}

/** Puts a button into or out of its busy state: disabled, with a spinner and a "Saving…" style label. */
export function setBusy(button, busy, label, busyLabel, { onAccent = true } = {}) {
    button.disabled = busy;
    render(button, busy
        ? html`<span class="spinner spinner-btn ${onAccent ? 'spinner-on-accent' : ''}" aria-hidden="true"></span>${busyLabel}`
        : label);
}

export function emptyState({ icon = boxIcon(24), title, text, actions = '' }) {
    return html`
        <div class="empty-state">
            <div class="empty-icon">${icon}</div>
            ${title ? html`<h3>${title}</h3>` : ''}
            ${text ? html`<p>${text}</p>` : ''}
            ${actions}
        </div>`;
}

export const loading = (label = 'Loading…') => html`<div class="loading-state" role="status"><span class="spinner"></span>${label}</div>`;

export function statCard({ label, value, hint, icon, variant = '', hintClass = '' }) {
    return html`
        <div class="card stat-card">
            <div class="stat-top"><span class="stat-label">${label}</span><span class="stat-icon ${variant}">${icon}</span></div>
            <span class="stat-value">${value}</span>
            <span class="stat-hint ${hintClass}">${hint}</span>
        </div>`;
}

/** Sets the browser tab title. */
export function setTitle(title) {
    document.title = title ? `${title} · LibriSphere` : 'LibriSphere';
}
