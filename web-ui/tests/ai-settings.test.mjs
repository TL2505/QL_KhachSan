import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { renderAiSettings } from '../public/js/admin/ai-settings.js';

const metadata = { provider: 'openai', model: 'synthetic-model', has_key: true, enabled: false, knowledge: 'Synthetic hotel facts' };
const response = (data, status = 200, message) => new Response(JSON.stringify({ status: status === 200 ? 'success' : 'error', data, message }), { status });
const settle = () => new Promise(resolve => setImmediate(resolve));
function fixture() {
  const dom = new JSDOM('<main></main>', { url: 'http://localhost:3000/portal.html' });
  const old = Object.fromEntries(['window','document','localStorage','fetch'].map(key => [key, globalThis[key]]));
  Object.assign(globalThis, { window: dom.window, document: dom.window.document, localStorage: dom.window.localStorage });
  localStorage.setItem('hotel_auth_session', JSON.stringify({ token: 'synthetic-admin-token' }));
  return { container: document.querySelector('main'), close() { Object.assign(globalThis, old); dom.window.close(); } };
}
test('admin configuration loads metadata, retains a saved key and clears newly submitted credentials', async () => {
  const ui = fixture();
  try {
    const calls = [];
    globalThis.fetch = async (url, options) => {
      calls.push({ url, method: options.method, headers: options.headers, body: options.body ? JSON.parse(options.body) : null });
      return response(metadata);
    };
    await renderAiSettings(ui.container);
    const key = document.getElementById('ai-api-key');
    assert.equal(key.type, 'password'); assert.equal(key.value, ''); assert.equal(key.required, false);
    key.value = 'synthetic-new-provider-key';
    document.getElementById('ai-settings-form').dispatchEvent(new window.Event('submit', { cancelable: true }));
    await settle();
    assert.equal(calls[1].method, 'PUT');
    assert.equal(calls[1].body.api_key, 'synthetic-new-provider-key');
    assert.equal(calls[1].headers.get('Authorization'), 'Bearer synthetic-admin-token');
    assert.equal(key.value, '');
    assert.doesNotMatch(JSON.stringify({ ...localStorage }), /synthetic-new-provider-key/);
    const provider = document.getElementById('ai-provider'); provider.value = 'gemini'; provider.dispatchEvent(new window.Event('change'));
    assert.equal(key.required, true);
  } finally { ui.close(); }
});
test('connection tests use the saved server configuration and deletion clears the enabled state', async () => {
  const ui = fixture();
  try {
    const calls = [];
    globalThis.fetch = async (url, options) => {
      calls.push({ url, method: options.method, body: options.body });
      if (url.endsWith('/test')) return response({ message: 'Kết nối AI thành công.', provider: 'openai', model: 'synthetic-model' });
      return response(options.method === 'DELETE' ? { ...metadata, has_key: false, enabled: false, model: '', knowledge: '' } : metadata);
    };
    await renderAiSettings(ui.container);
    document.getElementById('ai-test').click();
    assert.match(document.getElementById('ai-settings-feedback').textContent, /đang kiểm tra kết nối.*tối đa 20 giây/i);
    await settle();
    assert.match(calls[1].url, /\/admin\/ai-settings\/test$/); assert.equal(calls[1].body, undefined);
    assert.match(document.getElementById('ai-settings-feedback').textContent, /thành công/);
    window.showCustomConfirm = async () => true;
    document.getElementById('ai-delete').click(); await settle(); await settle();
    assert.equal(calls[2].method, 'DELETE');
    assert.equal(document.getElementById('ai-test').disabled, true);
    assert.equal(document.getElementById('ai-enabled').checked, false);
  } finally { ui.close(); }
});
test('forbidden configurations stay disabled and can recover through reload', async () => {
  const ui = fixture();
  try {
    globalThis.fetch = async () => response(null, 403);
    await renderAiSettings(ui.container);
    assert.equal(document.getElementById('ai-settings-fields').disabled, true);
    assert.match(document.getElementById('ai-settings-feedback').textContent, /Chỉ quản trị viên/);
    assert.equal(document.getElementById('ai-reload').hidden, false);
    globalThis.fetch = async () => response(metadata);
    document.getElementById('ai-reload').click(); await settle();
    assert.equal(document.getElementById('ai-settings-fields').disabled, false);
  } finally { ui.close(); }
});

