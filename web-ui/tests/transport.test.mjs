import test from 'node:test';
import assert from 'node:assert/strict';
import {apiFetch,fetchAuthenticatedFile,setAuthSession,getAuthSession,getApiBaseUrl,setApiBaseUrl,normalizeApiBase} from '../public/api.js';
const store=new Map();
globalThis.localStorage={getItem:key=>store.get(key)??null,setItem:(key,value)=>store.set(key,value),removeItem:key=>store.delete(key)};
const events=new EventTarget();
globalThis.window={location:{origin:'https://hotel.example.test'},dispatchEvent:event=>events.dispatchEvent(event)};
test.beforeEach(()=>store.clear());
test('same-origin versioned base preserves HTTPS and upgrades legacy configuration',()=>{
 assert.equal(getApiBaseUrl(),'https://hotel.example.test/api/v1');
 setApiBaseUrl('https://hotel.example.test/api/');assert.equal(getApiBaseUrl(),'https://hotel.example.test/api/v1');
 assert.throws(()=>normalizeApiBase('javascript:alert(1)'));assert.throws(()=>normalizeApiBase('https://user:password@hotel.example.test/api/v1'));
});
test('stored bearer token and idempotency header accompany a successful uppercase envelope',async()=>{
 setAuthSession({token:'synthetic-token'});
 globalThis.fetch=async(url,init)=>{assert.equal(url,'https://hotel.example.test/api/v1/rooms');assert.equal(init.headers.get('Authorization'),'Bearer synthetic-token');assert.equal(init.headers.get('Idempotency-Key'),'synthetic-key');return new Response(JSON.stringify({status:'SUCCESS',data:[{id:1}]}));};
 assert.deepEqual(await apiFetch('/rooms',{headers:{'Idempotency-Key':'synthetic-key'}}),[{id:1}]);
});
test('public room availability does not send or invalidate a stored session token',async()=>{
 setAuthSession({token:'possibly-expired-token'});
 globalThis.fetch=async(url,init)=>{
  assert.equal(url,'https://hotel.example.test/api/v1/rooms/availability?check_in=2026-10-17');
  assert.equal(init.headers.has('Authorization'),false);
  return new Response(JSON.stringify({status:'success',data:[]}));
 };
 assert.deepEqual(await apiFetch('/rooms/availability?check_in=2026-10-17'),[]);
 assert.equal(getAuthSession().token,'possibly-expired-token');
});
test('HTTP denial cannot be converted to success by a response envelope',async()=>{
 globalThis.fetch=async()=>new Response(JSON.stringify({status:'success',data:{secret:'must-not-return'}}),{status:403});
 await assert.rejects(apiFetch('/users'),error=>error.status===403);
});
test('expired session is cleared and announced without clearing a replacement session',async()=>{
 let count=0;events.addEventListener('hotel:session-expired',()=>count++,{once:true});setAuthSession({token:'old'});
 globalThis.fetch=async()=>new Response(JSON.stringify({message:'Missing or malformed Authorization header (Bearer <token>)'}),{status:401});
 await assert.rejects(apiFetch('/rooms'),error=>error.status===401&&error.message==='Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');assert.equal(getAuthSession(),null);assert.equal(count,1);
 setAuthSession({token:'old'});globalThis.fetch=async()=>{setAuthSession({token:'new'});return new Response('{}',{status:401});};await assert.rejects(apiFetch('/rooms'));assert.equal(getAuthSession().token,'new');
});
test('401 without a structured session clears legacy token and hides backend diagnostics',async()=>{
 store.set('auth_token','synthetic-invalid-token');
 let count=0;const listener=()=>count++;events.addEventListener('hotel:session-expired',listener);
 globalThis.fetch=async()=>new Response(JSON.stringify({message:'Missing or malformed Authorization header (Bearer <token>)'}),{status:401});
 await assert.rejects(apiFetch('/bookings'),error=>error.status===401&&!error.message.includes('Authorization header')&&error.message.includes('đăng nhập lại'));
 assert.equal(store.has('auth_token'),false);assert.equal(getAuthSession(),null);assert.equal(count,1);
 events.removeEventListener('hotel:session-expired',listener);
});
test('expired invoice download clears a legacy token and announces session expiry',async()=>{
 store.set('customer_token','synthetic-invalid-token');
 let count=0;const listener=()=>count++;events.addEventListener('hotel:session-expired',listener);
 globalThis.fetch=async()=>new Response('unauthorized',{status:401});
 await assert.rejects(fetchAuthenticatedFile('/invoices/1/pdf'),error=>error.status===401&&error.message.includes('đăng nhập lại'));
 assert.equal(store.has('customer_token'),false);assert.equal(count,1);
 events.removeEventListener('hotel:session-expired',listener);
});
test('abort remains distinguishable from connection failure',async()=>{
 globalThis.fetch=async()=>{throw new DOMException('cancelled','AbortError');};await assert.rejects(apiFetch('/rooms'),error=>error.name==='AbortError');
 globalThis.fetch=async()=>{throw new DOMException('deadline exceeded','TimeoutError');};await assert.rejects(apiFetch('/rooms'),error=>error.name==='TimeoutError');
});

