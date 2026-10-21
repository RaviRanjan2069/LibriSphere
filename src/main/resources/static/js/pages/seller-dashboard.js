// Seller dashboard: sales figures, orders to fulfil (with status updates) and listings.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { replaceQuery } from '../router.js';
import { capitalize, formatDate, plural, price } from '../format.js';
import { confirm } from '../ui.js';
import { avatar, cover, emptyState, flash, loading, setTitle, statCard, statusBadge } from '../components.js';
import { bagIcon, bookIcon, gridIcon, plusIcon, rupeeIcon } from '../icons.js';

const TABS = [['pending', 'To ship'], ['shipped', 'Shipped'], ['delivered', 'Delivered'], ['cancelled', 'Cancelled'], ['all', 'All']];
const matches = (item, tab) => tab === 'all' || item.status === tab.toUpperCase();

const statusForm = item => html`
    <form class="inline-form" data-status-form="${item.id}">
        <label class="sr-only" for="status-${item.id}">Status</label>
        <select id="status-${item.id}" class="form-input form-input-sm">
            ${['PENDING', 'SHIPPED', 'DELIVERED'].map(s => html`<option value="${s}" ${s === item.status ? 'selected' : ''}>${capitalize(s)}</option>`)}
            ${item.cancellable ? html`<option value="CANCELLED">Cancel item</option>` : ''}
        </select>
        <button type="submit" class="btn btn-secondary btn-sm">Save</button>
    </form>`;

function ordersTable(items) {
    return html`
        <div class="card table-card"><div class="table-scroll">
            <table class="data-table stack" id="order-table">
                <thead><tr><th>Order</th><th>Book</th><th class="num">Qty</th><th class="num">Amount</th><th>Status</th><th>Update</th></tr></thead>
                <tbody>
                    ${items.map(item => html`
                        <tr>
                            <td data-label="Order"><div class="cell-title mono">#LBS-${item.orderId}</div><div class="cell-sub nowrap">${formatDate(item.orderDate)}</div></td>
                            <td class="cell-full">
                                <div class="cell-book">
                                    ${cover(item, 'cover-xs')}
                                    <div><div class="cell-title">${item.title}</div><div class="cell-sub cell-buyer">${avatar({ url: item.buyerAvatarUrl, name: item.buyerName, size: 'xs' })} For ${item.buyerName}</div></div>
                                </div>
                            </td>
                            <td class="num" data-label="Qty">${item.quantity}</td>
                            <td class="num strong" data-label="Amount">${price(item.price * item.quantity)}</td>
                            <td data-label="Status">${statusBadge(item.status)}</td>
                            <td class="cell-full" data-label="Update">
                                ${item.status === 'CANCELLED' ? html`<span class="cell-note">Stock returned to inventory</span>` : statusForm(item)}
                            </td>
                        </tr>`)}
                </tbody>
            </table>
        </div></div>`;
}

function listingsTable(books) {
    return html`
        <div class="card table-card"><div class="table-scroll">
            <table class="data-table stack">
                <thead><tr><th>Book</th><th>Category</th><th class="num">Price</th><th class="num">Stock</th><th class="text-right">Actions</th></tr></thead>
                <tbody>
                    ${books.map(book => html`
                        <tr>
                            <td class="cell-full">
                                <div class="cell-book">
                                    ${cover(book, 'cover-xs')}
                                    <div><a class="cell-title" href="/book/${book.id}">${book.title}</a><div class="cell-sub">${book.author}</div></div>
                                </div>
                            </td>
                            <td data-label="Category"><span class="badge">${book.category}</span></td>
                            <td class="num strong" data-label="Price">${price(book.price)}</td>
                            <td class="num" data-label="Stock">
                                <span class="${book.stock <= 0 ? 'text-danger strong' : book.stock <= 5 ? 'text-warning strong' : ''}">${book.stock}</span>
                                ${book.stock <= 0 ? html`<div class="cell-sub text-danger">Sold out</div>` : ''}
                                ${book.stock > 0 && book.stock <= 5 ? html`<div class="cell-sub text-warning">Low stock</div>` : ''}
                            </td>
                            <td class="cell-full">
                                <div class="row-actions">
                                    <a href="/seller/books/${book.id}/edit" class="btn btn-secondary btn-sm">Edit</a>
                                    <button type="button" class="btn btn-danger btn-sm" data-delete="${book.id}">Delete</button>
                                </div>
                            </td>
                        </tr>`)}
                </tbody>
            </table>
        </div></div>`;
}

