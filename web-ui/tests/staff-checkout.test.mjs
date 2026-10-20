import test from 'node:test';
import assert from 'node:assert/strict';

function mockDom() {
    const element = () => ({
        innerHTML: '', children: [], style: {}, listeners: {}, value: '',
        classList: { add() {} },
        addEventListener(event, handler) { this.listeners[event] = handler; },
        appendChild(child) { this.children.push(child); }
    });
    const nodes = new Map();
    const get = id => {
        if (!nodes.has(id)) nodes.set(id, element());
        return nodes.get(id);
    };
    const grid = get('checkout-grid');
    const buttons = () => grid.children.map(card => {
        card.button ||= { ...element(), dataset: { id: card.innerHTML.match(/data-id="([^"]+)"/)[1] } };
        return card.button;
    });
    return {
        get, grid, buttons, container: element(),
        document: { getElementById: get, createElement: element, querySelectorAll: buttons }
    };
}

const booking = (id = 11, customerId = 7) => ({
    id, customerId, roomId: 2, status: 'checked_in', totalPrice: 2000000,
    checkInDate: '2026-10-20T14:00:00', checkOutDate: '2026-10-22T12:00:00'
});

for (const base of ['../', '../public/']) {
    const { api } = await import(`${base}api.js`);
    const { renderCheckout } = await import(`${base}js/staff/checkout.js`);

    test(`${base} staff checkout resolves customer identity in cards and payment confirmation`, async t => {
        const originalGet = api.get;
        const originalDocument = globalThis.document;
        const originalWindow = globalThis.window;
        const originalConsoleError = console.error;
        try {
            for (const scenario of [
                { label: 'collection uses full_name and string IDs', customers: [{ id: '7', full_name: 'Nguyễn Văn An' }], expected: 'Nguyễn Văn An' },
                { label: 'DTO uses fullName', customers: [{ id: 7, fullName: 'Trần Thị Bình' }], expected: 'Trần Thị Bình' },
                { label: 'blank camelCase name falls back to snake_case', customers: [{ id: 7, fullName: ' ', full_name: 'Lê Minh' }], expected: 'Lê Minh' },
                { label: 'older profile missing from the newest 50 is fetched once', customers: [{ id: 8, full_name: 'Khách khác' }], detail: { id: 7, fullName: 'Hồ Sơ Cũ' }, expected: 'Hồ Sơ Cũ', detailCalls: 1 },
                { label: 'customer names are displayed as text in both views', customers: [{ id: 7, full_name: '<img src=x onerror=alert(1)>' }], expected: '&lt;img src=x onerror=alert(1)&gt;' },
                { label: 'a missing profile is an explicit data issue', customers: [], error: Object.assign(new Error('Not found'), { status: 404 }), expected: 'Chưa có thông tin khách hàng (KH #7)', detailCalls: 1 }
            ]) {
                await t.test(scenario.label, async () => {
                    const dom = mockDom();
                    globalThis.document = dom.document;
                    globalThis.window = {};
                    const requests = [];
                    api.get = async path => {
                        requests.push(path);
                        if (path === '/bookings') return [booking(), { ...booking(12), status: 'CHECKED_IN' }, { ...booking(13), status: 'checked_out' }];
                        if (path === '/rooms') return [{ id: '2', roomNumber: '204' }];
                        if (path === '/customers') return scenario.customers;
                        assert.equal(path, '/customers/7');
                        if (scenario.error) throw scenario.error;
                        return scenario.detail;
                    };
                    await renderCheckout(dom.container, { role: 'STAFF', fullName: 'Nhân viên' });
                    assert.equal(dom.grid.children.length, 2, 'Only checked-in bookings belong in checkout');
                    assert.equal(requests.filter(path => path === '/customers/7').length, scenario.detailCalls || 0);
                    for (const card of dom.grid.children) {
                        assert.ok(card.innerHTML.includes(scenario.expected));
                        assert.ok(card.innerHTML.includes('Phòng 204'));
                        assert.doesNotMatch(card.innerHTML, /Khách vãng lai|Nhân viên|<img src=x/);
                    }
                    const button = dom.buttons()[0];
                    button.listeners.click({ target: button });
                    const content = dom.get('global-modal-content').innerHTML;
                    assert.ok(content.includes(scenario.expected), 'Payment confirms the same booking customer');
                    assert.doesNotMatch(content, /Khách vãng lai|<img src=x/);
                });
            }

            await t.test('failed customer lookup shows an error instead of inventing a guest', async () => {
                const dom = mockDom();
                globalThis.document = dom.document;
                globalThis.window = {};
                console.error = () => {};
                api.get = async path => {
                    if (path === '/bookings') return [booking()];
                    if (path === '/rooms' || path === '/customers') return [];
                    throw Object.assign(new Error('Không tải được hồ sơ'), { status: 503 });
                };
                await renderCheckout(dom.container, { role: 'STAFF' });
                assert.match(dom.grid.innerHTML, /Lỗi tải dữ liệu: Không tải được hồ sơ/);
                assert.equal(dom.grid.children.length, 0);
                assert.doesNotMatch(dom.grid.innerHTML, /Khách vãng lai/);
            });
        } finally {
            api.get = originalGet;
            globalThis.document = originalDocument;
            globalThis.window = originalWindow;
            console.error = originalConsoleError;
        }
    });
}
