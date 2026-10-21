// Storefront: hero, search, category chips, sorting and the book grid.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { navigate } from '../router.js';
import { plural, price } from '../format.js';
import { session, setCartCount } from '../session.js';
import { toast } from '../ui.js';
import { alert, cover, emptyState, flash, loading, setTitle } from '../components.js';
import { homeIcon, searchIcon, shieldIcon, truckIcon } from '../icons.js';

// Stored with the site (static/covers/<ISBN>.jpg), so they load even where Open Library is unreachable
const HERO_COVERS = ['9780441013593', '9780593135204', '9780141439518', '9780374533557', '9780804139021', '9788173711466'];
const SORTS = [
    ['featured', 'Featured'], ['newest', 'Newest'], ['price-asc', 'Price: low to high'],
    ['price-desc', 'Price: high to low'], ['title', 'Title A–Z'],
];

const hero = showSell => html`
    <section class="hero">
        <div class="container hero-inner">
            <div>
                <span class="eyebrow">Independent booksellers, one shop</span>
                <h1 class="hero-title">Find your next <em>great read</em> from shops that love books.</h1>
                <p class="hero-sub">Browse titles listed by independent bookstores and publishers, then check out once — each seller ships their part of your order.</p>
                <div class="hero-cta">
                    <a href="#catalog" class="btn btn-primary btn-lg">Browse the catalogue</a>
                    ${showSell ? html`<a href="/register?role=VENDOR" class="btn btn-secondary btn-lg">Sell your books</a>` : ''}
                </div>
                <ul class="hero-points">
                    <li>${truckIcon()} Free shipping on every order</li>
                    <li>${homeIcon()} Sold by independent shops</li>
                    <li>${shieldIcon()} Track every item you order</li>
                </ul>
            </div>
            <div class="hero-covers" aria-hidden="true">
                ${HERO_COVERS.map(id => html`<div class="cover"><img src="/covers/${id}.jpg" alt=""></div>`)}
            </div>
        </div>
    </section>`;

const bookCard = (book, canBuy) => html`
    <article class="book-card">
        <a href="/book/${book.id}" class="book-card-link">
            ${book.stock <= 0 ? html`<span class="cover-flag cover-flag-out">Sold out</span>` : ''}
            ${book.stock > 0 && book.stock <= 5 ? html`<span class="cover-flag cover-flag-low">Only ${book.stock} left</span>` : ''}
            ${cover(book)}
        </a>
        <div class="book-card-body">
            <span class="book-category">${book.category}</span>
            <h3 class="book-title"><a href="/book/${book.id}">${book.title}</a></h3>
            <span class="book-author">${book.author}</span>
            <span class="book-vendor">Sold by ${book.vendorName}</span>
            <div class="book-card-foot">
                <span class="price">${price(book.price)}</span>
                ${canBuy && book.stock > 0 ? html`<button type="button" class="btn btn-secondary btn-sm" data-add="${book.id}">Add to cart</button>` : ''}
            </div>
        </div>
    </article>`;

export default function home(root, { query, flash: message, alive }) {
    const search = (query.get('search') || '').trim();
    const category = query.get('category') || 'All';
    const sort = query.get('sort') || 'featured';
    const filtered = search !== '' || category !== 'All';
    const heading = search ? `Results for “${search}”` : category === 'All' ? 'All books' : category;
    const canBuy = !session.user || session.user.role === 'BUYER';
    setTitle(filtered ? heading : 'Browse books');

    /** Builds a ?query for the catalogue, dropping defaults. */
    const linkFor = changes => {
        const next = { search, category, sort, ...changes };
        const qs = new URLSearchParams();
        if (next.search) qs.set('search', next.search);
        if (next.category && next.category !== 'All') qs.set('category', next.category);
        if (next.sort && next.sort !== 'featured') qs.set('sort', next.sort);
        const s = qs.toString();
        return s ? `/?${s}` : '/';
    };

    const chips = categories => ['All', ...categories].map(cat => html`
        <a href="${linkFor({ category: cat })}" class="chip ${category === cat ? 'active' : ''}">${cat}</a>`);

    render(root, html`
        ${filtered ? '' : hero(!session.user)}
        <section class="container catalog" id="catalog">
            ${flash(message)}
            <div data-error></div>
            <div class="catalog-toolbar">
                <form class="search-form" role="search">
                    ${searchIcon(17, 'search-icon')}
                    <label for="search" class="sr-only">Search books</label>
                    <input type="search" id="search" class="form-input" placeholder="Search by title, author or category" value="${search}">
                    <button type="submit" class="btn btn-secondary">Search</button>
                </form>
                <nav class="chip-row" aria-label="Categories" data-chips>${chips([])}</nav>
            </div>
            <div class="results-bar">
                <h2 id="results-title">${heading}</h2>
                <div class="results-tools">
                    <span class="section-meta">
                        <span data-count></span>
                        ${filtered ? html`<a href="/">&nbsp;· Clear filters</a>` : ''}
                    </span>
                    <div class="sort-form">
                        <label for="sort">Sort</label>
                        <select id="sort" class="form-input form-input-sm">
                            ${SORTS.map(([value, label]) => html`<option value="${value}" ${value === sort ? 'selected' : ''}>${label}</option>`)}
                        </select>
                    </div>
                </div>
            </div>
            <div data-results>${loading('Loading books…')}</div>
        </section>`);

    const results = root.querySelector('[data-results]');
    const qs = new URLSearchParams({ sort });
    if (search) qs.set('search', search);
    if (category !== 'All') qs.set('category', category);
    api('books?' + qs)
        .then(data => {
            if (!alive()) return;
            render(root.querySelector('[data-chips]'), chips(data.categories || []));
            render(root.querySelector('[data-count]'), plural(data.books.length, 'title'));
            render(results, data.books.length === 0
                ? emptyState({
                    icon: searchIcon(24),
                    title: 'No books match your search',
                    text: 'Try a different title or author, or browse another category.',
                    actions: html`<a href="/" class="btn btn-secondary">Clear filters</a>`,
                })
                : html`<div class="book-grid">${data.books.map(book => bookCard(book, canBuy))}</div>`);
        })
        .catch(e => {
            if (!alive()) return;
            render(root.querySelector('[data-error]'), alert('error', e.message));
            results.replaceChildren();
        });

    root.addEventListener('submit', e => {
        if (!e.target.matches('.search-form')) return;
        e.preventDefault();
        navigate(linkFor({ search: root.querySelector('#search').value.trim() }));
    });
    root.addEventListener('change', e => {
        if (e.target.id === 'sort') navigate(linkFor({ sort: e.target.value }));
    });
    root.addEventListener('click', async e => {
        const button = e.target.closest('[data-add]');
        if (!button) return;
        button.disabled = true;
        try {
            const result = await api('cart/items', { method: 'POST', body: { bookId: Number(button.dataset.add), quantity: 1 } });
            setCartCount(result.cartCount);
            toast(result.message, { action: { to: '/cart', label: 'View cart' } });
        } catch (err) {
            toast(err.message, { error: true });
        }
        button.disabled = false;
    });
}