export default function sellerDashboard(root, { query, flash: initialFlash, alive }) {
    setTitle('Seller dashboard');
    root.className = 'container page';
    let tab = TABS.some(([k]) => k === query.get('tab')) ? query.get('tab') : 'pending';
    let message = initialFlash;
    let data = null;

    const paint = () => {
        const items = data ? data.orderItems.filter(i => matches(i, tab)) : [];
        render(root, html`
            <div class="page-header">
                <div>
                    <h1 class="page-title" id="shop-name">${data?.vendorName || 'Seller dashboard'}</h1>
                    <p class="page-subtitle">Manage your listings and fulfil customer orders.</p>
                </div>
                <a href="/seller/books/new" class="btn btn-primary">${plusIcon()} List a book</a>
            </div>
            ${flash(message)}
            ${!data ? loading() : html`
                <section class="stat-grid" aria-label="Sales summary">
                    ${statCard({ label: 'Revenue', value: price(data.totalSales), hint: 'Excludes cancelled items', icon: rupeeIcon() })}
                    ${statCard({ label: 'Units sold', value: data.booksSold, hint: 'Copies across all orders', icon: bookIcon(), variant: 'stat-icon-info' })}
                    ${statCard({ label: 'Orders', value: data.totalOrders, hint: 'Containing your books', icon: bagIcon(17), variant: 'stat-icon-warning' })}
                    ${statCard({
                        label: 'Listed titles',
                        value: data.books.length,
                        hint: data.lowStockCount > 0 ? `${data.lowStockCount} low or out of stock` : 'All well stocked',
                        hintClass: data.lowStockCount > 0 ? 'text-warning' : '',
                        icon: gridIcon(),
                        variant: 'stat-icon-success',
                    })}
                </section>

                <section class="section" id="orders">
                    <div class="section-head"><h2 class="section-title">Orders</h2></div>
                    <nav class="tabs" aria-label="Filter orders">
                        ${TABS.map(([key, label]) => html`
                            <a href="/seller?tab=${key}" data-tab="${key}" class="tab ${tab === key ? 'active' : ''}">
                                <span>${label}</span><span class="tab-count">${data.orderItems.filter(i => matches(i, key)).length}</span>
                            </a>`)}
                    </nav>
                    ${items.length === 0
                        ? emptyState({ text: tab === 'pending' ? 'Nothing to ship right now. New orders will appear here.' : 'No orders in this view.' })
                        : ordersTable(items)}
                </section>

                <section class="section" id="listings">
                    <div class="section-head">
                        <h2 class="section-title">Your listings</h2>
                        <span class="section-meta">${plural(data.books.length, 'title')}</span>
                    </div>
                    ${data.books.length === 0 ? emptyState({
                        title: 'No books listed yet',
                        text: 'Add your first title to start selling.',
                        actions: html`<a href="/seller/books/new" class="btn btn-primary">List a book</a>`,
                    }) : listingsTable(data.books)}
                </section>`}`);
    };

    const load = async () => {
        try {
            const result = await api('vendor/dashboard');
            if (alive()) data = result;
        } catch (e) {
            if (alive()) message = { type: 'error', text: e.message };
        }
        if (alive()) paint();
    };

    const run = async request => {
        try {
            const result = await request;
            message = { type: 'success', text: result.message };
        } catch (e) {
            message = { type: 'error', text: e.message };
        }
        await load();
    };

    paint();
    load().then(() => {
        // Arriving from the book form at /seller#listings
        if (alive() && location.hash) document.getElementById(location.hash.slice(1))?.scrollIntoView();
    });

    root.addEventListener('click', async e => {
        const tabLink = e.target.closest('[data-tab]');
        if (tabLink) {
            e.preventDefault();
            tab = tabLink.dataset.tab;
            replaceQuery({ tab });
            paint();
            return;
        }
        const del = e.target.closest('[data-delete]');
        if (del) {
            const book = data.books.find(b => String(b.id) === del.dataset.delete);
            const ok = await confirm({
                title: 'Delete this listing?',
                text: `“${book.title}” will be removed from the store. Books that already have orders can’t be deleted — set their stock to 0 instead.`,
                action: 'Delete listing',
            });
            if (ok) await run(api(`vendor/books/${book.id}`, { method: 'DELETE' }));
        }
    });

    root.addEventListener('submit', async e => {
        const form = e.target.closest('[data-status-form]');
        if (!form) return;
        e.preventDefault();
        const item = data.orderItems.find(i => String(i.id) === form.dataset.statusForm);
        const status = form.querySelector('select').value;
        if (status === 'CANCELLED') {
            const ok = await confirm({
                title: 'Cancel this item?',
                text: `The buyer will be refunded ${price(item.price * item.quantity)} and ${plural(item.quantity, 'copy', 'copies')} of “${item.title}” will go back into your stock.`,
                action: 'Cancel item',
            });
            if (!ok) return;
        }
        await run(api(`vendor/order-items/${item.id}/status`, { method: 'PUT', body: { status } }));
    });
}
