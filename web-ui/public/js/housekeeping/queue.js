import { api } from '../../api.js';
import { escapeHtml } from '../shared/ui.js';

const statusMeta = {
  pending: ['Chờ nhận dọn', 'badge-warning'],
  in_progress: ['Đang dọn', 'badge-info'],
  completed: ['Đã hoàn tất', 'badge-success'],
  not_released: ['Chưa đến giờ bàn giao', 'badge-neutral']
};

const asDate = value => {
  if (!value) return null;
  const parsed = new Date(String(value).replace(' ', 'T'));
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};

const formatDateTime = value => {
  const date = asDate(value);
  return date ? new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date) : '—';
};

const formatTime = value => {
  const date = asDate(value);
  return date ? new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' }).format(date) : '—';
};

function isReleased(task) {
  const readyAt = asDate(task.ready_not_before);
  return task.room_status === 'vacant_dirty' && (!readyAt || readyAt <= new Date());
}

function displayStatus(task) {
  if (task.status === 'pending' && !isReleased(task)) return 'not_released';
  return task.status;
}

function priority(task) {
  if (task.status === 'completed') return ['Đã xử lý', 'badge-success'];
  if (task.turnaround_conflict) return ['Cần ưu tiên', 'badge-error'];
  if (task.overdue) return ['Đã quá giờ', 'badge-warning'];
  const arrival = asDate(task.due_at);
  if (arrival && arrival.getTime() - Date.now() <= 2 * 60 * 60 * 1000) return ['Sắp có khách', 'badge-warning'];
  return ['Thông thường', 'badge-ghost'];
}

function actionMarkup(task, currentUserId, busyTaskId) {
  const status = displayStatus(task);
  if (busyTaskId === String(task.id)) return '<span class="loading loading-spinner loading-sm" aria-label="Đang cập nhật"></span>';
  if (status === 'not_released') {
    return `<button type="button" class="btn btn-sm" disabled title="Phòng chưa đến thời gian được bàn giao">Chờ đến ${escapeHtml(formatTime(task.ready_not_before))}</button>`;
  }
  if (task.status === 'pending') {
    return `<button type="button" class="btn btn-primary btn-sm" data-hk-action="start" data-task-id="${escapeHtml(task.id)}">Nhận dọn</button>`;
  }
  if (task.status === 'in_progress' && String(task.assigned_to_user_id || '') === String(currentUserId || '')) {
    return `<button type="button" class="btn btn-success btn-sm" data-hk-action="complete" data-task-id="${escapeHtml(task.id)}">Xác nhận phòng sạch</button>`;
  }
  if (task.status === 'in_progress') {
    return `<span class="text-sm text-base-content/60">${escapeHtml(task.assigned_to_name || 'Nhân viên khác')} đang xử lý</span>`;
  }
  return '<span class="text-sm text-base-content/60">Đã bàn giao</span>';
}

