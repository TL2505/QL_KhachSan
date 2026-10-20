import test from 'node:test';
import assert from 'node:assert/strict';
import {parseCccdQr,normalizeIdentity} from '../public/js/customer/identity-scan.js';
import jsQR from '../public/vendor/jsQR.js';

test('CCCD QR preserves leading zeros, Vietnamese names and optional former ID',()=>{
  const data=parseCccdQr('000000000000||KHÁCH GIẢ LẬP|29022000|Nam|ĐỊA CHỈ GIẢ LẬP|01012020');
  assert.deepEqual(data,{identityCard:'000000000000',fullName:'KHÁCH GIẢ LẬP',dateOfBirth:'2000-02-29',address:'ĐỊA CHỈ GIẢ LẬP'});
});
test('other QR formats and impossible or future dates cannot fill a profile',()=>{
  for(const text of ['https://example.invalid','123|4|Test|01012000|Nam|Test','000000000000||Test|31022000|Nam|Test','000000000000||Test|01012999|Nam|Test'])assert.throws(()=>parseCccdQr(text));
});
test('OCR data uses the same validation and does not carry image or additional identifiers',()=>{
  const data=normalizeIdentity({id_card:'000000000000',full_name:'SYNTHETIC',dob:'2000-01-01',address:'SYNTHETIC',image_base64:'must-not-persist'});
  assert.equal(data.dateOfBirth,'2000-01-01');assert.equal(Object.keys(data).length,4);
  assert.throws(()=>normalizeIdentity({id_card:'000000000000',full_name:'SYNTHETIC',dob:'2000-01-01'}));
});
test('bundled QR decoder runs offline and rejects an empty image',()=>{
  assert.equal(typeof jsQR,'function');assert.equal(jsQR(new Uint8ClampedArray(64*64*4).fill(255),64,64),null);
});
