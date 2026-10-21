// Email + one-time code: "Send code" with a resend countdown, code entry and verification.
// Shared by sign-up (verify the address), email-code sign-in, and the account page (verify an existing address).
//
// An EmailOtp holds the state. Pages render its markup with emailOtpFields() / codePanel() and call
// bindOtp(root, otp); the elements are then kept up to date through their data-otp-* attributes,
// so typing in a field never re-renders (and never loses focus).
import { api } from './api.js';
import { html, renderIfChanged, setText } from './dom.js';
import { toast } from './ui.js';
import { checkIcon, infoIcon } from './icons.js';

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/;

/** Quick check before calling the server, which does the real validation. */
export function emailProblem(email) {
    const value = email.trim();
    if (!value) return 'Enter your email address.';
    if (!EMAIL.test(value)) return 'Enter a valid email address, like name@example.com.';
    return null;
}

let configPromise = null;
/** Server settings for codes: { devMode, codeLength, resendSeconds, expiresSeconds }, or null if unavailable. */
function loadOtpConfig() {
    configPromise = configPromise || api('auth/otp/config').catch(() => { configPromise = null; return null; });
    return configPromise;
}

const fmtSeconds = s => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

/** Label for the send button: Send code → Resend in 0:42 → Resend code. */
export function sendLabel(otp, first = 'Send code') {
    if (otp.busy === 'send') return 'Sending…';
    if (otp.stage === 'idle') return first;
    return otp.left > 0 ? `Resend in ${fmtSeconds(otp.left)}` : 'Resend code';
}

/**
 * State and actions for one email-verification flow.
 * purpose:  REGISTER | LOGIN | VERIFY_EMAIL | CHANGE_EMAIL (tells the server which kind of code to send; may be changed later)
 * onVerify: async ({ email, code }) => any; throws ApiError on a bad code
 */
export class EmailOtp {
    constructor({ purpose, onVerify, initialEmail = '' }) {
        this.purpose = purpose;
        this.onVerify = onVerify;
        this.email = initialEmail;
        this.code = '';
        this.stage = 'idle'; // idle -> sent -> verified
        this.busy = null; // 'send' | 'verify' | null
        this.errors = {};
        this.config = null;
        this.until = 0;
        this.timer = null;
        this.inFlight = false;
        this.roots = new Set();
        this.views = new Set();
        this.dead = false;
        loadOtpConfig().then(config => { this.config = config; this.update(); });
    }

    /** Seconds until another code may be requested. */
    get left() {
        return Math.max(0, Math.ceil((this.until - Date.now()) / 1000));
    }

    update() {
        if (!this.dead) this.views.forEach(view => view());
    }

    setErrors(changes) {
        this.errors = { ...this.errors, ...changes };
        this.update();
    }

    startCountdown(seconds) {
        clearInterval(this.timer);
        this.until = Date.now() + seconds * 1000;
        if (seconds > 0) {
            this.timer = setInterval(() => {
                if (this.left <= 0) clearInterval(this.timer);
                this.update();
            }, 250);
        }
        this.update();
    }

    /** Stops timers and updates once the page is gone. */
    destroy() {
        clearInterval(this.timer);
        this.dead = true;
        this.views.clear();
        this.roots.clear();
    }

    focusCode() {
        for (const root of this.roots) {
            const input = root.querySelector('[data-otp-code]');
            if (input && input.offsetParent !== null) { input.focus(); return; }
        }
    }

    // Editing the address cancels any code sent for, or verification of, the previous one
    setEmail(value) {
        this.email = value;
        this.stage = 'idle';
        this.code = '';
        this.errors = { ...this.errors, email: null, code: null };
        this.startCountdown(0); // cooldowns are per address
    }

