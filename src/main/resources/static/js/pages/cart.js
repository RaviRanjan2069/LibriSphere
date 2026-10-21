// Cart: change quantities, remove items, go to checkout. Works for guests too.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { navigate } from '../router.js';
import { homeFor, plural, price } from '../format.js';
import { session, setCartCount } from '../session.js';
import { cover, emptyState, flash, loading, setTitle } from '../components.js';
import { arrowIcon, bagIcon, trashIcon } from '../icons.js';

export default function cart(root, { flash: initialFlash, alive }) {
    const { user } = session;
    if (user && user.role !== 'BUYER') {
        navigate(homeFor(user), { replace: true });
        return;
    }
    setTitle('Your cart');
    root.className = 'container page';
    let data = null;
    let message = initialFlash;

    const items = () => data.items.map(({ book, quantity, total }) => html`
        <div class="cart-item">
            <a href="/book/${book.id}">${cover(book, 'cover-xs')}</a>
            <div class="cart-item-info">
                <div class="cart-item-title"><a href="/book/${book.id}">${book.title}</a></div>
                <div class="cart-item-meta">${book.author}</div>
                <div class="cart-item-meta">Sold by ${book.vendorName}</div>
                <div class="cart-item-unit">
                    ${price(book.price)} each
                    ${book.stock < quantity ? html` · <span class="text-danger">only ${book.stock} left</span>` : ''}
                </div>
            </div>
            <div class="cart-item-qty">
                <div class="qty qty-sm" role="group" aria-label="Quantity of ${book.title}">
                    <button type="button" class="qty-btn" ${quantity <= 1 ? 'disabled' : ''}
                            data-set-qty="${book.id}" data-qty-to="${quantity - 1}" aria-label="Decrease quantity">−</button>
                    <span class="qty-input" data-qty="${book.id}" aria-live="polite">${quantity}</span>
                    <button type="button" class="qty-btn" ${quantity >= book.stock ? 'disabled' : ''}
                            data-set-qty="${book.id}" data-qty-to="${quantity + 1}" aria-label="Increase quantity">+</button>
                </div>
            </div>
            <div class="cart-item-total">${price(total)}</div>
            <div class="cart-item-remove">
                <button type="button" class="icon-btn" data-remove="${book.id}" aria-label="Remove ${book.title} from cart" title="Remove">${trashIcon()}</button>
            </div>
        </div>`);

    const paint = () => render(root, html`
        <div class="page-header">
            <div>
                <h1 class="page-title">Your cart</h1>
                ${data?.count > 0 ? html`<p class="page-subtitle">${plural(data.count, 'item')} ready for checkout</p>` : ''}
            </div>
        </div>
        ${flash(message)}
        ${!data ? loading() : ''}
        ${data && data.items.length === 0 ? emptyState({
            icon: bagIcon(24),
            title: 'Your cart is empty',
            text: 'Books you add will appear here.',
            actions: html`<a href="/" class="btn btn-primary">Browse books</a>`,
        }) : ''}
        ${data && data.items.length > 0 ? html`
            <div class="cart-layout">
                <section class="card cart-list" aria-label="Cart items">
                    <div class="cart-list-head">
                        <span>Item</span>
                        <a href="/">Continue shopping</a>
                    </div>
                    ${items()}
                </section>
                <aside class="card card-pad summary-card">
                    <h2 class="card-title">Order summary</h2>
                    <div class="summary-row"><span>Subtotal (${plural(data.count, 'item')})</span><span>${price(data.subtotal)}</span></div>
                    <div class="summary-row"><span>Shipping</span><span class="free">Free</span></div>
                    <div class="summary-row summary-total"><span>Total</span><span>${price(data.subtotal)}</span></div>
                    <a href="/checkout" class="btn btn-primary btn-lg btn-block summary-cta">Checkout ${arrowIcon()}</a>
                    <p class="summary-note">${user ? 'Each seller ships their items separately.' : "You'll be asked to sign in before paying."}</p>
                </aside>
            </div>` : ''}`);

    const apply = async request => {
        try {
            const next = await request;
            if (!alive()) return;
            data = next;
            setCartCount(next.count);
        } catch (e) {
            if (!alive()) return;
            message = { type: 'error', text: e.message };
        }
        paint();
    };

    paint();
    apply(api('cart'));

    root.addEventListener('click', e => {
        const qty = e.target.closest('[data-set-qty]');
        if (qty) {
            apply(api(`cart/items/${qty.dataset.setQty}`, { method: 'PUT', body: { quantity: Number(qty.dataset.qtyTo) } }));
            return;
        }
        const remove = e.target.closest('[data-remove]');
        if (remove) apply(api(`cart/items/${remove.dataset.remove}`, { method: 'DELETE' }));
    });
}
