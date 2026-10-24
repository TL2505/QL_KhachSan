import { escapeHtml as esc } from './ui.js';

const typeName = room => room.room_type_name || 'Chưa có thông tin';
const capacityOf = room => {
  const capacity = Number(room.capacity);
  return Number.isInteger(capacity) && capacity > 0 ? capacity : 0;
};
const imageUrl = value => {
  if (typeof value !== 'string' || !value.trim()) return '';
  try {
    const url = new URL(value, window.location.origin);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
  } catch {
    return '';
  }
};
const amenitiesFor = value => Array.isArray(value)
  ? value.map(item => String(item).trim()).filter(Boolean)
  : String(value || '').split(',').map(item => item.trim()).filter(Boolean);

/** Shared room-card markup for customer discovery and staff booking flows. */
export function renderRoomCard(room, {
  statusLabel = 'Chưa cập nhật',
  statusKind = 'not-ready',
  canSelect = false,
  reason = '',
  actionLabel = 'Đặt phòng',
  actionAttribute = 'data-book',
  selected = false,
  rentalType = 'DAILY',
  showDetails = true,
  money = value => new Intl.NumberFormat('vi-VN').format(Number(value)) + ' đ'
} = {}) {
  const id = esc(room.id);
  const capacity = capacityOf(room);
  const source = imageUrl(room.image_url);
  const amenities = amenitiesFor(room.amenities).slice(0, 3);
  const actionDataAttribute = ['data-book', 'data-select-room'].includes(actionAttribute) ? actionAttribute : 'data-book';
  const daily = rentalType === 'DAILY';
  return `<article class="card card-border customer-room-card${selected ? ' desk-room-card-selected' : ''}">
    <div class="customer-room-image">${source ? `<img src="${esc(source)}" alt="${esc(room.image_alt || typeName(room))}" loading="lazy">` : '<div class="room-image-placeholder"><span aria-hidden="true">A</span><small>Ảnh phòng đang cập nhật</small></div>'}<span class="room-status ${esc(statusKind)}">${esc(statusLabel)}</span></div>
    <div class="customer-room-content">
      <div class="discovery-room-heading"><small>HẠNG PHÒNG · PHÒNG ${esc(room.room_number || room.id)}</small><h3>${esc(typeName(room))}</h3><p>${capacity ? `Tối đa ${capacity} khách` : 'Chưa có thông tin sức chứa'}</p></div>
      ${room.description ? `<p class="discovery-room-description">${esc(room.description)}</p>` : ''}
      <div class="discovery-room-amenities" aria-label="Tiện nghi">${amenities.length ? amenities.map(item => `<span>${esc(item)}</span>`).join('') : '<span>Tiện nghi chưa cập nhật</span>'}</div>
      <div class="discovery-card-footer"><div class="discovery-room-price"><small>${daily ? 'GIÁ MỖI ĐÊM' : 'GIÁ GÓI'}</small><strong>${daily ? money(room.price) : 'Xem báo giá'}</strong></div>
        <div class="customer-room-actions">${showDetails ? `<button class="btn booking-room-detail" type="button" data-detail="${id}">Chi tiết</button>` : ''}<button class="btn booking-room-select" type="button" ${actionDataAttribute}="${id}" ${canSelect ? '' : `disabled title="${esc(reason)}" aria-label="${esc(actionLabel)}: ${esc(reason)}"`}>${selected ? 'Đã chọn phòng' : (canSelect ? esc(actionLabel) : 'Không thể đặt')}</button></div>
      </div>
      ${canSelect || !reason ? '' : `<p class="room-unavailable-reason">${esc(reason)}</p>`}
    </div>
  </article>`;
}
