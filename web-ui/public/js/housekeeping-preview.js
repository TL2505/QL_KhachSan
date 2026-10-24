const tasks = [
  { id: 'HK-302', room: '305', type: 'Standard', handoff: 'Đã bàn giao · 11:20', ready: true, nextArrival: '13:00', priority: 'Gấp', status: 'waiting', assignedTo: '' },
  { id: 'HK-301', room: '402', type: 'Deluxe', handoff: 'Đã bàn giao · 12:00', ready: true, nextArrival: '14:00', priority: 'Cao', status: 'waiting', assignedTo: '' },
  { id: 'HK-300', room: '701', type: 'Suite', handoff: 'Đã bàn giao · 11:45', ready: true, nextArrival: '15:30', priority: 'Thường', status: 'in_progress', assignedTo: 'Bạn', startedAt: '12:05', progress: 65 },
  { id: 'HK-299', room: '110', type: 'Family', handoff: 'Dự kiến bàn giao · 13:30', ready: false, nextArrival: '16:00', priority: 'Theo dõi', status: 'not_released', assignedTo: '' },
  { id: 'HK-298', room: '605', type: 'Deluxe', handoff: 'Đã bàn giao · 10:10', ready: true, nextArrival: '—', priority: 'Thường', status: 'done', assignedTo: 'Minh', finishedAt: '11:02' }
];

const statusLabel = {
  waiting: ['Chờ nhận dọn', 'badge-warning'],
  in_progress: ['Đang dọn', 'badge-info'],
  done: ['Đã hoàn tất', 'badge-success'],
  not_released: ['Chưa bàn giao', 'badge-neutral']
};

const list = document.getElementById('housekeeping-task-list');
const search = document.getElementById('task-search');
const filter = document.getElementById('task-status-filter');
const toast = document.getElementById('housekeeping-toast');
let toastTimer;
let currentView = 'active';
const activeNav = document.getElementById('show-active-tasks');
const allNav = document.getElementById('show-all-tasks');
const completedNav = document.getElementById('show-completed-tasks');
const heading = document.getElementById('hk-queue-heading');

const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[char]));

function taskAction(task) {
  if (task.status === 'waiting' && task.ready) {
    return '<button class="btn btn-primary btn-sm" type="button" data-task-action="start" data-task-id="' + escapeHtml(task.id) + '">Nhận dọn</button>';
  }
  if (task.status === 'waiting' && !task.ready) {
    return '<button class="btn btn-sm" type="button" disabled aria-describedby="handoff-rule">Chưa đến giờ</button>';
  }
  if (task.status === 'not_released') {
    return '<div><button class="btn btn-sm" type="button" disabled title="Chỉ có thể bắt đầu sau khi lễ tân bàn giao phòng">Chưa đến giờ</button>' +
      '<span class="hk-status-text">Nhận sau ' + escapeHtml(task.handoff.split(' · ')[1] || 'khi được bàn giao') + '</span></div>';
  }
  if (task.status === 'in_progress' && task.assignedTo === 'Bạn') {
    return '<button class="btn btn-sm hk-btn-complete" type="button" data-task-action="complete" data-task-id="' + escapeHtml(task.id) + '">Xác nhận sạch phòng</button>';
  }
  if (task.status === 'in_progress') return '<span class="hk-status-text">Đang được ' + escapeHtml(task.assignedTo) + ' xử lý</span>';
  return '<span class="hk-status-text">Hoàn tất lúc ' + escapeHtml(task.finishedAt || '—') + '</span>';
}

