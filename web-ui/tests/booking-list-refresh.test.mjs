import test from 'node:test';
import assert from 'node:assert/strict';

const settle = () => new Promise(resolve => setImmediate(resolve));
function environment() {
    const windowRef = new EventTarget();
    const documentRef = new EventTarget();
    documentRef.defaultView = windowRef; documentRef.visibilityState = 'visible'; documentRef.documentElement = {};
    let observerCallback;
    windowRef.MutationObserver = class {
        constructor(callback) { observerCallback = callback; }
        observe() {}
        disconnect() { this.disconnected = true; }
    };
    const element = () => ({
        ownerDocument: documentRef, isConnected: true, children: [], style: {}, value: '', listeners: {},
        set innerHTML(value) { this._html = value; this.children = []; },
        get innerHTML() { return this._html || ''; },
        addEventListener(event, callback) { this.listeners[event] = callback; },
        appendChild(child) { this.children.push(child); },
        querySelector() { return this.cell ||= { textContent: '' }; }
    });
    const nodes = new Map();
    const get = id => { if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id); };
    documentRef.getElementById = get; documentRef.createElement = element;
    windowRef.renderPaginationComponent = (_id,total,size,page,onPage) => { windowRef.pagination = { total,size,page,onPage }; };
    return { windowRef, documentRef, get, element, notifyRemoval: () => observerCallback?.() };
}

for (const base of ['../', '../public/']) {
    const { refreshBookingListOnReturn } = await import(`${base}js/shared/booking-list-refresh.js`);
    test(`${base} returning to a booking list reloads once and detaches after navigation`, async () => {
        const env = environment(); const body = env.element(); let calls = 0; let complete;
        const stop = refreshBookingListOnReturn(body, () => { calls++; return new Promise(resolve => { complete = resolve; }); });
        env.documentRef.visibilityState = 'hidden'; env.documentRef.dispatchEvent(new Event('visibilitychange')); assert.equal(calls, 0);
        env.documentRef.visibilityState = 'visible'; env.documentRef.dispatchEvent(new Event('visibilitychange'));
        env.windowRef.dispatchEvent(new Event('focus')); assert.equal(calls, 1, 'Focus and visibility events share one pending refresh');
        complete(); await settle(); env.windowRef.dispatchEvent(new Event('focus')); assert.equal(calls, 2);
        complete(); await settle(); body.isConnected = false; env.notifyRemoval();
        env.windowRef.dispatchEvent(new Event('focus')); assert.equal(calls, 2); stop();
    });

    const { api } = await import(`${base}api.js`);
    const { renderBookings } = await import(`${base}js/staff/bookings.js`);
    test(`${base} staff pagination and refresh retain server order and return new bookings to page one`, async () => {
        const original = { get: api.get, document: globalThis.document, window: globalThis.window, lucide: globalThis.lucide };
        const env = environment(); let requests = 0;
        const row = id => ({ id, roomId: 1, customerId: 2, status: 'pending', totalPrice: 100,
            createdAt: '2026-10-21T09:30:00', checkInDate: '2099-11-01T14:00:00', checkOutDate: '2099-11-02T12:00:00' });
        let serverRows = [5,3,99,98,97,96,95,94,93,92,91,90,89].map(row);
        try {
            globalThis.document = env.documentRef; globalThis.window = env.windowRef; globalThis.lucide = { createIcons() {} };
            api.get = async path => { assert.equal(path, '/bookings'); requests++; return serverRows; };
            await renderBookings(env.element(), {}); await settle();
            const ids = () => env.get('staff-bookings-body').children.map(child => Number(child.innerHTML.match(/>#(\d+)</)[1]));
            assert.deepEqual(ids(), serverRows.slice(0,10).map(row => row.id));
            env.windowRef.pagination.onPage(2); assert.deepEqual(ids(), [91,90,89]);
            serverRows = [row(7), ...serverRows]; env.windowRef.dispatchEvent(new Event('focus')); await settle();
            assert.equal(env.windowRef.pagination.page, 1); assert.equal(ids()[0], 7); assert.equal(requests, 2);
            env.get('staff-bookings-status').value = 'PENDING'; env.get('staff-bookings-status').listeners.change();
            assert.equal(ids()[0], 7); assert.equal(env.windowRef.pagination.total, 14);
            env.windowRef.pagination.onPage(2); await env.get('btn-refresh-bookings').listeners.click(); await settle();
            assert.equal(env.windowRef.pagination.page, 1); assert.equal(ids()[0], 7);
            api.get = async () => { throw new Error('<private failure>'); };
            env.windowRef.dispatchEvent(new Event('focus')); await settle();
            assert.equal(env.get('staff-bookings-body').children.length, 0);
            assert.equal(env.get('staff-bookings-body').cell.textContent, '<private failure>');
        } finally { api.get = original.get; globalThis.document = original.document; globalThis.window = original.window; globalThis.lucide = original.lucide; }
    });
}

test('admin list preserves server order through filters, return-to-tab and manual refresh', async () => {
    const { api } = await import('../public/api.js');
    const { renderAdminBookings } = await import('../public/js/admin/bookings.js');
    const env = environment(); const original = { get: api.get, document: globalThis.document, window: globalThis.window };
    let rows = [5,3,99].map(id => ({ id, roomId: 1, customerId: 2, status: 'pending', totalPrice: 100 }));
    try {
        globalThis.document = env.documentRef; globalThis.window = env.windowRef;
        api.get = async () => rows;
        await renderAdminBookings(env.element());
        const ids = () => [...env.get('admin-bookings-body').innerHTML.matchAll(/<strong>#(\d+)<\/strong>/g)].map(match => Number(match[1]));
        assert.deepEqual(ids(), [5,3,99]);
        env.get('admin-bookings-filter').value = 'pending'; env.get('admin-bookings-filter').listeners.change(); assert.deepEqual(ids(), [5,3,99]);
        rows = [{ ...rows[0], id: 7 }, ...rows]; env.windowRef.dispatchEvent(new Event('focus')); await settle(); assert.equal(ids()[0], 7);
        await env.get('admin-bookings-refresh').listeners.click(); assert.equal(ids()[0], 7);
        api.get = async () => { throw new Error('Database unavailable'); };
        await env.get('admin-bookings-refresh').listeners.click(); assert.match(env.get('admin-bookings-body').innerHTML, /Database unavailable/);
        assert.deepEqual(ids(), []);
    } finally { api.get = original.get; globalThis.document = original.document; globalThis.window = original.window; }
});
