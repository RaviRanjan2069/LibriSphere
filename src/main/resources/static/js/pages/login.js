// Sign in with a user id or email + password (or a one-time code emailed to a verified address),
// then go back to where the user was heading.
import { html, render } from '../dom.js';
import { navigate, replaceQuery } from '../router.js';
import { homeFor } from '../format.js';
import { session, signIn, signInWithOtp } from '../session.js';
import { authTabs } from '../layout.js';
import { bindPasswordToggles, fieldError, passwordInput, setBusy, setFieldError, setTitle, showFlash } from '../components.js';
import { bindOtp, EmailOtp, emailOtpFields } from '../otp.js';

/** Only same-site paths like "/orders?tab=all" are allowed as a return target. */
function safeNext(next) {
    return next && next.startsWith('/') && !next.startsWith('//') ? next : null;
}

export default function login(root, { query, flash }) {
    // ?as=admin: the Admin tab, which only lets administrator accounts in
    const asAdmin = query.get('as') === 'admin';
    setTitle(asAdmin ? 'Admin sign in' : 'Sign in');
    const next = safeNext(query.get('next'));
    // Already signed in (e.g. opened /login directly): go home
    if (session.user) {
        navigate(next || homeFor(session.user), { replace: true });
        return undefined;
    }

    let method = !asAdmin && query.get('method') === 'code' ? 'code' : 'password';
    const form = { username: '', password: '' };
    let remember = true;
    let busy = false;

    const finish = me => {
        // Shoppers who signed in mid-checkout return to their saved cart
        const fallback = me.user.role === 'BUYER' && me.cartCount > 0 ? '/cart' : homeFor(me.user);
        navigate(next || fallback, { replace: true });
    };

    const otp = new EmailOtp({
        purpose: 'LOGIN',
        onVerify: async values => {
            const me = await signInWithOtp({ ...values, remember });
            finish(me);
            return me;
        },
    });

    render(root, html`
        ${authTabs(asAdmin ? 'admin' : 'login')}
        <h1 class="auth-title">${asAdmin ? 'Admin sign in' : 'Welcome back'}</h1>
        <p class="auth-sub">${asAdmin ? 'For LibriSphere administrators only.' : 'Sign in to pick up where you left off.'}</p>
        <div data-flash></div>
        <div data-panel></div>`);
    showFlash(root, flash);
    bindOtp(root, otp);

    const submitLabel = asAdmin ? 'Sign in to admin' : 'Sign in';

    const rememberBox = () => html`
        <label class="check">
            <input type="checkbox" id="remember" ${remember ? 'checked' : ''}>
            <span>Keep me signed in</span>
        </label>`;

    /** Renders the chosen sign-in method. `focusField` puts the cursor in its first field. */
    const paintPanel = focusField => {
        const panel = root.querySelector('[data-panel]');
        if (method === 'password') {
            render(panel, html`
                <form id="login-form" novalidate>
                    <div class="form-group">
                        <label class="form-label" for="username">User id or email</label>
                        <input type="text" class="form-input" id="username" autocomplete="username" autocapitalize="none" spellcheck="false" value="${form.username}">
                        ${fieldError('username-error')}
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="password">Password</label>
                        ${passwordInput('password', 'current-password')}
                        ${fieldError('password-error')}
                    </div>
                    <div class="signin-options">
                        ${rememberBox()}
                        <a href="/forgot-password">Forgot password?</a>
                    </div>
                    <button type="submit" class="btn btn-primary btn-lg btn-block" data-submit>${submitLabel}</button>
                </form>
                ${asAdmin ? '' : html`<p class="auth-alt"><button type="button" class="link-btn" data-method="code">Sign in with an email code instead</button></p>`}`);
            root.querySelector('#password').value = form.password;
            if (focusField) root.querySelector(form.username ? '#password' : '#username').focus();
        } else {
            render(panel, html`
                <form id="otp-login-form" novalidate>
                    ${emailOtpFields(otp, { id: 'login-email', verifyLabel: 'Verify & sign in', hint: "We'll email a 6-digit sign-in code to the address on your account." })}
                    <div class="signin-options">${rememberBox()}</div>
                </form>
                <p class="form-hint otp-note">
                    Email not verified yet? Sign in with your password, then verify it from your Profile.
                </p>
                <p class="auth-alt"><button type="button" class="link-btn" data-method="password">Sign in with your password instead</button></p>`);
            otp.update();
            if (focusField) root.querySelector('#login-email').focus();
        }
    };

    const switchMethod = key => {
        method = key;
        const params = new URLSearchParams(location.search);
        if (key === 'code') params.set('method', 'code'); else params.delete('method');
        replaceQuery(params);
        showFlash(root, null);
        paintPanel(true);
    };

    paintPanel(true);

    const submitPassword = async () => {
        if (busy) return;
        const problems = {
            username: form.username.trim() ? null : 'Enter your user id or email.',
            password: form.password ? null : 'Enter your password.',
        };
        setFieldError(root, 'username', problems.username);
        setFieldError(root, 'password', problems.password);
        if (problems.username || problems.password) {
            root.querySelector(problems.username ? '#username' : '#password').focus();
            return;
        }
        const button = root.querySelector('[data-submit]');
        busy = true;
        setBusy(button, true, submitLabel, 'Signing in…');
        try {
            finish(await signIn(form.username.trim(), form.password, remember, asAdmin ? 'ADMIN' : null));
        } catch (err) {
            showFlash(root, { type: 'error', text: err.message });
            form.password = '';
            root.querySelector('#password').value = '';
            root.querySelector('#password').focus();
            busy = false;
            setBusy(button, false, submitLabel);
        }
    };

    root.addEventListener('click', e => {
        const switcher = e.target.closest('[data-method]');
        if (switcher) switchMethod(switcher.dataset.method);
    });
    bindPasswordToggles(root);

    root.addEventListener('change', e => {
        if (e.target.id === 'remember') remember = e.target.checked;
    });

    root.addEventListener('input', e => {
        const { id } = e.target;
        if (id !== 'username' && id !== 'password') return;
        form[id] = e.target.value;
        setFieldError(root, id, null);
    });

    root.addEventListener('submit', e => {
        e.preventDefault();
        if (e.target.id === 'login-form') submitPassword();
        else if (e.target.id === 'otp-login-form') (otp.stage === 'sent' ? otp.verify() : otp.send());
    });

    return () => otp.destroy();
}
