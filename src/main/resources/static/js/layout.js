// Page shells: the site layout (header, page, footer) and the split layout for sign-in and registration.
import { html, render } from './dom.js';
import { navigate } from './router.js';
import { onSessionChange, session, signOut } from './session.js';
import { avatar } from './components.js';
import {
    bagIcon, checkIcon, githubIcon, instagramIcon, leetcodeIcon, linkedinIcon, logoMark, mailIcon, moonIcon,
    phoneIcon, signOutIcon, sunIcon, whatsappIcon,
} from './icons.js';

// Footer Social and Contact links: [label, href, icon]. An item with an empty href is not shown.
const SOCIAL_LINKS = [
    ['GitHub', 'https://github.com/RaviRanjan2069', githubIcon],
    ['LinkedIn', 'https://www.linkedin.com/in/ravi-ranjan-320b56261', linkedinIcon],
    ['LeetCode', '', leetcodeIcon], // e.g. 'https://leetcode.com/u/<username>'
    ['Instagram', 'https://www.instagram.com/ravi.rajput.2003/', instagramIcon],
];
const CONTACT_LINKS = [
    // Gmail's compose page rather than mailto:, which does nothing on computers without an email app set up
    ['Email', 'https://mail.google.com/mail/?view=cm&fs=1&to=mentora.smartlearning@gmail.com', mailIcon],
    ['WhatsApp', 'https://wa.me/919875425127', whatsappIcon],
    ['+91 98754 25127', 'tel:+919875425127', phoneIcon],
];

const app = document.getElementById('app');
let mode = null; // 'site' | 'auth'
let header = null;
let footer = null;
let main = null;
let shownCartCount = 0;

const logo = () => html`<a href="/" class="logo" aria-label="LibriSphere home">${logoMark()}<span class="logo-word">LibriSphere</span></a>`;

const themeToggle = () => html`
    <button type="button" class="icon-btn theme-toggle" data-theme-toggle aria-label="Toggle dark mode" title="Toggle dark mode">${moonIcon()}${sunIcon()}</button>`;

function toggleTheme() {
    const root = document.documentElement;
    const current = root.getAttribute('data-theme')
        || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    const next = current === 'dark' ? 'light' : 'dark';
    root.setAttribute('data-theme', next);
    try { localStorage.setItem('theme', next); } catch { /* storage unavailable */ }
}

/** Matches like a nav link: "/orders" is active on /orders and /orders/5/confirmation; `end` requires an exact match. */
const isActive = (to, end = false) => location.pathname === to || (!end && location.pathname.startsWith(to + '/'));

function navLink(to, label, end = false) {
    const active = isActive(to, end);
    return html`<li><a href="${to}" class="nav-link ${active ? 'active' : ''}" ${active ? html`aria-current="page"` : ''}>${label}</a></li>`;
}

function renderHeader() {
    const { user, cartCount } = session;
    const accountActive = isActive('/account');
    render(header, html`
        <nav class="navbar" aria-label="Main">
            ${logo()}
            <ul class="nav-links">
                ${navLink('/', 'Browse', true)}
                ${user?.role === 'BUYER' ? navLink('/orders', 'My Orders') : ''}
                ${user?.role === 'VENDOR' ? navLink('/seller', 'Seller Dashboard') : ''}
                ${user?.role === 'ADMIN' ? navLink('/admin', 'Admin') : ''}
                ${user && user.role !== 'ADMIN' ? navLink('/support', 'Help & support') : ''}
            </ul>
            <div class="nav-actions">
                ${themeToggle()}
                ${!user || user.role === 'BUYER' ? html`
                    <a href="/cart" class="icon-btn" aria-label="Shopping cart" title="Cart">
                        ${bagIcon(19)}
                        <span class="cart-badge" ${cartCount > 0 ? '' : 'hidden'}>${cartCount}</span>
                    </a>` : ''}
                <span class="nav-divider"></span>
                ${user ? html`
                    <a href="/account" class="user-chip ${accountActive ? 'active' : ''}" ${accountActive ? html`aria-current="page"` : ''} aria-label="Your account" title="Your account">
                        ${avatar({ url: user.avatarUrl, name: user.username, size: 'sm' })}
                        <span class="user-meta">
                            <span class="user-name">${user.role === 'VENDOR' && user.vendorName ? user.vendorName : user.username}</span>
                            <span class="user-role">${user.role.toLowerCase()}</span>
                        </span>
                    </a>
                    <button type="button" class="icon-btn" data-sign-out aria-label="Sign out" title="Sign out">${signOutIcon()}</button>` : html`
                    <a href="/login" class="btn btn-ghost btn-sm">Sign in</a>
                    <a href="/register" class="btn btn-primary btn-sm nav-register">Create account</a>`}
            </div>
        </nav>`);

    // Re-run the badge "bump" animation whenever the count changes
    if (cartCount !== shownCartCount) {
        shownCartCount = cartCount;
        const badge = header.querySelector('.cart-badge');
        if (badge && cartCount > 0) {
            badge.classList.add('bump');
            setTimeout(() => badge.classList.remove('bump'), 400);
        }
    }
}

