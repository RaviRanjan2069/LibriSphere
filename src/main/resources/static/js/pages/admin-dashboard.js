// Admin: platform totals, recent orders and seller approvals.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { formatDate, labelOf, price, SUPPORT_AREAS, SUPPORT_PRIORITIES, SUPPORT_TOPICS } from '../format.js';
import {
    avatar, bindPasswordToggles, emptyState, fieldError, flash, loading, passwordInput, setBusy, setFieldError, setTitle, statCard, statusBadge,
} from '../components.js';
import { emailProblem } from '../otp.js';
import { toast } from '../ui.js';
import { bagIcon, bookIcon, checkIcon, headsetIcon, homeIcon, rupeeIcon, usersIcon } from '../icons.js';

const table = (head, rows, id = '') => html`
    <div class="card table-card"><div class="table-scroll">
        <table class="data-table stack" ${id ? html`id="${id}"` : ''}><thead><tr>${head}</tr></thead><tbody>${rows}</tbody></table>
    </div></div>`;

const shopCell = v => html`
    <td class="strong cell-full" data-label="Shop"><div class="cell-person">${avatar({ url: v.avatarUrl, name: v.vendorName || v.username, size: 'sm' })}<span>${v.vendorName}</span></div></td>`;

const ticket = r => html`
    <article class="card support-ticket ${r.status === 'OPEN' ? 'is-open' : ''}">
        <div class="support-ticket-head">
            <span class="mono strong">${r.reference}</span>
            ${r.status === 'RESOLVED'
                ? html`<span class="status-badge status-delivered">Resolved</span>`
                : html`<span class="status-badge status-pending">Open</span>`}
            <span class="support-ticket-title">${r.subject}</span>
        </div>
        <div class="support-ticket-meta">
            From @${r.username} (<a href="mailto:${r.email}">${r.email}</a>) · ${(r.role || '').toLowerCase()} ·
            ${labelOf(SUPPORT_TOPICS, r.topic)} · ${labelOf(SUPPORT_PRIORITIES, r.priority)} priority ·
            ${labelOf(SUPPORT_AREAS, r.area)} · ${formatDate(r.createdAt)}
        </div>
        <p class="support-ticket-text">${r.description}</p>
        ${r.status === 'RESOLVED' && r.resolution ? html`
            <div class="support-reply"><strong>Resolved ${formatDate(r.resolvedAt)}:</strong> ${r.resolution}</div>` : ''}
        ${r.status === 'OPEN' ? html`
            <form class="support-resolve" data-resolve="${r.id}" novalidate>
                ${r.resolution ? html`<div class="support-reply is-previous"><strong>Earlier reply:</strong> ${r.resolution}</div>` : ''}
                <label class="sr-only" for="reply-${r.id}">Reply to ${r.username}</label>
                <textarea class="form-input" id="reply-${r.id}" rows="3" maxlength="2000" placeholder="Reply to the customer. They'll get it by email."></textarea>
                ${fieldError(`reply-${r.id}-error`)}
                <button type="submit" class="btn btn-primary btn-sm">Send reply &amp; resolve</button>
            </form>` : ''}
    </article>`;

// Open requests first, newest first within each group
const supportSection = requests => {
    const open = requests.filter(r => r.status === 'OPEN');
    const done = requests.filter(r => r.status !== 'OPEN');
    return html`
        <section class="section" id="support">
            <div class="section-head"><h2 class="section-title">Support requests</h2><span class="section-meta">${open.length} open</span></div>
            ${requests.length === 0
                ? emptyState({ icon: headsetIcon(24), text: 'No support requests yet. New ones are also emailed to the support inbox.' })
                : html`<div class="support-tickets">${[...open, ...done].map(ticket)}</div>`}
        </section>`;
};

// Server field name -> input id in the "Add an administrator" form
const ADMIN_FIELDS = { username: 'newAdminUsername', email: 'newAdminEmail', password: 'newAdminPassword', confirmPassword: 'newAdminConfirm' };

