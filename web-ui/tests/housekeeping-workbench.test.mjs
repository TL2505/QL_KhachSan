import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { readFile } from 'node:fs/promises';
import { api } from '../public/api.js';
import { renderHousekeepingQueue } from '../public/js/housekeeping/queue.js';

const settle = () => new Promise(resolve => setImmediate(resolve));
const makeChecklist = completed => ['linens', 'bathroom', 'towels', 'trash', 'equipment', 'final_check'].map(key => ({
  key,
  label: key,
  required: true,
  completed_at: completed ? '2026-10-29T08:00:00' : null
}));

async function mount(t, status, checklist) {
  const dom = new JSDOM('<body><main id="view-container"></main></body>', { url: 'http://localhost/portal.html' });
  const previous = {
    window: globalThis.window,
    document: globalThis.document,
    get: api.get,
    post: api.post,
  };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  dom.window.renderPaginationComponent = () => {};
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  dom.window.HTMLDialogElement.prototype.close = function () { this.open = false; };

  const task = {
    id: 42,
    room_id: 8,
    room_number: 'QA-801',
    room_type_name: 'QA Standard',
    status,
    room_status: 'vacant_dirty',
    assigned_to_user_id: 9,
    assigned_to_name: 'Synthetic Housekeeper',
    room_version: 1,
    floor: 'Tầng 8',
    area: 'Khu QA',
  };
  const detail = { checklist: [...checklist], incidents: [], events: [] };
  const calls = [];
  api.get = async path => {
    if (path === '/housekeeping/tasks?view=all') return [task];
    if (path === '/housekeeping/metrics') return { cleaned_today: 1, average_duration_seconds: 120, needs_reinspection: 0 };
    if (path === '/housekeeping/tasks/42/details') return structuredClone(detail);
    throw new Error(`Unexpected GET ${path}`);
  };
  api.post = async (path, body) => {
    calls.push({ path, body });
    const item = detail.checklist.find(candidate => candidate.key === body.item_key);
    item.completed_at = body.checked ? '2026-10-29T08:01:00' : null;
    return { task_id: 42, item_key: body.item_key, checked: body.checked };
  };
  t.after(() => {
    api.get = previous.get;
    api.post = previous.post;
    dom.window.close();
    globalThis.window = previous.window;
    globalThis.document = previous.document;
  });

  const root = document.querySelector('#view-container');
  renderHousekeepingQueue(root, { userId: 9 });
  await settle();
  root.querySelector('[data-hk-view="done"], [data-hk-view="all"]').click();
  root.querySelector('[data-hk-details="42"]').click();
  await settle();
  return { root, calls, detail };
}

test('completed jobs show explicit check marks instead of blank disabled checkboxes', async t => {
  const { root } = await mount(t, 'completed', makeChecklist(true));
  assert.equal(root.querySelector('.hk-detail-modal .badge-success').textContent, '6/6 mục');
  assert.equal(root.querySelectorAll('.hk-check-item.is-complete .hk-check-mark').length, 6);
  assert.equal(root.querySelectorAll('.hk-check-item.is-complete input[type="checkbox"]').length, 0);
});

test('assigned in-progress jobs let staff update checklist items and keep completion blocked until required checks are done', async t => {
  const { root, calls } = await mount(t, 'in_progress', makeChecklist(false));
  const checkboxes = root.querySelectorAll('.hk-check-item input[type="checkbox"]');
  assert.equal(checkboxes.length, 6);
  assert.equal(checkboxes[0].disabled, false);
  assert.equal(root.querySelector('[data-hk-action="complete"]').disabled, true);

  checkboxes[0].checked = true;
  checkboxes[0].dispatchEvent(new window.Event('change', { bubbles: true }));
  await settle();
  assert.deepEqual(calls[0], { path: '/housekeeping/tasks/42/checklist', body: { item_key: 'linens', checked: true } });
  assert.equal(root.querySelectorAll('.hk-check-item.is-complete .hk-check-mark').length, 1);
  assert.equal(root.querySelector('[data-hk-action="complete"]').disabled, true);
});

test('unchecking a housekeeping item clears its completion timestamp in the backend', async () => {
  const controller = await readFile(new URL('../../src/quanlykhachsan/backend/room/HousekeepingController.java', import.meta.url), 'utf8');
  assert.match(controller, /completed_at=CASE WHEN \? THEN CURRENT_TIMESTAMP\(6\) ELSE NULL END/);
  assert.match(controller, /q\.setBoolean\(2, checked\); q\.setLong\(3, taskId\); q\.setString\(4, key\)/);
});
