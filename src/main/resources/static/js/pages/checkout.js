// Checkout: contact + shipping form and the order summary; places the order through the API.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { navigate } from '../router.js';
import { plural, price } from '../format.js';
import { session, setCartCount } from '../session.js';
import { cover, loading, setTitle, showFlash } from '../components.js';
import { cardIcon, lockIcon } from '../icons.js';

export default function checkout(root, { alive }) {
    setTitle('Checkout');
    root.className = 'container page';
    render(root, html`
        <nav class="breadcrumb" aria-label="Breadcrumb">
            <a href="/cart">Cart</a>
            <span aria-hidden="true">/</span>
            <span aria-current="page">Checkout</span>
        </nav>
        <div class="page-header"><h1 class="page-title">Checkout</h1></div>
        <div data-flash></div>

        <form class="checkout-layout" id="checkout-form">
            <div class="card">
                <section class="step">
                    <div class="step-head"><span class="step-num">1</span><h2 class="step-title">Contact</h2></div>
                    <div class="form-row">
                        <div class="form-group">
                            <label class="form-label" for="buyerName">Full name</label>
                            <input type="text" class="form-input" id="buyerName" name="buyerName" required autocomplete="name" placeholder="Jane Doe" value="${session.user.username}">
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="contactNumber">Phone number</label>
                            <input type="tel" class="form-input" id="contactNumber" name="contactNumber" required autocomplete="tel" placeholder="+91 98765 43210">
                        </div>
                    </div>
                </section>

                <section class="step">
                    <div class="step-head"><span class="step-num">2</span><h2 class="step-title">Shipping address</h2></div>
                    <div class="form-group">
                        <label class="form-label" for="shippingAddress">Address</label>
                        <textarea class="form-input" id="shippingAddress" name="shippingAddress" rows="3" required autocomplete="street-address"
                                  placeholder="House no., street, city, state, PIN code"></textarea>
                    </div>
                </section>

                <section class="step">
                    <div class="step-head"><span class="step-num">3</span><h2 class="step-title">Payment</h2></div>
                    <div class="test-card">
                        <span class="test-card-icon">${cardIcon()}</span>
                        <div>
                            <div class="test-card-number">•••• •••• •••• 4444</div>
                            <div class="test-card-note">Demo store — no real payment is taken</div>
                        </div>
                        <span class="badge">Test mode</span>
                    </div>
                </section>
            </div>

            <aside class="card card-pad summary-card" data-summary>${loading()}</aside>
        </form>`);

    let placeLabel = '';

    api('cart')
        .then(cart => {
            if (!alive()) return;
            if (cart.items.length === 0) {
                navigate('/cart', { replace: true });
                return;
            }
            placeLabel = `Place order · ${price(cart.subtotal)}`;
            render(root.querySelector('[data-summary]'), html`
                <h2 class="card-title">Order summary</h2>
                <div class="summary-items">
                    ${cart.items.map(({ book, quantity, total }) => html`
                        <div class="summary-item">
                            ${cover(book, 'cover-xs')}
                            <div class="summary-item-body">
                                <div class="summary-item-title">${book.title}</div>
                                <div class="summary-item-meta">Qty ${quantity} · ${price(book.price)} each</div>
                            </div>
                            <span class="summary-item-price">${price(total)}</span>
                        </div>`)}
                </div>
                <div class="summary-row"><span>Subtotal (${plural(cart.count, 'item')})</span><span>${price(cart.subtotal)}</span></div>
                <div class="summary-row"><span>Shipping</span><span class="free">Free</span></div>
                <div class="summary-row summary-total"><span>Total</span><span>${price(cart.subtotal)}</span></div>
                <button type="submit" class="btn btn-primary btn-lg btn-block summary-cta" id="place-order">${placeLabel}</button>
                <p class="summary-note">${lockIcon()} Your details are only shared with the sellers you buy from.</p>`);
        })
        .catch(e => {
            if (!alive()) return;
            showFlash(root, { type: 'error', text: e.message });
            root.querySelector('[data-summary]').replaceChildren();
        });

    const form = root.querySelector('#checkout-form');
    form.addEventListener('submit', async e => {
        e.preventDefault();
        const button = root.querySelector('#place-order');
        if (!button || button.disabled) return;
        button.disabled = true;
        button.textContent = 'Placing order…';
        showFlash(root, null);
        const body = Object.fromEntries(new FormData(form));
        try {
            const order = await api('orders', { method: 'POST', body });
            setCartCount(0);
            navigate(`/orders/${order.id}/confirmation`);
        } catch (err) {
            showFlash(root, { type: 'error', text: err.message });
            button.disabled = false;
            button.textContent = placeLabel;
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    });
}
