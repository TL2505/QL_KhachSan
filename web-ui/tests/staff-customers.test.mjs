import test from 'node:test';
import assert from 'node:assert/strict';

function environment() {
    const element = () => ({
        _html: '', children: [], listeners: {}, style: {}, value: '',
        set innerHTML(value) { this._html = value; this.children = []; },
        get innerHTML() { return this._html; },
        classList: { add() {} },
        addEventListener(type, handler) { this.listeners[type] = handler; },
        appendChild(child) { this.children.push(child); },
        querySelector(selector) { this.selectors ||= new Map(); if (!this.selectors.has(selector)) this.selectors.set(selector, element()); return this.selectors.get(selector); }
    });
    const nodes = new Map();
    const get = id => { if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id); };
    return { get, container: element(), document: { getElementById: get, createElement: element } };
}

for (const base of ['../', '../public/']) {
    const { api } = await import(`${base}api.js`);
    const { renderCustomers, normalizeStaffCustomer } = await import(`${base}js/staff/customers.js`);

    test(`${base} staff customer fields support both API naming formats and preserve leading zeroes`, () => {
        const expected = { fullName: 'Synthetic Customer', identityCard: '000000000001' };
        const snake = normalizeStaffCustomer({ full_name: expected.fullName, identity_card: expected.identityCard });
        const camel = normalizeStaffCustomer(expected);
        for (const customer of [snake, camel]) {
            assert.equal(customer.fullName, expected.fullName);
            assert.equal(customer.identityCard, expected.identityCard);
        }
        const empty = normalizeStaffCustomer({ fullName: ' ', full_name: expected.fullName, phone: ' ', email: null });
        assert.equal(empty.fullName, expected.fullName);
        assert.equal(empty.phone, '');
        assert.equal(empty.email, '');
    });

    test(`${base} staff directory shares complete data across table, summaries, search and detail`, async () => {
        const old = { get: api.get, document: globalThis.document, window: globalThis.window, lucide: globalThis.lucide };
        const env = environment();
        const customers = [
            { id: 55, full_name: 'Synthetic Customer', identity_card: '000000000001', phone: '0900000000', email: 'synthetic@example.invalid', address: 'Synthetic Address' },
            { id: 54, fullName: '<Synthetic & Customer>', identityCard: '000000000002', phone: ' ', email: ' ', address: '<Synthetic Address>' }
        ];
        try {
            globalThis.document = env.document; globalThis.window = {}; globalThis.lucide = { createIcons() {} };
            api.get = async path => { assert.equal(path, '/customers'); return customers; };
            await renderCustomers(env.container, { role: 'STAFF' });
            const body = env.get('staff-customers-body');
            assert.equal(body.children.length, 2);
            for (const field of ['Synthetic Customer', '000000000001', '0900000000', 'synthetic@example.invalid', 'Synthetic Address']) assert.ok(body.children[0].innerHTML.includes(field));
            assert.ok(body.children[1].innerHTML.includes('&lt;Synthetic &amp; Customer&gt;'));
            assert.doesNotMatch(body.children[1].innerHTML, /<Synthetic/);
            const summary = env.get('staff-cust-summary').innerHTML;
            assert.deepEqual([...summary.matchAll(/staff-customer-stat-value">(\d+)/g)].map(match => Number(match[1])), [2, 1, 1]);
            body.children[0].querySelector('.btn-view-sc').listeners.click();
            const detail = env.get('global-modal-content').innerHTML;
            for (const field of ['Synthetic Customer', '000000000001', '0900000000', 'synthetic@example.invalid', 'Synthetic Address']) assert.ok(detail.includes(field));
            const search = env.get('staff-cust-search').listeners.input;
            for (const value of ['Synthetic Customer', '000000000001', '0900000000', 'synthetic@example.invalid']) {
                search({ target: { value } });
                assert.equal(body.children.length, 1);
                assert.ok(body.children[0].innerHTML.includes('ID: 55'));
            }
        } finally {
            api.get = old.get; globalThis.document = old.document; globalThis.window = old.window; globalThis.lucide = old.lucide;
        }
    });
}
