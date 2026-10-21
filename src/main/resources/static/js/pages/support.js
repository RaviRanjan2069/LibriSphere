// Help & support: raise a problem or complaint, and follow your requests and the team's replies.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { formatDate, labelOf, SUPPORT_AREAS, SUPPORT_PRIORITIES, SUPPORT_TOPICS } from '../format.js';
import { confirm, toast } from '../ui.js';
import { emptyState, fieldError, loading, setBusy, setFieldError, setTitle } from '../components.js';
import { copyIcon, headsetIcon, mailIcon } from '../icons.js';

const FIELDS = ['topic', 'subject', 'description'];
const MAX_DESCRIPTION = 5000;

const gmailCompose = email => `https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(email)}`;
const options = (list, selected) => list.map(([value, label]) => html`<option value="${value}" ${value === selected ? 'selected' : ''}>${label}</option>`);
const snippet = text => (text.length > 160 ? `${text.slice(0, 160).trimEnd()}…` : text);

const statusPill = status => (status === 'RESOLVED'
    ? html`<span class="status-badge status-delivered">Resolved</span>`
    : html`<span class="status-badge status-pending">Open</span>`);

function requestsTable(requests) {
    if (requests.length === 0) {
        return emptyState({ icon: headsetIcon(24), text: "You haven't sent any requests yet. Anything you send appears here with our reply." });
    }
    return html`
        <div class="table-scroll">
            <table class="data-table stack support-table">
                <thead><tr><th>Reference</th><th>Subject</th><th>Topic</th><th>Priority</th><th>Submitted</th><th>Status</th></tr></thead>
                <tbody>
                    ${requests.map(r => html`
                        <tr>
                            <td data-label="Reference"><span class="mono strong">${r.reference}</span></td>
                            <td class="cell-full">
                                <div class="cell-title">${r.subject}</div>
                                <div class="cell-sub">${snippet(r.description)}</div>
                                ${r.resolution ? html`
                                    <div class="support-reply"><strong>Resolved ${formatDate(r.resolvedAt)}:</strong> ${r.resolution}</div>` : ''}
                            </td>
                            <td data-label="Topic" class="strong">${labelOf(SUPPORT_TOPICS, r.topic)}</td>
                            <td data-label="Priority">${labelOf(SUPPORT_PRIORITIES, r.priority)}</td>
                            <td data-label="Submitted" class="nowrap">${formatDate(r.createdAt)}</td>
                            <td data-label="Status">
                                <div class="support-status">
                                    ${statusPill(r.status)}
                                    ${r.status === 'RESOLVED' ? html`<button type="button" class="link-btn support-link" data-reopen="${r.id}">Reopen</button>` : ''}
                                    <button type="button" class="link-btn support-link is-muted" data-delete="${r.id}">Delete</button>
                                </div>
                            </td>
                        </tr>`)}
                </tbody>
            </table>
        </div>`;
}

