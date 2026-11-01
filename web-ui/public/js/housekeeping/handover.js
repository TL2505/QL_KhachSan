import { api } from '../../api.js';
import { escapeHtml } from '../shared/ui.js';

const esc = escapeHtml;
const statusMeta = {
  pending: ['Chờ tiếp nhận', 'badge-warning'],
  accepted: ['Đã tiếp nhận', 'badge-success'],
  cancelled: ['Đã hủy', 'badge-neutral']
};
const activeTask = task => ['pending', 'in_progress'].includes(task?.status);
const fmt = value => {
  if (!value) return '—';
  const date = new Date(String(value).replace(' ', 'T'));
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date);
};

export function renderHousekeepingHandover(container, session) {
  if (!container) return;
  const currentUserId = String(session?.userId ?? session?.user_id ?? '');
  const state = { tasks: [], handovers: [], selected: new Set(), loading: true, submitting: false, error: '', notice: '' };

  container.innerHTML = `
    <section class="hk-handover" aria-labelledby="hk-handover-title">
      <header class="hk-page-header"><div><p class="eyebrow">KHU VỰC NHÂN VIÊN BUỒNG PHÒNG</p><h2 id="hk-handover-title">Bàn giao ca</h2><p>Ghi lại các việc còn dở để ca tiếp theo tiếp nhận đầy đủ và có lịch sử đối chiếu.</p></div><button type="button" class="btn btn-outline" data-hh-refresh><i data-lucide="refresh-cw" aria-hidden="true"></i> Làm mới</button></header>
      <div class="hk-handover-stats" aria-label="Tổng quan bàn giao">
        <article class="card card-border"><div class="card-body"><span class="text-sm text-base-content/60">Việc bạn đang phụ trách</span><strong data-hh-stat="owned">—</strong></div></article>
        <article class="card card-border"><div class="card-body"><span class="text-sm text-base-content/60">Chờ bạn bàn giao</span><strong data-hh-stat="outgoing">—</strong></div></article>
        <article class="card card-border"><div class="card-body"><span class="text-sm text-base-content/60">Chờ bạn tiếp nhận</span><strong data-hh-stat="incoming">—</strong></div></article>
      </div>
      <div class="alert alert-info hk-handover-rule" role="note"><i data-lucide="info" aria-hidden="true"></i><span>Chỉ việc đang được giao cho bạn mới có thể bàn giao. Khi nhân viên ca sau tiếp nhận, hệ thống chuyển người phụ trách và lưu lại nhật ký.</span></div>
      <p class="hk-handover-message" data-hh-message role="status" aria-live="polite" hidden></p>
      <p class="hk-handover-error alert alert-error" data-hh-error role="alert" hidden></p>
      <section class="card card-border hk-handover-panel" aria-labelledby="hk-handover-create-title"><div class="card-body">
        <div class="hk-handover-section-heading"><div><h3 id="hk-handover-create-title">Tạo phiếu bàn giao</h3><p>Chọn các việc đang dọn hoặc đang được giao cho bạn; nhập ghi chú cho ca sau.</p></div><span class="badge badge-outline" data-hh-selection>0 việc được chọn</span></div>
        <div class="hk-handover-task-list" data-hh-owned><div class="hk-handover-empty"><span class="loading loading-spinner loading-sm"></span> Đang tải công việc…</div></div>
        <form class="hk-handover-form" data-hh-form>
          <label class="form-control"><span class="label-text">Ghi chú bàn giao <span aria-hidden="true">*</span></span><textarea class="textarea textarea-bordered" name="notes" maxlength="1000" required placeholder="Ví dụ: đã bổ sung khăn, cần kiểm tra lại vòi nước phòng tắm…"></textarea><span class="label-text-alt">Tối đa 1.000 ký tự; không ghi thông tin thanh toán hoặc dữ liệu cá nhân của khách.</span></label>
          <div class="hk-handover-form-footer"><span class="text-sm text-base-content/60">Phiếu sẽ được hiển thị cho nhân viên buồng phòng khác.</span><button class="btn btn-primary" type="submit" data-hh-submit disabled>Tạo phiếu bàn giao</button></div>
        </form>
      </div></section>
      <section class="card card-border hk-handover-panel" aria-labelledby="hk-handover-list-title"><div class="card-body">
        <div class="hk-handover-section-heading"><div><h3 id="hk-handover-list-title">Phiếu bàn giao</h3><p>Phiếu đang chờ được ưu tiên hiển thị trước; lịch sử được lưu 30 ngày trong màn hình này.</p></div><span class="badge badge-outline" data-hh-count>Đang tải…</span></div>
        <div class="hk-handover-list" data-hh-list><div class="hk-handover-empty"><span class="loading loading-spinner loading-sm"></span> Đang tải phiếu bàn giao…</div></div>
      </div></section>
      <dialog class="modal" data-hh-confirm-dialog aria-labelledby="hh-confirm-title"><div class="modal-box"><h3 id="hh-confirm-title" class="font-bold text-lg">Xác nhận thao tác</h3><p data-hh-confirm-text class="py-4"></p><div class="modal-action"><button type="button" class="btn btn-ghost" data-hh-confirm-close>Để sau</button><button type="button" class="btn btn-primary" data-hh-confirm-yes>Xác nhận</button></div></div><form method="dialog" class="modal-backdrop"><button aria-label="Đóng">Đóng</button></form></dialog>
    </section>`;

  const find = selector => container.querySelector(selector);
  const ownedTasks = () => state.tasks.filter(task => activeTask(task) && String(task.assigned_to_user_id ?? '') === currentUserId);
  const reservedTaskIds = () => new Set(state.handovers.filter(item => item.status === 'pending').flatMap(item => (item.items || []).map(task => String(task.task_id))));
  const renderStats = () => {
    const pending = state.handovers.filter(item => item.status === 'pending');
    find('[data-hh-stat="owned"]').textContent = String(ownedTasks().length);
    find('[data-hh-stat="outgoing"]').textContent = String(pending.filter(item => String(item.outgoing_user_id) === currentUserId).length);
    find('[data-hh-stat="incoming"]').textContent = String(pending.filter(item => String(item.outgoing_user_id) !== currentUserId).length);
  };
  const renderTasks = () => {
    const list = find('[data-hh-owned]'), reserved = reservedTaskIds();
    const tasks = ownedTasks();
    if (!tasks.length) {
      list.innerHTML = '<div class="hk-handover-empty">Bạn hiện không có công việc đang hoạt động để bàn giao.</div>';
    } else {
      list.innerHTML = tasks.map(task => {
        const id = String(task.id), isReserved = reserved.has(id), checked = state.selected.has(id);
        const status = task.status === 'in_progress' ? 'Đang dọn' : 'Chờ nhận dọn';
        return `<label class="hk-handover-task${isReserved ? ' is-reserved' : ''}"><input class="checkbox checkbox-primary" type="checkbox" data-hh-task="${esc(id)}" ${checked && !isReserved ? 'checked' : ''} ${isReserved ? 'disabled' : ''}><span class="hk-handover-task-main"><strong>Phòng ${esc(task.room_number || '—')}</strong><small>${esc(task.room_type_name || 'Hạng phòng')} · Việc #${esc(id)}</small></span><span class="badge ${task.status === 'in_progress' ? 'badge-info' : 'badge-warning'}">${status}</span>${isReserved ? '<small class="hk-handover-reserved-note">Đã có phiếu đang chờ</small>' : ''}</label>`;
      }).join('');
    }
    find('[data-hh-selection]').textContent = `${state.selected.size} việc được chọn`;
    find('[data-hh-submit]').disabled = state.loading || state.submitting || state.selected.size === 0 || !currentUserId;
  };
  const renderHandovers = () => {
    const list = find('[data-hh-list]');
    find('[data-hh-count]').textContent = `${state.handovers.length} phiếu`;
    if (!state.handovers.length) { list.innerHTML = '<div class="hk-handover-empty">Chưa có phiếu bàn giao nào. Tạo phiếu để ca tiếp theo có thể tiếp nhận việc còn tồn.</div>'; return; }
    list.innerHTML = state.handovers.map(item => {
      const [label, badge] = statusMeta[item.status] || ['Không xác định', 'badge-neutral'];
      const own = String(item.outgoing_user_id) === currentUserId;
      const items = item.items || [];
      return `<article class="hk-handover-record"><header><div><div class="hk-handover-record-title"><strong>Bàn giao #${esc(item.id)}</strong><span class="badge ${badge}">${label}</span></div><small>${own ? 'Bạn bàn giao' : `${esc(item.outgoing_name || 'Nhân viên')} bàn giao`} · ${esc(fmt(item.created_at))}</small></div>${item.status === 'pending' && !own ? `<button type="button" class="btn btn-primary btn-sm" data-hh-action="accept" data-hh-id="${esc(item.id)}">Tiếp nhận</button>` : item.status === 'pending' && own ? `<button type="button" class="btn btn-outline btn-sm" data-hh-action="cancel" data-hh-id="${esc(item.id)}">Hủy phiếu</button>` : ''}</header>
        <p class="hk-handover-notes">${esc(item.notes || 'Không có ghi chú')}</p>
        <ul class="hk-handover-items">${items.map(task => `<li><strong>Phòng ${esc(task.room_number || '—')}</strong><span>${esc(task.room_type || 'Hạng phòng')} · Việc #${esc(task.task_id)}</span><span class="badge badge-ghost">${task.current_status === 'in_progress' ? 'Đang dọn' : task.current_status === 'pending' ? 'Chờ nhận dọn' : 'Đã kết thúc'}</span></li>`).join('')}</ul>
        ${item.status === 'accepted' ? `<small class="hk-handover-result">${esc(item.accepted_by_name || 'Nhân viên ca sau')} đã tiếp nhận · ${esc(fmt(item.accepted_at))}</small>` : item.status === 'cancelled' ? '<small class="hk-handover-result">Các việc vẫn do người tạo phiếu phụ trách.</small>' : '<small class="hk-handover-result">Việc chỉ chuyển người phụ trách sau khi nhân viên khác xác nhận tiếp nhận.</small>'}
      </article>`;
    }).join('');
  };
  const render = () => {
    find('[data-hh-error]').hidden = !state.error;
    find('[data-hh-error]').textContent = state.error;
    find('[data-hh-message]').hidden = !state.notice;
    find('[data-hh-message]').textContent = state.notice;
    renderStats(); renderTasks(); renderHandovers();
  };
  const refresh = async () => {
    state.loading = true; state.error = ''; render();
    try {
      const [taskData, handoverData] = await Promise.all([
        api.get('/housekeeping/tasks?view=all', { timeoutMs: 10000 }),
        api.get('/housekeeping/handovers', { timeoutMs: 10000 })
      ]);
      state.tasks = Array.isArray(taskData) ? taskData : [];
      state.handovers = Array.isArray(handoverData) ? handoverData : [];
      const stillSelectable = new Set(ownedTasks().map(task => String(task.id)).filter(id => !reservedTaskIds().has(id)));
      state.selected = new Set([...state.selected].filter(id => stillSelectable.has(id)));
      state.error = '';
    } catch (error) {
      state.error = error?.status === 403 ? 'Tài khoản chưa được cấp quyền buồng phòng.' : error?.message || 'Không thể tải dữ liệu bàn giao. Hãy thử làm mới.';
    } finally { state.loading = false; render(); }
  };
  const confirm = (title, message) => new Promise(resolve => {
    const dialog = find('[data-hh-confirm-dialog]');
    find('#hh-confirm-title').textContent = title;
    find('[data-hh-confirm-text]').textContent = message;
    const yes = find('[data-hh-confirm-yes]');
    const close = find('[data-hh-confirm-close]');
    let settled = false;
    const finish = value => { if (settled) return; settled = true; yes.removeEventListener('click', onYes); close.removeEventListener('click', onClose); dialog.removeEventListener('close', onNativeClose); if (dialog.open) dialog.close(); resolve(value); };
    const onYes = () => finish(true), onClose = () => finish(false), onNativeClose = () => finish(false);
    yes.addEventListener('click', onYes, { once: true }); close.addEventListener('click', onClose, { once: true });
    dialog.addEventListener('close', onNativeClose, { once: true });
    dialog.showModal();
  });
  const act = async (action, id) => {
    const item = state.handovers.find(row => String(row.id) === String(id));
    if (!item) return;
    const accepted = await confirm(action === 'accept' ? 'Tiếp nhận bàn giao?' : 'Hủy phiếu bàn giao?', action === 'accept' ? `Các công việc trong phiếu #${id} sẽ được chuyển sang tài khoản của bạn.` : `Phiếu #${id} sẽ bị hủy; các công việc vẫn ở tài khoản người bàn giao.`);
    if (!accepted) return;
    state.submitting = true; state.error = ''; state.notice = ''; render();
    try {
      await api.post(`/housekeeping/handovers/${encodeURIComponent(id)}/${action}`, {}, { timeoutMs: 10000 });
      state.notice = action === 'accept' ? 'Đã tiếp nhận phiếu và chuyển công việc sang tài khoản của bạn.' : 'Đã hủy phiếu; công việc vẫn do bạn phụ trách.';
      await refresh();
    } catch (error) { state.error = error?.message || 'Không thể xử lý phiếu bàn giao.'; }
    finally { state.submitting = false; render(); }
  };

  container.onclick = event => {
    const button = event.target.closest('[data-hh-action]');
    if (button) { act(button.dataset.hhAction, button.dataset.hhId); return; }
    if (event.target.closest('[data-hh-refresh]')) { refresh(); return; }
  };
  container.onchange = event => {
    if (!event.target.matches('[data-hh-task]')) return;
    const id = String(event.target.dataset.hhTask);
    if (event.target.checked) state.selected.add(id); else state.selected.delete(id);
    find('[data-hh-submit]').disabled = state.loading || state.submitting || state.selected.size === 0 || !currentUserId;
    find('[data-hh-selection]').textContent = `${state.selected.size} việc được chọn`;
  };
  container.onsubmit = async event => {
    const form = event.target.closest('[data-hh-form]');
    if (!form) return;
    event.preventDefault();
    if (!state.selected.size || state.submitting) return;
    const button = find('[data-hh-submit]'); button.disabled = true; state.submitting = true; state.error = ''; state.notice = '';
    try {
      await api.post('/housekeeping/handovers', { task_ids: [...state.selected].map(Number), notes: String(new FormData(form).get('notes') ?? '').trim() }, { timeoutMs: 15000 });
      state.selected.clear(); form.reset(); state.notice = 'Đã tạo phiếu bàn giao. Công việc vẫn ở tài khoản của bạn cho đến khi ca sau tiếp nhận.';
      await refresh();
    } catch (error) { state.error = error?.message || 'Không thể tạo phiếu bàn giao.'; }
    finally { state.submitting = false; render(); }
  };
  if (window.lucide?.createIcons) window.lucide.createIcons();
  refresh();
}
