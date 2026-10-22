import { escapeHtml } from './ui.js';

// One entry point for the customer portal's availability, pricing and payment flow.
export const CUSTOMER_BOOKING_URL = 'portal.html?view=discovery';

export function renderBookingAction({ label = 'Đặt phòng mới' } = {}) {
  return `<a href="${CUSTOMER_BOOKING_URL}" class="btn portal-primary customer-booking-action">
    <i data-lucide="calendar-plus" class="w-4 h-4" aria-hidden="true"></i>
    <span>${escapeHtml(label)}</span>
  </a>`;
}
