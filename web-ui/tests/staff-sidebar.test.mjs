import test from 'node:test';
import assert from 'node:assert/strict';

for (const base of ['../', '../public/']) {
    const { createSidebarState } = await import(`${base}js/staff/sidebar.js`);
    test(`${base} staff sidebar choice survives reload without changing authentication storage`, () => {
        const saved = new Map([['hotel_auth_session', 'existing-session']]);
        const storage = { getItem: key => saved.get(key), setItem: (key,value) => saved.set(key,value) };
        const first = createSidebarState(storage);
        assert.equal(first.collapsed, false);
        assert.equal(first.toggle(), true);
        const restored = createSidebarState(storage);
        assert.equal(restored.collapsed, true);
        assert.equal(restored.toggle(), false);
        assert.equal(createSidebarState(storage).collapsed, false);
        assert.equal(saved.get('hotel_auth_session'), 'existing-session');
        assert.equal(saved.size, 2);
    });
    test(`${base} malformed or absent preference keeps the expanded default`, () => {
        for (const value of [null, undefined, '', 'invalid', 'false']) {
            assert.equal(createSidebarState({ getItem: () => value }).collapsed, false);
        }
    });
    test(`${base} blocked browser storage still permits toggling in memory`, () => {
        const state = createSidebarState({ getItem() { throw new Error('blocked'); }, setItem() { throw new Error('blocked'); } });
        assert.equal(state.collapsed, false);
        assert.equal(state.toggle(), true);
        assert.equal(state.toggle(), false);
        const withoutStorage = createSidebarState();
        assert.equal(withoutStorage.toggle(), true);
    });
}