    async send() {
        if (this.inFlight || this.left > 0) return;
        const problem = this.purpose === 'VERIFY_EMAIL' ? null : emailProblem(this.email);
        if (problem) { this.setErrors({ email: problem }); return; }
        this.inFlight = true;
        this.busy = 'send';
        this.setErrors({ email: null, code: null });
        try {
            const r = await api('auth/otp/send', { method: 'POST', body: { email: this.email.trim(), purpose: this.purpose } });
            this.stage = 'sent';
            this.code = '';
            this.startCountdown(r.resendIn);
            let filled = false;
            if (this.config?.devMode) {
                // Development mode: no email goes out, so take the code from this computer's dev inbox
                try {
                    const dev = await api('dev/mailbox/latest?email=' + encodeURIComponent(this.email.trim()));
                    this.code = dev.code;
                    filled = true;
                } catch { /* nothing was sent to this address (e.g. not a verified account) */ }
            }
            toast(filled ? 'Development mode: the code was filled in for you. Click Verify.' : r.message);
            setTimeout(() => this.focusCode(), 50);
        } catch (err) {
            this.errors = { ...this.errors, [err.field === 'code' ? 'code' : 'email']: err.message };
            toast(err.message, { error: true });
            const wait = /wait (\d+) seconds/.exec(err.message);
            if (wait) this.startCountdown(Number(wait[1]));
        } finally {
            this.inFlight = false;
            this.busy = null;
            this.update();
        }
    }

    async verify() {
        if (this.inFlight) return undefined;
        if (!/^\d{6}$/.test(this.code)) {
            this.setErrors({ code: 'Enter the 6-digit code we emailed you.' });
            this.focusCode();
            return undefined;
        }
        this.inFlight = true;
        this.busy = 'verify';
        this.setErrors({ code: null });
        try {
            const result = await this.onVerify({ email: this.email.trim(), code: this.code });
            this.stage = 'verified';
            this.errors = { ...this.errors, email: null, code: null };
            return result;
        } catch (err) {
            // Errors for other fields (e.g. a new password) are shown by the page's onVerify, not here
            const field = err.field === 'email' ? 'email' : !err.field || err.field === 'code' ? 'code' : null;
            if (field) this.errors = { ...this.errors, [field]: err.message };
            toast(err.message, { error: true });
            if (field === 'code') {
                this.code = '';
                setTimeout(() => this.focusCode());
            }
            return undefined;
        } finally {
            this.inFlight = false;
            this.busy = null;
            this.update();
        }
    }
}

const spinner = (onAccent = false) => html`<span class="spinner spinner-btn ${onAccent ? 'spinner-on-accent' : ''}" aria-hidden="true"></span>`;

/** Brings every data-otp-* element under `root` in line with the state. */
function refresh(root, otp) {
    const { stage, busy, errors, config } = otp;
    const left = otp.left;

    // data-otp-stage="idle sent": shown only in those stages; data-dev-hint: only in development mode;
    // data-otp-hint: hidden while the address has an error
    root.querySelectorAll('[data-otp-stage], [data-dev-hint], [data-otp-hint]').forEach(el => {
        const stages = el.dataset.otpStage;
        el.hidden = (stages != null && !stages.split(' ').includes(stage))
            || (el.hasAttribute('data-dev-hint') && !config?.devMode)
            || (el.hasAttribute('data-otp-hint') && Boolean(errors.email));
    });
    root.querySelectorAll('[data-otp-row]').forEach(el => el.classList.toggle('is-verified', stage === 'verified'));
    root.querySelectorAll('[data-send-otp]').forEach(btn => {
        btn.disabled = busy !== null || left > 0;
        renderIfChanged(btn, html`${busy === 'send' ? spinner() : ''}${sendLabel(otp, btn.dataset.first)}`);
    });
    root.querySelectorAll('[data-verify-otp]').forEach(btn => {
        btn.disabled = busy !== null;
        renderIfChanged(btn, busy === 'verify' ? html`${spinner(true)}Checking…` : btn.dataset.label);
    });
    root.querySelectorAll('[data-otp-error]').forEach(p => {
        const message = errors[p.dataset.otpError] || '';
        setText(p, message);
        p.hidden = !message;
    });
    for (const [selector, key] of [['[data-otp-email]', 'email'], ['[data-otp-code]', 'code']]) {
        root.querySelectorAll(selector).forEach(input => {
            const error = root.querySelector(`[data-otp-error="${key}"][id]`);
            if (errors[key] && error) {
                input.setAttribute('aria-invalid', 'true');
                input.setAttribute('aria-describedby', error.id);
            } else {
                input.removeAttribute('aria-invalid');
                input.removeAttribute('aria-describedby');
            }
        });
    }
    root.querySelectorAll('[data-otp-code]').forEach(input => { if (input.value !== otp.code) input.value = otp.code; });
    root.querySelectorAll('[data-otp-sent-to]').forEach(el => setText(el, el.dataset.sentTo || otp.email));
    root.querySelectorAll('[data-otp-valid]').forEach(el => {
        setText(el, config ? ` · valid for ${Math.round(config.expiresSeconds / 60)} minutes` : '');
    });
}

