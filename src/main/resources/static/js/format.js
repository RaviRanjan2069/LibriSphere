const rupees = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
export const price = n => '₹' + rupees.format(n);

const dateFmt = new Intl.DateTimeFormat('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false,
});
/** Server dates are local date-times without a zone ("2026-10-13T19:44:00"). */
export const formatDate = iso => (iso ? dateFmt.format(new Date(iso)) : '');

export const plural = (n, one, many = one + 's') => `${n} ${n === 1 ? one : many}`;

export const capitalize = s => (s ? s.charAt(0) + s.slice(1).toLowerCase() : '');

export const CATEGORIES = ['Fiction', 'Technology', 'Science', 'Business', 'Biography'];

/** Landing page for each role after signing in. */
export const homeFor = user => ({ VENDOR: '/seller', ADMIN: '/admin' }[user?.role] || '/');

/** Help & support form options; the keys must match SupportService on the server. */
export const SUPPORT_TOPICS = [
    ['ORDER', 'Order or delivery'], ['PAYMENT', 'Payment or refund'], ['BOOK', 'Book listing or details'],
    ['ACCOUNT', 'Account or sign-in'], ['SELLER', 'Selling on LibriSphere'], ['BUG', "Something isn't working"],
    ['COMPLAINT', 'Complaint'], ['OTHER', 'Other'],
];
export const SUPPORT_PRIORITIES = [['LOW', 'Low'], ['NORMAL', 'Normal'], ['HIGH', 'High'], ['URGENT', 'Urgent']];
export const SUPPORT_AREAS = [
    ['NONE', 'Not sure / not needed'], ['BROWSE', 'Browse & search'], ['BOOK_PAGE', 'Book page'], ['CART', 'Cart & checkout'],
    ['ORDERS', 'My orders'], ['SELLER', 'Seller dashboard'], ['ACCOUNT', 'Profile & sign-in'],
];
/** Label for a key from one of the option lists above. */
export const labelOf = (options, key) => (options.find(([k]) => k === key) || [key, key])[1];
