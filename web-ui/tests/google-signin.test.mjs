import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { JSDOM } from 'jsdom';
import { createGoogleSignIn } from '../public/js/auth/google-signin.js';
import { apiFetch } from '../public/api.js';

const settle = () => new Promise(resolve => setImmediate(resolve));
test('remembered device completes Google login without showing another OTP form', async () => {
  const f = fixture();
  try {
    f.api.post = async () => ({token:'synthetic-trusted-session',role:'CUSTOMER'});
    await f.controller.credential({credential:'synthetic-fresh-google-credential'});
    assert.equal(f.authenticated.length, 1);
    assert.equal(f.authenticated[0].token, 'synthetic-trusted-session');
    assert.equal(f.el('google-otp-form').hidden, true);
    assert.equal(f.el('google-otp-code').value, '');
    assert.equal(f.dom.window.localStorage.length, 0, 'Device trust is not a client-written boolean');
  } finally { f.close(); }
});
function fixture({ configured = true, sdkAvailable = true } = {}) {
  const html = fs.readFileSync(new URL('../public/portal.html', import.meta.url), 'utf8');
  const dom = new JSDOM(html, { url: 'http://localhost:8081/portal.html' });
  const calls = [], messages = [], authenticated = [], initializations = [], rendered = [];
  const sdk = { initialize: value => initializations.push(value), renderButton: (node, options) => rendered.push({ node, options }) };
  const api = {
    get: async path => { calls.push({ path }); return { enabled: configured, client_id: configured ? 'synthetic-client.apps.googleusercontent.com' : undefined }; },
    post: async (path, body) => {
      calls.push({ path, body });
      if (path.endsWith('verify-otp')) return { token: 'synthetic-app-token', customerId: 12, userId: 19, role: 'CUSTOMER' };
      return { challenge_id: 'synthetic-challenge', email: 't***@example.com' };
    }
  };
  const controller = createGoogleSignIn({ api, document: dom.window.document, window: dom.window,
    waitForSdk: async () => sdkAvailable ? sdk : null,
    onStatus: text => messages.push(text), onAuthenticated: data => authenticated.push(data),
    onCancel: () => { dom.window.document.getElementById('login-form').hidden = false; }
  });
  const el = id => dom.window.document.getElementById(id);
  return { dom, api, controller, calls, messages, authenticated, initializations, rendered, el, close: () => dom.window.close() };
}

test('disabled Google config never initializes a fake client and shows a clear disabled state', async () => {
  const f = fixture({ configured: false });
  try {
    await f.controller.initialise();
    assert.equal(f.initializations.length, 0);
    assert.equal(f.el('google-signin-fallback').disabled, true);
    assert.equal(f.el('google-signin-button').hidden, true);
    assert.match(f.el('google-signin-status').textContent, /chưa được bật/);
    assert.equal(f.calls.length, 1);
  } finally { f.close(); }
});

test('one official Google widget supports both auth tabs and only OTP verification creates a session', async () => {
  const f = fixture();
  try {
    await f.controller.initialise();
    await f.controller.initialise();
    assert.equal(f.initializations.length, 1);
    assert.equal(f.initializations[0].client_id, 'synthetic-client.apps.googleusercontent.com');
    assert.equal(f.rendered[0].node.id, 'google-signin-button');
    assert.equal(f.rendered[0].options.text, 'signin_with');
    assert.equal(f.rendered[0].options.size, 'medium', 'Google must not show account name/email on the login page');
    assert.equal(f.rendered[0].options.theme, 'outline_dark');
    f.dom.window.document.documentElement.dataset.theme = 'light';
    await settle();
    assert.equal(f.rendered.at(-1).options.theme, 'outline');
    f.dom.window.document.documentElement.dataset.theme = 'dark';
    await settle();
    assert.equal(f.rendered.at(-1).options.theme, 'outline_dark');
    assert.equal(f.initializations.length, 1, 'Changing button colors must not reset Google authentication');
    assert.equal(f.el('google-login-area').closest('form'), null);
    assert.equal(f.el('google-signin-fallback').hidden, true);
    await f.initializations[0].callback({ credential: 'synthetic-google-id-token' });
    assert.deepEqual(f.calls[2], { path: '/auth/google/request-otp', body: { credential: 'synthetic-google-id-token' } });
    assert.equal(f.authenticated.length, 0);
    assert.equal(f.el('google-otp-form').hidden, false);
    assert.equal(f.el('google-login-area').hidden, true);
    assert.equal(f.el('register-form').hidden, true);
    assert.equal(f.el('tab-register').disabled, true);
    f.el('google-otp-code').value = '1234';
    f.el('google-otp-form').dispatchEvent(new f.dom.window.Event('submit', { cancelable: true }));
    await settle();
    assert.equal(f.authenticated.length, 1);
    assert.equal(f.authenticated[0].customerId, 12);
    assert.deepEqual(f.calls.at(-1), { path: '/auth/google/verify-otp', body: { challenge_id: 'synthetic-challenge', otp: '1234' } });
    assert.equal(f.dom.window.localStorage.length, 0, 'Google credentials are never stored by the widget');
  } finally { f.close(); }
});

