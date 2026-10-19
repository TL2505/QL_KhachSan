import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { submitBooking, getRooms, lookupBooking, TRANSACTION_UNCONFIRMED } from '../public/api.js';
import { resolveServiceSchedule } from '../public/js/customer/service-order.js';

const values = new Map();
globalThis.localStorage = {getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key)};
globalThis.window = {location:{origin:'https://hotel.example.test'}};
const draft = () => ({roomId:1,roomVersion:2,checkInDate:'2026-12-01',checkOutDate:'2026-12-02',fullName:'Synthetic Guest',phone:'TEST'});
test.beforeEach(()=>values.clear());

test('network, HTTP, and malformed success never fabricate a booking or persist a receipt', async()=>{
  for (const response of [null, new Response('{"status":"success","data":{"booking_id":99}}',{status:500}), new Response('{"status":"success","data":{}}',{status:201})]) {
    globalThis.fetch=async()=>{if(!response)throw new Error('Synthetic outage');return response;};
    await assert.rejects(submitBooking(draft()),error=>error.message===TRANSACTION_UNCONFIRMED);
    assert.equal(values.has('aurelia_recent_bookings'),false);
  }
});
test('only a valid backend receipt is displayed and an uncertain retry keeps its key',async()=>{
  const payload=draft();let key;
  globalThis.fetch=async(url,init)=>{key=init.headers.get('Idempotency-Key');throw new Error('Synthetic disconnect');};
  await assert.rejects(submitBooking(payload));
  globalThis.fetch=async(url,init)=>{
    assert.equal(init.headers.get('Idempotency-Key'),key);
    assert.equal(JSON.parse(init.body).identity_card,null);
    return new Response(JSON.stringify({status:'success',data:{booking_id:42,booking_code:'BKG-42',status:'pending',folio:{id:3,balance:'123.45'}}}),{status:201});
  };
  const receipt=await submitBooking(payload);assert.equal(receipt.bookingId,42);assert.equal(receipt.totalAmount,123.45);assert.equal(receipt.status,'pending');
  assert.equal(values.has('aurelia_recent_bookings'),false);
});
test('unavailable and empty inventory cannot become sample bookable rooms',async()=>{
  globalThis.fetch=async()=>{throw new Error('Synthetic outage');};await assert.rejects(getRooms());
  globalThis.fetch=async()=>new Response('{"status":"success","data":[]}');assert.deepEqual((await getRooms()).rooms,[]);
});
test('a cached receipt cannot satisfy a failed authoritative lookup',async()=>{
  values.set('aurelia_recent_bookings',JSON.stringify([{bookingId:42,status:'CONFIRMED',paymentStatus:'PAID'}]));
  globalThis.fetch=async()=>{throw new Error('Synthetic outage');};await assert.rejects(lookupBooking('42','TEST'));
});
test('relative schedules become explicit instants in hotel time',()=>{
  const now=new Date('2026-10-20T10:00:00Z');
  assert.equal(resolveServiceSchedule('now',now),null);
  assert.equal(resolveServiceSchedule('30m',now),'2026-10-20T10:30:00.000Z');
  assert.equal(resolveServiceSchedule('1h',now),'2026-10-20T11:00:00.000Z');
  assert.equal(resolveServiceSchedule('19:30',now),'2026-10-20T19:30:00+07:00');
  assert.equal(resolveServiceSchedule('08:00',now),'2026-10-21T08:00:00+07:00');
  assert.throws(()=>resolveServiceSchedule('19:30',new Date('2026-10-20T14:00:00Z')));
});
test('all served booking modules exclude simulation and external QR generators',()=>{
  for(const file of ['api.js','js/api.js','public/api.js','public/js/api.js','js/app.js','public/app.js','public/js/app.js','js/customer/bookings.js','public/js/customer/bookings.js']){
    const source=fs.readFileSync(new URL('../'+file,import.meta.url),'utf8');
    assert.doesNotMatch(source,/isSimulated|api\.qrserver\.com|Math\.floor\(100000/);
  }
});
