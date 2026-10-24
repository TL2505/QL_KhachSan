import { escapeHtml as esc } from './ui.js';

/** Shared stay/guest search controls used by guest discovery and staff reservations. */
export function renderBookingSearchFields({
  checkInId, checkInValue, checkInMin = checkInValue, checkInReadOnly = false,
  checkOutId, checkOutValue, checkOutMin = checkOutValue,
  guestCountId, guestCount = 2, guestLabel = count => `${count} khách`,
  voucher = null, searchButtonId, searchButtonLabel = 'Kiểm tra phòng trống'
}) {
  const guests = Array.from({length:20},(_,index)=>index+1);
  return `<div class="booking-search-grid">
    <label class="booking-search-field"><span>Ngày nhận phòng</span><input class="input input-sm booking-search-control" id="${esc(checkInId)}" type="date" value="${esc(checkInValue)}" min="${esc(checkInMin)}" ${checkInReadOnly?'readonly':''} required></label>
    <label class="booking-search-field"><span>Ngày trả phòng</span><input class="input input-sm booking-search-control" id="${esc(checkOutId)}" type="date" value="${esc(checkOutValue)}" min="${esc(checkOutMin)}" required></label>
    <label class="booking-search-field"><span>Số khách</span><select class="select select-sm booking-search-control" id="${esc(guestCountId)}" required>${guests.map(count=>`<option value="${count}"${count===Number(guestCount)?' selected':''}>${esc(guestLabel(count))}</option>`).join('')}</select></label>
    ${voucher ? `<label class="booking-search-field"><span>Mã ưu đãi</span><input class="input input-sm booking-search-control booking-promo-input" id="${esc(voucher.id)}" maxlength="${Number(voucher.maxLength)||50}" placeholder="${esc(voucher.placeholder||'')}" autocomplete="off" value="${esc(voucher.value||'')}"></label>` : ''}
    <button id="${esc(searchButtonId)}" class="btn booking-search-submit" type="button"><i data-lucide="search" aria-hidden="true"></i><span data-search-label>${esc(searchButtonLabel)}</span></button>
  </div>`;
}

/** Keep request parameters identical for public discovery and the staff room picker. */
export function roomAvailabilityPath({checkIn, checkOut, rentalType = 'DAILY', checkInTime = '14:00', checkOutTime = '12:00', includeStatus = true}) {
  const query = new URLSearchParams({
    check_in: checkIn,
    check_out: checkOut,
    rental_type: rentalType,
    check_in_time: checkInTime,
    check_out_time: checkOutTime,
    include_status: String(Boolean(includeStatus))
  });
  return `/rooms/availability?${query}`;
}
