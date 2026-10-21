// Create a buyer or seller account. The email address must be verified with an emailed code before submitting.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { navigate } from '../router.js';
import { homeFor } from '../format.js';
import { session } from '../session.js';
import { authTabs } from '../layout.js';
import { toast } from '../ui.js';
import {
    avatar, AVATAR_TYPES, avatarProblem, fieldError, previewUrl, setBusy, setFieldError, setTitle, showFlash,
} from '../components.js';
import { cameraIcon } from '../icons.js';
import { bindOtp, EmailOtp, emailOtpFields } from '../otp.js';

const USERNAME = /^[A-Za-z0-9_.-]{3,30}$/;
const FIELDS = ['vendorShopName', 'username', 'password', 'confirmPassword'];

/** Client-side checks mirroring the server's, so most mistakes are caught before submitting. */
function validate(form, emailVerified) {
    const e = {};
    if (form.role === 'VENDOR' && !form.vendorShopName.trim()) e.vendorShopName = 'Enter your shop or publisher name.';
    if (!USERNAME.test(form.username.trim())) {
        e.username = /\s/.test(form.username.trim())
            ? 'Usernames can’t contain spaces. Try Ravi_Rajput or ravirajput.'
            : 'Use 3–30 letters, numbers, dots, dashes or underscores.';
    }
    if (!emailVerified) e.email = 'Verify your email address with the code we send you.';
    if (form.password.length < 8) e.password = 'Use at least 8 characters.';
    else if (form.password.length > 72) e.password = 'Use at most 72 characters.';
    if (form.confirmPassword !== form.password) e.confirmPassword = "The passwords don't match.";
    return e;
}