test('a new-key draft can replace an unreadable saved configuration after model discovery', async () => {
  const ui = fixture();
  try {
    const calls = [];
    globalThis.fetch = async (url, options = {}) => {
      const body = options.body ? JSON.parse(options.body) : null;
      calls.push({ url, method: options.method || 'GET', body });
      if (calls.length === 1) return response(null, 503);
      if (url.endsWith('/models')) return response({ provider: 'openai', models: [{ id: 'synthetic-recovery-model', name: 'Recovery model' }] });
      if (options.method === 'PUT') return response({ ...metadata, model: body.model, api_key: undefined });
      return response(metadata);
    };
    await renderAiSettings(ui.container);
    const fields = document.getElementById('ai-settings-fields');
    const key = document.getElementById('ai-api-key');
    assert.equal(fields.disabled, false);
    assert.match(document.getElementById('ai-settings-feedback').textContent, /nhập bản nháp/);
    assert.match(document.getElementById('ai-settings-feedback').textContent, /key mới.*khôi phục/);
    key.value = 'synthetic-temporary-key'; key.dispatchEvent(new window.Event('input'));
    assert.equal(document.getElementById('ai-load-models').disabled, false);
    assert.equal(document.getElementById('ai-save').disabled, true);
    assert.equal(document.getElementById('ai-test').disabled, true);
    assert.equal(document.getElementById('ai-delete').disabled, true);
    assert.doesNotMatch(document.getElementById('ai-settings-feedback').textContent, /tải lại cấu hình trước khi lưu/);

    document.getElementById('ai-load-models').click(); await settle();
    const select = document.getElementById('ai-model-select'); select.value = 'synthetic-recovery-model'; select.dispatchEvent(new window.Event('change'));
    assert.equal(document.getElementById('ai-save').disabled, false);
    assert.equal(document.getElementById('ai-test').disabled, true);
    document.getElementById('ai-settings-form').requestSubmit(); await settle();
    assert.equal(calls.some(call => call.method === 'PUT' && call.body.model === 'synthetic-recovery-model'), true);
    assert.equal(fields.disabled, false);
    assert.equal(document.getElementById('ai-save').disabled, false);
    assert.equal(document.getElementById('ai-test').disabled, false);
    assert.match(document.getElementById('ai-settings-feedback').textContent, /Đã lưu cấu hình/);
    assert.equal(key.value, '');
  } finally { ui.close(); }
});

test('Groq and Claude require a new provider key and preserve model identifiers when saved', async () => {
  for (const selected of ['groq', 'claude']) {
    const ui = fixture();
    try {
      const calls = [];
      globalThis.fetch = async (url, options) => {
        const data = options.body ? JSON.parse(options.body) : null;
        calls.push(data);
        return response(data ? { ...data, api_key: undefined, has_key: true } : metadata);
      };
      await renderAiSettings(ui.container);
      const provider = document.getElementById('ai-provider');
      provider.value = selected; provider.dispatchEvent(new window.Event('change'));
      assert.equal(document.getElementById('ai-api-key').required, true);
      assert.equal(document.getElementById('ai-model').value, '');
      assert.equal(document.getElementById('ai-test').disabled, true);
      assert.equal(document.getElementById('ai-workspace-field').hidden, selected !== 'claude');
      const modelId = selected === 'groq' ? 'developer/synthetic-model' : 'claude-synthetic-model';
      document.getElementById('ai-model').value = modelId;
      document.getElementById('ai-api-key').value = 'synthetic-provider-key';
      document.getElementById('ai-workspace').value = 'wrkspc_synthetic123';
      document.getElementById('ai-settings-form').dispatchEvent(new window.Event('submit', { cancelable: true })); await settle();
      assert.equal(calls[1].provider, selected); assert.equal(calls[1].model, modelId);
      assert.equal(calls[1].workspace_id, selected === 'claude' ? 'wrkspc_synthetic123' : '');
      assert.equal(document.getElementById('ai-api-key').value, '');
      assert.equal(document.getElementById('ai-test').disabled, false);
    } finally { ui.close(); }
  }
});