const adminsSection = admins => html`
    <section class="section" id="administrators">
        <div class="section-head"><h2 class="section-title">Administrators</h2><span class="section-meta">${admins.length} ${admins.length === 1 ? 'account' : 'accounts'}</span></div>
        ${table(
            html`<th>Administrator</th><th>Email</th><th>Email status</th>`,
            admins.map(a => html`
                <tr>
                    <td class="strong cell-full" data-label="Administrator"><div class="cell-person">${avatar({ url: a.avatarUrl, name: a.username, size: 'sm' })}<div><div class="cell-title">${a.fullName || a.username}</div><div class="cell-sub">@${a.username}</div></div></div></td>
                    <td data-label="Email"><a href="mailto:${a.email}">${a.email}</a></td>
                    <td data-label="Email status">${a.emailVerified
                        ? html`<span class="status-badge status-delivered">Verified</span>`
                        : html`<span class="status-badge status-pending">Not verified</span>`}</td>
                </tr>`),
            'admins')}
        <div class="card card-pad admin-form-card">
            <h3 class="card-title">Add an administrator</h3>
            <p class="profile-note">They sign in from the Admin tab with this user id and password, and can change the password from their Profile.</p>
            <form id="admin-form" novalidate>
                <div class="form-row">
                    <div class="form-group">
                        <label class="form-label" for="newAdminUsername">User id</label>
                        <input type="text" class="form-input" id="newAdminUsername" maxlength="30" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="e.g. ravi_admin">
                        ${fieldError('newAdminUsername-error')}
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="newAdminEmail">Email</label>
                        <input type="email" class="form-input" id="newAdminEmail" maxlength="254" autocomplete="off" placeholder="name@example.com">
                        ${fieldError('newAdminEmail-error')}
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label class="form-label" for="newAdminPassword">Password (at least 8 characters)</label>
                        ${passwordInput('newAdminPassword', 'new-password')}
                        ${fieldError('newAdminPassword-error')}
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="newAdminConfirm">Confirm password</label>
                        ${passwordInput('newAdminConfirm', 'new-password')}
                        ${fieldError('newAdminConfirm-error')}
                    </div>
                </div>
                <button type="submit" class="btn btn-primary" data-create-admin>Create administrator</button>
            </form>
        </div>
    </section>`;

