import { api } from '../../api.js';
import { escapeHtml } from '../shared/ui.js';
import { uploadMedia } from '../shared/media-upload.js';

const statusMeta = {
  pending: ['Chờ nhận dọn', 'badge-warning'], in_progress: ['Đang dọn', 'badge-info'],
  completed: ['Đã hoàn tất', 'badge-success'], not_released: ['Chưa đến giờ bàn giao', 'badge-neutral']
};
const checklistTitles = { linens: 'Thay ga giường và vỏ gối', bathroom: 'Vệ sinh phòng tắm', towels: 'Bổ sung khăn và vật dụng', trash: 'Đổ rác và thay túi rác', equipment: 'Kiểm tra thiết bị và đèn', final_check: 'Kiểm tra tổng thể trước bàn giao' };
const esc = escapeHtml;
const asDate = value => { if (!value) return null; const date = new Date(String(value).replace(' ', 'T')); return Number.isNaN(date.getTime()) ? null : date; };
const formatDateTime = value => { const date = asDate(value); return date ? new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date) : '—'; };
const formatTime = value => { const date = asDate(value); return date ? new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' }).format(date) : '—'; };
const formatDuration = seconds => seconds == null ? 'Chưa có dữ liệu' : seconds < 60 ? `${seconds} giây` : `${Math.floor(seconds / 60)} phút`;

function isReleased(task) { const readyAt = asDate(task.ready_not_before); return task.room_status === 'vacant_dirty' && (!readyAt || readyAt <= new Date()); }
function displayStatus(task) { return task.status === 'pending' && !isReleased(task) ? 'not_released' : task.status; }
function priority(task) {
  if (task.status === 'completed') return ['Đã xử lý', 'badge-success'];
  if (task.turnaround_conflict) return ['Cần ưu tiên', 'badge-error'];
  if (task.overdue) return ['Đã quá giờ', 'badge-warning'];
  const arrival = asDate(task.due_at);
  if (arrival && arrival.getTime() - Date.now() <= 2 * 60 * 60 * 1000) return ['Sắp có khách', 'badge-warning'];
  return ['Thông thường', 'badge-ghost'];
}

export function renderHousekeepingQueue(container, session) {
  if (!container) return;
  const currentUserId = session?.userId ?? session?.user_id ?? '';
  const state = { tasks: [], query: '', filter: 'all', floor: 'all', area: 'all', view: 'active', page: 1, pageSize: 10, busyTaskId: '', loading: true, error: '', detailsTask: null, detailData: null, detailLoading: false, incidentMode: false };

  container.innerHTML = `
    <section class="hk-workbench" aria-labelledby="hk-page-title">
      <header class="hk-page-header"><div><p class="eyebrow">KHU VỰC NHÂN VIÊN BUỒNG PHÒNG</p><h2 id="hk-page-title">Công việc dọn phòng</h2><p>Nhận bàn giao, xử lý phòng và xác nhận sẵn sàng cho kỳ lưu trú tiếp theo.</p></div><button type="button" class="btn btn-outline" data-hk-refresh><i data-lucide="refresh-cw" aria-hidden="true"></i> Làm mới</button></header>
      <section class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 hk-stats" aria-label="Tổng quan công việc">
        <article class="card card-border"><div class="card-body"><p class="text-sm text-base-content/60">Chờ bàn giao</p><strong class="stat-value" data-hk-stat="release">—</strong><span class="text-xs text-base-content/60">Chưa đến thời gian nhận dọn</span></div></article>
        <article class="card card-border"><div class="card-body"><p class="text-sm text-base-content/60">Sẵn sàng nhận dọn</p><strong class="stat-value" data-hk-stat="pending">—</strong><span class="text-xs text-base-content/60">Phòng đã bàn giao</span></div></article>
        <article class="card card-border"><div class="card-body"><p class="text-sm text-base-content/60">Đang dọn</p><strong class="stat-value" data-hk-stat="progress">—</strong><span class="text-xs text-base-content/60">Đang có nhân viên xử lý</span></div></article>
        <article class="card card-border"><div class="card-body"><p class="text-sm text-base-content/60">Hoàn tất hôm nay</p><strong class="stat-value" data-hk-stat="complete">—</strong><span class="text-xs text-base-content/60">Đã xác nhận sạch phòng</span></div></article>
        <article class="card card-border hk-metric"><div class="card-body"><p class="text-sm text-base-content/60">Phòng đã dọn trong ca</p><strong class="stat-value" data-hk-metric="cleaned">—</strong><span class="text-xs text-base-content/60">Theo ngày hiện tại</span></div></article>
        <article class="card card-border hk-metric"><div class="card-body"><p class="text-sm text-base-content/60">Thời gian dọn trung bình</p><strong class="stat-value" data-hk-metric="duration">—</strong><span class="text-xs text-base-content/60">Từ lúc nhận đến khi hoàn tất</span></div></article>
        <article class="card card-border hk-metric"><div class="card-body"><p class="text-sm text-base-content/60">Cần kiểm tra lại</p><strong class="stat-value" data-hk-metric="reinspection">—</strong><span class="text-xs text-base-content/60">Được ghi nhận từ báo cáo sự cố</span></div></article>
      </section>
      <section class="card card-border hk-queue-panel" aria-labelledby="hk-queue-title"><div class="card-body">
        <div class="hk-queue-heading"><div><h3 id="hk-queue-title">Danh sách bàn giao</h3><p>Ưu tiên phòng có khách sắp nhận; thông tin khách và thanh toán được ẩn.</p></div><span class="badge badge-outline" data-hk-count>Đang tải…</span></div>
        <div class="hk-view-tabs" role="tablist" aria-label="Nhóm công việc"><button type="button" class="btn btn-sm" role="tab" data-hk-view="active" aria-selected="true">Đang xử lý</button><button type="button" class="btn btn-sm btn-ghost" role="tab" data-hk-view="all" aria-selected="false">Tất cả bàn giao</button><button type="button" class="btn btn-sm btn-ghost" role="tab" data-hk-view="done" aria-selected="false">Đã hoàn tất</button></div>
        <div class="hk-filters">
          <label class="input input-bordered"><i data-lucide="search" aria-hidden="true"></i><input type="search" data-hk-search placeholder="Tìm theo số phòng, hạng phòng hoặc mã việc" aria-label="Tìm công việc dọn phòng"></label>
          <select class="select select-bordered" data-hk-floor aria-label="Lọc tầng"><option value="all">Tất cả tầng</option></select>
          <select class="select select-bordered" data-hk-area aria-label="Lọc khu vực"><option value="all">Tất cả khu vực</option></select>
          <select class="select select-bordered" data-hk-filter aria-label="Lọc trạng thái"><option value="all">Tất cả trạng thái</option><option value="not_released">Chờ bàn giao</option><option value="pending">Chờ nhận dọn</option><option value="in_progress">Đang dọn</option><option value="completed">Đã hoàn tất</option></select>
          <button type="button" class="btn btn-ghost" data-hk-reset>Làm mới bộ lọc</button>
        </div>
        <div class="alert alert-info hk-rule" role="note"><i data-lucide="info" aria-hidden="true"></i><span>Chỉ nhận dọn sau khi hết thời gian bàn giao. Mở checklist để ghi nhận từng hạng mục, báo sự cố và xác nhận phòng sạch.</span></div>
        <div class="hk-error alert alert-error" data-hk-error role="alert" hidden></div>
        <div class="hk-table-wrap"><table class="table table-zebra"><thead><tr><th scope="col">Phòng · khu vực</th><th scope="col">Bàn giao</th><th scope="col">Khách kế tiếp</th><th scope="col">Ưu tiên</th><th scope="col">Trạng thái</th><th scope="col">Nhân viên</th><th scope="col">Thao tác</th></tr></thead><tbody data-hk-rows><tr><td colspan="7"><span class="loading loading-spinner loading-sm"></span> Đang tải công việc…</td></tr></tbody></table></div>
        <div class="hk-table-footer"><span data-hk-range></span><div id="hk-pagination"></div></div>
      </div></section>
      <dialog class="modal" data-hk-dialog aria-labelledby="hk-dialog-title"><div class="modal-box hk-detail-modal"><div class="hk-modal-heading"><div><p class="eyebrow">HỒ SƠ CÔNG VIỆC</p><h3 id="hk-dialog-title">Checklist và nhật ký</h3></div><button class="btn btn-sm btn-circle btn-ghost" type="button" data-hk-close aria-label="Đóng">×</button></div><div data-hk-dialog-content><span class="loading loading-spinner"></span> Đang tải…</div></div><form method="dialog" class="modal-backdrop"><button aria-label="Đóng">Đóng</button></form></dialog>
      <div class="toast toast-end" aria-live="polite"><div class="alert alert-success" data-hk-toast hidden></div></div>
    </section>`;

  const find = selector => container.querySelector(selector);
  const renderStats = () => {
    const count = { release: 0, pending: 0, progress: 0, complete: 0 };
    state.tasks.forEach(task => { if (task.status === 'completed') count.complete++; else if (task.status === 'in_progress') count.progress++; else if (!isReleased(task)) count.release++; else count.pending++; });
    Object.entries(count).forEach(([key, value]) => { const node = find(`[data-hk-stat="${key}"]`); if (node) node.textContent = String(value); });
  };
  const updateFilterOptions = () => {
    for (const [selector, property, label] of [['[data-hk-floor]', 'floor', 'Tất cả tầng'], ['[data-hk-area]', 'area', 'Tất cả khu vực']]) {
      const select = find(selector), previous = select.value;
      const values = [...new Set(state.tasks.map(task => task[property]).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'vi', { numeric: true }));
      select.innerHTML = `<option value="all">${label}</option>${values.map(value => `<option value="${esc(value)}">${esc(value)}</option>`).join('')}`;
      select.value = values.includes(previous) ? previous : 'all';
    }
  };
  const matchingTasks = () => state.tasks.filter(task => {
    const status = displayStatus(task), query = state.query.trim().toLocaleLowerCase('vi');
    const searchable = `${task.room_number || ''} ${task.room_type_name || ''} ${task.id || ''}`.toLocaleLowerCase('vi');
    if (query && !searchable.includes(query)) return false;
    if (state.floor !== 'all' && task.floor !== state.floor) return false;
    if (state.area !== 'all' && task.area !== state.area) return false;
    if (state.view === 'active' && task.status === 'completed') return false;
    if (state.view === 'done' && task.status !== 'completed') return false;
    return state.filter === 'all' || status === state.filter;
  });
  const renderTable = () => {
    const visible = matchingTasks(), maxPage = Math.max(1, Math.ceil(visible.length / state.pageSize));
    state.page = Math.min(state.page, maxPage);
    const start = (state.page - 1) * state.pageSize, pageTasks = visible.slice(start, start + state.pageSize);
    const rows = find('[data-hk-rows]');
    if (find('[data-hk-count]')) find('[data-hk-count]').textContent = state.loading ? 'Đang tải…' : `${visible.length} công việc`;
    if (find('[data-hk-range]')) find('[data-hk-range]').textContent = state.loading ? '' : `${visible.length} công việc trong danh sách`;
    if (rows) {
      if (state.loading) rows.innerHTML = '<tr><td colspan="7"><span class="loading loading-spinner loading-sm"></span> Đang tải công việc…</td></tr>';
      else if (!pageTasks.length) rows.innerHTML = `<tr><td class="hk-empty" colspan="7">${state.error ? 'Không tải được danh sách công việc.' : 'Không có công việc phù hợp với bộ lọc.'}</td></tr>`;
      else rows.innerHTML = pageTasks.map(task => {
        const shownStatus = displayStatus(task), [statusLabel, statusClass] = statusMeta[shownStatus] || ['Chưa xác định', 'badge-neutral'], [priorityLabel, priorityClass] = priority(task);
        const handoff = task.status === 'completed' ? 'Đã hoàn tất' : isReleased(task) ? 'Đã bàn giao' : `Có thể nhận từ ${formatDateTime(task.ready_not_before)}`;
        const due = task.due_at ? formatDateTime(task.due_at) : 'Chưa có lịch kế tiếp';
        const assigned = task.status === 'in_progress' || task.status === 'completed' ? (task.assigned_to_name || (task.status === 'completed' ? 'Đã hoàn tất' : 'Đang xử lý')) : 'Chưa nhận';
        const ownTask = String(task.assigned_to_user_id || '') === String(currentUserId || '');
        return `<tr class="${task.status === 'completed' ? 'opacity-75' : ''}">
          <td><strong>Phòng ${esc(task.room_number || '—')}</strong><small class="block text-xs text-base-content/60">${esc(task.floor || '—')} · ${esc(task.area || '—')} · ${esc(task.room_type_name || 'Hạng phòng')} · Việc #${esc(task.id)}</small>${task.needs_reinspection ? '<span class="badge badge-error mt-1">Cần kiểm tra lại</span>' : ''}</td>
          <td><strong>${esc(handoff)}</strong><small class="block text-xs text-base-content/60">Tạo lúc ${esc(formatDateTime(task.created_at))}</small></td>
          <td>${esc(due)}${task.expected_finish_at ? `<small class="block text-xs text-base-content/60">Dự kiến xong ${esc(formatDateTime(task.expected_finish_at))}</small>` : ''}</td>
          <td><span class="badge ${priorityClass}">${esc(priorityLabel)}</span></td><td><span class="badge ${statusClass}">${esc(statusLabel)}</span></td><td>${esc(assigned)}</td>
          <td class="hk-action-cell"><div class="hk-actions">${shownStatus === 'not_released' ? `<button type="button" class="btn btn-sm" disabled title="Phòng chưa đến thời gian được bàn giao">Chờ đến ${esc(formatTime(task.ready_not_before))}</button>` : task.status === 'pending' ? `<button type="button" class="btn btn-primary btn-sm" data-hk-action="start" data-task-id="${esc(task.id)}">Nhận dọn</button>` : ''}<button type="button" class="btn btn-outline btn-sm" data-hk-details="${esc(task.id)}">${task.status === 'in_progress' && ownTask ? 'Checklist / nhật ký' : 'Chi tiết'}</button>${task.status === 'in_progress' && ownTask ? `<button type="button" class="btn btn-warning btn-sm" data-hk-incident="${esc(task.id)}">Báo sự cố</button>` : ''}</div></td>
        </tr>`;
      }).join('');
    }
    if (window.renderPaginationComponent) window.renderPaginationComponent('hk-pagination', visible.length, state.pageSize, state.page, page => { state.page = page; renderTable(); }, size => { state.pageSize = size; state.page = 1; renderTable(); });
    const error = find('[data-hk-error]'); if (error) { error.hidden = !state.error; error.textContent = state.error; }
  };
  const refreshMetrics = async () => {
    try {
      const data = await api.get('/housekeeping/metrics', { timeoutMs: 10000 });
      find('[data-hk-metric="cleaned"]').textContent = String(data?.cleaned_today ?? 0);
      find('[data-hk-metric="duration"]').textContent = formatDuration(data?.average_duration_seconds ?? null);
      find('[data-hk-metric="reinspection"]').textContent = String(data?.needs_reinspection ?? 0);
    } catch { ['cleaned', 'duration', 'reinspection'].forEach(key => { const node = find(`[data-hk-metric="${key}"]`); if (node) node.textContent = '—'; }); }
  };
  const refresh = async () => {
    state.loading = true; state.error = ''; const error = find('[data-hk-error]'); if (error) error.hidden = true; renderTable();
    try { const response = await api.get('/housekeeping/tasks?view=all', { timeoutMs: 10000 }); state.tasks = Array.isArray(response) ? response : []; state.loading = false; updateFilterOptions(); renderStats(); renderTable(); await refreshMetrics(); }
    catch (err) { state.loading = false; state.error = err?.status === 403 ? 'Tài khoản chưa được cấp quyền buồng phòng.' : err?.message || 'Không thể tải công việc. Hãy thử làm mới.'; renderStats(); renderTable(); }
  };
  const notify = message => { const toast = find('[data-hk-toast]'); if (!toast) return; toast.hidden = false; toast.textContent = message; window.clearTimeout(toast._timer); toast._timer = window.setTimeout(() => { if (toast.isConnected) toast.hidden = true; }, 3500); };
  const dialog = find('[data-hk-dialog]'), dialogContent = find('[data-hk-dialog-content]');
  const renderDetails = () => {
    const task = state.detailsTask, data = state.detailData;
    if (!task || !data) { dialogContent.innerHTML = '<span class="loading loading-spinner"></span> Đang tải…'; return; }
    const checklist = data.checklist || [], allDone = task.status === 'completed' || (checklist.length > 0 && checklist.every(item => !item.required || item.completed_at));
    const incidentMarkup = (data.incidents || []).length ? data.incidents.map(item => `<article class="hk-record"><div class="hk-record-head"><strong>${esc(({ damage: 'Hư hỏng', missing_item: 'Thiếu vật dụng', maintenance: 'Cần bảo trì', cleanliness: 'Vệ sinh chưa đạt', other: 'Khác' })[item.type] || item.type)}</strong><span class="badge ${item.status === 'resolved' ? 'badge-success' : 'badge-warning'}">${item.status === 'resolved' ? 'Đã xử lý' : 'Đang mở'}</span></div><p>${esc(item.description)}</p><small>${esc(item.reported_by)} · ${esc(formatDateTime(item.created_at))}${item.needs_reinspection ? ' · Cần kiểm tra lại' : ''}</small>${item.photo_url ? `<a class="link link-primary block mt-2" href="${esc(item.photo_url)}" target="_blank" rel="noopener">Xem ảnh đính kèm</a>` : ''}</article>`).join('') : '<p class="text-sm text-base-content/60">Chưa có báo cáo sự cố.</p>';
    const eventsMarkup = (data.events || []).length ? data.events.map(item => `<li><strong>${esc(({ task_started: 'Bắt đầu dọn', checklist_checked: 'Đánh dấu checklist', checklist_reopened: 'Mở lại checklist', incident_reported: 'Báo sự cố', task_completed: 'Hoàn tất dọn' })[item.type] || item.type)}</strong><span>${esc(item.details || '')}</span><small>${esc(item.actor)} · ${esc(formatDateTime(item.created_at))}</small></li>`).join('') : '<li>Chưa có lịch sử thao tác.</li>';
    const owner = task.status === 'in_progress' && String(task.assigned_to_user_id || '') === String(currentUserId || '');
    dialogContent.innerHTML = `<div class="hk-detail-room"><strong>Phòng ${esc(task.room_number)}</strong><span>${esc(task.floor)} · ${esc(task.area)} · Công việc #${esc(task.id)}</span></div>
      <section class="hk-detail-section"><div class="hk-section-title"><h4>Checklist dọn phòng</h4><span class="badge ${allDone ? 'badge-success' : 'badge-warning'}">${checklist.filter(item => item.completed_at || task.status === 'completed' || !item.required).length}/${checklist.length} mục</span></div><div class="hk-checklist">${checklist.map(item => { const completed = Boolean(item.completed_at) || task.status === 'completed'; const control = completed ? '<span class="hk-check-mark" role="img" aria-label="Đã hoàn tất">✓</span>' : `<input class="checkbox checkbox-success" type="checkbox" data-hk-check="${esc(item.key)}" ${!owner || state.detailLoading ? 'disabled' : ''}>`; return `<label class="hk-check-item${completed ? ' is-complete' : ''}">${control}<span>${esc(item.label)}${item.required ? `<small>${completed ? 'Đã hoàn tất · Bắt buộc' : 'Bắt buộc'}</small>` : ''}</span></label>`; }).join('')}</div></section>
      ${state.incidentMode && owner ? `<form class="hk-incident-form" data-hk-incident-form><h4>Báo sự cố phòng</h4><label class="form-control"><span class="label-text">Loại sự cố</span><select class="select select-bordered" name="incident_type" required><option value="damage">Hư hỏng</option><option value="missing_item">Thiếu vật dụng</option><option value="maintenance">Cần bảo trì</option><option value="cleanliness">Vệ sinh chưa đạt</option><option value="other">Khác</option></select></label><label class="form-control"><span class="label-text">Mô tả</span><textarea class="textarea textarea-bordered" name="description" maxlength="1000" required placeholder="Mô tả vị trí và tình trạng..."></textarea></label><label class="form-control"><span class="label-text">Ảnh minh chứng (không bắt buộc)</span><input class="file-input file-input-bordered" name="photo" type="file" accept="image/jpeg,image/png"></label><label class="hk-check-item"><input class="checkbox checkbox-warning" type="checkbox" name="needs_reinspection"><span>Đánh dấu phòng cần kiểm tra lại trước khi bàn giao</span></label><p class="hk-form-error" data-hk-form-error role="alert" hidden></p><button class="btn btn-warning btn-sm" type="submit" ${state.detailLoading ? 'disabled' : ''}>Gửi báo cáo sự cố</button></form>` : ''}
      <section class="hk-detail-section"><div class="hk-section-title"><h4>Sự cố đã ghi nhận</h4>${owner ? `<button class="btn btn-outline btn-sm" type="button" data-hk-report>${state.incidentMode ? 'Đóng biểu mẫu' : 'Báo sự cố'}</button>` : ''}</div><div class="hk-record-list">${incidentMarkup}</div></section>
      <section class="hk-detail-section"><h4>Lịch sử thao tác</h4><ol class="hk-timeline">${eventsMarkup}</ol></section>
      <div class="hk-dialog-actions"><button type="button" class="btn btn-ghost" data-hk-close>Đóng</button>${owner ? `<button type="button" class="btn btn-primary" data-hk-action="complete" data-task-id="${esc(task.id)}" ${!allDone || state.detailLoading ? 'disabled title="Hoàn thành các mục bắt buộc trong checklist trước"' : ''}>Xác nhận phòng sạch</button>` : ''}</div>`;
  };
  const openDetails = async (taskId, incident = false) => {
    const task = state.tasks.find(item => String(item.id) === String(taskId)); if (!task) return;
    state.detailsTask = task; state.detailData = null; state.detailLoading = false; state.incidentMode = incident;
    dialog.showModal(); renderDetails();
    try { state.detailData = await api.get(`/housekeeping/tasks/${encodeURIComponent(task.id)}/details`, { timeoutMs: 10000 }); renderDetails(); }
    catch (error) { dialogContent.innerHTML = `<div class="alert alert-error">${esc(error?.message || 'Không thể tải checklist và nhật ký.')}</div><button class="btn btn-ghost" type="button" data-hk-close>Đóng</button>`; }
  };
  const performAction = async (action, taskId) => {
    const task = state.tasks.find(item => String(item.id) === String(taskId)); if (!task) return;
    state.busyTaskId = String(taskId); renderTable();
    try {
      if (action === 'start') { await api.post(`/housekeeping/tasks/${encodeURIComponent(task.id)}/start`, {}, { timeoutMs: 10000 }); notify(`Đã nhận dọn phòng ${task.room_number}.`); }
      else { await api.post(`/housekeeping/tasks/${encodeURIComponent(task.id)}/complete`, { room_version: task.room_version }, { timeoutMs: 10000 }); notify(`Phòng ${task.room_number} đã được xác nhận sạch.`); dialog.close(); }
      state.busyTaskId = ''; await refresh();
    } catch (err) { state.busyTaskId = ''; notify(err?.status === 409 ? (err.message || 'Hoàn thành checklist và tải lại trước khi xác nhận sạch.') : err?.status === 403 ? 'Công việc này đã được nhân viên khác nhận hoặc tài khoản chưa có quyền.' : err?.message || 'Chưa thể cập nhật công việc.'); await refresh(); if (state.detailsTask) { state.detailsTask = state.tasks.find(x => String(x.id) === String(taskId)) || state.detailsTask; openDetails(taskId, state.incidentMode); } }
  };
  const showDialogError = message => { const error = find('[data-hk-form-error]'); if (error) { error.textContent = message; error.hidden = false; } else notify(message); };

  container.onclick = event => {
    const button = event.target.closest('[data-hk-action]'); if (button) { performAction(button.dataset.hkAction, button.dataset.taskId); return; }
    const detail = event.target.closest('[data-hk-details]'); if (detail) { openDetails(detail.dataset.hkDetails); return; }
    const incident = event.target.closest('[data-hk-incident]'); if (incident) { openDetails(incident.dataset.hkIncident, true); return; }
    if (event.target.closest('[data-hk-close]')) { dialog.close(); return; }
    if (event.target.closest('[data-hk-report]')) { state.incidentMode = !state.incidentMode; renderDetails(); return; }
    if (event.target.closest('[data-hk-refresh]')) { refresh(); return; }
    if (event.target.closest('[data-hk-reset]')) { state.query = ''; state.filter = state.floor = state.area = 'all'; state.view = 'active'; state.page = 1; find('[data-hk-search]').value = ''; find('[data-hk-filter]').value = find('[data-hk-floor]').value = find('[data-hk-area]').value = 'all'; container.querySelectorAll('[data-hk-view]').forEach(tab => { const selected = tab.dataset.hkView === 'active'; tab.setAttribute('aria-selected', String(selected)); tab.classList.toggle('btn-ghost', !selected); }); renderTable(); return; }
    const tab = event.target.closest('[data-hk-view]'); if (tab) { state.view = tab.dataset.hkView; state.page = 1; container.querySelectorAll('[data-hk-view]').forEach(item => { const selected = item === tab; item.setAttribute('aria-selected', String(selected)); item.classList.toggle('btn-ghost', !selected); }); renderTable(); }
  };
  container.oninput = event => { if (event.target.matches('[data-hk-search]')) { state.query = event.target.value; state.page = 1; renderTable(); } };
  container.onchange = async event => {
    if (event.target.matches('[data-hk-filter]')) state.filter = event.target.value;
    else if (event.target.matches('[data-hk-floor]')) state.floor = event.target.value;
    else if (event.target.matches('[data-hk-area]')) state.area = event.target.value;
    else if (event.target.matches('[data-hk-check]')) {
      if (!state.detailsTask) return;
      state.detailLoading = true; renderDetails();
      try { await api.post(`/housekeeping/tasks/${encodeURIComponent(state.detailsTask.id)}/checklist`, { item_key: event.target.dataset.hkCheck, checked: event.target.checked }, { timeoutMs: 10000 }); state.detailData = await api.get(`/housekeeping/tasks/${encodeURIComponent(state.detailsTask.id)}/details`, { timeoutMs: 10000 }); }
      catch (error) { notify(error?.message || 'Không thể cập nhật checklist.'); state.detailData = await api.get(`/housekeeping/tasks/${encodeURIComponent(state.detailsTask.id)}/details`).catch(() => state.detailData); }
      state.detailLoading = false; renderDetails(); await refreshMetrics(); return;
    } else return;
    state.page = 1; renderTable();
  };
  container.onsubmit = async event => {
    const form = event.target.closest('[data-hk-incident-form]'); if (!form) return;
    event.preventDefault(); const submit = form.querySelector('[type="submit"]'); submit.disabled = true;
    try {
      const values = new FormData(form), file = values.get('photo'); let photoUrl = '';
      if (file instanceof File && file.size) photoUrl = (await uploadMedia(file, '/housekeeping/media')).url;
      await api.post(`/housekeeping/tasks/${encodeURIComponent(state.detailsTask.id)}/incidents`, { incident_type: values.get('incident_type'), description: values.get('description'), photo_url: photoUrl, needs_reinspection: values.has('needs_reinspection') }, { timeoutMs: 15000 });
      notify('Đã gửi sự cố đến trung tâm thông báo nhân viên.'); state.incidentMode = false; await openDetails(state.detailsTask.id); await refresh();
    } catch (error) { submit.disabled = false; showDialogError(error?.message || 'Không thể gửi báo cáo sự cố.'); }
  };
  if (window.lucide?.createIcons) window.lucide.createIcons();
  refresh();
}
