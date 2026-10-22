import { api } from '../../api.js';
import { bookingKey } from '../booking-api.js';
import { bindRoomImageFallbacks } from '../shared/room-images.js';

const money = value => value == null || value === '' || !Number.isFinite(Number(value))
  ? 'Chưa có giá'
  : new Intl.NumberFormat('vi-VN').format(Number(value)) + ' đ';
const capacityOf = room => {
  const capacity = Number(room.capacity);
  return Number.isInteger(capacity) && capacity > 0 ? capacity : 0;
};
const typeName = room => room.room_type_name || 'Chưa có thông tin';
const labels = {
  available: ['Còn phòng', 'available'],
  reserved: ['Đã đặt', 'reserved'],
  occupied: ['Đang có khách', 'occupied'],
  maintenance: ['Bảo trì', 'maintenance'],
  not_ready: ['Chưa sẵn sàng', 'not-ready'],
  pricing_unconfigured: ['Chưa cấu hình giá', 'not-ready']
};
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[c]));
const localDate = date => {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date(date));
  const value = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
};
const shiftDate = (value, days) => {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
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
const statusFor = room => {
  if (room.availability_status === 'not_ready' && room.room_operational_status === 'vacant_dirty') {
    return ['Cần dọn phòng', 'not-ready'];
  }
  if (room.availability_status === 'not_ready' && room.room_operational_status === 'occupied_dirty') {
    return ['Có khách - cần dọn', 'not-ready'];
  }
  return labels[room.availability_status] || ['Chưa cập nhật', 'not-ready'];
};
const statusSummaryItems = [
  ['Còn phòng', 'available'],
  ['Đã đặt', 'reserved'],
  ['Đang có khách', 'occupied'],
  ['Bảo trì', 'maintenance'],
  ['Cần dọn phòng', 'not-ready'],
  ['Có khách - cần dọn', 'not-ready'],
  ['Chưa cấu hình giá', 'pricing_unconfigured'],
  ['Chưa sẵn sàng', 'not-ready']
];
const availabilityReason = status => ({
  reserved: 'Phòng đã có lịch đặt trùng khoảng thời gian này.',
  occupied: 'Phòng đang được sử dụng trong khoảng thời gian đã chọn.',
  maintenance: 'Phòng đang bảo trì và không thể đặt.',
  pricing_unconfigured: 'Loại hình này chưa có đơn giá được cấu hình cho hạng phòng.',
  not_ready: 'Phòng chưa sẵn sàng cho ngày nhận phòng đã chọn.',
  unknown: 'Chưa có thông tin tình trạng phòng mới nhất.'
}[status] || 'Phòng không khả dụng.');
const validDateRange = (checkIn, checkOut, rentalType = 'DAILY') => {
  const today = localDate(new Date());
  const calendarDate = value => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
    const [year, month, day] = value.split('-').map(Number);
    const parsed = new Date(Date.UTC(year, month - 1, day));
    return parsed.getUTCFullYear() === year && parsed.getUTCMonth() === month - 1 && parsed.getUTCDate() === day;
  };
  return /^\d{4}-\d{2}-\d{2}$/.test(checkIn) &&
    /^\d{4}-\d{2}-\d{2}$/.test(checkOut) &&
    calendarDate(checkIn) && calendarDate(checkOut) && checkIn >= today &&
    (rentalType === 'HOURLY' ? checkOut >= checkIn : checkOut > checkIn);
};
const validTime = value => /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value);
const validStayWindow = (checkIn, checkOut, rentalType, inTime, outTime) => {
  if (!validDateRange(checkIn, checkOut, rentalType) || !validTime(inTime) || !validTime(outTime)) return false;
  if (rentalType === 'DAILY') return inTime === '14:00' && outTime === '12:00';
  if (rentalType === 'OVERNIGHT') {
    return checkOut === shiftDate(checkIn, 1) && inTime <= '22:00' && outTime >= '06:00';
  }
  const [inHour, inMinute] = inTime.split(':').map(Number);
  const [outHour, outMinute] = outTime.split(':').map(Number);
  const days = Math.round((Date.parse(`${checkOut}T00:00:00Z`) - Date.parse(`${checkIn}T00:00:00Z`)) / 86400000);
  const durationMinutes = days * 1440 + outHour * 60 + outMinute - inHour * 60 - inMinute;
  return durationMinutes > 0;
};

