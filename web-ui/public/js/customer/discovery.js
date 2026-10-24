import { api } from '../../api.js';
import { bookingKey } from '../booking-api.js';
import { bindRoomImageFallbacks } from '../shared/room-images.js';
import { renderRoomCard } from '../shared/room-card.js';
import { renderBookingSearchFields, roomAvailabilityPath } from '../shared/booking-search.js';

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
const hotelClockMinutes = () => {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return Number(value.hour) * 60 + Number(value.minute);
};
const formatMinutes = value => `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
const earliestHourlyCheckIn = () => Math.ceil(hotelClockMinutes() / 15) * 15;
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
const timeMinutes = value => {
  if (!validTime(value)) return NaN;
  const [hours, minutes] = value.split(':').map(Number);
  return hours * 60 + minutes;
};
const stayWindowIssue = (checkIn, checkOut, rentalType, inTime, outTime) => {
  if (!validDateRange(checkIn, checkOut, rentalType)) return 'Ngày trả phòng phải sau ngày nhận phòng; ngày nhận không được ở quá khứ.';
  if (!validTime(inTime) || !validTime(outTime)) return 'Vui lòng chọn giờ nhận và giờ trả phòng hợp lệ.';
  const inMinutes = timeMinutes(inTime);
  if (rentalType === 'HOURLY' && checkIn === localDate(new Date())) {
    const earliest = earliestHourlyCheckIn();
    if (earliest > 23 * 60 + 45) return 'Hôm nay đã hết giờ nhận phòng theo giờ. Vui lòng chọn ngày khác.';
    if (inMinutes < earliest) return `Giờ nhận phòng theo giờ phải từ ${formatMinutes(earliest)} trở đi vì ngày nhận là hôm nay.`;
  }
  if (checkIn === localDate(new Date()) && inMinutes < hotelClockMinutes()) {
    return 'Giờ nhận phòng đã qua. Vui lòng chọn ngày khác hoặc khung giờ trong tương lai.';
  }
  if (rentalType === 'DAILY') {
    return inTime === '14:00' && outTime === '12:00' ? '' : 'Gói theo ngày cố định nhận phòng lúc 14:00 và trả phòng lúc 12:00.';
  }
  if (rentalType === 'OVERNIGHT') {
    if (checkOut !== shiftDate(checkIn, 1)) return 'Gói qua đêm chỉ áp dụng từ ngày nhận đến ngày kế tiếp.';
    if (inTime !== '18:00' || outTime !== '08:00') return 'Gói qua đêm cố định 18:00–08:00 hôm sau (14 tiếng).';
    return '';
  }
  const days = Math.round((Date.parse(`${checkOut}T00:00:00Z`) - Date.parse(`${checkIn}T00:00:00Z`)) / 86400000);
  const outMinutes = timeMinutes(outTime);
  if (inMinutes % 15 !== 0 || outMinutes % 15 !== 0) return 'Gói theo giờ chỉ nhận giờ theo từng khoảng 15 phút.';
  const durationMinutes = days * 1440 + outMinutes - inMinutes;
  if (durationMinutes <= 0) return 'Giờ trả phòng phải sau giờ nhận phòng.';
  if (durationMinutes > 360) return 'Lưu trú theo giờ tối đa 6 tiếng. Hãy chọn giờ trả phòng trong giới hạn này hoặc chọn gói phù hợp.';
  return '';
};
const validStayWindow = (checkIn, checkOut, rentalType, inTime, outTime) => {
  return !stayWindowIssue(checkIn, checkOut, rentalType, inTime, outTime);
};

export async function renderDiscovery(container, session) {
  const requestedVoucher = new URLSearchParams(window.location.search).get('voucher') || '';
  const today = localDate(new Date());
  const tomorrow = localDate(Date.now() + 86400000);
  container.innerHTML = `
    <div class="page-header">
      <h1>Khám phá phòng</h1>
      <p>Chọn ngày lưu trú, số khách và mã ưu đãi để kiểm tra phòng trống.</p>
    </div>
    <section class="card card-border discovery-filter booking-search-panel" aria-label="Tìm phòng trống">
      ${renderBookingSearchFields({checkInId:'search-checkin',checkInValue:today,checkInMin:today,checkOutId:'search-checkout',checkOutValue:tomorrow,checkOutMin:tomorrow,guestCountId:'guest-count',guestCount:2,guestLabel:count=>count===2?'2 người lớn (tiêu chuẩn)':count<=4?`${count} người lớn`:`${count} khách`,voucher:{id:'booking-voucher-code',value:requestedVoucher,placeholder:'LUXURY2026',maxLength:50},searchButtonId:'btn-search-rooms',searchButtonLabel:'Kiểm tra phòng trống'})}
      <details class="collapse collapse-arrow booking-advanced-filters">
        <summary class="collapse-title">Tùy chọn lưu trú nâng cao</summary>
        <div class="collapse-content">
          <div class="booking-advanced-grid">
            <label class="booking-search-field"><span>Loại hình lưu trú</span><select class="select select-sm booking-search-control" id="search-rental-type"><option value="DAILY">Theo ngày</option><option value="OVERNIGHT">Qua đêm</option><option value="HOURLY">Theo giờ</option></select></label>
            <label class="booking-search-field"><span>Giờ nhận phòng</span><input class="input input-sm booking-search-control" id="search-checkin-time" type="time" value="14:00" step="900" required></label>
            <label class="booking-search-field"><span>Giờ trả phòng</span><input class="input input-sm booking-search-control" id="search-checkout-time" type="time" value="12:00" step="900" required></label>
            <label class="booking-search-field"><span>Hạng phòng</span><select class="select select-sm booking-search-control" id="filter-type"><option value="">Tất cả hạng</option></select></label>
            <label class="booking-search-field"><span>Giá tối đa mỗi đêm</span><select class="select select-sm booking-search-control" id="filter-price"><option value="0">Mọi mức giá</option><option value="1000000">Dưới 1 triệu</option><option value="2000000">Dưới 2 triệu</option><option value="5000000">Dưới 5 triệu</option></select></label>
          </div>
          <small id="stay-time-hint" class="booking-time-help">Gói theo ngày cố định: nhận phòng 14:00, trả phòng 12:00 hôm sau.</small>
        </div>
      </details>
      <p id="discovery-feedback" class="discovery-feedback" role="status" aria-live="polite"></p>
    </section>
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
        <button type="button" class="btn booking-dialog-confirm" id="confirm-booking">Xác nhận đặt phòng</button>
      </div>
    </dialog>
  `;

  let rooms = [];
  let pendingRoom = null;
  let loading = false;
  let booking = false;
  let bookingSucceeded = false;
  let paymentWatchTimer = null;
  let paymentWindowTimer = null;
  let paymentWindowDeadline = 0;
  const byId = id => container.querySelector(`#${id}`);
  const syncStayControls = () => {
    const rentalType = byId('search-rental-type').value;
    const checkIn = byId('search-checkin').value;
    const checkOut = byId('search-checkout');
    const checkInTime = byId('search-checkin-time');
    const checkOutTime = byId('search-checkout-time');
    const hint = byId('stay-time-hint');
    checkInTime.disabled = rentalType !== 'HOURLY';
    checkOutTime.disabled = rentalType !== 'HOURLY';
    checkOut.disabled = rentalType === 'OVERNIGHT';
    checkInTime.removeAttribute('min');
    checkInTime.removeAttribute('max');
    checkOutTime.removeAttribute('min');
    checkOutTime.removeAttribute('max');
    checkInTime.step = rentalType === 'HOURLY' ? '900' : '60';
    checkOutTime.step = rentalType === 'HOURLY' ? '900' : '60';
    checkOut.removeAttribute('max');

    if (rentalType === 'DAILY') {
      checkInTime.value = '14:00';
      checkOutTime.value = '12:00';
      checkOut.min = checkIn ? shiftDate(checkIn, 1) : today;
      hint.textContent = 'Gói theo ngày cố định: nhận phòng 14:00, trả phòng 12:00 hôm sau.';
      return;
    }
    if (rentalType === 'OVERNIGHT') {
      checkInTime.value = '18:00';
      checkOutTime.value = '08:00';
      checkOut.min = checkIn ? shiftDate(checkIn, 1) : today;
      checkOut.max = checkIn ? shiftDate(checkIn, 1) : today;
      if (checkIn) checkOut.value = shiftDate(checkIn, 1);
      hint.textContent = 'Gói qua đêm cố định 18:00 hôm trước đến 08:00 hôm sau (14 tiếng).';
      return;
    }

    checkOut.min = checkIn || today;
    if (checkIn === localDate(new Date())) {
      const earliest = earliestHourlyCheckIn();
      const latestToday = 23 * 60 + 45;
      if (earliest <= latestToday) {
        checkInTime.min = formatMinutes(earliest);
        if (timeMinutes(checkInTime.value) < earliest) checkInTime.value = formatMinutes(earliest);
      } else {
        checkInTime.min = formatMinutes(latestToday);
        checkInTime.max = formatMinutes(latestToday);
        checkInTime.value = formatMinutes(latestToday);
        if (checkOut.value === checkIn) checkOut.value = shiftDate(checkIn, 1);
      }
    }
    const checkInMinutes = timeMinutes(checkInTime.value);
    if (checkIn && Number.isFinite(checkInMinutes)) {
      const canExtendToNextDay = checkInMinutes >= 18 * 60;
      if (checkOut.value === checkIn && checkInMinutes >= 23 * 60 + 45) checkOut.value = shiftDate(checkIn, 1);
      const latestDate = canExtendToNextDay ? shiftDate(checkIn, 1) : checkIn;
      checkOut.max = latestDate;
      if (checkOut.value > latestDate) checkOut.value = latestDate;
      if (checkOut.value === checkIn) {
        const earliest = Math.floor(checkInMinutes / 15) * 15 + 15;
        const latest = Math.min(23 * 60 + 45, Math.floor((checkInMinutes + 360) / 15) * 15);
        checkOutTime.min = formatMinutes(earliest);
        checkOutTime.max = formatMinutes(latest);
        if (timeMinutes(checkOutTime.value) < earliest) checkOutTime.value = formatMinutes(earliest);
        if (timeMinutes(checkOutTime.value) > latest) checkOutTime.value = formatMinutes(latest);
      } else if (checkOut.value === shiftDate(checkIn, 1) && canExtendToNextDay) {
        const latest = Math.floor((checkInMinutes + 360 - 1440) / 15) * 15;
        checkOutTime.min = '00:00';
        checkOutTime.max = formatMinutes(latest);
        if (timeMinutes(checkOutTime.value) > latest) checkOutTime.value = formatMinutes(latest);
      }
    }
    if (checkIn === localDate(new Date())) {
      const earliest = earliestHourlyCheckIn();
      hint.textContent = earliest > 23 * 60 + 45
        ? 'Hôm nay đã hết khung giờ nhận phòng theo giờ. Hãy chọn ngày nhận phòng khác.'
        : `Lưu trú tối đa 6 tiếng. Nhận phòng hôm nay từ ${formatMinutes(earliest)} trở đi.`;
    } else hint.textContent = 'Lưu trú theo giờ tối đa 6 tiếng. Chọn giờ nhận phòng trong tương lai.';
  };
  const feedback = (message = '', error = false) => {
    byId('discovery-feedback').textContent = message;
    byId('discovery-feedback').classList.toggle('is-error', error);
  };
  const closeDialog = dialog => {
    if (dialog.open) dialog.close();
  };
  const stopPaymentWatch = () => {
    clearTimeout(paymentWatchTimer);
    paymentWatchTimer = null;
  };
  const stopQrCountdown = () => {
    clearTimeout(paymentWindowTimer);
    paymentWindowTimer = null;
    paymentWindowDeadline = 0;
  };
  const expireQrInDialog = (final = false, reconciling = true) => {
    const panel = byId('booking-payment-panel');
    const image = panel?.querySelector('.booking-payment-qr');
    const simulate = panel?.querySelector('.booking-payment-test-button');
    if (image) image.hidden = true;
    if (simulate) simulate.disabled = true;
    const status = byId('booking-payment-status');
    if (status) status.textContent = final
      ? 'Booking đã kết thúc vì phiên QR hết hạn. Phòng đã được trả lại.'
      : reconciling
        ? 'QR đã hết hạn. Hệ thống đang chờ tối đa 2 phút để nhận callback giao dịch đã bắt đầu trước hạn.'
        : 'Đã hết thời gian đối soát. Hệ thống đang hoàn tất việc kết thúc booking và trả phòng.';
    const countdown = byId('booking-payment-countdown');
    if (countdown) countdown.textContent = final ? 'Bạn có thể đặt lại nếu vẫn muốn lưu trú.' : 'Bạn có thể đóng cửa sổ này; trạng thái sẽ được cập nhật trong Lịch sử đặt phòng.';
    panel?.querySelector('.booking-rebook-link')?.remove();
    if (final && panel && !panel.querySelector('.booking-rebook-link')) {
      const link = document.createElement('a'); link.href = 'portal.html?view=discovery'; link.className = 'btn btn-primary btn-sm booking-rebook-link'; link.textContent = 'Đặt phòng mới'; panel.append(link);
    }
    stopQrCountdown();
    if (final) stopPaymentWatch();
  };
  const startQrCountdown = seconds => {
    stopQrCountdown();
    paymentWindowDeadline = Date.now() + Math.max(0, Math.min(3600, Number(seconds) || 0)) * 1000;
    const tick = () => {
      const remaining = Math.max(0, Math.ceil((paymentWindowDeadline - Date.now()) / 1000));
      const countdown = byId('booking-payment-countdown');
      if (!remaining) { expireQrInDialog(false, true); return; }
      if (countdown) countdown.textContent = `Còn ${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, '0')} để quét mã QR.`;
      paymentWindowTimer = setTimeout(tick, 1000);
    };
    tick();
  };
  const watchPayment = folioId => {
    stopPaymentWatch();
    const poll = async () => {
      if (!byId('booking-dialog').open) return;
      try {
        const payment = await api.get(`/folios/${folioId}/payment-status`);
        const state=String(payment.payment_status||'WAITING');
        if (state === 'PAID') {
          byId('booking-payment-status').textContent = 'Đã nhận thanh toán thành công.';
          stopQrCountdown();
          return;
        }
        if (state === 'QR_EXPIRED') {
          const bookingStatus = String(payment.booking_status || '').toLowerCase();
          if (bookingStatus === 'cancelled') { expireQrInDialog(true, false); return; }
          expireQrInDialog(false, Boolean(payment.payment_reconciliation_pending));
          paymentWatchTimer = setTimeout(poll, 3000);
          return;
        }
        byId('booking-payment-status').textContent=state==='PARTIAL'?`Thanh toán thiếu. Còn ${Number(payment.remaining_amount).toLocaleString('vi-VN')} đ.`:state==='RECONCILIATION_ERROR'?'Giao dịch cần lễ tân đối soát trước khi xác nhận.':state==='QR_PENDING'?'Mã QR còn hiệu lực. Đang chờ ngân hàng xác nhận…':'Đang cập nhật giao dịch…';
      } catch {
        byId('booking-payment-status').textContent = 'Chưa thể cập nhật trạng thái thanh toán. Đơn đặt phòng vẫn được lưu; vui lòng kiểm tra Lịch sử đặt phòng.';
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
      const timeIssue = stayWindowIssue(byId('search-checkin').value, byId('search-checkout').value, rentalType, checkInTime, checkOutTime);
      const reason = room.availability_status === 'available' && !supportedBookingTimes
        ? timeIssue
        : room.availability_status === 'available'
        ? (capacity ? `Phòng chỉ phục vụ tối đa ${capacity} khách.` : 'Chưa có thông tin sức chứa để xác nhận đặt phòng.')
        : availabilityReason(room.availability_status);
      return renderRoomCard(room, {
        statusLabel: text, statusKind: kind, canSelect: canBook, reason,
        actionLabel: 'Đặt phòng', actionAttribute: 'data-book', rentalType, showDetails: true, money
      });
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
      render();
      byId('room-result-count').textContent = 'Ngày lưu trú chưa hợp lệ';
      feedback('Ngày trả phòng phải sau ngày nhận phòng; ngày nhận không được ở quá khứ.', true);
      return;
    }
    const checkInTime = byId('search-checkin-time').value;
    const checkOutTime = byId('search-checkout-time').value;
    const timeIssue = stayWindowIssue(checkIn, checkOut, byId('search-rental-type').value, checkInTime, checkOutTime);
    if (timeIssue) {
      rooms = [];
      render();
      byId('room-result-count').textContent = 'Giờ lưu trú chưa hợp lệ';
      feedback(timeIssue, true);
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
  const searchLabel = searchButton.querySelector('[data-search-label]');
    searchButton.disabled = true;
    searchLabel.textContent = 'Đang kiểm tra…';
    byId('room-result-count').textContent = 'Đang tải phòng…';
    byId('search-rooms-grid').replaceChildren();
    feedback('');
    try {
      const result = await api.get(roomAvailabilityPath({
        checkIn,checkOut,rentalType:byId('search-rental-type').value,checkInTime,checkOutTime,includeStatus:true
      }), { signal: AbortSignal.timeout(15000) });
      if (!Array.isArray(result)) throw new Error('Dữ liệu phòng không hợp lệ. Vui lòng thử lại.');
      rooms = result;
      const type = byId('filter-type');
      const previous = preferredType || type.value;
      const types = [...new Set(rooms.map(room => room.room_type_name).filter(Boolean))];
      type.innerHTML = '<option value="">Tất cả hạng</option>' + types.map(name => `<option value="${esc(name)}">${esc(name)}</option>`).join('');
      type.value = types.includes(previous) ? previous : '';
      render();
      feedback(rooms.length ? 'Tình trạng được kiểm tra theo thời gian lưu trú đã chọn.' : 'Chưa có dữ liệu phòng.');
    } catch (error) {
      rooms = [];
      byId('room-result-count').textContent = 'Không thể tải phòng';
      const message = error.name === 'TimeoutError'
        ? 'Máy chủ phản hồi quá lâu khi tải phòng. Vui lòng thử tìm lại.'
        : error.message || 'Không thể tải phòng.';
      byId('search-rooms-grid').innerHTML = `<p class="customer-error">${esc(message)}</p>`;
      feedback(message, true);
    } finally {
      loading = false;
      searchButton.disabled = false;
      searchLabel.textContent = 'Kiểm tra phòng trống';
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
    const conciergeSuggestion = room.conciergeSuggestion || null;
    const requireFreshConciergeSearch = () => {
      try { sessionStorage.removeItem('aurelia-discovery-booking-intent'); } catch {}
      feedback('Tình trạng phòng hoặc giá đã thay đổi từ lúc Quản gia AI tra cứu. Vui lòng tìm lại trong Quản gia AI để nhận gợi ý mới.', true);
    };
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
        if (conciergeSuggestion) { requireFreshConciergeSearch(); return; }
        if (current) {
          room.availability_status = current.availability_status;
          room.version = current.version;
          render();
        }
        feedback(current ? availabilityReason(current.availability_status) : 'Không tìm thấy phòng trong dữ liệu hiện tại. Vui lòng tìm lại.', true);
        return;
      }
      if (conciergeSuggestion && Number(current.version) !== Number(conciergeSuggestion.roomVersion)) {
        requireFreshConciergeSearch();
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
      if (conciergeSuggestion && Number(Number(quote?.total_amount).toFixed(2)) !== Number(Number(conciergeSuggestion.quotedTotal).toFixed(2))) {
        requireFreshConciergeSearch();
        return;
      }
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
      if (effectiveType !== rentalType || effectiveCheckIn !== checkIn || effectiveCheckOut !== checkOut
          || effectiveCheckInTime !== checkInTime || effectiveCheckOutTime !== checkOutTime) {
        throw new Error('Báo giá đã điều chỉnh loại gói hoặc giờ lưu trú. Vui lòng chọn lại khung giờ phù hợp rồi tìm phòng.');
      }
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
          <select id="booking-payment-choice"><option value="counter">Thanh toán tại quầy khi trả phòng</option><option value="qr">Thanh toán bằng mã QR · quét trong 1 giờ</option></select>
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
    const onlinePayment = byId('booking-payment-choice').value === 'qr';
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
      personal_info_confirmed: true,
      payment_choice: onlinePayment ? 'qr' : 'counter'
    };
    if (voucherCode) payload.voucher_code = voucherCode;
    try {
      const result = await api.post('/customer-portal/bookings', payload, {
        headers: { 'Idempotency-Key': bookingKey(payload) }
      });
      try { sessionStorage.removeItem('aurelia-discovery-booking-intent'); } catch {}
      pendingRoom = null;
      byId('booking-feedback').textContent = `Đặt phòng thành công${result?.booking_code ? ` · ${result.booking_code}` : ''}. Tổng tiền ${money(result?.folio?.balance ?? quote.total_amount)}. ${onlinePayment ? 'Đang chuẩn bị mã thanh toán VietQR…' : 'Đơn đã được xác nhận. Quý khách thanh toán tại quầy trước khi trả phòng.'}`;
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
          panel.innerHTML = `<strong>${qr.provider === 'mock' ? 'Thanh toán mô phỏng (không chuyển tiền thật)' : 'Thanh toán qua VietQR'}</strong><img class="booking-payment-qr" src="data:image/png;base64,${imageData}" alt="Mã thanh toán"><p>${money(qr.amount)}</p><p>${esc(qr.beneficiary_bank)} · ${esc(qr.account_number)} · ${esc(qr.account_holder)}</p><p>Nội dung chuyển khoản: <strong>${esc(qr.reference_code)}</strong></p><p id="booking-payment-countdown" role="status"></p><p id="booking-payment-status" role="status">Chờ quét mã QR.</p>`;
          if (qr.checkout_url) {
            try {
              const target = new URL(qr.checkout_url, window.location.href);
              if (target.protocol === 'https:' && !target.username && !target.password) {
                const checkout = document.createElement('a'); checkout.href = target.href; checkout.target = '_blank'; checkout.rel = 'noopener noreferrer'; checkout.className = 'btn btn-outline btn-sm'; checkout.textContent = 'Mở trang thanh toán của nhà cung cấp'; panel.append(checkout);
              }
            } catch { /* Keep the QR available if a provider URL is malformed. */ }
          }
          if (qr.provider === 'mock') {
            const simulate = document.createElement('button');
            simulate.type = 'button';
            simulate.className = 'btn btn-primary btn-sm booking-payment-test-button';
            simulate.textContent = 'Mô phỏng thanh toán thành công';
            simulate.addEventListener('click', async () => {
              const status = byId('booking-payment-status');
              simulate.disabled = true;
              status.textContent = 'Đang xử lý thanh toán mô phỏng…';
              stopPaymentWatch();
              try {
                const payment = await api.post(`/folios/${result.folio.id}/mock-payment`, {});
                if (payment.payment_status === 'PAID') {
                  status.textContent = 'Thanh toán mô phỏng thành công. Đặt phòng đã được xác nhận.';
                  simulate.textContent = 'Đã thanh toán';
                  stopQrCountdown();
                } else {
                  status.textContent = payment.payment_status === 'PARTIAL'
                    ? `Thanh toán thiếu. Còn ${Number(payment.remaining_amount).toLocaleString('vi-VN')} đ.`
                    : 'Chưa thể xác nhận thanh toán mô phỏng.';
                  simulate.disabled = false;
                }
              } catch (error) {
                status.textContent = error.message || 'Không thể hoàn tất thanh toán mô phỏng.';
                simulate.disabled = false;
              }
            });
            panel.append(simulate);
          }
          startQrCountdown(qr.expires_in_seconds ?? result.payment_seconds_remaining ?? 3600);
          watchPayment(result.folio.id);
        } catch (error) {
          panel.hidden = false;
          panel.textContent = `Đơn đã được lưu nhưng chưa thể mở phiên QR (${error.message || 'lỗi máy chủ'}). Hãy thử mở lại giao dịch từ Lịch sử đặt phòng khi phiên còn hạn.`;
        }
      } else {
        byId('booking-payment-panel').hidden = false;
        byId('booking-payment-panel').textContent = 'Đơn đã xác nhận. Vui lòng thanh toán tại quầy trước khi trả phòng.';
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
    if (intent.fromConcierge && (!Number.isInteger(Number(intent.roomVersion))
        || !Number.isFinite(Number(intent.quotedTotal)) || Number(intent.quotedTotal) <= 0
        || typeof intent.checkedAt !== 'string')) {
      try { sessionStorage.removeItem('aurelia-discovery-booking-intent'); } catch {}
      feedback('Dữ liệu gợi ý phòng không còn hợp lệ. Vui lòng tìm lại trong Quản gia AI.', true);
      return null;
    }
    byId('search-checkin').value = intent.checkIn;
    byId('search-checkout').value = intent.checkOut;
    byId('search-rental-type').value = intent.rentalType || 'DAILY';
    byId('search-checkout').min = intent.rentalType === 'HOURLY' ? intent.checkIn : shiftDate(intent.checkIn, 1);
    byId('search-checkin-time').value = intent.checkInTime || '14:00';
    byId('search-checkout-time').value = intent.checkOutTime || '12:00';
      syncStayControls();
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
        stopQrCountdown();
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
      byId('search-checkin-time').value = '18:00';
      byId('search-checkout-time').value = '08:00';
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
    syncStayControls();
    rooms = [];
    render();
    byId('room-result-count').textContent = 'Hãy tìm phòng theo loại hình đã chọn';
  };
  byId('search-rental-type').addEventListener('change', rentalTypeChanged);
  const datesChanged = () => {
    const checkIn = byId('search-checkin').value;
    const rentalType = byId('search-rental-type').value;
    const sameDayAllowed = rentalType === 'HOURLY';
    const minCheckOut = checkIn ? (sameDayAllowed ? checkIn : shiftDate(checkIn, 1)) : today;
    byId('search-checkout').min = minCheckOut;
    if (byId('search-checkout').value < minCheckOut) byId('search-checkout').value = minCheckOut;
    if (rentalType === 'OVERNIGHT' && checkIn) byId('search-checkout').value = shiftDate(checkIn, 1);
    syncStayControls();
    rooms = [];
    render();
    byId('room-result-count').textContent = 'Hãy tìm phòng theo ngày đã chọn';
    const issue = stayWindowIssue(checkIn, byId('search-checkout').value, rentalType,
      byId('search-checkin-time').value, byId('search-checkout-time').value);
    feedback(issue || 'Ngày lưu trú đã thay đổi. Hãy tìm lại để kiểm tra tình trạng phòng.', Boolean(issue));
  };
  byId('search-checkin').addEventListener('change', datesChanged);
  byId('search-checkout').addEventListener('change', datesChanged);
  const timesChanged = () => {
    syncStayControls();
    rooms = [];
    render();
    byId('room-result-count').textContent = 'Hãy tìm phòng theo thời gian đã chọn';
    const issue = stayWindowIssue(byId('search-checkin').value, byId('search-checkout').value,
      byId('search-rental-type').value, byId('search-checkin-time').value, byId('search-checkout-time').value);
    feedback(issue || 'Giờ lưu trú đã thay đổi. Hãy tìm lại để cập nhật tình trạng phòng.', Boolean(issue));
  };
  byId('search-checkin-time').addEventListener('change', timesChanged);
  byId('search-checkout-time').addEventListener('change', timesChanged);
  syncStayControls();
  byId('room-detail-dialog').addEventListener('click', event => {
    if (event.target === byId('room-detail-dialog')) closeDialog(byId('room-detail-dialog'));
  });
  byId('booking-dialog').addEventListener('click', event => {
    if (event.target === byId('booking-dialog') && !booking) closeDialog(byId('booking-dialog'));
  });
  byId('booking-dialog').addEventListener('close', () => { stopPaymentWatch(); stopQrCountdown(); });

  const resumeIntent = takeResumeIntent();
  await load({ preferredType: resumeIntent?.type || '' });
  if (resumeIntent) {
    const room = rooms.find(item => String(item.id) === String(resumeIntent.roomId));
    if (!room && resumeIntent.fromConcierge) {
      try { sessionStorage.removeItem('aurelia-discovery-booking-intent'); } catch {}
      feedback('Phòng không còn trong dữ liệu tình trạng hiện tại. Vui lòng tìm lại trong Quản gia AI.', true);
    } else if (!room) feedback('Phòng đã chọn không còn trong kết quả. Vui lòng kiểm tra lại ngày và tìm phòng.', true);
    else {
      if (resumeIntent.fromConcierge) room.conciergeSuggestion = resumeIntent;
      await showBookingConfirmation(room);
    }
  }
}
