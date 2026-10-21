// Who is signed in, and how many items are in the cart (kept server-side in the HTTP session).
import { api } from './api.js';

export const session = { user: null, cartCount: 0 };
const listeners = new Set();

function update(changes) {
    Object.assign(session, changes);
    listeners.forEach(fn => fn(session));
}

/** Calls fn whenever the session changes; returns a function that stops listening. */
export function onSessionChange(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
}

export async function refreshSession() {
    try {
        const me = await api('auth/me');
        update({ user: me.user, cartCount: me.cartCount });
    } catch { /* stay signed out */ }
}

export const setCartCount = cartCount => update({ cartCount });

/** Replaces the signed-in user after a profile change (picture, email verification). */
export const setUser = user => update({ user });

/**
 * `remember` keeps the user signed in for 30 days instead of until the browser closes.
 * `role` "ADMIN" (from the admin tab) only lets administrators in.
 */
export async function signIn(username, password, remember = false, role = null) {
    const me = await api('auth/login', { method: 'POST', body: { username, password, remember, role } });
    update({ user: me.user, cartCount: me.cartCount });
    return me;
}

export async function signInWithOtp({ email, code, remember = false }) {
    const me = await api('auth/login/otp', { method: 'POST', body: { email, code, remember } });
    update({ user: me.user, cartCount: me.cartCount });
    return me;
}

export async function signOut() {
    await api('auth/logout', { method: 'POST' }).catch(() => {});
    update({ user: null, cartCount: 0 });
}
