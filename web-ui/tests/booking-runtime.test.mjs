import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const values=new Map(),attempts=new Map();
globalThis.localStorage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
globalThis.sessionStorage={getItem:k=>attempts.get(k)??null,setItem:(k,v)=>attempts.set(k,v),removeItem:k=>attempts.delete(k)};
globalThis.window={location:{origin:'https://hotel.example.test'}};
const draft=()=>({roomId:71,roomVersion:2,guestCount:3,checkInDate:'2027-01-01',checkOutDate:'2027-01-02',fullName:'Isolated Guest',phone:'0901234567',identityCard:'TEST12345'});
const receipt=()=>new Response(JSON.stringify({status:'success',data:{booking_id:42,booking_code:'BKG-42',status:'pending',folio:{id:3,balance:'123.45'}}}),{status:201});
const moduleFor=label=>import('../public/api.js?runtime-test='+label);
test.beforeEach(()=>{values.clear();attempts.clear();});
test('uncertain booking retry survives page reload without storing guest contact or identity',async()=>{
 let key,body;
 globalThis.fetch=async(url,init)=>{key=init.headers.get('Idempotency-Key');body=init.body;throw new Error('Isolated lost response');};
 await assert.rejects((await moduleFor('before-reload')).submitBooking(draft()));
 const saved=[...attempts.values()].join('');assert.ok(saved.includes(key));assert.ok(!saved.includes('0901234567'));assert.ok(!saved.includes('TEST12345'));assert.ok(!saved.includes('Isolated Guest'));
 globalThis.fetch=async(url,init)=>{assert.equal(init.headers.get('Idempotency-Key'),key);assert.equal(init.body,body);assert.equal(JSON.parse(init.body).guest_count,3);return receipt();};
 assert.equal((await (await moduleFor('after-reload')).submitBooking(draft())).bookingId,42);assert.equal(attempts.size,0);
});
test('blocked attempt storage retains an in-memory retry key',async()=>{
 const working=globalThis.sessionStorage;globalThis.sessionStorage={getItem(){throw Error('blocked');},setItem(){throw Error('blocked');},removeItem(){throw Error('blocked');}};
 try {const api=await moduleFor('blocked');let key;globalThis.fetch=async(url,init)=>{key=init.headers.get('Idempotency-Key');throw Error('isolated');};await assert.rejects(api.submitBooking(draft()));
 globalThis.fetch=async(url,init)=>{assert.equal(init.headers.get('Idempotency-Key'),key);return receipt();};await api.submitBooking(draft());}finally{globalThis.sessionStorage=working;}
});
test('definitive booking validation, permission and room conflicts keep their HTTP meaning',async()=>{
 const api=await moduleFor('errors');for(const status of [400,403,409]){globalThis.fetch=async()=>new Response(JSON.stringify({message:'Isolated actionable error'}),{status});await assert.rejects(api.submitBooking(draft()),e=>e.status===status&&e.message==='Isolated actionable error');}
});
test('503 retains safe support reference and night-audit Retry-After without claiming rollback',async()=>{
 const api=await moduleFor('diagnostics');const reference='517c2443-b912-477e-8c5d-fa1a225732ef';
 globalThis.fetch=async()=>new Response(JSON.stringify({message:'PRIVATE_DATABASE_DETAIL'}),{status:503,headers:{'X-Booking-Error-Id':reference}});
 await assert.rejects(api.submitBooking(draft()),e=>e.status===503&&e.bookingErrorId===reference&&e.message.includes(reference)&&!e.message.includes('PRIVATE')&&!e.message.includes('chưa được ghi nhận'));
 globalThis.fetch=async()=>new Response('{}',{status:503,headers:{'Retry-After':'300'}});await assert.rejects(api.submitBooking(draft()),e=>e.retryAfter==='300'&&e.message.includes('tạm thời'));
});
test('public review reads do not attach stale credentials; review writes and booking retain authentication',async()=>{
 const api=await moduleFor('reviews');api.setAuthSession({token:'isolated-token',role:'CUSTOMER'});
 globalThis.fetch=async(url,init)=>{assert.equal(init.headers.has('Authorization'),false);return new Response('{"summary":{},"reviews":[]}');};
 await api.apiFetch('/reviews/public?page=1&limit=6');assert.equal(api.getAuthSession().token,'isolated-token');
 globalThis.fetch=async(url,init)=>{assert.equal(init.headers.get('Authorization'),'Bearer isolated-token');return new Response('{"status":"success","data":[]}');};
 await api.apiFetch('/reviews');await api.apiFetch('/reviews/public',{method:'POST',body:'{}'});
 globalThis.fetch=async(url,init)=>{assert.equal(init.headers.get('Authorization'),'Bearer isolated-token');return receipt();};await api.submitBooking(draft());
});
test('all deployed API copies match and no homepage review fallback invents guests or ratings',()=>{
 const base=fs.readFileSync(new URL('../api.js',import.meta.url),'utf8');
 for(const path of ['js/api.js','public/api.js','public/js/api.js'])assert.equal(fs.readFileSync(new URL('../'+path,import.meta.url),'utf8'),base,path);
 for(const path of ['js/app.js','public/js/app.js','public/app.js']){
  const s=fs.readFileSync(new URL('../'+path,import.meta.url),'utf8');const widget=s.slice(s.indexOf('export async function loadPublicReviewsWidget'));
  assert.doesNotMatch(widget,/total_reviews: 128|Trần H\*\*\*|summary\.overall_average \|\| 5/);
 }
});
