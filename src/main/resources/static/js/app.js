// Entry point: routes, the role guard, and start-up.
import { html, render } from './dom.js';
import { addRoute, navigate, startRouter } from './router.js';
import { refreshSession, session } from './session.js';
import { authLayout, initLayout, siteLayout } from './layout.js';
import { emptyState, setTitle, watchImages } from './components.js';
import { homeFor } from './format.js';
import { searchIcon } from './icons.js';
import home from './pages/home.js';
import bookDetail from './pages/book-detail.js';
import cart from './pages/cart.js';
import checkout from './pages/checkout.js';
import orderSuccess from './pages/order-success.js';
import orders from './pages/orders.js';
import login from './pages/login.js';
import register from './pages/register.js';
import forgotPassword from './pages/forgot-password.js';
import resetPassword from './pages/reset-password.js';
import sellerDashboard from './pages/seller-dashboard.js';
import bookForm from './pages/book-form.js';
import adminDashboard from './pages/admin-dashboard.js';
import account from './pages/account.js';
import support from './pages/support.js';

function notFound(root) {
    setTitle('Page not found');
    root.className = 'container page';
    render(root, emptyState({
        icon: searchIcon(24),
        title: 'Page not found',
        text: "The page you were looking for doesn't exist.",
        actions: html`<a href="/" class="btn btn-primary">Back to the store</a>`,
    }));
}

// Keep these paths in sync with SPA_ROUTES in WebConfig on the server, which serves index.html for them.
// `role`: the page needs a signed-in user with that role (true = any signed-in user).
addRoute('/login', login, { layout: 'auth' });
addRoute('/register', register, { layout: 'auth' });
addRoute('/forgot-password', forgotPassword, { layout: 'auth' });
addRoute('/reset-password', resetPassword, { layout: 'auth' });
addRoute('/', home);
addRoute('/book/:id', bookDetail);
addRoute('/cart', cart);
addRoute('/checkout', checkout, { role: 'BUYER' });
addRoute('/orders', orders, { role: 'BUYER' });
addRoute('/orders/:id/confirmation', orderSuccess, { role: 'BUYER' });
addRoute('/seller', sellerDashboard, { role: 'VENDOR' });
addRoute('/seller/books/new', bookForm, { role: 'VENDOR' });
addRoute('/seller/books/:id/edit', bookForm, { role: 'VENDOR' });
addRoute('/admin', adminDashboard, { role: 'ADMIN' });
addRoute('/account', account, { role: true });
addRoute('/support', support, { role: true });
addRoute('*', notFound);

const ROLE_MESSAGES = {
    BUYER: 'That page is for buyer accounts.',
    VENDOR: 'The seller dashboard is only available to seller accounts.',
    ADMIN: 'The admin area is only available to administrators.',
};

let cleanup = null;

/** Shows a route's page, or redirects with an explanation if the user may not see it. */
function show(route, ctx) {
    if (cleanup) cleanup();
    cleanup = null;

    const { role, layout } = route.options;
    const { user } = session;
    if (role && !user) {
        navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`,
            { replace: true, flash: { type: 'info', text: 'Please sign in to continue.' } });
        return;
    }
    if (typeof role === 'string' && user.role !== role) {
        navigate(homeFor(user), { replace: true, flash: { type: 'error', text: ROLE_MESSAGES[role] } });
        return;
    }

    const root = layout === 'auth' ? authLayout() : siteLayout();
    // A page renders into `root` and may return a function that tidies up when the user leaves
    cleanup = route.page(root, ctx) || null;
}

watchImages();
initLayout();
refreshSession().then(() => startRouter(show));
