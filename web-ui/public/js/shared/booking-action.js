import { escapeHtml } from './ui.js';

// One entry point for the customer portal's availability, pricing and payment flow.
export const CUSTOMER_BOOKING_URL = 'portal.html?view=discovery';

export function customerBookingUrl(voucherCode = '') {
  return CUSTOMER_BOOKING_URL + (voucherCode ? `&voucher=${encodeURIComponent(voucherCode)}` : '');
}

export function renderBookingAction({ label = 'Đặt phòng mới', voucherCode = '' } = {}) {
  return `<a href="${escapeHtml(customerBookingUrl(voucherCode))}" class="btn portal-primary customer-booking-action">
    <i data-lucide="calendar-plus" class="w-4 h-4" aria-hidden="true"></i>
    <span>${escapeHtml(label)}</span>
  </a>`;
}
