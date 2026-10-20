export function renderPaginationComponent(containerId, totalItems, itemsPerPage, currentPage, onPageChange, onPageSizeChange) {
  const container = document.getElementById(containerId);
  if (!container) return;

  const total = Math.max(0, Number(totalItems) || 0);
  const pageSize = Math.max(1, Number(itemsPerPage) || 10);
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const page = Math.min(pageCount, Math.max(1, Number(currentPage) || 1));
  const firstItem = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const lastItem = Math.min(page * pageSize, total);

  container.replaceChildren();

  const nav = document.createElement('div');
  nav.className = 'portal-pagination';
  nav.setAttribute('role', 'group');
  nav.setAttribute('aria-label', 'Phân trang');

  const summary = document.createElement('span');
  summary.className = 'portal-pagination-summary';
  summary.textContent = `Hiển thị ${firstItem}–${lastItem} trên ${total} mục`;

  const controls = document.createElement('div');
  controls.className = 'portal-pagination-controls';

  const sizeLabel = document.createElement('label');
  sizeLabel.className = 'portal-pagination-size-label';
  sizeLabel.textContent = 'Số dòng';

  const sizeSelect = document.createElement('select');
  sizeSelect.className = 'portal-pagination-size';
  sizeSelect.setAttribute('aria-label', 'Số dòng mỗi trang');
  const sizes = [...new Set([5, 10, 25, 50, pageSize])].sort((a, b) => a - b);
  sizes.forEach(size => {
    const option = document.createElement('option');
    option.value = String(size);
    option.textContent = String(size);
    option.selected = size === pageSize;
    sizeSelect.append(option);
  });
  sizeLabel.append(sizeSelect);
  sizeSelect.addEventListener('change', () => onPageSizeChange?.(Number(sizeSelect.value)));

  const previous = document.createElement('button');
  previous.type = 'button';
  previous.className = 'portal-pagination-button';
  previous.textContent = 'Trước';
  previous.setAttribute('aria-label', 'Trang trước');
  previous.disabled = page <= 1;
  previous.addEventListener('click', () => {
    if (page > 1) onPageChange?.(page - 1);
  });

  const indicator = document.createElement('span');
  indicator.className = 'portal-pagination-page';
  indicator.setAttribute('aria-live', 'polite');
  indicator.textContent = `Trang ${page} / ${pageCount}`;

  const next = document.createElement('button');
  next.type = 'button';
  next.className = 'portal-pagination-button';
  next.textContent = 'Sau';
  next.setAttribute('aria-label', 'Trang sau');
  next.disabled = page >= pageCount;
  next.addEventListener('click', () => {
    if (page < pageCount) onPageChange?.(page + 1);
  });

  controls.append(sizeLabel, previous, indicator, next);
  nav.append(summary, controls);
  container.append(nav);
}

if (typeof window !== 'undefined') {
  window.renderPaginationComponent = renderPaginationComponent;
}
