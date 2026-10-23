import test from 'node:test';
import assert from 'node:assert/strict';
import {paymentState,paymentLabel} from '../js/shared/payment-state.js';
test('only the server payment projection signals a completed payment',()=>{
  assert.equal(paymentState({status:'confirmed'}),'UNTRACKED');assert.equal(paymentState({status:'settled'}),'UNTRACKED');assert.equal(paymentState({payment_status:'PAID'}),'PAID');
  assert.equal(paymentState({payment:{payment_status:'WAITING',remaining_amount:100,paid_amount:0}}),'WAITING');
  assert.equal(paymentState({payment_status:'QR_PENDING'}),'QR_PENDING');assert.equal(paymentState({payment_status:'QR_EXPIRED'}),'QR_EXPIRED');
  assert.equal(paymentLabel({payment:{payment_status:'QR_EXPIRED',payment_reconciliation_pending:true}}),'Đang đối soát giao dịch QR');
  assert.equal(paymentLabel({payment:{payment_status:'QR_EXPIRED',booking_status:'pending'}}),'QR hết hạn · đang kết thúc booking');
  assert.equal(paymentLabel({paymentStatus:'PARTIAL'}),'Thanh toán thiếu');assert.equal(paymentLabel({payment_status:'WAITING'}),'Đang chờ thanh toán tại quầy');assert.match(paymentLabel({payment_status:'RECONCILIATION_ERROR'}),/chưa hoàn tất/);
});
