// Order confirmation.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { price } from '../format.js';
import { emptyState, loading, setTitle } from '../components.js';
import { checkIcon } from '../icons.js';

export default function orderSuccess(root, { params, alive }) {
    setTitle('Order confirmed');
    root.className = 'container page';
    render(root, loading());

    api('orders/' + Number(params.id))
        .then(order => {
            if (!alive()) return;
            render(root, html`
                <div class="card success-card">
                    <div class="success-icon">${checkIcon(30)}</div>
                    <h1>Thank you, your order is placed</h1>
                    <p>We've sent it to the sellers. You can follow each item's progress in My Orders, and cancel anything that hasn't shipped yet.</p>
                    <dl class="kv-list">
                        <div class="kv-row"><dt>Order number</dt><dd class="mono">#LBS-${order.id}</dd></div>
                        <div class="kv-row"><dt>Deliver to</dt><dd>${order.buyerName}</dd></div>
                        <div class="kv-row"><dt>Address</dt><dd>${order.shippingAddress}</dd></div>
                        <div class="kv-row"><dt>Total paid</dt><dd><strong>${price(order.totalPrice)}</strong></dd></div>
                    </dl>
                    <div class="actions-center">
                        <a href="/orders" class="btn btn-primary">View my orders</a>
                        <a href="/" class="btn btn-secondary">Continue shopping</a>
                    </div>
                </div>`);
        })
        .catch(() => {
            if (!alive()) return;
            render(root, emptyState({
                title: 'Order not found',
                text: 'You can see all your orders in My Orders.',
                actions: html`<a href="/orders" class="btn btn-primary">View my orders</a>`,
            }));
        });
}
