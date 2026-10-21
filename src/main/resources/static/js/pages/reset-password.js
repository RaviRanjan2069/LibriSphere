// Opened from the "reset your password" email: checks the link, then sets a new password.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { navigate } from '../router.js';
import { homeFor } from '../format.js';
import { session } from '../session.js';
import { bindPasswordToggles, fieldError, loading, passwordInput, setBusy, setFieldError, setTitle } from '../components.js';
import { alertIcon } from '../icons.js';

const FIELDS = ['newPassword', 'confirmPassword'];

export default function resetPassword(root, { query, alive }) {
    setTitle('Choose a new password');
    if (session.user) {
        navigate(homeFor(session.user), { replace: true });
        return;
    }

    // Keep the token in memory only: take it out of the address bar and browser history
    const token = query.get('token') || '';
    history.replaceState(null, '', '/reset-password');

    const paintInvalid = () => render(root, html`
        <div class="reset-sent" role="alert">
            <span class="reset-sent-icon is-error" aria-hidden="true">${alertIcon(22)}</span>
            <h1 class="auth-title">This link can't be used</h1>
            <p class="auth-sub">Reset links work once and expire after a short time. Ask for a new one and use the newest email.</p>
        </div>
        <a href="/forgot-password" class="btn btn-primary btn-lg btn-block">Request a new link</a>
        <p class="auth-alt"><a href="/login">Back to sign in</a></p>`);

    const paintForm = () => {
        render(root, html`
            <h1 class="auth-title">Choose a new password</h1>
            <p class="auth-sub">Use at least 8 characters. You'll sign in with it from now on.</p>
            <form id="reset-form" novalidate>
                <div class="form-group">
                    <label class="form-label" for="newPassword">New password</label>
                    ${passwordInput('newPassword', 'new-password')}
                    ${fieldError('newPassword-error')}
                </div>
                <div class="form-group">
                    <label class="form-label" for="confirmPassword">Confirm new password</label>
                    ${passwordInput('confirmPassword', 'new-password')}
                    ${fieldError('confirmPassword-error')}
                </div>
                <button type="submit" class="btn btn-primary btn-lg btn-block" data-reset>Reset password</button>
            </form>
            <p class="auth-alt"><a href="/login">Back to sign in</a></p>`);
        root.querySelector('#newPassword').focus();
    };

    render(root, loading('Checking your link…'));
    if (!token) {
        paintInvalid();
    } else {
        api('auth/password/reset/check', { method: 'POST', body: { token } })
            .then(() => { if (alive()) paintForm(); })
            .catch(() => { if (alive()) paintInvalid(); });
    }

    bindPasswordToggles(root);
    root.addEventListener('input', e => {
        if (FIELDS.includes(e.target.id)) setFieldError(root, e.target.id, null);
    });

    root.addEventListener('submit', async e => {
        e.preventDefault();
        const button = root.querySelector('[data-reset]');
        if (!button || button.disabled) return;
        const newPassword = root.querySelector('#newPassword').value;
        const confirmPassword = root.querySelector('#confirmPassword').value;
        const problems = {
            newPassword: newPassword.length < 8 ? 'Use at least 8 characters.' : newPassword.length > 72 ? 'Use at most 72 characters.' : null,
            confirmPassword: confirmPassword !== newPassword ? "The passwords don't match." : null,
        };
        FIELDS.forEach(name => setFieldError(root, name, problems[name]));
        const first = FIELDS.find(name => problems[name]);
        if (first) {
            root.querySelector(`#${first}`).focus();
            return;
        }
        setBusy(button, true, 'Reset password', 'Saving…');
        try {
            const r = await api('auth/password/reset', { method: 'POST', body: { token, newPassword, confirmPassword } });
            navigate('/login', { replace: true, flash: { type: 'success', text: r.message } });
        } catch (err) {
            if (!alive()) return;
            if (err.field === 'token') {
                paintInvalid();
                return;
            }
            if (FIELDS.includes(err.field)) setFieldError(root, err.field, err.message);
            setBusy(button, false, 'Reset password');
        }
    });
}