test('model discovery works before saving a model, renders provider text safely and does not save the key', async () => {
  const ui = fixture();
  try {
    const calls = [];
    globalThis.fetch = async (url, options) => {
      calls.push({ url, method: options.method, body: options.body ? JSON.parse(options.body) : null });
      if (url.endsWith('/models')) return response({ provider: 'groq', models: [{ id: 'developer/synthetic-model', name: '<img src=x onerror=alert(1)>' }], more_available: false });
      return response(metadata);
    };
    await renderAiSettings(ui.container);
    const provider = document.getElementById('ai-provider'); provider.value = 'groq'; provider.dispatchEvent(new window.Event('change'));
    const key = document.getElementById('ai-api-key'); key.value = 'synthetic-catalog-key'; key.dispatchEvent(new window.Event('input'));
    assert.equal(document.getElementById('ai-model').value, '');
    document.getElementById('ai-load-models').click(); await settle();
    assert.equal(calls[1].method, 'POST'); assert.match(calls[1].url, /\/models$/);
    assert.deepEqual(calls[1].body, { provider: 'groq', api_key: 'synthetic-catalog-key', workspace_id: '' });
    assert.equal(calls.some(call => call.method === 'PUT'), false);
    assert.equal(document.getElementById('ai-model-picker').hidden, false);
    assert.equal(document.querySelector('img'), null);
    assert.match(document.getElementById('ai-model-status').textContent, /1 model/);
    const select = document.getElementById('ai-model-select'); select.value = 'developer/synthetic-model'; select.dispatchEvent(new window.Event('change'));
    assert.equal(document.getElementById('ai-model').value, select.value);
    assert.equal(document.getElementById('ai-test').disabled, true);
    assert.doesNotMatch(JSON.stringify({ ...localStorage }), /synthetic-catalog-key/);
    key.value = 'synthetic-changed-key'; key.dispatchEvent(new window.Event('input'));
    assert.equal(document.getElementById('ai-model-picker').hidden, true);
  } finally { ui.close(); }
});

test('model discovery can use a saved key and provider rejection stays recoverable', async () => {
  const ui = fixture();
  try {
    let requestBody;
    globalThis.fetch = async (url, options) => {
      if (!url.endsWith('/models')) return response(metadata);
      requestBody = JSON.parse(options.body);
      return response(null, 422, 'Nhà cung cấp từ chối API key.');
    };
    await renderAiSettings(ui.container);
    document.getElementById('ai-load-models').click(); await settle();
    assert.equal(requestBody.api_key, '');
    assert.match(document.getElementById('ai-model-status').textContent, /từ chối/);
    assert.equal(document.getElementById('ai-settings-fields').disabled, false);
    assert.equal(document.getElementById('ai-load-models').disabled, false);
    assert.equal(document.getElementById('ai-model-picker').hidden, true);
    assert.equal(document.getElementById('ai-model').value, metadata.model);
  } finally { ui.close(); }
});

test('model discovery shows actionable provider connection failures without echoing the API key', async () => {
  const ui = fixture();
  try {
    const secret = 'synthetic-private-provider-key';
    globalThis.fetch = async (url) => url.endsWith('/models')
      ? new Response(JSON.stringify({ error_code: 'AI_PROVIDER_UNREACHABLE', message: 'Backend không kết nối được tới Google Gemini.' }), { status: 503 })
      : response(metadata);
    await renderAiSettings(ui.container);
    const key = document.getElementById('ai-api-key');
    key.value = secret; key.dispatchEvent(new window.Event('input'));
    document.getElementById('ai-load-models').click(); await settle();
    const status = document.getElementById('ai-model-status');
    assert.match(status.textContent, /Backend không kết nối được tới Google Gemini/);
    assert.equal(status.dataset.state, 'error');
    assert.equal(document.getElementById('ai-settings-fields').disabled, false);
    assert.equal(document.getElementById('ai-load-models').disabled, false);
    assert.equal(document.getElementById('ai-model-picker').hidden, true);
    assert.doesNotMatch(status.textContent, new RegExp(secret));
    assert.doesNotMatch(document.getElementById('ai-settings-feedback').textContent, new RegExp(secret));
  } finally { ui.close(); }
});