export default function adminDashboard(root, { flash: initialFlash, alive }) {
    setTitle('Admin');
    root.className = 'container page';
    let message = initialFlash;
    let d = null;

    const paint = () => render(root, html`
        <div class="page-header">
            <div>
                <h1 class="page-title">Platform overview</h1>
                <p class="page-subtitle">Review seller applications and keep an eye on store activity.</p>
            </div>
        </div>
        ${flash(message)}
        ${!d ? loading() : html`
            <section class="stat-grid" aria-label="Platform totals">
                ${statCard({ label: 'Gross sales', value: price(d.totalSales), hint: 'Excludes cancelled items', icon: rupeeIcon() })}
                ${statCard({ label: 'Orders', value: d.totalOrders, hint: 'Across all sellers', icon: bagIcon(17), variant: 'stat-icon-warning' })}
                ${statCard({ label: 'Sellers', value: d.vendorCount, hint: `${d.pendingVendors.length} awaiting approval`, icon: homeIcon(17), variant: 'stat-icon-success' })}
                ${statCard({ label: 'Buyers', value: d.buyerCount, hint: 'Registered accounts', icon: usersIcon(), variant: 'stat-icon-info' })}
                ${statCard({ label: 'Catalogue', value: d.bookCount, hint: 'Titles listed', icon: bookIcon() })}
            </section>

            <section class="section">
                <div class="section-head"><h2 class="section-title">Recent orders</h2><span class="section-meta">Latest 10</span></div>
                ${d.recentOrders.length === 0 ? emptyState({ text: 'No orders have been placed yet.' }) : table(
                    html`<th>Order</th><th>Customer</th><th class="num">Items</th><th class="num">Total</th><th class="num">Refunded</th><th>Status</th>`,
                    d.recentOrders.map(o => html`
                        <tr>
                            <td data-label="Order"><div class="cell-title mono">#LBS-${o.id}</div><div class="cell-sub nowrap">${formatDate(o.orderDate)}</div></td>
                            <td data-label="Customer"><div class="cell-person">${avatar({ url: o.buyerAvatarUrl, name: o.buyerName, size: 'sm' })}<div><div class="cell-title">${o.buyerName}</div><div class="cell-sub">${o.buyerEmail}</div></div></div></td>
                            <td class="num" data-label="Items">${o.items.length}</td>
                            <td class="num strong" data-label="Total">${price(o.totalPrice)}</td>
                            <td class="num" data-label="Refunded">${o.refundAmount > 0 ? price(o.refundAmount) : '—'}</td>
                            <td data-label="Status">${statusBadge(o.status)}</td>
                        </tr>`),
                    'recent-orders')}
            </section>

            <section class="section">
                <div class="section-head"><h2 class="section-title">Seller applications</h2><span class="section-meta">${d.pendingVendors.length} pending</span></div>
                ${d.pendingVendors.length === 0
                    ? emptyState({ icon: checkIcon(22), text: "You're all caught up. New seller applications will appear here." })
                    : table(
                        html`<th>Shop</th><th>Username</th><th>Email</th><th>Status</th><th class="text-right">Action</th>`,
                        d.pendingVendors.map(v => html`
                            <tr>
                                ${shopCell(v)}
                                <td data-label="Username">${v.username}</td>
                                <td data-label="Email"><a href="mailto:${v.email}">${v.email}</a></td>
                                <td data-label="Status">${statusBadge('PENDING')}</td>
                                <td class="cell-full"><div class="row-actions">
                                    <button type="button" class="btn btn-primary btn-sm" data-approve="${v.id}">${checkIcon(13)} Approve</button>
                                </div></td>
                            </tr>`),
                        'pending')}
            </section>

            <section class="section">
                <div class="section-head"><h2 class="section-title">Active sellers</h2><span class="section-meta">${d.approvedVendors.length} approved</span></div>
                ${d.approvedVendors.length === 0 ? emptyState({ text: 'No approved sellers yet.' }) : table(
                    html`<th>Shop</th><th>Username</th><th>Email</th><th>Status</th>`,
                    d.approvedVendors.map(v => html`
                        <tr>
                            ${shopCell(v)}
                            <td data-label="Username">${v.username}</td>
                            <td data-label="Email"><a href="mailto:${v.email}">${v.email}</a></td>
                            <td data-label="Status"><span class="status-badge status-delivered">Active</span></td>
                        </tr>`))}
            </section>

            ${supportSection(d.supportRequests || [])}

            ${adminsSection(d.admins || [])}`}`);

    const load = async () => {
        try {
            const result = await api('admin/dashboard');
            if (alive()) d = result;
        } catch (e) {
            if (alive()) message = { type: 'error', text: e.message };
        }
        if (alive()) paint();
    };

    paint();
    load();

    bindPasswordToggles(root);
    root.addEventListener('input', e => {
        if (Object.values(ADMIN_FIELDS).includes(e.target.id)) setFieldError(root, e.target.id, null);
    });

    root.addEventListener('submit', async e => {
        const form = e.target.closest('[data-resolve]');
        if (!form) return;
        e.preventDefault();
        const id = form.dataset.resolve;
        const button = form.querySelector('button[type="submit"]');
        const reply = form.querySelector('textarea').value.trim();
        if (reply.length < 2) {
            setFieldError(form, `reply-${id}`, 'Write a reply for the customer.');
            form.querySelector('textarea').focus();
            return;
        }
        setBusy(button, true, 'Send reply & resolve', 'Sending…');
        try {
            const r = await api(`admin/support/${id}/resolve`, { method: 'POST', body: { reply } });
            toast(r.message);
            message = { type: 'success', text: r.message };
            await load();
        } catch (err) {
            if (err.field === 'reply') setFieldError(form, `reply-${id}`, err.message);
            toast(err.message, { error: true });
            setBusy(button, false, 'Send reply & resolve');
        }
    });

    root.addEventListener('submit', async e => {
        if (e.target.id !== 'admin-form') return;
        e.preventDefault();
        const button = root.querySelector('[data-create-admin]');
        if (button.disabled) return;
        const value = id => root.querySelector(`#${id}`).value;
        const body = {
            username: value(ADMIN_FIELDS.username).trim(),
            email: value(ADMIN_FIELDS.email).trim(),
            password: value(ADMIN_FIELDS.password),
            confirmPassword: value(ADMIN_FIELDS.confirmPassword),
        };
        const problems = {
            username: /^[A-Za-z0-9_.-]{3,30}$/.test(body.username) ? null : 'Use 3–30 letters, numbers, dots, dashes or underscores (no spaces).',
            email: emailProblem(body.email),
            password: body.password.length < 8 ? 'Use at least 8 characters.' : body.password.length > 72 ? 'Use at most 72 characters.' : null,
            confirmPassword: body.confirmPassword !== body.password ? "The passwords don't match." : null,
        };
        Object.entries(ADMIN_FIELDS).forEach(([field, id]) => setFieldError(root, id, problems[field]));
        const first = Object.keys(ADMIN_FIELDS).find(field => problems[field]);
        if (first) {
            root.querySelector(`#${ADMIN_FIELDS[first]}`).focus();
            return;
        }
        setBusy(button, true, 'Create administrator', 'Creating…');
        try {
            const r = await api('admin/admins', { method: 'POST', body });
            toast(r.message);
            message = { type: 'success', text: r.message };
            await load(); // repaints with the new admin listed and an empty form
        } catch (err) {
            if (ADMIN_FIELDS[err.field]) {
                setFieldError(root, ADMIN_FIELDS[err.field], err.message);
                root.querySelector(`#${ADMIN_FIELDS[err.field]}`).focus();
            }
            toast(err.message, { error: true });
            setBusy(button, false, 'Create administrator');
        }
    });

    root.addEventListener('click', async e => {
        const button = e.target.closest('[data-approve]');
        if (!button) return;
        button.disabled = true;
        try {
            const result = await api(`admin/vendors/${button.dataset.approve}/approve`, { method: 'POST' });
            message = { type: 'success', text: result.message };
        } catch (err) {
            message = { type: 'error', text: err.message };
        }
        await load();
    });
}
