import test from 'node:test';
import assert from 'node:assert/strict';

for (const file of ['../api.js', '../js/api.js', '../public/api.js', '../public/js/api.js']) {
  test(`${file}: a customer endpoint cannot log out a valid staff session`, async () => {
    const original = { fetch: globalThis.fetch, window: globalThis.window, localStorage: globalThis.localStorage };
    const data = new Map(); let expired = 0;
    globalThis.localStorage = { getItem: k => data.get(k) ?? null, setItem: (k,v) => data.set(k,String(v)), removeItem: k => data.delete(k) };
    globalThis.window = { location: { origin: 'http://localhost:3000', hostname: 'localhost', protocol: 'http:' }, dispatchEvent: () => { expired++; } };
    const api = await import(new URL(file, import.meta.url));
    const reject = code => new Response(JSON.stringify({ error_code: code, instance: '/api/v1/notifications', message: 'Rejected' }), { status: 401, headers: { 'Content-Type': 'application/json' } });
    try {
      api.setAuthSession({ token: 'staff-session', role: 'STAFF' });
      globalThis.fetch = async () => reject('ERR_401_UNAUTHORIZED');
      await assert.rejects(api.api.get('/notifications'));
      assert.equal(api.getAuthSession().token, 'staff-session');
      assert.equal(expired, 0);
      globalThis.fetch = async () => new Response(JSON.stringify({ status: 'success', data: [] }), { headers: { 'Content-Type': 'application/json' } });
      assert.deepEqual(await api.api.get('/staff-notifications'), []);
      globalThis.fetch = async () => reject('ERR_401_TOKEN_INVALID');
      await assert.rejects(api.api.get('/staff-notifications'));
      assert.equal(api.getAuthSession(), null);
      assert.equal(expired, 1);

      api.setAuthSession({ token: 'old-session', role: 'STAFF' });
      globalThis.fetch = async () => { api.setAuthSession({ token: 'new-session', role: 'STAFF' }); return reject('ERR_401_TOKEN_INVALID'); };
      await assert.rejects(api.api.get('/staff-notifications'));
      assert.equal(api.getAuthSession().token, 'new-session');
      assert.equal(expired, 1);
    } finally { Object.assign(globalThis, original); }
  });
}
