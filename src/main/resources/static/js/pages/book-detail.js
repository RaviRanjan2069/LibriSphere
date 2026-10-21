// Book detail: cover, price, stock, quantity picker and add to cart.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { price } from '../format.js';
import { session, setCartCount } from '../session.js';
import { toast } from '../ui.js';
import { cover, emptyState, loading, setTitle } from '../components.js';
import { homeIcon, searchIcon, shieldIcon, truckIcon } from '../icons.js';

function stockLine(stock) {
    if (stock <= 0) return html`<div class="stock-line stock-out">Out of stock</div>`;
    if (stock <= 5) return html`<div class="stock-line stock-low">Only ${stock} left in stock</div>`;
    return html`<div class="stock-line stock-in">In stock · ${stock} available</div>`;
}

export default function bookDetail(root, { params, alive }) {
    root.className = 'container page';
    render(root, loading());
    const { user } = session;
    const canBuy = !user || user.role === 'BUYER';
    let book = null;
    let qty = 1;

    api('books/' + Number(params.id))
        .then(b => {
            if (!alive()) return;
            book = b;
            setTitle(book.title);
            render(root, html`
                <nav class="breadcrumb" aria-label="Breadcrumb">
                    <a href="/">Books</a>
                    <span aria-hidden="true">/</span>
                    <a href="/?category=${encodeURIComponent(book.category)}">${book.category}</a>
                    <span aria-hidden="true">/</span>
                    <span aria-current="page">${book.title}</span>
                </nav>

                <div class="detail">
                    <div class="detail-cover">${cover(book)}</div>
                    <div class="detail-info">
                        <span class="badge badge-accent">${book.category}</span>
                        <h1 class="detail-title">${book.title}</h1>
                        <p class="detail-author">by <strong>${book.author}</strong></p>

                        <div class="buy-box">
                            <div class="detail-price">${price(book.price)}</div>
                            ${stockLine(book.stock)}
                            ${!canBuy ? html`<p class="buy-note">You're signed in as ${user.role.toLowerCase()}. Sign in with a buyer account to purchase.</p>` : ''}
                            ${canBuy && book.stock > 0 ? html`
                                <div class="buy-row">
                                    <div class="qty" role="group" aria-label="Quantity">
                                        <button type="button" class="qty-btn" data-step="-1" aria-label="Decrease quantity">−</button>
                                        <input type="number" id="qty" class="qty-input" value="1" readonly aria-label="Quantity">
                                        <button type="button" class="qty-btn" data-step="1" aria-label="Increase quantity">+</button>
                                    </div>
                                    <button type="button" class="btn btn-primary btn-lg add-to-cart-btn" data-add>Add to cart</button>
                                </div>` : ''}
                            <ul class="perks">
                                <li>${truckIcon()} Free shipping, dispatched by the seller</li>
                                <li>${shieldIcon()} Cancel any time before it ships</li>
                            </ul>
                        </div>

                        <div class="seller-card">
                            <span class="seller-avatar">${homeIcon(20)}</span>
                            <div>
                                <div class="seller-label">Sold by</div>
                                <div class="seller-name">${book.vendorName}</div>
                            </div>
                            ${book.vendorEmail ? html`<a href="mailto:${book.vendorEmail}" class="btn btn-secondary btn-sm">Contact seller</a>` : ''}
                        </div>

                        ${book.description ? html`
                            <section class="detail-desc">
                                <h2>About this book</h2>
                                <p>${book.description}</p>
                            </section>` : ''}
                    </div>
                </div>`);
        })
        .catch(() => {
            if (!alive()) return;
            setTitle('Book not found');
            render(root, emptyState({
                icon: searchIcon(24),
                title: 'Book not found',
                text: 'It may have been removed by its seller.',
                actions: html`<a href="/" class="btn btn-primary">Browse books</a>`,
            }));
        });

    root.addEventListener('click', async e => {
        const step = e.target.closest('[data-step]');
        if (step) {
            qty = Math.min(book.stock, Math.max(1, qty + Number(step.dataset.step)));
            root.querySelector('#qty').value = qty;
            return;
        }
        const add = e.target.closest('[data-add]');
        if (!add) return;
        add.disabled = true;
        try {
            const result = await api('cart/items', { method: 'POST', body: { bookId: book.id, quantity: qty } });
            setCartCount(result.cartCount);
            toast(result.message, { action: { to: '/cart', label: 'View cart' } });
        } catch (err) {
            toast(err.message, { error: true });
        } finally {
            add.disabled = false;
        }
    });
}