test('SDK failure is recoverable through retry without reporting success', async () => {
  const f = fixture({ sdkAvailable: false });
  try {
    await f.controller.initialise();
    assert.match(f.el('google-signin-status').textContent, /Không tải được nút Google/);
    assert.equal(f.el('google-signin-retry').hidden, false);
    f.el('google-signin-retry').click();
    await settle();
    assert.equal(f.calls.length, 2);
    assert.equal(f.authenticated.length, 0);
  } finally { f.close(); }
});

test('missing/rejected credentials cannot open the OTP step or complete login', async () => {
  const f = fixture();
  try {
    await f.controller.credential({});
    assert.equal(f.calls.length, 0);
    f.api.post = async () => { throw new Error('Token không hợp lệ'); };
    await f.controller.credential({ credential: 'synthetic-rejected-token' });
    assert.equal(f.el('google-otp-form').hidden, true);
    assert.equal(f.authenticated.length, 0);
    assert.equal(f.messages.at(-1), 'Token không hợp lệ');
  } finally { f.close(); }
});

test('a delayed real transport response opens and focuses OTP entry instead of timing out at 2.5 seconds', async t => {
  const f = fixture();
  const previous = {window:globalThis.window,localStorage:globalThis.localStorage,fetch:globalThis.fetch};
  t.mock.timers.enable({apis:['setTimeout']});
  try {
    globalThis.window=f.dom.window;
    globalThis.localStorage=f.dom.window.localStorage;
    let requests=0;
    globalThis.fetch=async(url,options)=>{
      requests++;
      return new Promise((resolve,reject)=>{
        options.signal.addEventListener('abort',()=>reject(options.signal.reason),{once:true});
        setTimeout(()=>resolve(new Response(JSON.stringify({status:'success',data:{challenge_id:'synthetic-delayed-challenge',email:'t***@example.com'}}))),3000);
      });
    };
    f.api.post=(path,body,options)=>apiFetch(path,{...options,method:'POST',body:JSON.stringify(body)});
    const pending=f.controller.credential({credential:'synthetic-google-token'});
    t.mock.timers.tick(3000);
    await pending;
    assert.equal(requests,1);
    assert.equal(f.el('google-otp-form').hidden,false);
    assert.equal(f.el('login-form').hidden,true);
    assert.equal(f.el('register-form').hidden,true);
    assert.equal(f.dom.window.document.activeElement.id,'google-otp-code');
    assert.equal(f.authenticated.length,0,'No account/session before OTP confirmation');
  } finally { Object.assign(globalThis,previous); f.close(); }
});

test('an expired Google request releases controls and explains how to request a new code', async () => {
  const f=fixture();
  try {
    f.api.post=async()=>{throw new DOMException('synthetic timeout','TimeoutError');};
    await f.controller.credential({credential:'synthetic-google-token'});
    assert.match(f.messages.at(-1),/chọn lại Google/);
    assert.doesNotMatch(f.messages.at(-1),/Request timed out/);
    assert.equal(f.el('google-login-area').getAttribute('aria-busy'),'false');
    assert.equal(f.el('google-otp-form').hidden,true);
  } finally { f.close(); }
});

