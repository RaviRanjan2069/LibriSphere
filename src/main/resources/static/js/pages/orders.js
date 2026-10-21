// My orders: tabs (all / active / delivered / cancelled) and cancelling orders or single items.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { replaceQuery } from '../router.js';
import { formatDate, price } from '../format.js';
import { confirm, toast } from '../ui.js';
import { cover, emptyState, flash, loading, setTitle, statusBadge } from '../components.js';

const TABS = [
    { key: 'all', label: 'All orders', match: () => true, empty: ['No orders yet', 'When you place an order, it will show up here.'] },
    { key: 'active', label: 'Active', match: o => o.status === 'PENDING' || o.status === 'SHIPPED', empty: ['No active orders', 'Orders that are being prepared or are on their way appear here.'] },
    { key: 'delivered', label: 'Delivered', match: o => o.status === 'DELIVERED', empty: ['Nothing delivered yet', 'Completed orders appear here once every item has arrived.'] },
    { key: 'cancelled', label: 'Cancelled', match: o => o.status === 'CANCELLED' || o.refundAmount > 0, empty: ['No cancellations', 'Orders and items you cancel appear here with their refund amount.'] },
];

const orderCard = order => html`
    <article class="card order-card" id="order-${order.id}">
        <dl class="order-head">
            <div><dt>Order</dt><dd class="mono">#LBS-${order.id}</dd></div>
            <div><dt>Placed</dt><dd>${formatDate(order.orderDate)}</dd></div>
            <div><dt>Total</dt><dd>${price(order.totalPrice)}</dd></div>
            <div class="order-status">${statusBadge(order.status)}</div>
        </dl>
        ${order.items.map(item => html`
            <div class="order-item ${item.status === 'CANCELLED' ? 'is-cancelled' : ''}">
                <a href="/book/${item.bookId}">${cover(item, 'cover-xs')}</a>
                <div>
                    <div class="order-item-title"><a href="/book/${item.bookId}">${item.title}</a></div>
                    <div class="order-item-meta">Sold by ${item.vendorName}</div>
                </div>
                <div class="order-item-right">
                    <span class="order-item-price">${item.quantity} × ${price(item.price)}</span>
                    ${statusBadge(item.status)}
                    ${item.cancellable && order.activeItemCount > 1
                        ? html`<button type="button" class="link-btn" data-cancel-item="${item.id}" data-order="${order.id}">Cancel item</button>` : ''}
                </div>
            </div>`)}
        <div class="order-foot">
            <span>Ship to: <strong>${order.shippingAddress}</strong></span>
            <span>Phone: <strong>${order.contactNumber}</strong></span>
            ${order.refundAmount > 0 ? html`<span class="refund-note">Refund: ${price(order.refundAmount)}</span>` : ''}
            ${order.cancellable ? html`
                <div class="order-foot-actions">
                    <button type="button" class="btn btn-danger btn-sm" data-cancel-order="${order.id}">Cancel order</button>
                </div>` : ''}
        </div>
    </article>`;

export default function orders(root, { query, flash: initialFlash, alive }) {
    setTitle('My orders');
    root.className = 'container container-narrow page';
    let tab = TABS.find(t => t.key === query.get('tab')) || TABS[0];
    let message = initialFlash;
    let list = null;

    const paint = () => {
        const shown = list ? list.filter(tab.match) : [];
        render(root, html`
            <div class="page-header">
                <div>
                    <h1 class="page-title">My orders</h1>
                    <p class="page-subtitle">Each item is shipped by its seller. You can cancel anything that hasn't shipped yet.</p>
                </div>
            </div>
            ${flash(message)}
            <nav class="tabs" aria-label="Filter orders">
                ${TABS.map(t => html`
                    <a href="/orders?tab=${t.key}" data-tab="${t.key}" class="tab ${t.key === tab.key ? 'active' : ''}" ${t.key === tab.key ? html`aria-current="page"` : ''}>
                        ${t.label} <span class="tab-count">${list ? list.filter(t.match).length : '–'}</span>
                    </a>`)}
            </nav>
            ${!list ? loading('Loading your orders…') : ''}
            ${list && shown.length === 0 ? emptyState({
                title: tab.empty[0],
                text: tab.empty[1],
                actions: html`<a href="/" class="btn btn-primary">Browse books</a>`,
            }) : ''}
            ${shown.map(orderCard)}`);
    };

    const load = async () => {
        try {
            const result = await api('orders');
            if (alive()) list = result;
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
            toast(e.message, { error: true });
        }
        await load();
    };

    paint();
    load();

    root.addEventListener('click', async e => {
        const tabLink = e.target.closest('[data-tab]');
        if (tabLink) {
            e.preventDefault();
            tab = TABS.find(t => t.key === tabLink.dataset.tab);
            replaceQuery({ tab: tab.key });
            paint();
            return;
        }

        const orderButton = e.target.closest('[data-cancel-order]');
        if (orderButton) {
            const id = orderButton.dataset.cancelOrder;
            const ok = await confirm({
                title: 'Cancel this order?',
                text: `All items in order #LBS-${id} that haven’t shipped will be cancelled and refunded. This can’t be undone.`,
                action: 'Cancel order',
            });
            if (ok) await run(api(`orders/${id}/cancel`, { method: 'POST' }));
            return;
        }

        const itemButton = e.target.closest('[data-cancel-item]');
        if (itemButton) {
            const order = list.find(o => String(o.id) === itemButton.dataset.order);
            const item = order.items.find(i => String(i.id) === itemButton.dataset.cancelItem);
            const ok = await confirm({
                title: 'Cancel this item?',
                text: `“${item.title}” will be removed from your order and ${price(item.price * item.quantity)} refunded. The rest of the order is not affected.`,
                action: 'Cancel item',
            });
            if (ok) await run(api(`orders/items/${item.id}/cancel`, { method: 'POST' }));
        }
    });
}