function renderFooter() {
    const { user } = session;
    const role = user?.role;
    const item = (to, label, show = true) => (show ? html`<li><a href="${to}">${label}</a></li>` : '');
    // Outside links open in a new tab (and so are left to the browser by the router); mailto:/tel: open the app
    const iconColumn = (title, links) => {
        const shown = links.filter(([, href]) => href);
        return shown.length === 0 ? '' : html`
            <div>
                <h4 class="footer-title">${title}</h4>
                <ul class="footer-links">
                    ${shown.map(([label, href, icon]) => html`
                        <li><a href="${href}" class="footer-icon-link" ${/^https?:/.test(href) ? html`target="_blank" rel="noopener noreferrer"` : ''}>${icon()}<span>${label}</span></a></li>`)}
                </ul>
            </div>`;
    };
    render(footer, html`
        <div class="container footer-inner">
            <div>
                <h4 class="footer-title">Shop</h4>
                <ul class="footer-links">
                    ${item('/', 'All books')}
                    ${item('/?category=Fiction', 'Fiction')}
                    ${item('/?category=Technology', 'Technology')}
                    ${item('/?category=Business', 'Business')}
                </ul>
            </div>
            <div>
                <h4 class="footer-title">Account</h4>
                <ul class="footer-links">
                    ${item('/login', 'Sign in', !user)}
                    ${item('/register', 'Create account', !user)}
                    ${item('/cart', 'Cart', !user || role === 'BUYER')}
                    ${item('/orders', 'My orders', role === 'BUYER')}
                    ${item('/orders?tab=cancelled', 'Cancellations', role === 'BUYER')}
                    ${item('/seller', 'Seller dashboard', role === 'VENDOR')}
                    ${item('/seller/books/new', 'List a book', role === 'VENDOR')}
                    ${item('/register?role=VENDOR', 'Become a seller', !user)}
                    ${item('/admin', 'Admin dashboard', role === 'ADMIN')}
                    ${item('/account', 'Your account', Boolean(user))}
                    ${item('/support', 'Help & support', role !== 'ADMIN')}
                    ${user ? html`<li><a href="/login" data-sign-out>Sign out</a></li>` : ''}
                </ul>
            </div>
            ${iconColumn('Social', SOCIAL_LINKS)}
            ${iconColumn('Contact', CONTACT_LINKS)}
        </div>
        <div class="container">
            <div class="footer-bottom">
                <span>&copy; 2026 LibriSphere</span>
                <span>Prices in Indian Rupees (₹)</span>
            </div>
        </div>`);
}

/** Shows the standard header + footer shell and returns a fresh, empty <main> for the page. */
export function siteLayout() {
    const fresh = document.createElement('main');
    if (mode !== 'site') {
        mode = 'site';
        header = document.createElement('header');
        header.className = 'app-header';
        footer = document.createElement('footer');
        footer.className = 'app-footer';
        app.replaceChildren(header, fresh, footer);
    } else {
        main.replaceWith(fresh);
    }
    main = fresh;
    renderHeader();
    renderFooter();
    window.scrollTo(0, 0);
    return main;
}

/** Shows the split sign-in layout and returns its (empty) form box. */
export function authLayout() {
    mode = 'auth';
    render(app, html`
        <div class="auth-page">
            <aside class="auth-aside">
                <div>
                    ${logo()}
                    <h2>Every bookshop's shelf, <em>in one place.</em></h2>
                    <p class="auth-aside-sub">Shop from independent sellers with a single cart, or open your own storefront and reach new readers.</p>
                    <ul class="auth-list">
                        <li>${checkIcon(18)} One checkout for books from many sellers</li>
                        <li>${checkIcon(18)} Track every item from order to doorstep</li>
                        <li>${checkIcon(18)} Sellers manage stock and orders from a simple dashboard</li>
                    </ul>
                </div>
                <blockquote class="auth-quote">
                    <p>“A reader lives a thousand lives before he dies. The man who never reads lives only one.”</p>
                    <cite>George R.R. Martin</cite>
                </blockquote>
            </aside>
            <main class="auth-main">
                <div class="auth-main-top">
                    <a href="/" class="btn btn-ghost btn-sm">Back to store</a>
                    ${themeToggle()}
                </div>
                <div class="auth-box"></div>
            </main>
        </div>`);
    return app.querySelector('.auth-box');
}

/** The "Sign in | Create account | Admin" switch at the top of the sign-in and sign-up forms. */
export function authTabs(active) {
    const tab = (to, label, key) => html`
        <a href="${to}" class="auth-tab ${active === key ? 'active' : ''}" ${active === key ? html`aria-current="page"` : ''}>${label}</a>`;
    return html`
        <nav class="auth-tabs" aria-label="Account">
            ${tab('/login', 'Sign in', 'login')}${tab('/register', 'Create account', 'register')}${tab('/login?as=admin', 'Admin', 'admin')}
        </nav>`;
}

/** Wires up the theme toggle and sign-out buttons, and keeps the header in step with the session. */
export function initLayout() {
    document.addEventListener('click', async e => {
        if (e.target.closest('[data-theme-toggle]')) {
            toggleTheme();
            return;
        }
        if (e.target.closest('[data-sign-out]')) {
            e.preventDefault();
            await signOut();
            navigate('/login', { flash: { type: 'success', text: 'You have been signed out.' } });
        }
    });
    onSessionChange(() => {
        if (mode !== 'site') return;
        renderHeader();
        renderFooter();
    });
}
