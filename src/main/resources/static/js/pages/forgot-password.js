// Forgotten password: email a single-use link that opens /reset-password, where a new password is chosen.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { navigate } from '../router.js';
import { homeFor } from '../format.js';
import { session } from '../session.js';
import { toast } from '../ui.js';
import { fieldError, setBusy, setFieldError, setTitle } from '../components.js';
import { emailProblem } from '../otp.js';
import { infoIcon, mailIcon } from '../icons.js';

const RESEND_SECONDS = 60;

export default function forgotPassword(root, { query, alive }) {
    setTitle('Forgot password');
    if (session.user) {
        navigate(homeFor(session.user), { replace: true });
        return undefined;
    }

    let email = query.get('email') || '';
    let timer = null;

    const paintForm = () => {
        render(root, html`
            <h1 class="auth-title">Forgot your password?</h1>
            <p class="auth-sub">Enter your account's email and we will send you a link to choose a new password.</p>
            <form id="forgot-form" novalidate>
                <div class="form-group">
                    <label class="form-label" for="email">Email</label>
                    <input type="email" class="form-input" id="email" autocomplete="email" inputmode="email" maxlength="254" value="${email}">
                    ${fieldError('email-error')}
                </div>
                <button type="submit" class="btn btn-primary btn-lg btn-block" data-send>Send reset link</button>
            </form>
            <p class="auth-alt"><a href="/login">Back to sign in</a></p>`);
        root.querySelector('#email').focus();
    };

    const paintSent = (message, minutes) => {
        render(root, html`
            <div class="reset-sent" role="status">
                <span class="reset-sent-icon" aria-hidden="true">${mailIcon(22)}</span>
                <h1 class="auth-title">Check your email</h1>
                <p class="auth-sub">${message} The link expires in ${minutes} minutes.</p>
            </div>
            <p class="dev-hint form-hint" data-dev-hint hidden>${infoIcon(12)} Development mode: no email is sent. The link is printed in the server console.</p>
            <p class="reset-resend">Didn't get it? Check your spam folder, or
                <button type="button" class="link-btn" data-resend disabled></button></p>
            <p class="auth-alt"><a href="/login">Back to sign in</a></p>`);
        api('auth/otp/config').then(c => {
            if (alive() && c?.devMode) root.querySelector('[data-dev-hint]').hidden = false;
        }).catch(() => {});
        startCooldown();
    };

    // "Send it again" unlocks after a minute, so a slow inbox doesn't lead to a burst of links
    const startCooldown = () => {
        const until = Date.now() + RESEND_SECONDS * 1000;
        clearInterval(timer);
        const tick = () => {
            const button = root.querySelector('[data-resend]');
            if (!button) { clearInterval(timer); return; }
            const left = Math.ceil((until - Date.now()) / 1000);
            button.disabled = left > 0;
            button.textContent = left > 0 ? `send it again in ${left}s` : 'send it again';
            if (left <= 0) clearInterval(timer);
        };
        tick();
        timer = setInterval(tick, 500);
    };

    const send = async button => {
        const problem = emailProblem(email);
        if (problem) {
            setFieldError(root, 'email', problem);
            root.querySelector('#email')?.focus();
            return;
        }
        const label = button.textContent;
        setBusy(button, true, label, 'Sending…', { onAccent: button.matches('.btn-primary') });
        try {
            const r = await api('auth/password/forgot', { method: 'POST', body: { email: email.trim() } });
            if (!alive()) return;
            if (root.querySelector('.reset-sent')) {
                toast('We sent another link. Only the newest link works.');
                setBusy(button, false, label);
                startCooldown();
            } else {
                paintSent(r.message, r.expiresInMinutes);
            }
        } catch (err) {
            if (!alive()) return;
            if (root.querySelector('#email')) {
                setFieldError(root, 'email', err.message);
            } else {
                toast(err.message, { error: true });
            }
            setBusy(button, false, label);
        }
    };

    paintForm();

    root.addEventListener('input', e => {
        if (e.target.id !== 'email') return;
        email = e.target.value;
        setFieldError(root, 'email', null);
    });
    root.addEventListener('submit', e => {
        e.preventDefault();
        send(root.querySelector('[data-send]'));
    });
    root.addEventListener('click', e => {
        const resend = e.target.closest('[data-resend]');
        if (resend && !resend.disabled) send(resend);
    });

    return () => clearInterval(timer);
}