export default function register(root, { query }) {
    setTitle('Create account');
    if (session.user) {
        navigate(homeFor(session.user), { replace: true });
        return undefined;
    }

    const form = {
        role: query.get('role') === 'VENDOR' ? 'VENDOR' : 'BUYER',
        vendorShopName: '', username: '', password: '', confirmPassword: '',
    };
    let avatarFile = null;
    let avatarError = null;
    let busy = false;
    const preview = previewUrl();

    const otp = new EmailOtp({
        purpose: 'REGISTER',
        onVerify: async values => {
            const r = await api('auth/otp/verify', { method: 'POST', body: values });
            toast(r.message);
            return r;
        },
    });

    const roleChoice = (value, title, desc) => html`
        <label class="choice-card">
            <input type="radio" name="role" value="${value}" ${form.role === value ? 'checked' : ''}>
            <span class="choice-card-body">
                <span class="choice-card-title">${title}</span>
                <span class="choice-card-desc">${desc}</span>
            </span>
        </label>`;

    render(root, html`
        ${authTabs('register')}
        <h1 class="auth-title">Create your account</h1>
        <p class="auth-sub">Shop as a reader, or apply to sell your books.</p>
        <div data-flash></div>

        <form id="register-form" novalidate>
            <div class="form-group">
                <span class="form-label" id="role-label">I want to</span>
                <div class="choice-grid" role="radiogroup" aria-labelledby="role-label">
                    ${roleChoice('BUYER', 'Buy books', 'Shop from every seller')}
                    ${roleChoice('VENDOR', 'Sell books', 'Open a storefront')}
                </div>
            </div>

            <div class="form-group reveal-panel" id="vendor-fields" ${form.role === 'VENDOR' ? '' : 'hidden'}>
                <label class="form-label" for="vendorShopName">Shop or publisher name</label>
                <input type="text" class="form-input" id="vendorShopName" name="vendorShopName" placeholder="e.g. Vintage Reads" maxlength="80">
                ${fieldError('vendorShopName-error')}
                <p class="form-hint">Seller accounts are reviewed by an administrator before you can sign in.</p>
            </div>

            <div class="form-group avatar-picker" data-avatar-picker></div>

            <div class="form-group">
                <label class="form-label" for="username">Username</label>
                <input type="text" class="form-input" id="username" name="username" autocomplete="username" maxlength="30" placeholder="e.g. ravi_rajput">
                ${fieldError('username-error')}
                <p class="form-hint" id="username-hint">Letters, numbers, dots, dashes or underscores — no spaces.</p>
            </div>

            ${emailOtpFields(otp, { id: 'email', hint: "We'll email you a 6-digit code to confirm it's yours." })}

            <div class="form-row form-row-stack">
                <div class="form-group">
                    <label class="form-label" for="password">Password</label>
                    <input type="password" class="form-input" id="password" name="password" autocomplete="new-password">
                    ${fieldError('password-error')}
                    <p class="form-hint" id="password-hint">At least 8 characters.</p>
                </div>
                <div class="form-group">
                    <label class="form-label" for="confirmPassword">Confirm password</label>
                    <input type="password" class="form-input" id="confirmPassword" name="confirmPassword" autocomplete="new-password">
                    ${fieldError('confirmPassword-error')}
                </div>
            </div>

            <button type="submit" class="btn btn-primary btn-lg btn-block" data-submit>Create account</button>
            <p class="form-hint form-hint-center" data-otp-stage="idle sent">You'll need to verify your email address before creating the account.</p>
        </form>`);

    const picker = root.querySelector('[data-avatar-picker]');
    // Optional photo with a live preview; shows initials of the username until one is chosen
    const paintPicker = () => render(picker, html`
        <div class="avatar-picker-preview">
            ${avatar({ url: preview.url, name: form.username || 'You', size: 'lg' })}
            <span class="avatar-picker-badge" aria-hidden="true">${cameraIcon(14)}</span>
        </div>
        <div class="avatar-picker-body">
            <span class="form-label">Profile picture <span class="optional">optional</span></span>
            <div class="avatar-picker-actions">
                <input id="avatar-file" type="file" class="sr-only file-input" accept="${AVATAR_TYPES.join(',')}" ${busy ? 'disabled' : ''}
                       aria-describedby="${avatarError ? 'avatar-file-error' : 'avatar-file-hint'}" data-avatar-input>
                <label for="avatar-file" class="btn btn-secondary btn-sm ${busy ? 'is-disabled' : ''}">${avatarFile ? 'Change photo' : 'Upload photo'}</label>
                ${avatarFile ? html`<button type="button" class="btn btn-ghost btn-sm" data-remove-avatar>Remove</button>` : ''}
            </div>
            ${avatarError
                ? html`<p class="field-error" id="avatar-file-error" role="alert">${avatarError}</p>`
                : html`<p class="form-hint" id="avatar-file-hint">JPG, PNG or WebP, up to 2 MB.</p>`}
        </div>`);
    paintPicker();
    bindOtp(root, otp);

    const setAvatar = (file, error = null) => {
        avatarFile = file;
        avatarError = error;
        preview.set(file);
        paintPicker();
    };

    root.addEventListener('change', e => {
        if (e.target.name === 'role') {
            form.role = e.target.value;
            root.querySelector('#vendor-fields').hidden = form.role !== 'VENDOR';
        } else if (e.target.matches('[data-avatar-input]')) {
            const chosen = e.target.files?.[0];
            if (!chosen) return;
            const problem = avatarProblem(chosen);
            if (problem) { avatarError = problem; paintPicker(); return; }
            setAvatar(chosen);
        }
    });

    root.addEventListener('click', e => {
        if (e.target.closest('[data-remove-avatar]')) setAvatar(null);
    });

    root.addEventListener('input', e => {
        const { name } = e.target;
        if (!FIELDS.includes(name)) return;
        form[name] = e.target.value;
        setFieldError(root, name, null);
        if (name === 'username' && !avatarFile) paintPicker();
    });

    root.addEventListener('submit', async e => {
        e.preventDefault();
        if (busy) return;
        const { email: emailError, ...problems } = validate(form, otp.stage === 'verified');
        if (emailError) otp.setErrors({ email: emailError }); // shown by the email field itself
        FIELDS.forEach(name => setFieldError(root, name, problems[name]));
        showFlash(root, null);
        const first = ['vendorShopName', 'username', 'email', 'password', 'confirmPassword']
            .find(k => (k === 'email' ? emailError : problems[k]));
        if (first) {
            document.getElementById(first)?.focus();
            return;
        }

        const data = new FormData();
        data.set('role', form.role);
        if (form.role === 'VENDOR') data.set('vendorShopName', form.vendorShopName.trim());
        data.set('username', form.username.trim());
        data.set('email', otp.email.trim());
        data.set('password', form.password);
        data.set('confirmPassword', form.confirmPassword);
        if (avatarFile) data.set('avatar', avatarFile);

        const button = root.querySelector('[data-submit]');
        busy = true;
        setBusy(button, true, 'Create account', 'Creating account…');
        paintPicker();
        try {
            const result = await api('auth/register', { method: 'POST', body: data });
            navigate('/login', { flash: { type: 'success', text: result.message } });
        } catch (err) {
            if (err.field === 'email') {
                otp.setErrors({ email: err.message });
            } else if (err.field === 'avatar') {
                avatarError = err.message;
            } else if (err.field) {
                setFieldError(root, err.field, err.message);
                document.getElementById(err.field)?.focus();
            } else {
                showFlash(root, { type: 'error', text: err.message });
            }
            toast(err.message, { error: true });
            busy = false;
            setBusy(button, false, 'Create account');
            paintPicker();
        }
    });

    return () => {
        otp.destroy();
        preview.set(null);
    };
}