test('key entry submits independently of model selection and enables saving only after a model is chosen', async () => {
  const ui = fixture();
  try {
    const calls = [];
    globalThis.fetch = async (url, options) => {
      const body = options.body ? JSON.parse(options.body) : null;
      calls.push({ url, method: options.method, body });
      if (url.endsWith('/models')) return response({ provider: 'groq', models: [{ id: 'developer/synthetic-model', name: 'Synthetic model' }] });
      return response(options.method === 'PUT' ? { ...body, api_key: undefined, has_key: true } : metadata);
    };
    await renderAiSettings(ui.container);
    const provider = document.getElementById('ai-provider'); provider.value = 'groq'; provider.dispatchEvent(new window.Event('change'));
    const key = document.getElementById('ai-api-key'), model = document.getElementById('ai-model');
    const keyForm = document.getElementById('ai-key-form'), configForm = document.getElementById('ai-settings-form');
    assert.equal(key.form, keyForm); assert.equal(model.form, configForm);
    assert.equal(document.getElementById('ai-manual-model').open, false);
    key.value = 'synthetic-new-groq-key'; key.dispatchEvent(new window.Event('input'));
    assert.equal(model.value, ''); assert.equal(keyForm.checkValidity(), true);
    assert.equal(document.getElementById('ai-save').disabled, true);
    // The native form submission path used by Enter in the credential field must not validate a model.
    keyForm.requestSubmit(); await settle();
    assert.equal(calls.length, 2); assert.match(calls[1].url, /\/models$/);
    assert.equal(calls[1].body.model, undefined);
    assert.equal(document.getElementById('ai-save').disabled, true);
    const select = document.getElementById('ai-model-select'); select.value = 'developer/synthetic-model'; select.dispatchEvent(new window.Event('change'));
    assert.equal(document.getElementById('ai-save').disabled, false);
    assert.match(document.getElementById('ai-selected-model').textContent, /developer\/synthetic-model/);
    configForm.requestSubmit(); await settle();
    assert.equal(calls[2].method, 'PUT'); assert.equal(calls[2].body.model, 'developer/synthetic-model');
    assert.equal(calls[2].body.api_key, 'synthetic-new-groq-key'); assert.equal(key.value, '');
  } finally { ui.close(); }
});

test('missing key or model cannot send a configuration or provider request', async () => {
  const ui = fixture();
  try {
    const calls = [];
    globalThis.fetch = async (url, options) => { calls.push({ url, method: options.method }); return response(metadata); };
    await renderAiSettings(ui.container);
    const provider = document.getElementById('ai-provider'); provider.value = 'groq'; provider.dispatchEvent(new window.Event('change'));
    document.getElementById('ai-key-form').dispatchEvent(new window.Event('submit', { cancelable: true })); await settle();
    assert.match(document.getElementById('ai-model-status').textContent, /nhập API key/);
    document.getElementById('ai-settings-form').dispatchEvent(new window.Event('submit', { cancelable: true })); await settle();
    assert.equal(calls.length, 1);
    assert.match(document.getElementById('ai-model-status').textContent, /bước 1/);
    assert.equal(document.getElementById('ai-save').disabled, true);
  } finally { ui.close(); }
});

test('project context is visible as read-only data and is not submitted as editable hotel notes', async () => {
  const ui = fixture();
  try {
    const calls = [];
    const project = { text: 'Nhận phòng từ 14:00. Trả phòng trước 12:00. <img src=x onerror=alert(1)>', sources: ['Trang chủ · Chính sách'] };
    globalThis.fetch = async (url, options) => {
      calls.push(options.body ? JSON.parse(options.body) : null);
      return response({ ...metadata, project_context: project });
    };
    await renderAiSettings(ui.container);
    const preview = document.getElementById('ai-project-context');
    assert.equal(preview.readOnly, true); assert.equal(preview.value, project.text);
    assert.equal(document.querySelector('img'), null);
    assert.match(document.getElementById('ai-project-state').textContent, /Đã nạp ngữ cảnh/);
    assert.equal(document.getElementById('ai-knowledge').value, metadata.knowledge);
    document.getElementById('ai-settings-form').requestSubmit(); await settle();
    assert.equal(calls[1].knowledge, metadata.knowledge);
    assert.equal(calls[1].project_context, undefined);
  } finally { ui.close(); }
});
