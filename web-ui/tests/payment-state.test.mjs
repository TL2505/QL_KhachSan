import test from 'node:test';
import assert from 'node:assert/strict';
import {paymentState,paymentLabel} from '../js/shared/payment-state.js';
test('only the server payment projection signals a completed payment',()=>{
  assert.equal(paymentState({status:'confirmed'}),'WAITING');assert.equal(paymentState({status:'settled'}),'WAITING');assert.equal(paymentState({payment_status:'PAID'}),'PAID');
  assert.equal(paymentLabel({paymentStatus:'PARTIAL'}),'Thanh toán thiếu');assert.match(paymentLabel({payment_status:'RECONCILIATION_ERROR'}),/chưa hoàn tất/);
});