/** Connects the OTP markup inside `root` to `otp`: button clicks, typing, Enter to send/verify, and live updates. */
export function bindOtp(root, otp) {
    root.addEventListener('click', e => {
        if (e.target.closest('[data-send-otp]')) otp.send();
        else if (e.target.closest('[data-verify-otp]')) otp.verify();
    });
    root.addEventListener('input', e => {
        const t = e.target;
        if (t.matches('[data-otp-email]')) {
            otp.setEmail(t.value);
        } else if (t.matches('[data-otp-code]')) {
            t.value = t.value.replace(/\D/g, '').slice(0, 6);
            otp.code = t.value;
        }
    });
    root.addEventListener('keydown', e => {
        if (e.key !== 'Enter') return;
        if (e.target.matches('[data-otp-email]')) { e.preventDefault(); otp.send(); }
        else if (e.target.matches('[data-otp-code]')) { e.preventDefault(); otp.verify(); }
    });
    otp.roots.add(root);
    otp.views.add(() => refresh(root, otp));
    refresh(root, otp);
}

/** Development-mode note; `stages` limits when it shows (all stages by default). */
export const devModeHint = (stages = '') => html`
    <p class="form-hint dev-hint" data-dev-hint ${stages ? html`data-otp-stage="${stages}"` : ''} hidden>${infoIcon(12)} Development mode: no email is sent — after you click Send code, the code is filled in automatically.</p>`;

/** The code entry panel, shown once a code has been sent. Enter verifies. An empty verifyLabel leaves out the button. */
export function codePanel({ id, verifyLabel = 'Verify', sentTo = '' }) {
    return html`
        <div class="form-group otp-panel reveal-panel" data-otp-stage="sent" hidden>
            <label class="form-label" for="${id}-code">Verification code</label>
            <p class="otp-sent-to">
                Sent to <strong data-otp-sent-to data-sent-to="${sentTo}"></strong><span data-otp-valid></span> · check your spam folder too
            </p>
            <div class="otp-row">
                <input type="text" id="${id}-code" class="form-input otp-input" inputmode="numeric" autocomplete="one-time-code"
                       placeholder="••••••" maxlength="6" pattern="\\d{6}" data-otp-code>
                ${verifyLabel ? html`<button type="button" class="btn btn-primary otp-verify" data-verify-otp data-label="${verifyLabel}">${verifyLabel}</button>` : ''}
            </div>
            <p class="field-error" id="${id}-code-error" role="alert" data-otp-error="code" hidden></p>
        </div>`;
}

/**
 * The email row (address + Send code) and the code entry panel below it.
 * Enter in the address field sends a code.
 */
export function emailOtpFields(otp, { id = 'email', label = 'Email address', verifyLabel = 'Verify', hint = '' } = {}) {
    return html`
        <div class="form-group">
            <label class="form-label" for="${id}">${label}</label>
            <div class="email-row" data-otp-row>
                <input type="email" id="${id}" class="form-input" autocomplete="email" inputmode="email"
                       placeholder="you@example.com" value="${otp.email}" maxlength="254" data-otp-email>
                <span class="verified-pill" data-verified data-otp-stage="verified" hidden>${checkIcon(14)} Verified</span>
                <button type="button" class="btn btn-secondary otp-send" data-send-otp data-otp-stage="idle sent" aria-live="polite"></button>
            </div>
            <p class="field-error" id="${id}-error" role="alert" data-otp-error="email" hidden></p>
            ${hint ? html`<p class="form-hint" data-otp-hint data-otp-stage="idle sent">${hint}</p>` : ''}
            ${devModeHint('idle sent')}
        </div>
        ${codePanel({ id, verifyLabel })}`;
}
