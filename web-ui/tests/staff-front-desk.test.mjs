import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { api } from '../public/api.js';
import { renderFrontDesk } from '../public/js/staff/front-desk.js';
import { renderLoyalty } from '../public/js/staff/loyalty.js';

const tick=()=>new Promise(resolve=>setImmediate(resolve));
async function until(predicate) {for(let i=0;i<100;i++){if(predicate())return;await tick();}assert.fail('Expected UI state not reached');}
function fixture(t) {
  const dom=new JSDOM('<main></main>',{url:'http://localhost'});
  const saved={window:globalThis.window,document:globalThis.document,sessionStorage:globalThis.sessionStorage,get:api.get,post:api.post,put:api.put};
  Object.assign(globalThis,{window:dom.window,document:dom.window.document,sessionStorage:dom.window.sessionStorage});
  dom.window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
  dom.window.HTMLDialogElement.prototype.close=function(){this.open=false;};
  t.after(()=>{api.get=saved.get;api.post=saved.post;api.put=saved.put;Object.assign(globalThis,{window:saved.window,document:saved.document,sessionStorage:saved.sessionStorage});dom.window.close();});
  return {dom,root:document.querySelector('main'),$:id=>document.getElementById(id),event:type=>new dom.window.Event(type,{bubbles:true,cancelable:true})};
}

test('walk-in uses the server quote, prevents duplicate creation and requires collection before check-in',async t=>{
  const realDate=globalThis.Date;
  const hotelDay=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new realDate());
  let fixedNow=new realDate(`${hotelDay}T13:00:00+07:00`).getTime();
  globalThis.Date=class extends realDate { constructor(...args){super(...(args.length?args:[fixedNow]));} static now(){return fixedNow;} };
  t.after(()=>{globalThis.Date=realDate;});
  const f=fixture(t);const calls=[];
  api.get=async path=>path.startsWith('/customers?search=') ? [
    {id:9,fullName:'Khách QA',phone:'0901234567',identityCard:'P123456789',email:'fixture@example.invalid'}]
    : path.startsWith('/rooms/availability') ? [
    {id:4,version:8,room_type_id:1,room_number:'104',room_type_name:'Standard',capacity:2,availability_status:'available'},
    {id:5,version:8,room_type_id:1,capacity:2,availability_status:'not_ready'}]
    : path.startsWith('/pricing') ? {room_subtotal:100,service_charge:5,vat:8,total_amount:113}
    : [{id:22,balance:113}];
  api.post=async(path,body,options)=>{calls.push({path,body,options});return path.endsWith('walk-in') ? {booking_id:12,booking_code:'WALKIN-12',room_number:'104',folio:{id:22,balance:113}} : {};};
  api.put=async(path,body)=>{calls.push({path,body});return {};};
  await renderFrontDesk(f.root,{role:'STAFF'});
  f.$('desk-phone').value='0901234567';f.$('desk-phone').dispatchEvent(f.event('input'));
  await new Promise(resolve=>setTimeout(resolve,300));
  await until(()=>f.root.querySelector('[data-select-customer="9"]'));
  f.root.querySelector('[data-select-customer="9"]').click();
  assert.equal(f.$('desk-name').value,'Khách QA');assert.equal(f.$('desk-phone').readOnly,true);
  f.$('desk-search').click();await until(()=>f.root.querySelector('[data-select-room="4"]'));
  const roomChoices=[...f.root.querySelectorAll('[data-select-room]')];
  assert.equal(roomChoices.length,2,'Both available and unavailable rooms should show their status');
  assert.equal(roomChoices.filter(button=>!button.disabled).length,1,'Only the available room may be selected');
  assert.equal(roomChoices.find(button=>button.disabled).disabled,true,'Dirty room must not be selectable');
  f.root.querySelector('[data-select-room="4"]').click();await until(()=>!f.$('desk-create').disabled);
  f.$('desk-form').dispatchEvent(f.event('submit'));f.$('desk-form').dispatchEvent(f.event('submit'));
  await until(()=>!f.$('desk-receipt').hidden);
  assert.equal(calls.length,1);assert.equal(calls[0].body.room_version,8);assert.equal(calls[0].body.quoted_total,113);
  assert.match(calls[0].options.headers['Idempotency-Key'],/^[a-f0-9-]{36}$/i);
  assert.equal(f.$('desk-checkin').disabled,true);
  f.$('desk-pay').click();await tick();assert.equal(calls.length,1,'Must acknowledge actual collection first');
  fixedNow=new realDate(`${hotelDay}T15:00:00+07:00`).getTime();
  f.$('desk-collected').checked=true;f.$('desk-pay').click();await until(()=>!f.$('desk-checkin').disabled);
  assert.equal(calls[1].path,'/folios/22/pay');assert.deepEqual(calls[1].body,{amount:113,payment_method:'cash',checkout:false});
  f.$('desk-checkin').click();await until(()=>f.$('desk-checkin').hidden);
  assert.equal(calls[2].path,'/bookings/checkin/12');
});

test('changing stay invalidates a selected quote and non-reception roles cannot use the desk form',async t=>{
  const f=fixture(t);await renderFrontDesk(f.root,{role:'HOUSEKEEPING'});
  assert.equal(f.$('desk-form'),null);
  await renderFrontDesk(f.root,{role:'STAFF'});
  f.$('desk-out').dispatchEvent(f.event('change'));
  assert.equal(f.$('desk-create').disabled,true);
  assert.match(f.$('desk-room-results').textContent,/Chưa có dữ liệu phòng/,'Stale room results should be replaced with the empty-state guidance');
});

test('a new guest can be entered without selecting an existing customer',async t=>{
  const f=fixture(t);
  api.get=async path=>path.startsWith('/customers?search=') ? [] : assert.fail(`Unexpected lookup request: ${path}`);
  await renderFrontDesk(f.root,{role:'FRONT_DESK'});
  f.$('desk-name').value='Khách QA mới';f.$('desk-name').dispatchEvent(f.event('input'));
  await new Promise(resolve=>setTimeout(resolve,300));
  assert.equal(f.$('desk-customer-suggestions').hidden,true,'No-match lookup must not show a suggestion');
  assert.equal(f.$('desk-name').readOnly,false);
  f.$('desk-phone').value='0900000000';f.$('desk-identity').value='P123456789';
});

test('staff can search membership and read history without adjustment controls',async t=>{
  const f=fixture(t);
  api.get=async path=>path.includes('/history/') ? [{type:'earn',pointsChange:1200,description:'Lưu trú',createdAt:'2026-10-22'}]
    : [{id:7,fullName:'Đặng Ánh',phone:'0901234567',email:'fixture@example.invalid',loyaltyPoints:1000,totalLoyaltyPoints:1200}];
  api.post=async()=>assert.fail('Read-only membership must not mutate points');
  await renderLoyalty(f.root);
  assert.equal(f.root.querySelector('[data-adjust]'),null);
  f.$('membership-search').value='dang anh';f.$('membership-search').dispatchEvent(f.event('input'));
  assert.match(f.$('membership-body').textContent,/Đặng Ánh/);
  f.root.querySelector('[data-history]').click();await until(()=>f.root.querySelector('#membership-history').textContent.includes('Lưu trú'));
  assert.equal(f.$('membership-dialog').open,true);
});
