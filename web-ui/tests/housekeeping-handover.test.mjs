import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { readFile } from 'node:fs/promises';
import { api } from '../public/api.js';
import { renderHousekeepingHandover } from '../public/js/housekeeping/handover.js';

const settle = () => new Promise(resolve => setImmediate(resolve));
const tasks = [
  { id: 11, room_number: 'QA-801', room_type_name: 'QA Standard', status: 'in_progress', assigned_to_user_id: 9 },
  { id: 12, room_number: 'QA-802', room_type_name: 'QA Deluxe', status: 'pending', assigned_to_user_id: 9 },
  { id: 13, room_number: 'QA-803', room_type_name: 'QA Suite', status: 'in_progress', assigned_to_user_id: 10 }
];

async function mount(t) {
  const dom = new JSDOM('<body><main id="view-container"></main></body>', { url: 'http://localhost/portal.html' });
  const previous = { window: globalThis.window, document: globalThis.document, FormData: globalThis.FormData, get: api.get, post: api.post };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  globalThis.FormData = dom.window.FormData;
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  dom.window.HTMLDialogElement.prototype.close = function () { this.open = false; };
  const calls = [];
  const handovers = [{
    id: 7, outgoing_user_id: 10, outgoing_name: 'Ca trước', accepted_by_user_id: null,
    accepted_by_name: null, status: 'pending', notes: 'Kiểm tra máy lạnh lần nữa',
    created_at: '2026-10-29T08:00:00', accepted_at: null,
    items: [{ task_id: 13, room_number: 'QA-803', room_type: 'QA Suite', status_at_handover: 'in_progress', current_status: 'in_progress' }]
  }];
  handovers.push({ id: 6, outgoing_user_id: 9, outgoing_name: 'Ca hiện tại', accepted_by_user_id: null, accepted_by_name: null, status: 'pending', notes: 'Đang chờ ca sau nhận', created_at: '2026-10-29T07:30:00', accepted_at: null, items: [{ task_id: 12, room_number: 'QA-802', room_type: 'QA Deluxe', status_at_handover: 'pending', current_status: 'pending' }] });
  api.get = async path => {
    if (path === '/housekeeping/tasks?view=all') return structuredClone(tasks);
    if (path === '/housekeeping/handovers') return structuredClone(handovers);
    throw new Error(`Unexpected GET ${path}`);
  };
  api.post = async (path, body) => {
    calls.push({ path, body });
    if (path === '/housekeeping/handovers') {
      handovers.push({ id: 8, outgoing_user_id: 9, outgoing_name: 'Ca hiện tại', status: 'pending', notes: body.notes, created_at: '2026-10-29T09:00:00', accepted_by_user_id: null, accepted_by_name: null, accepted_at: null, items: body.task_ids.map(id => ({ task_id: id, room_number: 'QA-801', room_type: 'QA Standard', current_status: 'in_progress' })) });
    } else if (path.endsWith('/accept')) {
      handovers.find(item => String(item.id) === path.split('/')[3]).status = 'accepted';
    } else if (path.endsWith('/cancel')) {
      handovers.find(item => String(item.id) === path.split('/')[3]).status = 'cancelled';
    }
    return {};
  };
  t.after(() => {
    api.get = previous.get; api.post = previous.post;
    dom.window.close();
    globalThis.window = previous.window; globalThis.document = previous.document; globalThis.FormData = previous.FormData;
  });
  const root = document.querySelector('#view-container');
  renderHousekeepingHandover(root, { userId: 9 });
  await settle();
  return { root, calls, handovers };
}

test('housekeeping role receives a third sidebar tab for shift handover', async () => {
  const app = await readFile(new URL('../public/portal-app.js', import.meta.url), 'utf8');
  const tabs = app.match(/HOUSEKEEPING:\s*\[([\s\S]*?)\n\s*\],/);
  assert.ok(tabs);
  assert.match(tabs[1], /\['housekeeping', 'Công việc dọn phòng'/);
  assert.match(tabs[1], /\['shift-handover', 'Bàn giao ca'/);
  assert.match(tabs[1], /\['profile', 'Hồ sơ & bảo mật'/);
  assert.match(app, /role === 'HOUSEKEEPING' && tab === 'shift-handover'/);
});

test('only current user jobs can be selected, and a job in a pending handover cannot be added again', async t => {
  const { root } = await mount(t);
  assert.equal(root.querySelectorAll('[data-hh-task]').length, 2);
  assert.equal(root.querySelector('[data-hh-task="11"]').disabled, false);
  assert.equal(root.querySelector('[data-hh-task="12"]').disabled, true);
  assert.equal(root.querySelector('[data-hh-task="13"]'), null);
});

test('handover form sends selected task identifiers and the note to the API', async t => {
  const { root, calls } = await mount(t);
  const checkbox = root.querySelector('[data-hh-task="11"]');
  checkbox.checked = true;
  checkbox.dispatchEvent(new window.Event('change', { bubbles: true }));
  const notes = root.querySelector('[name="notes"]');
  notes.value = 'Đã vệ sinh xong phòng tắm; kiểm tra vòi nước.';
  root.querySelector('[data-hh-form]').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
  await settle(); await settle();
  assert.deepEqual(calls[0], { path: '/housekeeping/handovers', body: { task_ids: [11], notes: 'Đã vệ sinh xong phòng tắm; kiểm tra vòi nước.' } });
  assert.match(root.querySelector('[data-hh-message]').textContent, /Đã tạo phiếu bàn giao/);
});

test('another housekeeping employee must confirm before accepting a pending handover', async t => {
  const { root, calls } = await mount(t);
  root.querySelector('[data-hh-action="accept"]').click();
  const dialog = root.querySelector('[data-hh-confirm-dialog]');
  assert.equal(dialog.open, true);
  assert.match(root.querySelector('[data-hh-confirm-text]').textContent, /chuyển sang tài khoản của bạn/);
  root.querySelector('[data-hh-confirm-yes]').click();
  await settle(); await settle();
  assert.equal(calls[0].path, '/housekeeping/handovers/7/accept');
  assert.match(root.querySelector('[data-hh-message]').textContent, /Đã tiếp nhận/);
});

test('handover author can cancel their own pending handover', async t => {
  const { root, calls } = await mount(t);
  root.querySelector('[data-hh-action="cancel"]').click();
  root.querySelector('[data-hh-confirm-yes]').click();
  await settle(); await settle();
  assert.equal(calls[0].path, '/housekeeping/handovers/6/cancel');
  assert.match(root.querySelector('[data-hh-message]').textContent, /Đã hủy phiếu/);
});