test('a slow OTP request can exceed 2.5 seconds and transport-only options never reach fetch', async t => {
 t.mock.timers.enable({apis:['setTimeout']});
 let calls=0;
 globalThis.fetch=async (url,init)=>{
  calls++;
  assert.equal('timeoutMs' in init,false);
  assert.equal('retryOnNetworkError' in init,false);
  return new Promise((resolve,reject)=>{
   init.signal.addEventListener('abort',()=>reject(init.signal.reason),{once:true});
   setTimeout(()=>resolve(new Response(JSON.stringify({status:'success',data:{challenge_id:'synthetic-challenge'}}))),3000);
  });
 };
 const pending=apiFetch('/auth/google/request-otp',{method:'POST',body:'{}',timeoutMs:45000,retryOnNetworkError:false});
 t.mock.timers.tick(3000);
 assert.deepEqual(await pending,{challenge_id:'synthetic-challenge'});
 assert.equal(calls,1);
});

test('OTP failures are never replayed to an alternative host and the chosen deadline still aborts', async t => {
 const previousLocation=window.location;
 window.location={origin:'http://localhost:8081',hostname:'localhost',protocol:'http:',port:'8081'};
 t.mock.timers.enable({apis:['setTimeout']});
 try {
  let calls=0;
  globalThis.fetch=async()=>{calls++;throw new TypeError('synthetic connection lost after sending request');};
  await assert.rejects(apiFetch('/auth/google/request-otp',{method:'POST',body:'{}',retryOnNetworkError:false}));
  assert.equal(calls,1,'Do not resend a possibly delivered OTP request to the fallback host');
  calls=0;
  globalThis.fetch=async(url,init)=>{calls++;return new Promise((resolve,reject)=>init.signal.addEventListener('abort',()=>reject(init.signal.reason),{once:true}));};
  const expired=assert.rejects(apiFetch('/auth/google/request-otp',{method:'POST',body:'{}',timeoutMs:45000,retryOnNetworkError:false}),error=>error.name==='TimeoutError');
  t.mock.timers.tick(45000);
  await expired;
  assert.equal(calls,1);
 } finally { window.location=previousLocation; }
});
test('freeze retains Retry-After and hides server internals',async()=>{
 globalThis.fetch=async()=>new Response(JSON.stringify({message:'PRIVATE_DATABASE_DETAILS'}),{status:503,headers:{'Retry-After':'300'}});
 await assert.rejects(apiFetch('/rooms'),error=>error.status===503&&error.retryAfter==='300'&&!error.message.includes('PRIVATE'));
});
test('AI model discovery exposes only allowlisted backend diagnostics',async()=>{
 globalThis.fetch=async()=>new Response(JSON.stringify({error_code:'AI_PROVIDER_UNREACHABLE',message:'Backend không kết nối được tới Google Gemini.'}),{status:503});
 await assert.rejects(apiFetch('/admin/ai-settings/models'),error=>error.status===503&&error.message==='Backend không kết nối được tới Google Gemini.');
 globalThis.fetch=async()=>new Response(JSON.stringify({error_code:'PRIVATE_INTERNAL_ERROR',message:'PRIVATE_DATABASE_DETAILS'}),{status:503});
 await assert.rejects(apiFetch('/admin/ai-settings/models'),error=>error.status===503&&!error.message.includes('PRIVATE'));
 globalThis.fetch=async()=>new Response(JSON.stringify({error_code:'AI_PROVIDER_UNREACHABLE',message:'PRIVATE_DATABASE_DETAILS'}),{status:503});
 await assert.rejects(apiFetch('/rooms'),error=>error.status===503&&!error.message.includes('PRIVATE'));
});
test('invalid JSON and invalid session storage fail safely',async()=>{
 store.set('hotel_auth_session','bad-json');assert.equal(getAuthSession(),null);globalThis.fetch=async()=>new Response('<html>private debug</html>');await assert.rejects(apiFetch('/rooms'),error=>!error.message.includes('private debug'));
});
test('204 responses and direct data preserve supported response contracts',async()=>{
 globalThis.fetch=async()=>new Response(null,{status:204});assert.equal(await apiFetch('/users/1'),null);
 globalThis.fetch=async()=>new Response(JSON.stringify([{id:1}]));assert.deepEqual(await apiFetch('/rooms'),[{id:1}]);
});
test('authenticated file fetch attaches the session token and returns binary data',async()=>{
 setAuthSession({token:'pdf-token'});
 globalThis.fetch=async(url,init)=>{assert.equal(url,'https://hotel.example.test/api/v1/invoices/2/pdf');assert.equal(init.headers.get('Authorization'),'Bearer pdf-token');assert.equal(init.headers.get('Accept'),'application/pdf');return new Response(new Blob(['synthetic pdf']),{headers:{'Content-Type':'application/pdf'}});};
 assert.equal(await (await fetchAuthenticatedFile('/invoices/2/pdf')).text(),'synthetic pdf');
});