test('duplicate Google callbacks are blocked and cancellation discards the OTP challenge', async () => {
  const f = fixture();
  try {
    let resolveRequest, count = 0;
    f.api.post = () => { count++; return new Promise(resolve => { resolveRequest = resolve; }); };
    const pending = f.controller.credential({ credential: 'synthetic-token' });
    await f.controller.credential({ credential: 'synthetic-token' });
    assert.equal(count, 1);
    resolveRequest({ challenge_id: 'synthetic-challenge' });
    await pending;
    f.el('google-otp-cancel').click();
    assert.equal(f.el('google-otp-form').hidden, true);
    assert.equal(f.el('google-login-area').hidden, false);
    assert.equal(f.el('tab-register').disabled, false);
    f.el('google-otp-code').value = '1234';
    f.el('google-otp-form').dispatchEvent(new f.dom.window.Event('submit', { cancelable: true }));
    await settle();
    assert.equal(count, 1);
    assert.equal(f.authenticated.length, 0);
  } finally { f.close(); }
});

test('wrong OTP stays recoverable and resend uses the existing challenge', async () => {
  const f = fixture();
  try {
    await f.controller.credential({ credential: 'synthetic-token' });
    const post = f.api.post;
    f.api.post = async (path, body) => { if (path.endsWith('verify-otp')) throw new Error('Mã xác thực không đúng'); return post(path, body); };
    f.el('google-otp-code').value = '1234';
    f.el('google-otp-form').dispatchEvent(new f.dom.window.Event('submit', { cancelable: true }));
    await settle();
    assert.equal(f.el('google-otp-form').hidden, false);
    assert.equal(f.authenticated.length, 0);
    assert.equal(f.el('google-otp-cancel').disabled, false);
    f.el('google-otp-resend').click(); await settle();
    assert.deepEqual(f.calls.at(-1), { path: '/auth/google/resend-otp', body: { challenge_id: 'synthetic-challenge' } });
    assert.equal(f.el('google-otp-code').value, '');
  } finally { f.close(); }
});

test('a missing OTP session returns to Google instead of trapping the user in a dead resend form', async () => {
  const f = fixture();
  try {
    await f.controller.credential({credential:'synthetic-token'});
    f.api.post = async () => { throw Object.assign(new Error('Phiên xác thực không còn tồn tại. Chọn lại Google.'), {status:410}); };
    f.el('google-otp-code').value='1234';
    f.el('google-otp-form').dispatchEvent(new f.dom.window.Event('submit',{cancelable:true}));
    await settle();
    assert.equal(f.el('google-otp-form').hidden,true);
    assert.equal(f.el('google-login-area').hidden,false);
    assert.equal(f.el('login-form').hidden,false);
    assert.equal(f.el('tab-login').disabled,false);
    assert.match(f.messages.at(-1),/Chọn lại Google/);
    assert.equal(f.authenticated.length,0);
  } finally { f.close(); }
});

test('expired codes remain resendable and resend shows progress and failure feedback', async () => {
  const f = fixture();
  try {
    await f.controller.credential({credential:'synthetic-token'});
    f.api.post = async () => { throw Object.assign(new Error('Mã đã hết hạn. Bấm Gửi lại mã.'),{status:422}); };
    f.el('google-otp-code').value='1234';
    f.el('google-otp-form').dispatchEvent(new f.dom.window.Event('submit',{cancelable:true}));
    await settle();
    assert.equal(f.el('google-otp-form').hidden,false);
    let rejectSend;
    f.api.post=()=>new Promise((resolve,reject)=>{rejectSend=reject;});
    f.el('google-otp-resend').click();
    assert.match(f.messages.at(-1),/Đang gửi lại/);
    assert.equal(f.el('google-otp-resend').disabled,true);
    rejectSend(Object.assign(new Error('Phiên đã kết thúc. Chọn lại Google.'),{status:410}));
    await settle();
    assert.equal(f.el('google-otp-form').hidden,true);
    assert.equal(f.el('google-login-area').hidden,false);
    assert.match(f.messages.at(-1),/Chọn lại Google/);
  } finally { f.close(); }
});