export async function renderDiscovery(container, session) {
  const requestedVoucher = new URLSearchParams(window.location.search).get('voucher') || '';
  const today = localDate(new Date());
  const tomorrow = localDate(Date.now() + 86400000);
  container.innerHTML = `
    <div class="page-header">
      <h1>Khám phá phòng</h1>
      <p>Tìm phòng theo thời gian lưu trú, số khách, hạng phòng và mức giá.</p>
    </div>
    <section class="card discovery-filter">
      <div class="discovery-fields">
        <label>Nhận phòng<input id="search-checkin" type="date" value="${today}" min="${today}" required></label>
        <label>Trả phòng<input id="search-checkout" type="date" value="${tomorrow}" min="${tomorrow}" required></label>
        <label>Loại hình lưu trú<select class="select select-sm" id="search-rental-type"><option value="DAILY">Theo ngày</option><option value="OVERNIGHT">Qua đêm</option><option value="HOURLY">Theo giờ</option></select></label>
        <label>Giờ nhận phòng<input id="search-checkin-time" type="time" value="14:00" required></label>
        <label>Giờ trả phòng<input id="search-checkout-time" type="time" value="12:00" required></label>
        <label>Số khách<input id="guest-count" type="number" min="1" max="20" step="1" value="2" required></label>
        <label>Hạng phòng<select id="filter-type"><option value="">Tất cả hạng</option></select></label>
        <label>Giá tối đa<select id="filter-price"><option value="0">Mọi mức giá</option><option value="1000000">Dưới 1 triệu</option><option value="2000000">Dưới 2 triệu</option><option value="5000000">Dưới 5 triệu</option></select></label>
        <button id="btn-search-rooms" class="btn btn-primary" type="button">Tìm phòng</button>
      </div>
      <p id="discovery-feedback" class="discovery-feedback" role="status" aria-live="polite"></p>
      <div class="discovery-status-overview" aria-live="polite">
        <div id="room-status-summary" class="room-status-summary" role="group" aria-label="Thống kê số phòng theo trạng thái">
          <p class="room-status-summary-message">Tìm phòng để xem thống kê trạng thái.</p>
        </div>
      </div>
    </section>
    <div class="card p-4 mb-4">
      <label for="booking-voucher-code">Mã ưu đãi tiền phòng</label>
      <input id="booking-voucher-code" class="input input-sm" maxlength="50" placeholder="Nhập mã hoặc để trống" value="${esc(requestedVoucher)}">
      <small>Mã được kiểm tra khi chọn phòng. Bạn sẽ xem giá sau giảm trước khi xác nhận; có thể xóa mã để đặt không dùng ưu đãi.</small>
    </div>
    <div class="discovery-result-head"><strong id="room-result-count">Đang tải phòng…</strong><span id="discovery-price-unit">Giá theo mỗi đêm</span></div>
    <div id="search-rooms-grid" class="customer-room-grid"></div>

    <dialog id="room-detail-dialog" class="discovery-dialog" aria-labelledby="room-detail-title">
      <div class="discovery-dialog-header">
        <div><p class="portal-eyebrow">CHI TIẾT PHÒNG</p><h2 id="room-detail-title"></h2></div>
        <button type="button" class="discovery-close" data-close-detail aria-label="Đóng chi tiết">×</button>
      </div>
      <div id="room-detail-content" class="discovery-detail-content"></div>
      <div class="discovery-dialog-actions"><button type="button" class="btn btn-secondary" data-close-detail>Quay lại danh sách</button></div>
    </dialog>

    <dialog id="booking-dialog" class="discovery-dialog discovery-booking-dialog" aria-labelledby="booking-dialog-title">
      <div class="discovery-dialog-header">
        <div><p class="portal-eyebrow">XÁC NHẬN LƯU TRÚ</p><h2 id="booking-dialog-title">Xác nhận đặt phòng</h2></div>
        <button type="button" class="discovery-close" data-close-booking aria-label="Đóng xác nhận">×</button>
      </div>
      <div id="booking-dialog-content" class="discovery-booking-content"></div>
      <p id="booking-feedback" class="discovery-feedback" role="status" aria-live="polite"></p>
      <div class="discovery-dialog-actions">
        <button type="button" class="btn btn-secondary" data-close-booking>Quay lại</button>
        <button type="button" class="btn btn-primary" id="confirm-booking">Xác nhận đặt phòng</button>
      </div>
    </dialog>
  `;

  let rooms = [];
  let pendingRoom = null;
  let loading = false;
  let booking = false;
  let bookingSucceeded = false;
  let paymentWatchTimer = null;
  const byId = id => container.querySelector(`#${id}`);
  const feedback = (message = '', error = false) => {
    byId('discovery-feedback').textContent = message;
    byId('discovery-feedback').classList.toggle('is-error', error);
  };
  const renderStatusSummary = (sourceRooms, message = '') => {
    const summary = byId('room-status-summary');
    if (message) {
      summary.innerHTML = `<p class="room-status-summary-message">${esc(message)}</p>`;
      return;
    }
    const counts = new Map();
    sourceRooms.forEach(room => {
      const [label] = statusFor(room);
      counts.set(label, (counts.get(label) || 0) + 1);
    });
    summary.innerHTML = `
      <div class="room-status-summary-total"><strong>${sourceRooms.length}</strong><span>Tổng số phòng</span></div>
      <div class="room-status-summary-items">
        ${statusSummaryItems.map(([label, kind]) => `
          <div class="room-status-summary-item ${kind}" title="${esc(label)}">
            <span>${esc(label)}</span><strong>${counts.get(label) || 0}</strong>
          </div>
        `).join('')}
      </div>`;
  };
  const closeDialog = dialog => {
    if (dialog.open) dialog.close();
  };
  const stopPaymentWatch = () => {
    clearTimeout(paymentWatchTimer);
    paymentWatchTimer = null;
  };
  const watchPayment = folioId => {
    stopPaymentWatch();
    const deadline = Date.now() + 5 * 60 * 1000;
    const poll = async () => {
      if (!byId('booking-dialog').open || Date.now() >= deadline) return;
      try {
        const payment = await api.get(`/folios/${folioId}/payment-status`);
        const state=String(payment.payment_status||'WAITING');
        byId('booking-payment-status').textContent=state==='PARTIAL'?`Thanh toán thiếu. Còn ${Number(payment.remaining_amount).toLocaleString('vi-VN')} đ.`:state==='RECONCILIATION_ERROR'?'Lỗi đối soát, thanh toán chưa hoàn tất. Vui lòng liên hệ lễ tân.':'Đang chờ xác nhận thanh toán.';
        if (state === 'PAID') {
          byId('booking-payment-status').textContent = 'Đã nhận thanh toán thành công.';
          return;
        }
      } catch {
        byId('booking-payment-status').textContent = 'Chưa thể cập nhật trạng thái thanh toán. Đơn đặt phòng vẫn được lưu; vui lòng kiểm tra Lịch sử đặt phòng.';
        return;
      }
      paymentWatchTimer = setTimeout(poll, 3000);
    };
    paymentWatchTimer = setTimeout(poll, 2500);
  };

  const render = () => {
    const guests = Number(byId('guest-count').value);
    const rentalType = byId('search-rental-type').value;
    const type = byId('filter-type').value;
    const price = Number(byId('filter-price').value);
    byId('discovery-price-unit').textContent = rentalType === 'DAILY' ? 'Giá theo mỗi đêm' : 'Báo giá theo loại hình đã chọn';
    const filtered = rooms.filter(room =>
      (!type || String(room.room_type_name || '') === type) &&
      (!price || Number(room.price) <= price) &&
      capacityOf(room) >= guests
    );
    byId('room-result-count').textContent = `${filtered.length} phòng phù hợp`;
    byId('search-rooms-grid').innerHTML = filtered.length ? filtered.map(room => {
      const [text, kind] = statusFor(room);
      const capacity = capacityOf(room);
      const capacityOkay = capacity > 0 && Number.isInteger(guests) && guests >= 1 && guests <= capacity;
      const checkInTime = byId('search-checkin-time').value;
      const checkOutTime = byId('search-checkout-time').value;
      const supportedBookingTimes = validStayWindow(byId('search-checkin').value, byId('search-checkout').value, rentalType, checkInTime, checkOutTime);
      const canBook = room.availability_status === 'available' && capacityOkay && supportedBookingTimes;
      const src = imageUrl(room.image_url);
      const amenities = amenitiesFor(room.amenities).slice(0, 3);
      const reason = room.availability_status === 'available' && !supportedBookingTimes
        ? 'Đặt phòng trực tuyến hiện áp dụng giờ nhận phòng 14:00 và trả phòng 12:00.'
        : room.availability_status === 'available'
        ? (capacity ? `Phòng chỉ phục vụ tối đa ${capacity} khách.` : 'Chưa có thông tin sức chứa để xác nhận đặt phòng.')
        : availabilityReason(room.availability_status);
      return `<article class="customer-room-card">
        <div class="customer-room-image">${src ? `<img src="${esc(src)}" alt="${esc(room.image_alt || typeName(room))}" loading="lazy">` : '<div class="room-image-placeholder"><span aria-hidden="true">A</span><small>Ảnh phòng đang cập nhật</small></div>'}<span class="room-status ${kind}">${esc(text)}</span></div>
        <div class="customer-room-content">
          <div class="discovery-room-heading">
            <small>HẠNG PHÒNG · PHÒNG ${esc(room.room_number || room.id)}</small>
            <h3>${esc(typeName(room))}</h3>
            <p>${capacity ? `Tối đa ${capacity} khách` : 'Chưa có thông tin sức chứa'}</p>
          </div>
          ${room.description ? `<p class="discovery-room-description">${esc(room.description)}</p>` : ''}
          <div class="discovery-room-amenities" aria-label="Tiện nghi">${amenities.length ? amenities.map(item => `<span>${esc(item)}</span>`).join('') : '<span>Tiện nghi chưa cập nhật</span>'}</div>
          <div class="discovery-card-footer">
            <div class="discovery-room-price"><small>${rentalType === 'DAILY' ? 'GIÁ MỖI ĐÊM' : 'GIÁ GÓI'}</small><strong>${rentalType === 'DAILY' ? money(room.price) : 'Xem báo giá'}</strong></div>
            <div class="customer-room-actions">
              <button class="btn btn-secondary" type="button" data-detail="${esc(room.id)}">Chi tiết</button>
              <button class="btn btn-primary" type="button" data-book="${esc(room.id)}" ${canBook ? '' : `disabled title="${esc(reason)}" aria-label="Đặt phòng: ${esc(reason)}"`}>${canBook ? 'Đặt phòng' : 'Không thể đặt'}</button>
            </div>
          </div>
          ${canBook ? '' : `<p class="room-unavailable-reason">${esc(reason)}</p>`}
        </div>
      </article>`;
    }).join('') : rooms.length && Number.isInteger(guests) && guests > 0
      ? `<p class="customer-empty">Không có phòng đủ sức chứa cho ${guests} khách trong kết quả tìm kiếm.</p>`
      : '<p class="customer-empty">Không có phòng phù hợp với bộ lọc đã chọn.</p>';
    bindRoomImageFallbacks(byId('search-rooms-grid'));
  };

  const load = async ({preferredType = ''} = {}) => {
    if (loading || booking) return;
    const checkIn = byId('search-checkin').value;
    const checkOut = byId('search-checkout').value;
    if (!validDateRange(checkIn, checkOut, byId('search-rental-type').value)) {
      rooms = [];
      renderStatusSummary([], 'Nhập thời gian lưu trú hợp lệ rồi tìm phòng để xem thống kê.');
      render();
      byId('room-result-count').textContent = 'Ngày lưu trú chưa hợp lệ';
      feedback('Ngày trả phòng phải sau ngày nhận phòng; ngày nhận không được ở quá khứ.', true);
      return;
    }
    const checkInTime = byId('search-checkin-time').value;
    const checkOutTime = byId('search-checkout-time').value;
    if (!validTime(checkInTime) || !validTime(checkOutTime)) {
      rooms = [];
      render();
      byId('room-result-count').textContent = 'Giờ lưu trú chưa hợp lệ';
      renderStatusSummary([], 'Chọn giờ nhận và trả phòng hợp lệ rồi tìm lại.');
      feedback('Vui lòng chọn giờ nhận phòng và giờ trả phòng hợp lệ.', true);
      return;
    }
    const guests = Number(byId('guest-count').value);
    if (!Number.isInteger(guests) || guests < 1 || guests > 20) {
      rooms = [];
      render();
      byId('room-result-count').textContent = 'Số khách chưa hợp lệ';
      feedback('Số khách phải là số nguyên từ 1 đến 20.', true);
      return;
    }
    loading = true;
    const searchButton = byId('btn-search-rooms');
    searchButton.disabled = true;
    searchButton.textContent = 'Đang tìm…';
    renderStatusSummary([], 'Đang kiểm tra trạng thái phòng theo thời gian đã chọn…');
    byId('room-result-count').textContent = 'Đang tải phòng…';
    byId('search-rooms-grid').replaceChildren();
    feedback('');
    try {
      const query = new URLSearchParams({
        check_in: checkIn,
        check_out: checkOut,
        rental_type: byId('search-rental-type').value,
        check_in_time: checkInTime,
        check_out_time: checkOutTime,
        include_status: 'true'
      });
      const result = await api.get(`/rooms/availability?${query}`, { signal: AbortSignal.timeout(15000) });
      if (!Array.isArray(result)) throw new Error('Dữ liệu phòng không hợp lệ. Vui lòng thử lại.');
      rooms = result;
      renderStatusSummary(rooms);
      const type = byId('filter-type');
      const previous = preferredType || type.value;
      const types = [...new Set(rooms.map(room => room.room_type_name).filter(Boolean))];
      type.innerHTML = '<option value="">Tất cả hạng</option>' + types.map(name => `<option value="${esc(name)}">${esc(name)}</option>`).join('');
      type.value = types.includes(previous) ? previous : '';
      render();
      feedback(rooms.length ? 'Tình trạng được kiểm tra theo thời gian lưu trú đã chọn.' : 'Chưa có dữ liệu phòng.');
    } catch (error) {
      rooms = [];
      renderStatusSummary([], 'Không thể thống kê vì chưa tải được tình trạng phòng.');
      byId('room-result-count').textContent = 'Không thể tải phòng';
      const message = error.name === 'TimeoutError'
        ? 'Máy chủ phản hồi quá lâu khi tải phòng. Vui lòng thử tìm lại.'
        : error.message || 'Không thể tải phòng.';
      byId('search-rooms-grid').innerHTML = `<p class="customer-error">${esc(message)}</p>`;
      feedback(message, true);
    } finally {
      loading = false;
      searchButton.disabled = false;
      searchButton.textContent = 'Tìm phòng';
    }
  };

  const openDetails = room => {
    const [label, kind] = statusFor(room);
    const rentalType = byId('search-rental-type').value;
    const src = imageUrl(room.image_url);
    const amenities = amenitiesFor(room.amenities);
    byId('room-detail-title').textContent = typeName(room);
    byId('room-detail-content').innerHTML = `
      <div class="discovery-detail-hero">${src ? `<img class="discovery-detail-image" src="${esc(src)}" alt="${esc(room.image_alt || typeName(room))}">` : '<div class="discovery-detail-no-image"><span aria-hidden="true">A</span><small>Ảnh phòng đang cập nhật</small></div>'}</div>
      <div class="discovery-room-facts">
        <div><span>Mã phòng</span><strong>${esc(room.room_number || room.id)}</strong></div>
        <div><span>Loại phòng</span><strong>${esc(typeName(room))}</strong></div>
        <div><span>${rentalType === 'DAILY' ? 'Giá mỗi đêm' : 'Giá gói'}</span><strong>${rentalType === 'DAILY' ? money(room.price) : 'Xem báo giá khi đặt'}</strong></div>
        <div><span>Sức chứa</span><strong>${capacityOf(room) || 'Chưa có thông tin'}${capacityOf(room) ? ' khách' : ''}</strong></div>
        <div><span>Tình trạng phòng</span><strong><span class="room-status ${kind}">${esc(label)}</span></strong></div>
      </div>
      <section class="discovery-detail-section"><h3>Mô tả</h3><p>${esc(room.description || 'Chưa có mô tả cho phòng này.')}</p></section>
      <section class="discovery-detail-section"><h3>Tiện nghi</h3>${amenities.length ? `<ul>${amenities.map(item => `<li>${esc(item)}</li>`).join('')}</ul>` : '<p>Chưa có thông tin tiện nghi.</p>'}</section>`;
    bindRoomImageFallbacks(byId('room-detail-content'));
    byId('room-detail-dialog').showModal();
  };

  const showBookingConfirmation = async room => {
    if (loading || booking) return;
    const checkIn = byId('search-checkin').value;
    const checkOut = byId('search-checkout').value;
    const rentalType = byId('search-rental-type').value;
    const checkInTime = byId('search-checkin-time').value;
    const checkOutTime = byId('search-checkout-time').value;
    const guests = Number(byId('guest-count').value);
    if (!validStayWindow(checkIn, checkOut, rentalType, checkInTime, checkOutTime)) {
      feedback('Khung ngày/giờ chưa phù hợp với loại hình lưu trú đã chọn.', true);
      return;
    }
    const capacity = capacityOf(room);
    if (!Number.isInteger(guests) || guests < 1 || !capacity || guests > capacity) {
      feedback(capacity ? `Phòng này có sức chứa tối đa ${capacity} khách.` : 'Chưa có thông tin sức chứa để xác nhận đặt phòng.', true);
      return;
    }
    if (room.availability_status !== 'available') {
      feedback(availabilityReason(room.availability_status), true);
      return;
    }
    if (!session?.token) {
      try {
        sessionStorage.setItem('aurelia-discovery-booking-intent', JSON.stringify({
          roomId: room.id, checkIn, checkOut, checkInTime, checkOutTime, guests, rentalType,
          type: byId('filter-type').value, price: byId('filter-price').value,
          voucherCode: byId('booking-voucher-code').value.trim()
        }));
      } catch {
        feedback('Không thể lưu lựa chọn phòng. Vui lòng đăng nhập trước khi tìm và đặt phòng.', true);
        return;
      }
      window.location.assign('portal.html?view=discovery&resumeBooking=1');
      return;
    }
    if (String(session.role || '').toUpperCase() !== 'CUSTOMER') {
      feedback('Chức năng đặt phòng chỉ dành cho tài khoản khách hàng.', true);
      return;
    }

    const button = container.querySelector(`[data-book="${CSS.escape(String(room.id))}"]`);
    if (button) {
      button.disabled = true;
      button.textContent = 'Đang kiểm tra…';
    }
    try {
      const query = new URLSearchParams({
        check_in: checkIn,
        check_out: checkOut,
        rental_type: rentalType,
        check_in_time: checkInTime,
        check_out_time: checkOutTime,
        include_status: 'true'
      });
      const currentRooms = await api.get(`/rooms/availability?${query}`);
      const current = Array.isArray(currentRooms) ? currentRooms.find(item => String(item.id) === String(room.id)) : null;
      if (!current || current.availability_status !== 'available') {
        if (current) {
          room.availability_status = current.availability_status;
          room.version = current.version;
          render();
        }
        feedback(current ? availabilityReason(current.availability_status) : 'Không tìm thấy phòng trong dữ liệu hiện tại. Vui lòng tìm lại.', true);
        return;
      }
      if (Number(current.version) !== Number(room.version)) {
        Object.assign(room, current);
        render();
        feedback('Thông tin phòng vừa thay đổi. Tình trạng đã được cập nhật; vui lòng kiểm tra và bấm Đặt phòng lại.', true);
        return;
      }
      const currentCapacity = capacityOf(current);
      if (!currentCapacity || guests > currentCapacity) {
        feedback(currentCapacity ? `Phòng này có sức chứa tối đa ${currentCapacity} khách.` : 'Chưa có thông tin sức chứa để xác nhận đặt phòng.', true);
        return;
      }
      const voucherCode = byId('booking-voucher-code').value.trim();
      if (voucherCode && rentalType !== 'DAILY') {
        throw new Error('Mã ưu đãi hiện chỉ áp dụng cho lưu trú theo ngày. Hãy xóa mã để tiếp tục.');
      }
      const [profile, baseQuote] = await Promise.all([
        api.get('/customer-portal/profile'),
        api.get(`/pricing/quote?${new URLSearchParams({
          room_type_id: String(current.room_type_id),
          check_in: checkIn,
          check_out: checkOut,
          rental_type: rentalType,
          check_in_time: checkInTime,
          check_out_time: checkOutTime
        })}`)
      ]);
      const discount = voucherCode ? await api.post('/promotions/validate', {
        voucher_code: voucherCode,
        room_type_id: Number(current.room_type_id),
        check_in_date: checkIn,
        check_out_date: checkOut
      }) : null;
      const quote = discount ? { ...baseQuote, ...discount } : baseQuote;
      if (!profile?.fullName || !profile?.phone) {
        throw new Error('Hồ sơ cần có họ tên và số điện thoại. Vui lòng cập nhật tại mục Hồ sơ & bảo mật trước khi đặt.');
      }
      if (!quote || !Array.isArray(quote.nightly_rates) || !Number.isFinite(Number(quote.total_amount)) || Number(quote.total_amount) <= 0) {
        throw new Error('Chưa thể lấy báo giá hợp lệ từ máy chủ. Vui lòng thử lại.');
      }
      const effectiveType = quote.rental_type || rentalType;
      const effectiveCheckIn = quote.check_in_date?.slice(0, 10) || checkIn;
      const effectiveCheckOut = quote.check_out_date?.slice(0, 10) || checkOut;
      const effectiveCheckInTime = quote.check_in_date?.slice(11, 16) || checkInTime;
      const effectiveCheckOutTime = quote.check_out_date?.slice(11, 16) || checkOutTime;
      pendingRoom = { room: current, checkIn: effectiveCheckIn, checkOut: effectiveCheckOut, checkInTime: effectiveCheckInTime, checkOutTime: effectiveCheckOutTime, rentalType: effectiveType, guests, profile, quote, voucherCode };
      bookingSucceeded = false;
      const nights = Math.round((Date.parse(`${effectiveCheckOut}T00:00:00Z`) - Date.parse(`${effectiveCheckIn}T00:00:00Z`)) / 86400000);
      byId('booking-dialog-content').innerHTML = `
        <div class="booking-summary-line"><span>Loại hình</span><strong>${effectiveType === 'HOURLY' ? 'Theo giờ' : effectiveType === 'OVERNIGHT' ? 'Qua đêm' : 'Theo ngày'}${effectiveType !== rentalType ? ' · hệ thống đã chuyển gói' : ''}</strong></div>
        <div class="booking-summary-line"><span>Phòng</span><strong>${esc(current.room_number || current.id)} · ${esc(typeName(current))}</strong></div>
        <div class="booking-summary-line"><span>Nhận phòng</span><strong>${esc(effectiveCheckIn)} · ${esc(effectiveCheckInTime)}</strong></div>
        <div class="booking-summary-line"><span>Trả phòng</span><strong>${esc(effectiveCheckOut)} · ${esc(effectiveCheckOutTime)}</strong></div>
        ${effectiveType === 'DAILY' ? `<div class="booking-summary-line"><span>Số đêm</span><strong>${nights} đêm</strong></div>` : ''}
        <div class="booking-summary-line"><span>Số khách</span><strong>${guests}</strong></div>
        <section class="booking-rate-breakdown"><strong>Chi tiết giá lưu trú</strong>
          ${quote.nightly_rates.length ? quote.nightly_rates.map(rate => `<div class="booking-summary-line"><span>${esc(rate.date)}</span><strong>${money(rate.amount)}</strong></div>`).join('') : `<div class="booking-summary-line"><span>Giá gói ${effectiveType === 'HOURLY' ? '2 giờ đầu' : 'qua đêm'}</span><strong>${money(quote.base_price)}</strong></div>${Number(quote.hourly_add_on_units) > 0 ? `<div class="booking-summary-line"><span>Giờ phụ thu</span><strong>${quote.hourly_add_on_units} giờ</strong></div>` : ''}`}
          ${discount ? `<div class="booking-summary-line"><span>Mã ưu đãi</span><strong>${esc(voucherCode)}</strong></div>
          <div class="booking-summary-line"><span>Tiền phòng trước giảm</span><strong>${money(baseQuote.room_subtotal)}</strong></div>
          <div class="booking-summary-line"><span>Giảm giá tiền phòng</span><strong>−${money(discount.discount_amount)}</strong></div>` : ''}
          <div class="booking-summary-line"><span>Tiền phòng${discount ? ' sau giảm' : ''}</span><strong>${money(quote.room_subtotal)}</strong></div>
          <div class="booking-summary-line"><span>Phí dịch vụ</span><strong>${money(quote.service_charge)}</strong></div>
          <div class="booking-summary-line"><span>Thuế</span><strong>${money(quote.vat)}</strong></div>
          <div class="booking-summary-line booking-total-line"><span>Tổng cộng</span><strong>${money(quote.total_amount)}</strong></div>
        </section>
        <section class="booking-contact-summary"><strong>Thông tin khách đặt</strong>
          <div class="booking-summary-line"><span>Họ và tên</span><strong>${esc(profile.fullName)}</strong></div>
          <div class="booking-summary-line"><span>Điện thoại</span><strong>${esc(profile.phone)}</strong></div>
          <div class="booking-summary-line"><span>Email</span><strong>${esc(profile.email || 'Chưa cập nhật')}</strong></div>
        </section>
        <label class="booking-confirm-personal"><input id="booking-personal-confirmed" type="checkbox"><span>Tôi xác nhận thông tin cá nhân và chi tiết đặt phòng ở trên là chính xác.</span></label>
        <label class="booking-payment-choice">Hình thức thanh toán
          <select id="booking-payment-choice"><option value="later">Thanh toán tại khách sạn</option><option value="online">Thanh toán online qua VietQR</option></select>
        </label>
        <div id="booking-payment-panel" class="booking-payment-panel" hidden></div>`;
      byId('booking-feedback').textContent = '';
      byId('booking-feedback').classList.remove('is-error');
      byId('confirm-booking').disabled = true;
      byId('confirm-booking').textContent = 'Xác nhận đặt phòng';
      byId('booking-personal-confirmed').addEventListener('change', event => {
        byId('confirm-booking').disabled = !event.currentTarget.checked;
      });
      byId('booking-dialog').showModal();
    } catch (error) {
      feedback(error.message || 'Không thể xác minh tình trạng phòng. Vui lòng thử lại.', true);
    } finally {
      if (button?.isConnected) {
        button.disabled = room.availability_status !== 'available' || !capacityOf(room) || guests > capacityOf(room);
        button.textContent = button.disabled ? 'Không thể đặt' : 'Đặt phòng';
      }
    }
  };

  const submitBooking = async () => {
    if (booking || !pendingRoom) return;
    const { room, checkIn, checkOut, guests, profile, quote, voucherCode } = pendingRoom;
    if (!byId('booking-personal-confirmed')?.checked) {
      byId('booking-feedback').textContent = 'Vui lòng xác nhận thông tin cá nhân và chi tiết đặt phòng.';
      byId('booking-feedback').classList.add('is-error');
      return;
    }
    if (!validStayWindow(checkIn, checkOut, pendingRoom.rentalType, pendingRoom.checkInTime, pendingRoom.checkOutTime)
        || !capacityOf(room) || guests > capacityOf(room)) {
      byId('booking-feedback').textContent = 'Ngày hoặc số khách không còn hợp lệ. Vui lòng tìm phòng lại.';
      byId('booking-feedback').classList.add('is-error');
      return;
    }
    booking = true;
    const confirmButton = byId('confirm-booking');
    confirmButton.disabled = true;
    confirmButton.textContent = 'Đang gửi yêu cầu…';
    byId('booking-feedback').textContent = 'Đang xác nhận tình trạng phòng và tạo đơn…';
    byId('booking-feedback').classList.remove('is-error');
    const payload = {
      room_id: Number(room.id),
      room_version: Number(room.version),
      check_in_date: checkIn,
      check_out_date: checkOut,
      rental_type: pendingRoom.rentalType,
      check_in_time: pendingRoom.checkInTime,
      check_out_time: pendingRoom.checkOutTime,
      guest_count: guests,
      quoted_total: quote.total_amount,
      personal_info_confirmed: true
    };
    if (voucherCode) payload.voucher_code = voucherCode;
    try {
      const result = await api.post('/customer-portal/bookings', payload, {
        headers: { 'Idempotency-Key': bookingKey(payload) }
      });
      try { sessionStorage.removeItem('aurelia-discovery-booking-intent'); } catch {}
      const onlinePayment = byId('booking-payment-choice').value === 'online';
      pendingRoom = null;
      byId('booking-feedback').textContent = `Đặt phòng thành công${result?.booking_code ? ` · ${result.booking_code}` : ''}. Tổng tiền ${money(result?.folio?.balance ?? quote.total_amount)}. ${onlinePayment ? 'Đang chuẩn bị mã thanh toán VietQR…' : 'Trạng thái thanh toán: Chờ thanh toán tại khách sạn.'}`;
      byId('booking-feedback').classList.remove('is-error');
      confirmButton.textContent = 'Đóng';
      confirmButton.disabled = false;
      bookingSucceeded = true;
      const closeButtons = container.querySelectorAll('[data-close-booking]');
      closeButtons.forEach(button => { button.disabled = true; });
      if (onlinePayment && result?.folio?.id) {
        const panel = byId('booking-payment-panel');
        try {
          const qr = await api.get(`/folios/${result.folio.id}/vietqr`);
          const imageData = String(qr.qr_image_base64 || '').replace(/^data:image\/png;base64,/, '');
          if (!/^[A-Za-z0-9+/]+={0,2}$/.test(imageData)) throw new Error('Ảnh VietQR không hợp lệ.');
          panel.hidden = false;
          panel.innerHTML = `<strong>Chờ thanh toán qua VietQR</strong><img class="booking-payment-qr" src="data:image/png;base64,${imageData}" alt="Mã VietQR thanh toán"><p>${money(qr.amount)}</p><p>${esc(qr.beneficiary_bank)} · ${esc(qr.account_number)} · ${esc(qr.account_holder)}</p><p>Nội dung chuyển khoản: <strong>${esc(qr.reference_code)}</strong></p><p id="booking-payment-status" role="status">Đang chờ ngân hàng xác nhận…</p>`;
          watchPayment(result.folio.id);
        } catch (error) {
          panel.hidden = false;
          panel.textContent = `Đơn đã được lưu nhưng chưa thể tạo mã thanh toán trực tuyến (${error.message || 'lỗi máy chủ'}). Trạng thái: Chờ thanh toán. Bạn có thể thanh toán tại khách sạn hoặc kiểm tra lại lịch sử đặt phòng.`;
        }
      } else {
        byId('booking-payment-panel').hidden = false;
        byId('booking-payment-panel').textContent = 'Trạng thái thanh toán: Chờ thanh toán tại khách sạn.';
      }
    } catch (error) {
      byId('booking-feedback').textContent = error.message || 'Không thể đặt phòng. Vui lòng thử lại.';
      byId('booking-feedback').classList.add('is-error');
      confirmButton.disabled = false;
      confirmButton.textContent = 'Thử lại';
    } finally {
      booking = false;
    }
  };

  const takeResumeIntent = () => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('resumeBooking') !== '1') return null;
    params.delete('resumeBooking');
    const nextUrl = new URL(window.location.href);
    nextUrl.search = params.toString();
    history.replaceState(null, '', nextUrl);
    let intent;
    try { intent = JSON.parse(sessionStorage.getItem('aurelia-discovery-booking-intent')); } catch {}
    if (!intent || !validDateRange(intent.checkIn, intent.checkOut, intent.rentalType || 'DAILY')) {
      try { sessionStorage.removeItem('aurelia-discovery-booking-intent'); } catch {}
      feedback('Không tìm thấy lựa chọn đặt phòng đã lưu. Vui lòng tìm lại phòng.');
      return null;
    }
    byId('search-checkin').value = intent.checkIn;
    byId('search-checkout').value = intent.checkOut;
    byId('search-rental-type').value = intent.rentalType || 'DAILY';
    byId('search-checkout').min = intent.rentalType === 'HOURLY' ? intent.checkIn : shiftDate(intent.checkIn, 1);
    byId('search-checkin-time').value = intent.checkInTime || '14:00';
    byId('search-checkout-time').value = intent.checkOutTime || '12:00';
    byId('guest-count').value = String(intent.guests);
    byId('filter-price').value = String(intent.price || '0');
    byId('booking-voucher-code').value = intent.rentalType && intent.rentalType !== 'DAILY' ? '' : (intent.voucherCode || '');
    byId('booking-voucher-code').disabled = Boolean(intent.rentalType && intent.rentalType !== 'DAILY');
    return intent;
  };

  const previousClickHandler = container.discoveryClickHandler;
  if (previousClickHandler) container.removeEventListener('click', previousClickHandler);
  const clickHandler = event => {
    const target = event.target.closest('button');
    if (!target || !container.contains(target)) return;
    if (target.matches('[data-detail]')) {
      const room = rooms.find(item => String(item.id) === target.dataset.detail);
      if (room) openDetails(room);
    } else if (target.matches('[data-book]')) {
      const room = rooms.find(item => String(item.id) === target.dataset.book);
      if (room) showBookingConfirmation(room);
    } else if (target.matches('[data-close-detail]')) {
      closeDialog(byId('room-detail-dialog'));
    } else if (target.matches('[data-close-booking]')) {
      if (!booking) closeDialog(byId('booking-dialog'));
    } else if (target.id === 'btn-search-rooms') {
      load();
    } else if (target.id === 'confirm-booking') {
      if (bookingSucceeded) {
        stopPaymentWatch();
        closeDialog(byId('booking-dialog'));
        bookingSucceeded = false;
        load();
      } else submitBooking();
    }
  };
  container.discoveryClickHandler = clickHandler;
  container.addEventListener('click', clickHandler);
  byId('filter-type').addEventListener('change', render);
  byId('filter-price').addEventListener('change', render);
  byId('guest-count').addEventListener('input', render);
  const rentalTypeChanged = () => {
    const rentalType = byId('search-rental-type').value;
    const checkIn = byId('search-checkin').value;
    const checkOut = byId('search-checkout');
    if (rentalType === 'DAILY') {
      byId('search-checkin-time').value = '14:00';
      byId('search-checkout-time').value = '12:00';
      checkOut.min = checkIn ? shiftDate(checkIn, 1) : today;
      if (checkIn && checkOut.value <= checkIn) checkOut.value = shiftDate(checkIn, 1);
    } else if (rentalType === 'OVERNIGHT') {
      byId('search-checkin-time').value = '22:00';
      byId('search-checkout-time').value = '06:00';
      checkOut.min = checkIn ? shiftDate(checkIn, 1) : today;
      if (checkIn) checkOut.value = shiftDate(checkIn, 1);
    } else {
      byId('search-checkin-time').value = '14:00';
      byId('search-checkout-time').value = '16:00';
      checkOut.min = checkIn || today;
      if (checkIn) checkOut.value = checkIn;
    }
    const voucher = byId('booking-voucher-code');
    voucher.disabled = rentalType !== 'DAILY';
    if (rentalType !== 'DAILY') voucher.value = '';
    voucher.title = rentalType === 'DAILY' ? '' : 'Mã ưu đãi hiện chỉ áp dụng cho lưu trú theo ngày.';
    rooms = [];
    renderStatusSummary([], 'Tìm phòng lại để kiểm tra theo loại hình lưu trú mới.');
    render();
    byId('room-result-count').textContent = 'Hãy tìm phòng theo loại hình đã chọn';
  };
  byId('search-rental-type').addEventListener('change', rentalTypeChanged);
  const datesChanged = () => {
    const checkIn = byId('search-checkin').value;
    const sameDayAllowed = byId('search-rental-type').value === 'HOURLY';
    const minCheckOut = checkIn ? (sameDayAllowed ? checkIn : shiftDate(checkIn, 1)) : today;
    byId('search-checkout').min = minCheckOut;
    if (byId('search-checkout').value < minCheckOut) byId('search-checkout').value = minCheckOut;
    rooms = [];
    renderStatusSummary([], 'Tìm phòng lại để cập nhật thống kê theo thời gian mới.');
    render();
    byId('room-result-count').textContent = 'Hãy tìm phòng theo ngày đã chọn';
    feedback('Ngày lưu trú đã thay đổi. Hãy tìm lại để kiểm tra tình trạng phòng.');
  };
  byId('search-checkin').addEventListener('change', datesChanged);
  byId('search-checkout').addEventListener('change', datesChanged);
  const timesChanged = () => {
    rooms = [];
    render();
    byId('room-result-count').textContent = 'Hãy tìm phòng theo thời gian đã chọn';
    renderStatusSummary([], 'Tìm phòng lại để kiểm tra theo giờ nhận và trả phòng mới.');
    feedback('Giờ lưu trú đã thay đổi. Hãy tìm lại để cập nhật tình trạng phòng.');
  };
  byId('search-checkin-time').addEventListener('change', timesChanged);
  byId('search-checkout-time').addEventListener('change', timesChanged);
  byId('room-detail-dialog').addEventListener('click', event => {
    if (event.target === byId('room-detail-dialog')) closeDialog(byId('room-detail-dialog'));
  });
  byId('booking-dialog').addEventListener('click', event => {
    if (event.target === byId('booking-dialog') && !booking) closeDialog(byId('booking-dialog'));
  });
  byId('booking-dialog').addEventListener('close', stopPaymentWatch);

  const resumeIntent = takeResumeIntent();
  await load({ preferredType: resumeIntent?.type || '' });
  if (resumeIntent) {
    const room = rooms.find(item => String(item.id) === String(resumeIntent.roomId));
    if (!room) feedback('Phòng đã chọn không còn trong kết quả. Vui lòng kiểm tra lại ngày và tìm phòng.', true);
    else await showBookingConfirmation(room);
  }
}
