// Toast notifications and a promise-based confirmation dialog, available to every page.
import { html, render } from './dom.js';
import { alertIcon } from './icons.js';

let toastContainer = null;

/** Shows a short message in the corner. `action` is an optional { to, label } link. */
export function toast(message, { error = false, action } = {}) {
    if (!toastContainer) {
        toastContainer = document.createElement('div');
        toastContainer.className = 'toast-container';
        toastContainer.setAttribute('role', 'status');
        toastContainer.setAttribute('aria-live', 'polite');
        document.body.append(toastContainer);
    }
    const el = document.createElement('div');
    el.className = `toast ${error ? 'toast-error' : 'toast-success'}`;
    render(el, html`
        <span class="toast-icon">${error ? '!' : '✓'}</span>
        <span>${message}</span>
        ${action ? html`<a href="${action.to}">${action.label}</a>` : ''}`);
    toastContainer.append(el);
    setTimeout(() => el.classList.add('leaving'), 3500);
    setTimeout(() => el.remove(), 3700);
}

/** Asks the user to confirm a destructive action. Resolves true if they confirm. */
export function confirm({ title, text, action = 'Confirm' }) {
    return new Promise(resolve => {
        const dialog = document.createElement('dialog');
        dialog.className = 'dialog';
        dialog.setAttribute('aria-labelledby', 'confirm-title');
        render(dialog, html`
            <form method="dialog">
                <div class="dialog-body">
                    <div class="dialog-icon" aria-hidden="true">${alertIcon()}</div>
                    <h2 class="dialog-title" id="confirm-title">${title}</h2>
                    <p class="dialog-text">${text}</p>
                </div>
                <div class="dialog-actions">
                    <button value="cancel" class="btn btn-secondary">Keep it</button>
                    <button value="confirm" class="btn btn-danger-solid">${action}</button>
                </div>
            </form>`);
        dialog.addEventListener('close', () => {
            resolve(dialog.returnValue === 'confirm');
            dialog.remove();
        });
        document.body.append(dialog);
        dialog.showModal();
        dialog.querySelector('button[value="cancel"]').focus();
    });
}
