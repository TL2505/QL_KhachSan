export function paymentState(p = {}) {
  const payment = p.payment && typeof p.payment === 'object' ? p.payment : p;
  const state = String(p.payment_status ?? p.paymentStatus ?? payment.payment_status ?? payment.paymentStatus ?? '').toUpperCase();
  if (state === 'WAITING'
      && payment.remaining_amount != null
      && payment.paid_amount != null
      && Number(payment.remaining_amount) === 0
      && Number(payment.paid_amount) === 0) return 'UNTRACKED';
  return ['WAITING', 'QR_PENDING', 'QR_EXPIRED', 'PAID', 'PARTIAL', 'RECONCILIATION_ERROR', 'UNTRACKED'].includes(state) ? state : 'UNTRACKED';
}
export function paymentLabel(p) {
  const payment = p.payment && typeof p.payment === 'object' ? p.payment : p;
  if (paymentState(p) === 'QR_EXPIRED' && payment.payment_reconciliation_pending) return 'Đang đối soát giao dịch QR';
  if (paymentState(p) === 'QR_EXPIRED' && String(payment.booking_status || '').toLowerCase() === 'pending') return 'QR hết hạn · đang kết thúc booking';
  const choice = String(payment.payment_choice ?? p.paymentChoice ?? p.payment_choice ?? '').toLowerCase();
  const bookingStatus = String(payment.booking_status ?? p.status ?? '').toLowerCase();
  if (paymentState(p) === 'WAITING' && choice === 'counter' && ['confirmed', 'checked_in'].includes(bookingStatus)) {
    return 'Thanh toán tại quầy trước khi trả phòng';
  }
  return {WAITING:'Đang chờ thanh toán tại quầy', QR_PENDING:'Chờ quét mã QR', QR_EXPIRED:'Phiên QR hết hạn · cần đặt lại', PAID:'Đã thanh toán', PARTIAL:'Thanh toán thiếu', RECONCILIATION_ERROR:'Lỗi đối soát – chưa hoàn tất', UNTRACKED:'Chưa có dữ liệu thanh toán'}[paymentState(p)];
}
export function paymentBadge(p) { return {WAITING:'badge badge-info', QR_PENDING:'badge badge-info', QR_EXPIRED:'badge badge-warning', PAID:'badge badge-success', PARTIAL:'badge badge-warning', RECONCILIATION_ERROR:'badge badge-error', UNTRACKED:'badge badge-outline'}[paymentState(p)]; }
