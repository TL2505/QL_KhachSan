export function paymentState(p = {}) {
  const state = String(p.payment_status ?? p.paymentStatus ?? '').toUpperCase();
  return ['WAITING', 'PAID', 'PARTIAL', 'RECONCILIATION_ERROR'].includes(state) ? state : 'WAITING';
}
export function paymentLabel(p) { return {WAITING:'Đang chờ thanh toán', PAID:'Đã thanh toán', PARTIAL:'Thanh toán thiếu', RECONCILIATION_ERROR:'Lỗi đối soát – chưa hoàn tất'}[paymentState(p)]; }
export function paymentBadge(p) { return {WAITING:'badge badge-info', PAID:'badge badge-success', PARTIAL:'badge badge-warning', RECONCILIATION_ERROR:'badge badge-error'}[paymentState(p)]; }