export default function support(root, { alive }) {
    setTitle('Help & support');
    root.className = 'container page';
    let data = null;

    render(root, html`
        <div class="page-header">
            <div>
                <h1 class="page-title">Help & support</h1>
                <p class="page-subtitle">Report a problem or raise a complaint with the LibriSphere team</p>
            </div>
        </div>
        <div data-body>${loading()}</div>`);

    const paint = () => {
        const { supportEmail, replyTo, openCount, requests } = data;
        render(root.querySelector('[data-body]'), html`
            <dl class="support-stats">
                <div><dt>Email</dt><dd>${supportEmail}</dd></div>
                <div><dt>Typical reply</dt><dd>Within 2 working days</dd></div>
                <div><dt>Your open requests</dt><dd data-open-count>${openCount}</dd></div>
            </dl>

            <div class="support-grid">
                <section class="card profile-card" aria-labelledby="new-request-title">
                    <div class="support-card-head">
                        <h2 class="profile-card-title" id="new-request-title">New request</h2>
                        <span class="support-required">Fields marked * are required</span>
                    </div>
                    <form id="support-form" novalidate>
                        <div class="support-row3">
                            <div class="form-group">
                                <label class="form-label" for="topic">Topic *</label>
                                <select class="form-input" id="topic" name="topic">
                                    <option value="">Choose a topic</option>
                                    ${options(SUPPORT_TOPICS, '')}
                                </select>
                                ${fieldError('topic-error')}
                            </div>
                            <div class="form-group">
                                <label class="form-label" for="priority">Priority</label>
                                <select class="form-input" id="priority" name="priority">${options(SUPPORT_PRIORITIES, 'NORMAL')}</select>
                            </div>
                            <div class="form-group">
                                <label class="form-label" for="area">Where in LibriSphere</label>
                                <select class="form-input" id="area" name="area">${options(SUPPORT_AREAS, 'NONE')}</select>
                            </div>
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="subject">Subject *</label>
                            <input type="text" class="form-input" id="subject" name="subject" maxlength="120"
                                   placeholder="A short summary, e.g. Order #LBS-12 arrived with a damaged book">
                            ${fieldError('subject-error')}
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="description">Description *</label>
                            <textarea class="form-input" id="description" name="description" rows="6" maxlength="${MAX_DESCRIPTION}"
                                      placeholder="What were you doing, what did you expect, and what happened instead? Include your order number (#LBS-…) or the book title if it's about one."></textarea>
                            <div class="support-counter"><span data-count>0</span> / ${MAX_DESCRIPTION}</div>
                            ${fieldError('description-error')}
                        </div>
                        <div class="support-form-foot">
                            <p>We reply to <strong>${replyTo}</strong>. Please do not include passwords or card details.</p>
                            <button type="submit" class="btn btn-primary" data-submit>Submit request</button>
                        </div>
                    </form>
                </section>

                <div class="support-side">
                    <section class="card profile-card" aria-labelledby="contact-title">
                        <h2 class="profile-card-title" id="contact-title">Contact</h2>
                        <p class="profile-note">Prefer email? Write to us directly:</p>
                        <p class="support-email">${supportEmail}</p>
                        <div class="support-email-actions">
                            <a href="${gmailCompose(supportEmail)}" target="_blank" rel="noopener noreferrer" class="btn btn-primary btn-block">${mailIcon()} Email us</a>
                            <button type="button" class="btn btn-secondary support-copy" data-copy="${supportEmail}" aria-label="Copy email address" title="Copy email address">${copyIcon()}</button>
                        </div>
                        <a href="mailto:${supportEmail}" class="support-mailapp">Use your mail app instead</a>
                    </section>
                    <section class="card profile-card" aria-labelledby="tips-title">
                        <h2 class="profile-card-title" id="tips-title">Before you send</h2>
                        <ul class="support-tips">
                            <li>Reload the page with Ctrl + F5; many display problems go away.</li>
                            <li>For an order, include its number (#LBS-…) from My Orders.</li>
                            <li>For sign-in problems, try Forgot password on the sign-in page first.</li>
                            <li>Mention your reference (SUP-…) if you write again.</li>
                        </ul>
                    </section>
                </div>
            </div>

            <section class="card profile-card support-list" aria-labelledby="requests-title">
                <h2 class="profile-card-title" id="requests-title">Your requests</h2>
                <div data-requests>${requestsTable(requests)}</div>
            </section>`);
    };

    const refreshList = () => {
        render(root.querySelector('[data-requests]'), requestsTable(data.requests));
        root.querySelector('[data-open-count]').textContent = data.openCount;
    };

    const load = async () => {
        const result = await api('support');
        if (!alive()) return false;
        data = result;
        return true;
    };

    load().then(ok => { if (ok) paint(); }).catch(e => {
        if (alive()) render(root.querySelector('[data-body]'), emptyState({ title: "Couldn't load your requests", text: e.message }));
    });

    root.addEventListener('input', e => {
        if (FIELDS.includes(e.target.id)) setFieldError(root, e.target.id, null);
        if (e.target.id === 'description') root.querySelector('[data-count]').textContent = e.target.value.length;
    });
    root.addEventListener('change', e => {
        if (e.target.id === 'topic') setFieldError(root, 'topic', null);
    });

    root.addEventListener('submit', async e => {
        if (e.target.id !== 'support-form') return;
        e.preventDefault();
        const form = e.target;
        const button = root.querySelector('[data-submit]');
        if (button.disabled) return;
        const body = Object.fromEntries(new FormData(form));
        body.subject = body.subject.trim();
        body.description = body.description.trim();
        const problems = {
            topic: body.topic ? null : 'Choose a topic.',
            subject: body.subject.length < 4 ? 'Add a short summary (at least 4 characters).' : null,
            description: body.description.length < 10 ? 'Describe the problem in a few words (at least 10 characters).' : null,
        };
        FIELDS.forEach(name => setFieldError(root, name, problems[name]));
        const first = FIELDS.find(name => problems[name]);
        if (first) {
            root.querySelector(`#${first}`).focus();
            return;
        }
        setBusy(button, true, 'Submit request', 'Sending…');
        try {
            const r = await api('support', { method: 'POST', body });
            toast(r.message);
            form.reset();
            root.querySelector('[data-count]').textContent = '0';
            if (await load()) refreshList();
        } catch (err) {
            if (FIELDS.includes(err.field)) setFieldError(root, err.field, err.message);
            toast(err.message, { error: true });
        }
        setBusy(button, false, 'Submit request');
    });

    root.addEventListener('click', async e => {
        const copy = e.target.closest('[data-copy]');
        if (copy) {
            try {
                await navigator.clipboard.writeText(copy.dataset.copy);
                toast('Email address copied.');
            } catch {
                toast(`Couldn't copy. The address is ${copy.dataset.copy}`, { error: true });
            }
            return;
        }
        const reopen = e.target.closest('[data-reopen]');
        const del = e.target.closest('[data-delete]');
        if (!reopen && !del) return;
        try {
            if (reopen) {
                toast((await api(`support/${reopen.dataset.reopen}/reopen`, { method: 'POST' })).message);
            } else {
                const ok = await confirm({ title: 'Delete this request?', text: 'It will be removed from your list. This can’t be undone.', action: 'Delete request' });
                if (!ok) return;
                toast((await api(`support/${del.dataset.delete}`, { method: 'DELETE' })).message);
            }
            if (await load()) refreshList();
        } catch (err) {
            toast(err.message, { error: true });
        }
    });
}
