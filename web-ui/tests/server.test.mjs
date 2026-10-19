import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {createWebServer} from '../server.js';
const listen=server=>new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const stop=server=>new Promise(resolve=>{server.closeAllConnections();server.close(resolve);});
test('development origin serves both entries and forwards actual bearer headers',async()=>{
 const backend=http.createServer((req,res)=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify({path:req.url,authorization:req.headers.authorization}));});
 await listen(backend);const server=createWebServer({backendOrigin:'http://127.0.0.1:'+backend.address().port});await listen(server);
 try {
  const base='http://127.0.0.1:'+server.address().port;
  for(const page of ['/','/portal.html','/booking/']){const response=await fetch(base+page);assert.equal(response.status,200);assert.match(response.headers.get('content-type'),/text\/html/);}
  const portal=await (await fetch(base+'/portal.html')).text();
  assert.match(portal,/id="login-form"/);
  assert.doesNotMatch(portal,/http-equiv="refresh"/);
  for(const asset of ['/portal-app.js','/portal.css','/portal-customer.css','/portal-admin.css','/portal-theme.css','/public/api.js','/public/js/view-customer.js']){
   assert.equal((await fetch(base+asset)).status,200,asset);
  }
  assert.equal((await fetch(base+'/server.js')).status,404);
  const response=await fetch(base+'/api/v1/rooms?filter=all',{headers:{Authorization:'Bearer synthetic-fixture'}});assert.deepEqual(await response.json(),{path:'/api/v1/rooms?filter=all',authorization:'Bearer synthetic-fixture'});
  assert.equal((await fetch(base+'/missing.js')).status,404);
  assert.equal((await fetch(base+'/%2e%2e%2fsecret.txt')).status,400);
  assert.equal((await fetch(base+'/index.html:private')).status,400);
  assert.equal((await fetch(base+'/portal.html',{method:'POST'})).status,405);
 }finally{await stop(server);await stop(backend);}
});
test('an unavailable backend is an explicit error rather than an HTML success',async()=>{
 const probe=http.createServer();await listen(probe);const origin='http://127.0.0.1:'+probe.address().port;await stop(probe);
 const server=createWebServer({backendOrigin:origin});await listen(server);
 try {const response=await fetch('http://127.0.0.1:'+server.address().port+'/api/v1/rooms');assert.equal(response.status,502);assert.equal((await response.json()).status,'error');}finally{await stop(server);}
});