export function renderHousekeepingQueue(container, session) {
  if (!container) return;
  const currentUserId = session?.userId ?? session?.user_id ?? '';
  const state = { tasks: [], query: '', filter: 'all', view: 'active', page: 1, pageSize: 10, busyTaskId: '', loading: true, error: '' };

  container.innerHTML = `
    <section class="hk-workbench" aria-labelledby="hk-page-title">
      <header class="hk-page-header">
        <div>
          <p class="eyebrow">KHU VỰC NHÂN VIÊN BUỒNG PHÒNG</p>
          <h2 id="hk-page-title">Công việc dọn phòng</h2>
          <p>Nhận bàn giao, xử lý phòng và xác nhận sẵn sàng cho kỳ lưu trú tiếp theo.</p>
        </div>
        <button type="button" class="btn btn-outline" data-hk-refresh><i data-lucide="refresh-cw" aria-hidden="true"></i> Làm mới</button>
      </header>

      <section class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 hk-stats" aria-label="Tổng quan công việc">
        <article class="card card-border"><div class="card-body"><p class="text-sm text-base-content/60">Chờ bàn giao</p><strong class="stat-value" data-hk-stat="release">—</strong><span class="text-xs text-base-content/60">Chưa đến thời gian nhận dọn</span></div></article>
        <article class="card card-border"><div class="card-body"><p class="text-sm text-base-content/60">Sẵn sàng nhận dọn</p><strong class="stat-value" data-hk-stat="pending">—</strong><span class="text-xs text-base-content/60">Phòng đã bàn giao</span></div></article>
        <article class="card card-border"><div class="card-body"><p class="text-sm text-base-content/60">Đang dọn</p><strong class="stat-value" data-hk-stat="progress">—</strong><span class="text-xs text-base-content/60">Đang có nhân viên xử lý</span></div></article>
        <article class="card card-border"><div class="card-body"><p class="text-sm text-base-content/60">Hoàn tất hôm nay</p><strong class="stat-value" data-hk-stat="complete">—</strong><span class="text-xs text-base-content/60">Đã xác nhận sạch phòng</span></div></article>
      </section>

      <section class="card card-border hk-queue-panel" aria-labelledby="hk-queue-title">
        <div class="card-body">
          <div class="hk-queue-heading">
            <div><h3 id="hk-queue-title">Danh sách bàn giao</h3><p>Ưu tiên phòng có khách sắp nhận; thông tin khách và thanh toán được ẩn.</p></div>
            <span class="badge badge-outline" data-hk-count>Đang tải…</span>
          </div>
          <div class="hk-view-tabs" role="tablist" aria-label="Nhóm công việc">
            <button type="button" class="btn btn-sm" role="tab" data-hk-view="active" aria-selected="true">Đang xử lý</button>
            <button type="button" class="btn btn-sm btn-ghost" role="tab" data-hk-view="all" aria-selected="false">Tất cả bàn giao</button>
            <button type="button" class="btn btn-sm btn-ghost" role="tab" data-hk-view="done" aria-selected="false">Đã hoàn tất</button>
          </div>
          <div class="hk-filters">
            <label class="input input-bordered"><i data-lucide="search" aria-hidden="true"></i><input type="search" data-hk-search placeholder="Tìm theo số phòng, hạng phòng hoặc mã việc" aria-label="Tìm công việc dọn phòng"></label>
            <select class="select select-bordered" data-hk-filter aria-label="Lọc trạng thái">
              <option value="all">Tất cả trạng thái</option><option value="not_released">Chờ bàn giao</option><option value="pending">Chờ nhận dọn</option><option value="in_progress">Đang dọn</option><option value="completed">Đã hoàn tất</option>
            </select>
            <button type="button" class="btn btn-ghost" data-hk-reset>Làm mới bộ lọc</button>
          </div>
          <div class="alert alert-info hk-rule" role="note"><i data-lucide="info" aria-hidden="true"></i><span>Chỉ nhận dọn sau khi hết thời gian bàn giao. Nhân viên nhận việc sẽ chịu trách nhiệm xác nhận phòng sạch.</span></div>
          <div class="hk-error alert alert-error" data-hk-error role="alert" hidden></div>
          <div class="hk-table-wrap">
            <table class="table table-zebra">
              <thead><tr><th scope="col">Phòng</th><th scope="col">Bàn giao</th><th scope="col">Khách kế tiếp</th><th scope="col">Ưu tiên</th><th scope="col">Trạng thái</th><th scope="col">Nhân viên</th><th scope="col">Thao tác</th></tr></thead>
              <tbody data-hk-rows><tr><td colspan="7"><span class="loading loading-spinner loading-sm"></span> Đang tải công việc…</td></tr></tbody>
            </table>
          </div>
          <div class="hk-table-footer"><span data-hk-range></span><div id="hk-pagination"></div></div>
        </div>
      </section>
      <div class="toast toast-end" aria-live="polite"><div class="alert alert-success" data-hk-toast hidden></div></div>
    </section>`;

  const escape = escapeHtml;
  const find = selector => container.querySelector(selector);
  const renderStats = () => {
    const count = { release: 0, pending: 0, progress: 0, complete: 0 };
    state.tasks.forEach(task => {
      if (task.status === 'completed') count.complete++;
      else if (task.status === 'in_progress') count.progress++;
      else if (!isReleased(task)) count.release++;
      else count.pending++;
    });
    Object.entries(count).forEach(([key, value]) => { const node = find(`[data-hk-stat="${key}"]`); if (node) node.textContent = String(value); });
  };

  const matchingTasks = () => state.tasks.filter(task => {
    const status = displayStatus(task);
    const query = state.query.trim().toLocaleLowerCase('vi');
    const searchable = `${task.room_number || ''} ${task.room_type_name || ''} ${task.id || ''}`.toLocaleLowerCase('vi');
    if (query && !searchable.includes(query)) return false;
    if (state.view === 'active' && task.status === 'completed') return false;
    if (state.view === 'done' && task.status !== 'completed') return false;
    return state.filter === 'all' || status === state.filter;
  });

  const renderTable = () => {
    const visible = matchingTasks();
    const maxPage = Math.max(1, Math.ceil(visible.length / state.pageSize));
    state.page = Math.min(state.page, maxPage);
    const start = (state.page - 1) * state.pageSize;
    const pageTasks = visible.slice(start, start + state.pageSize);
    const rows = find('[data-hk-rows]');
    const counter = find('[data-hk-count]');
    const range = find('[data-hk-range]');
    if (counter) counter.textContent = state.loading ? 'Đang tải…' : `${visible.length} công việc`;
    if (range) range.textContent = state.loading ? '' : `${visible.length} công việc trong danh sách`;
    if (rows) {
      if (state.loading) rows.innerHTML = '<tr><td colspan="7"><span class="loading loading-spinner loading-sm"></span> Đang tải công việc…</td></tr>';
      else if (!pageTasks.length) rows.innerHTML = `<tr><td class="hk-empty" colspan="7">${state.error ? 'Không tải được danh sách công việc.' : 'Không có công việc phù hợp với bộ lọc.'}</td></tr>`;
      else rows.innerHTML = pageTasks.map(task => {
        const shownStatus = displayStatus(task);
        const [statusLabel, statusClass] = statusMeta[shownStatus] || ['Chưa xác định', 'badge-neutral'];
        const [priorityLabel, priorityClass] = priority(task);
        const handoff = task.status === 'completed' ? 'Đã hoàn tất' : isReleased(task) ? 'Đã bàn giao' : `Có thể nhận từ ${formatDateTime(task.ready_not_before)}`;
        const due = task.due_at ? formatDateTime(task.due_at) : 'Chưa có lịch kế tiếp';
        const assigned = task.status === 'in_progress' || task.status === 'completed'
          ? (task.assigned_to_name || (task.status === 'completed' ? 'Đã hoàn tất' : 'Đang xử lý'))
          : 'Chưa nhận';
        return `<tr class="${task.status === 'completed' ? 'opacity-75' : ''}">
          <td><strong>Phòng ${escape(task.room_number || '—')}</strong><small class="block text-xs text-base-content/60">${escape(task.room_type_name || 'Hạng phòng')} · Việc #${escape(task.id)}</small></td>
          <td><strong>${escape(handoff)}</strong><small class="block text-xs text-base-content/60">Tạo lúc ${escape(formatDateTime(task.created_at))}</small></td>
          <td>${escape(due)}${task.expected_finish_at ? `<small class="block text-xs text-base-content/60">Dự kiến xong ${escape(formatDateTime(task.expected_finish_at))}</small>` : ''}</td>
          <td><span class="badge ${priorityClass}">${escape(priorityLabel)}</span></td>
          <td><span class="badge ${statusClass}">${escape(statusLabel)}</span></td>
          <td>${escape(assigned)}</td>
          <td class="hk-action-cell">${actionMarkup(task, currentUserId, state.busyTaskId)}</td>
        </tr>`;
      }).join('');
    }
    if (window.renderPaginationComponent) {
      window.renderPaginationComponent('hk-pagination', visible.length, state.pageSize, state.page,
        page => { state.page = page; renderTable(); },
        size => { state.pageSize = size; state.page = 1; renderTable(); });
    }
    if (state.error) {
      const error = find('[data-hk-error]');
      if (error) { error.hidden = false; error.textContent = state.error; }
    }
  };

  const refresh = async () => {
    state.loading = true;
    state.error = '';
    const error = find('[data-hk-error]');
    if (error) error.hidden = true;
    renderTable();
    try {
      const response = await api.get('/housekeeping/tasks?view=all', { timeoutMs: 10000 });
      state.tasks = Array.isArray(response) ? response : [];
      state.loading = false;
      renderStats();
      renderTable();
    } catch (err) {
      state.loading = false;
      state.error = err?.status === 403 ? 'Tài khoản chưa được cấp quyền buồng phòng.' : err?.message || 'Không thể tải công việc. Hãy thử làm mới.';
      renderStats();
      renderTable();
    }
  };

  const notify = message => {
    const toast = find('[data-hk-toast]');
    if (!toast) return;
    toast.hidden = false;
    toast.textContent = message;
    window.clearTimeout(toast._timer);
    toast._timer = window.setTimeout(() => { if (toast.isConnected) toast.hidden = true; }, 3200);
  };

  const performAction = async (action, taskId) => {
    const task = state.tasks.find(item => String(item.id) === String(taskId));
    if (!task) return;
    state.busyTaskId = String(taskId);
    renderTable();
    try {
      if (action === 'start') await api.post(`/housekeeping/tasks/${encodeURIComponent(task.id)}/start`, {}, { timeoutMs: 10000 });
      else await api.post(`/housekeeping/tasks/${encodeURIComponent(task.id)}/complete`, { room_version: task.room_version }, { timeoutMs: 10000 });
      state.busyTaskId = '';
      notify(action === 'start' ? `Đã nhận dọn phòng ${task.room_number}.` : `Phòng ${task.room_number} đã được xác nhận sạch.`);
      await refresh();
    } catch (err) {
      state.busyTaskId = '';
      const message = err?.status === 409 ? 'Trạng thái phòng vừa thay đổi hoặc chưa đến thời gian bàn giao. Danh sách đã được tải lại.' : err?.status === 403 ? 'Công việc này đã được nhân viên khác nhận hoặc tài khoản chưa có quyền.' : err?.message || 'Chưa thể cập nhật công việc.';
      notify(message);
      await refresh();
    }
  };

  container.onclick = event => {
    const actionButton = event.target.closest('[data-hk-action]');
    if (actionButton) { performAction(actionButton.dataset.hkAction, actionButton.dataset.taskId); return; }
    if (event.target.closest('[data-hk-refresh]')) { refresh(); return; }
    if (event.target.closest('[data-hk-reset]')) {
      state.query = ''; state.filter = 'all'; state.view = 'active'; state.page = 1;
      find('[data-hk-search]').value = ''; find('[data-hk-filter]').value = 'all';
      container.querySelectorAll('[data-hk-view]').forEach(button => { const selected = button.dataset.hkView === 'active'; button.setAttribute('aria-selected', String(selected)); button.classList.toggle('btn-ghost', !selected); });
      renderTable(); return;
    }
    const viewButton = event.target.closest('[data-hk-view]');
    if (viewButton) {
      state.view = viewButton.dataset.hkView; state.page = 1;
      container.querySelectorAll('[data-hk-view]').forEach(button => { const selected = button === viewButton; button.setAttribute('aria-selected', String(selected)); button.classList.toggle('btn-ghost', !selected); });
      renderTable();
    }
  };
  container.oninput = event => {
    if (event.target.matches('[data-hk-search]')) { state.query = event.target.value; state.page = 1; renderTable(); }
  };
  container.onchange = event => {
    if (event.target.matches('[data-hk-filter]')) { state.filter = event.target.value; state.page = 1; renderTable(); }
  };

  if (window.lucide?.createIcons) window.lucide.createIcons();
  refresh();
}
