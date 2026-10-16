import test from 'node:test';
import assert from 'node:assert/strict';
import {apiFetch,fetchAuthenticatedFile,setAuthSession,clearAuthSession,getAuthSession,getApiBaseUrl,setApiBaseUrl,normalizeApiBase} from '../public/api.js';
const store=new Map();
globalThis.localStorage={getItem:key=>store.get(key)??null,setItem:(key,value)=>store.set(key,value),removeItem:key=>store.delete(key)};
const events=new EventTarget();
globalThis.window={location:{origin:'https://hotel.example.test'},dispatchEvent:event=>events.dispatchEvent(event)};
test.beforeEach(()=>store.clear());
test('port 3000 uses the same-origin proxy and portal logout export clears the session',()=>{
 const previous=window.location;
 try {
  window.location={origin:'http://hotel.example.test:3000',hostname:'hotel.example.test',port:'3000'};
  assert.equal(getApiBaseUrl(),'http://hotel.example.test:3000/api/v1');
  setAuthSession({token:'test'});clearAuthSession();assert.equal(getAuthSession(),null);
 } finally {window.location=previous;}
});
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
test('HTTP denial cannot be converted to success by a response envelope',async()=>{
 globalThis.fetch=async()=>new Response(JSON.stringify({status:'success',data:{secret:'must-not-return'}}),{status:403});
 await assert.rejects(apiFetch('/users'),error=>error.status===403);
});
test('expired session is cleared and announced without clearing a replacement session',async()=>{
 let count=0;events.addEventListener('hotel:session-expired',()=>count++,{once:true});setAuthSession({token:'old'});
 globalThis.fetch=async()=>new Response('{}',{status:401});await assert.rejects(apiFetch('/rooms'));assert.equal(getAuthSession(),null);assert.equal(count,1);
 setAuthSession({token:'old'});globalThis.fetch=async()=>{setAuthSession({token:'new'});return new Response('{}',{status:401});};await assert.rejects(apiFetch('/rooms'));assert.equal(getAuthSession().token,'new');
});
test('abort remains distinguishable from connection failure',async()=>{
 globalThis.fetch=async()=>{throw new DOMException('cancelled','AbortError');};await assert.rejects(apiFetch('/rooms'),error=>error.name==='AbortError');
});
test('freeze retains Retry-After and hides server internals',async()=>{
 globalThis.fetch=async()=>new Response(JSON.stringify({message:'PRIVATE_DATABASE_DETAILS'}),{status:503,headers:{'Retry-After':'300'}});
 await assert.rejects(apiFetch('/rooms'),error=>error.status===503&&error.retryAfter==='300'&&!error.message.includes('PRIVATE'));
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