function render() {
  const query = search.value.trim().toLocaleLowerCase('vi');
  const selectedStatus = filter.value;
  const visible = tasks.filter(task => {
    const matchesQuery = !query || (task.room + ' ' + task.type + ' ' + task.id).toLocaleLowerCase('vi').includes(query);
    const matchesStatus = selectedStatus === 'all' || task.status === selectedStatus;
    const matchesView = currentView === 'all'
      || currentView === 'active' && task.status !== 'done'
      || currentView === 'done' && task.status === 'done';
    return matchesQuery && matchesStatus && matchesView;
  });

  list.innerHTML = visible.length ? visible.map(task => {
    const [label, badge] = statusLabel[task.status];
    const priorityClass = task.priority === 'Gấp' ? 'badge-error' : task.priority === 'Cao' ? 'badge-warning' : task.priority === 'Thường' ? 'badge-ghost' : 'badge-neutral';
    const taskClass = task.status === 'done' ? ' class="hk-row-done"' : '';
    const progress = task.status === 'in_progress' && task.progress ? '<span class="hk-status-text">Bắt đầu ' + escapeHtml(task.startedAt) + ' · ' + escapeHtml(task.progress) + '%</span>' : '';
    return '<tr' + taskClass + '>' +
      '<td class="hk-room-cell"><strong>Phòng ' + escapeHtml(task.room) + '</strong><small>' + escapeHtml(task.type) + ' · Mã việc ' + escapeHtml(task.id) + '</small></td>' +
      '<td class="hk-hand-off"><strong>' + escapeHtml(task.handoff.split(' · ')[0]) + '</strong><small>' + escapeHtml(task.handoff.split(' · ')[1] || '') + '</small></td>' +
      '<td class="hk-next-checkin">' + escapeHtml(task.nextArrival === '—' ? 'Không có lịch kế tiếp' : task.nextArrival) + '</td>' +
      '<td class="hk-priority-cell"><span class="badge ' + priorityClass + '">' + escapeHtml(task.priority) + '</span></td>' +
      '<td><span class="badge ' + badge + '">' + escapeHtml(label) + '</span>' + progress + '</td>' +
      '<td class="hk-action-cell">' + taskAction(task) + '</td>' +
      '</tr>';
  }).join('') : '<tr><td class="hk-empty" colspan="6">Không tìm thấy công việc phù hợp. Hãy thử thay đổi bộ lọc.</td></tr>';

  const counts = tasks.reduce((result, task) => {
    result[task.status] = (result[task.status] || 0) + 1;
    return result;
  }, {});
  document.getElementById('count-waiting').textContent = counts.waiting || 0;
  document.getElementById('count-progress').textContent = counts.in_progress || 0;
  document.getElementById('count-done').textContent = counts.done || 0;
  const nextArrival = tasks
    .filter(task => task.status === 'waiting' && task.ready && task.nextArrival !== '—')
    .map(task => task.nextArrival)
    .sort()[0];
  document.getElementById('next-arrival-value').textContent = nextArrival || '—';
  document.getElementById('task-count').textContent = 'Đang xem ' + visible.length + ' / ' + tasks.length + ' công việc';
  document.getElementById('task-range').textContent = visible.length + ' công việc · dữ liệu minh họa';
}

function setView(view) {
  currentView = view;
  [activeNav, allNav, completedNav].forEach(button => button.removeAttribute('aria-current'));
  if (view === 'active') {
    activeNav.setAttribute('aria-current', 'page');
    heading.textContent = 'Công việc đang xử lý';
  } else if (view === 'done') {
    completedNav.setAttribute('aria-current', 'page');
    heading.textContent = 'Phòng đã hoàn tất';
  } else {
    allNav.setAttribute('aria-current', 'page');
    heading.textContent = 'Tất cả công việc';
  }
}

function announce(message) {
  toast.textContent = message;
  toast.classList.add('is-visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('is-visible'), 3200);
}

list.addEventListener('click', event => {
  const button = event.target.closest('[data-task-action]');
  if (!button) return;
  const task = tasks.find(item => item.id === button.dataset.taskId);
  if (!task) return;
  if (button.dataset.taskAction === 'start' && task.status === 'waiting' && task.ready) {
    task.status = 'in_progress';
    task.assignedTo = 'Bạn';
    task.startedAt = '12:15';
    task.progress = 0;
    announce('Đã nhận dọn phòng ' + task.room + '. Công việc đã chuyển sang Đang dọn.');
  } else if (button.dataset.taskAction === 'complete' && task.status === 'in_progress' && task.assignedTo === 'Bạn') {
    task.status = 'done';
    task.finishedAt = '12:15';
    task.progress = 100;
    announce('Đã xác nhận phòng ' + task.room + ' sạch và sẵn sàng.');
  }
  render();
});

search.addEventListener('input', render);
filter.addEventListener('change', () => {
  setView('all');
  render();
});
document.getElementById('reset-filters').addEventListener('click', () => {
  search.value = '';
  filter.value = 'all';
  setView('active');
  render();
});
document.getElementById('show-all-tasks').addEventListener('click', () => {
  setView('all');
  filter.value = 'all';
  render();
});
document.getElementById('show-completed-tasks').addEventListener('click', () => {
  setView('done');
  filter.value = 'all';
  render();
});
activeNav.addEventListener('click', () => {
  setView('active');
  filter.value = 'all';
  render();
});

render();
