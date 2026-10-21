// Helpers for building pages from HTML strings.
// Values interpolated into html`` are escaped, so text from the server can never inject markup.

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

class SafeHtml {
    constructor(value) { this.value = value; }
    toString() { return this.value; }
}

/** Marks a string as trusted HTML (fixed markup such as icons) so it is inserted without escaping. */
export const raw = value => new SafeHtml(String(value));

function toHtml(value) {
    if (value == null || value === false || value === true) return '';
    if (value instanceof SafeHtml) return value.value;
    if (Array.isArray(value)) return value.map(toHtml).join('');
    return String(value).replace(/[&<>"']/g, c => ESCAPES[c]);
}

/**
 * Tagged template for markup: html`<p>${text}</p>`.
 * Strings and numbers are escaped, arrays are joined, null/undefined/true/false render nothing,
 * and nested html`` results are inserted as-is.
 */
export function html(strings, ...values) {
    let out = strings[0];
    for (let i = 0; i < values.length; i++) out += toHtml(values[i]) + strings[i + 1];
    return new SafeHtml(out);
}

/** Replaces the contents of `el` with the given html`` result (or escaped text). */
export function render(el, content) {
    el.innerHTML = toHtml(content);
}

const lastRendered = new WeakMap();

/** Same as render(), but skips the DOM write when nothing changed (avoids re-announcing live regions). */
export function renderIfChanged(el, content) {
    const markup = toHtml(content);
    if (lastRendered.get(el) !== markup) {
        el.innerHTML = markup;
        lastRendered.set(el, markup);
    }
}

export function setText(el, text) {
    if (el.textContent !== text) el.textContent = text;
}
