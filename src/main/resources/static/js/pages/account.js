// The signed-in user's profile: photo, details (name, gender, email), password, and deleting the account.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { navigate } from '../router.js';
import { onSessionChange, session, setCartCount, setUser } from '../session.js';
import { confirm, toast } from '../ui.js';
import { avatar, AVATAR_TYPES, avatarProblem, fieldError, setBusy, setFieldError, setTitle } from '../components.js';
import { trashIcon, uploadIcon } from '../icons.js';
import { bindOtp, EmailOtp, emailOtpFields } from '../otp.js';

const ROLE_LABELS = { BUYER: 'Buyer', VENDOR: 'Seller', ADMIN: 'Administrator' };
const GENDERS = [['', 'Prefer not to say'], ['MALE', 'Male'], ['FEMALE', 'Female'], ['OTHER', 'Other']];
const DETAIL_FIELDS = ['fullName', 'gender', 'vendorName'];
const PASSWORD_FIELDS = ['currentPassword', 'newPassword', 'confirmPassword'];

export default function account(root) {
    setTitle('Profile');
    root.className = 'container page';
    const { user } = session;
    const isVendor = user.role === 'VENDOR';

    // Email: unchanged + unverified -> verify the current address; changed -> confirm the new one with a code
    const otp = new EmailOtp({
        purpose: 'VERIFY_EMAIL',
        initialEmail: user.email,
        onVerify: async ({ email, code }) => {
            const r = otp.purpose === 'CHANGE_EMAIL'
                ? await api('users/me/email', { method: 'POST', body: { email, code } })
                : await api('users/me/email/verify', { method: 'POST', body: { code } });
            setUser(r.user);
            otp.purpose = 'VERIFY_EMAIL';
            toast(r.message);
            return r;
        },
    });
    otp.stage = user.emailVerified ? 'verified' : 'idle';

    render(root, html`
        <div class="page-header">
            <div>
                <h1 class="page-title">Profile</h1>
                <p class="page-subtitle">Your photo, details, password and account</p>
            </div>
        </div>

        <section class="card profile-card profile-hero" aria-label="Profile photo" data-photo></section>

        <div class="profile-grid">
            <section class="card profile-card" aria-labelledby="details-title" data-details>
                <h2 class="profile-card-title" id="details-title">Your details</h2>
                <form id="details-form" novalidate>
                    <div class="form-group">
                        <label class="form-label" for="fullName">Full name</label>
                        <input type="text" class="form-input" id="fullName" name="fullName" autocomplete="name" maxlength="60" value="${user.fullName || ''}">
                        ${fieldError('fullName-error')}
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="gender">Gender</label>
                        <select class="form-input" id="gender" name="gender">
                            ${GENDERS.map(([value, label]) => html`<option value="${value}" ${value === (user.gender || '') ? 'selected' : ''}>${label}</option>`)}
                        </select>
                        ${fieldError('gender-error')}
                    </div>
                    ${isVendor ? html`
                        <div class="form-group">
                            <label class="form-label" for="vendorName">Shop or publisher name</label>
                            <input type="text" class="form-input" id="vendorName" name="vendorName" maxlength="80" value="${user.vendorName || ''}">
                            ${fieldError('vendorName-error')}
                        </div>` : ''}
                    <div class="form-group">
                        <label class="form-label" for="username">User id</label>
                        <input type="text" class="form-input" id="username" value="${user.username}" readonly>
                        <p class="form-hint">Your user id is permanent. You can sign in with it or with your email.</p>
                    </div>
                    ${emailOtpFields(otp, { id: 'email', label: 'Email', hint: 'Confirm it with a 6-digit code we email you. To change it, type the new address first.' })}
                    <button type="submit" class="btn btn-primary" data-save-details>Save</button>
                </form>
            </section>

            <section class="card profile-card" aria-labelledby="password-title">
                <h2 class="profile-card-title" id="password-title">Password</h2>
                <form id="password-form" novalidate>
                    <div class="form-group">
                        <label class="form-label" for="currentPassword">Current password</label>
                        <input type="password" class="form-input" id="currentPassword" name="currentPassword" autocomplete="current-password">
                        ${fieldError('currentPassword-error')}
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="newPassword">New password (at least 8 characters)</label>
                        <input type="password" class="form-input" id="newPassword" name="newPassword" autocomplete="new-password">
                        ${fieldError('newPassword-error')}
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="confirmPassword">Confirm new password</label>
                        <input type="password" class="form-input" id="confirmPassword" name="confirmPassword" autocomplete="new-password">
                        ${fieldError('confirmPassword-error')}
                    </div>
                    <button type="submit" class="btn btn-primary" data-change-password>Change password</button>
                </form>
            </section>
        </div>

        <section class="card profile-card" aria-labelledby="delete-title">
            <h2 class="profile-card-title" id="delete-title">Delete account</h2>
            ${user.role === 'BUYER' ? html`
                <p class="profile-note">This permanently deletes your account and profile photo. Orders you've already received stay on record with the sellers who sent them.</p>
                <form class="delete-row" id="delete-form" novalidate>
                    <div class="form-group">
                        <label class="sr-only" for="deletePassword">Your password</label>
                        <input type="password" class="form-input" id="deletePassword" placeholder="Your password" autocomplete="current-password">
                        ${fieldError('deletePassword-error')}
                    </div>
                    <button type="submit" class="btn btn-danger" data-delete-account>${trashIcon(15)} Delete my account</button>
                </form>` : html`
                <p class="profile-note">${isVendor
                    ? "Seller accounts can't be deleted here, because your listings and customers' orders depend on them. Contact an administrator to close your shop."
                    : "Administrator accounts can't be deleted."}</p>`}
        </section>`);

    // ---------- Photo ----------
    const photoCard = root.querySelector('[data-photo]');
    let photoBusy = false;
    let photoError = null;

    const paintPhoto = () => {
        const current = session.user;
        render(photoCard, html`
            ${avatar({ url: current.avatarUrl, name: current.username, size: 'xl' })}
            <div class="profile-hero-body">
                <div class="profile-name">${current.fullName || current.username}</div>
                <div class="profile-meta">@${current.username} · ${ROLE_LABELS[current.role] || current.role}</div>
                <div class="row-actions">
                    <input id="avatar-file" type="file" class="sr-only file-input" accept="${AVATAR_TYPES.join(',')}" ${photoBusy ? 'disabled' : ''}
                           aria-describedby="${photoError ? 'avatar-error' : 'avatar-hint'}" data-avatar-input>
                    <label for="avatar-file" class="btn btn-primary btn-sm ${photoBusy ? 'is-disabled' : ''}">
                        ${photoBusy ? html`<span class="spinner spinner-btn spinner-on-accent" aria-hidden="true"></span>Saving…` : html`${uploadIcon()} ${current.avatarUrl ? 'Change photo' : 'Upload photo'}`}
                    </label>
                    ${current.avatarUrl ? html`<button type="button" class="btn btn-secondary btn-sm" data-remove-avatar ${photoBusy ? 'disabled' : ''}>Remove photo</button>` : ''}
                </div>
                ${photoError
                    ? html`<p class="field-error" id="avatar-error" role="alert">${photoError}</p>`
                    : html`<p class="form-hint" id="avatar-hint">Your photo is shown in the navigation bar and to sellers fulfilling your orders. JPG, PNG or WebP, up to 2 MB.</p>`}
            </div>`);
    };

    const runPhoto = async request => {
        photoBusy = true;
        photoError = null;
        paintPhoto();
        try {
            const r = await request();
            photoBusy = false;
            setUser(r.user); // repaints the card through the session listener
            toast(r.message);
        } catch (err) {
            photoBusy = false;
            photoError = err.message;
            paintPhoto();
            toast(err.message, { error: true });
        }
    };

    photoCard.addEventListener('change', e => {
        if (!e.target.matches('[data-avatar-input]')) return;
        const chosen = e.target.files?.[0];
        if (!chosen) return;
        const problem = avatarProblem(chosen);
        if (problem) {
            photoError = problem;
            paintPhoto();
            return;
        }
        const data = new FormData();
        data.set('file', chosen);
        runPhoto(() => api('users/me/profile-picture', { method: 'POST', body: data }));
    });

    photoCard.addEventListener('click', async e => {
        if (!e.target.closest('[data-remove-avatar]')) return;
        const ok = await confirm({ title: 'Remove your profile photo?', text: 'Your initials will be shown instead.', action: 'Remove photo' });
        if (ok) runPhoto(() => api('users/me/profile-picture', { method: 'DELETE' }));
    });

    paintPhoto();

    // ---------- Details ----------
    const detailsCard = root.querySelector('[data-details]');
    const detailsForm = root.querySelector('#details-form');
    bindOtp(detailsCard, otp);

    // Typing an address other than the account's own switches to "confirm the new address"; typing it back restores its status
    detailsCard.addEventListener('input', e => {
        if (e.target.matches('[data-otp-email]')) {
            const same = e.target.value.trim().toLowerCase() === session.user.email.toLowerCase();
            otp.purpose = same ? 'VERIFY_EMAIL' : 'CHANGE_EMAIL';
            if (same && session.user.emailVerified) {
                otp.stage = 'verified';
                otp.update();
            }
        } else if (DETAIL_FIELDS.includes(e.target.name)) {
            setFieldError(root, e.target.name, null);
        }
    });

    detailsForm.addEventListener('submit', async e => {
        e.preventDefault();
        const button = root.querySelector('[data-save-details]');
        if (button.disabled) return;
        if (otp.purpose === 'CHANGE_EMAIL') {
            otp.setErrors({ email: 'Confirm the new address with the code we email you, or change it back.' });
            root.querySelector('#email').focus();
            return;
        }
        const body = {
            fullName: detailsForm.elements.fullName.value.trim(),
            gender: detailsForm.elements.gender.value,
            vendorName: isVendor ? detailsForm.elements.vendorName.value.trim() : null,
        };
        if (isVendor && !body.vendorName) {
            setFieldError(root, 'vendorName', 'Enter your shop or publisher name.');
            root.querySelector('#vendorName').focus();
            return;
        }
        setBusy(button, true, 'Save', 'Saving…');
        try {
            const r = await api('users/me', { method: 'PUT', body });
            setUser(r.user);
            toast(r.message);
        } catch (err) {
            if (DETAIL_FIELDS.includes(err.field)) {
                setFieldError(root, err.field, err.message);
                root.querySelector(`#${err.field}`)?.focus();
            }
            toast(err.message, { error: true });
        }
        setBusy(button, false, 'Save');
    });

    // ---------- Password ----------
    const passwordForm = root.querySelector('#password-form');
    passwordForm.addEventListener('input', e => {
        if (PASSWORD_FIELDS.includes(e.target.name)) setFieldError(root, e.target.name, null);
    });

    passwordForm.addEventListener('submit', async e => {
        e.preventDefault();
        const button = root.querySelector('[data-change-password]');
        if (button.disabled) return;
        const values = Object.fromEntries(PASSWORD_FIELDS.map(name => [name, passwordForm.elements[name].value]));
        const problems = {
            currentPassword: values.currentPassword ? null : 'Enter your current password.',
            newPassword: values.newPassword.length < 8 ? 'Use at least 8 characters.'
                : values.newPassword.length > 72 ? 'Use at most 72 characters.' : null,
            confirmPassword: values.confirmPassword !== values.newPassword ? "The passwords don't match." : null,
        };
        PASSWORD_FIELDS.forEach(name => setFieldError(root, name, problems[name]));
        const first = PASSWORD_FIELDS.find(name => problems[name]);
        if (first) {
            root.querySelector(`#${first}`).focus();
            return;
        }
        setBusy(button, true, 'Change password', 'Changing…');
        try {
            const r = await api('users/me/password', { method: 'POST', body: values });
            passwordForm.reset();
            toast(r.message);
        } catch (err) {
            if (PASSWORD_FIELDS.includes(err.field)) {
                setFieldError(root, err.field, err.message);
                root.querySelector(`#${err.field}`).focus();
            }
            toast(err.message, { error: true });
        }
        setBusy(button, false, 'Change password');
    });

    // ---------- Delete account ----------
    const deleteForm = root.querySelector('#delete-form');
    deleteForm?.addEventListener('input', () => setFieldError(root, 'deletePassword', null));
    deleteForm?.addEventListener('submit', async e => {
        e.preventDefault();
        const button = root.querySelector('[data-delete-account]');
        const input = root.querySelector('#deletePassword');
        if (button.disabled) return;
        if (!input.value) {
            setFieldError(root, 'deletePassword', 'Enter your password to confirm.');
            input.focus();
            return;
        }
        const ok = await confirm({
            title: 'Delete your account?',
            text: 'Your account and profile photo will be deleted permanently. This can’t be undone.',
            action: 'Delete account',
        });
        if (!ok) return;
        const label = html`${trashIcon(15)} Delete my account`;
        setBusy(button, true, label, 'Deleting…', { onAccent: false });
        try {
            const r = await api('users/me', { method: 'DELETE', body: { password: input.value } });
            setUser(null);
            setCartCount(0);
            navigate('/', { replace: true, flash: { type: 'success', text: r.message } });
        } catch (err) {
            if (err.field === 'password') setFieldError(root, 'deletePassword', err.message);
            input.value = '';
            toast(err.message, { error: true });
            setBusy(button, false, label);
        }
    });

    // Keep the photo card (name, avatar) in step with saved changes
    const stopListening = onSessionChange(({ user: current }) => {
        if (current && !photoBusy) paintPhoto();
    });

    return () => {
        stopListening();
        otp.destroy();
    };
}
