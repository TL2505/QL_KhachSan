import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { api } from '../public/api.js';
import { renderSupport } from '../public/js/customer/support.js';

const tick = () => new Promise(resolve => setImmediate(resolve));
function fixture(t) {
  const dom = new JSDOM('<section id="app-view"><main></main></section>',{url:'http://localhost'});
  const saved = Object.fromEntries(['window','document','MutationObserver'].map(key=>[key,globalThis[key]]));
  const get = api.get, post = api.post;
  Object.assign(globalThis,{window:dom.window,document:dom.window.document,MutationObserver:dom.window.MutationObserver});
  t.after(()=>{window.dispatchEvent(new window.Event('hotel:session-expired'));api.get=get;api.post=post;Object.assign(globalThis,saved);dom.window.close();});
  const root = document.querySelector('main');
  return {root, $:name=>root.querySelector('#customer-support-'+name), event:name=>new dom.window.Event(name,{cancelable:true,bubbles:true})};
}

test('support loads the common queue, renders staff replies safely and blocks duplicate sends',async t=>{
  const f=fixture(t);let rows=[{senderId:2,content:'<img src=x onerror=alert(1)>',createdAt:'2026-10-22T10:00:00Z'}];
  const paths=[];api.get=async path=>{paths.push(path);return rows;};
  let finish;const sent=[];api.post=(path,payload,options)=>{sent.push({path,payload,options});return new Promise(resolve=>{finish=resolve;});};
  await renderSupport(f.root,{userId:3});
  assert.equal(paths[0],'/chat/history?u1=3&u2=0');
  assert.equal(f.$('messages').querySelector('img'),null);
  assert.match(f.$('messages').textContent,/Nhân viên lễ tân/);
  f.$('input').value='Cần hỗ trợ đặt phòng';f.$('input').dispatchEvent(f.event('input'));
  f.$('form').dispatchEvent(f.event('submit'));f.$('form').dispatchEvent(f.event('submit'));
  assert.equal(sent.length,1);assert.equal(f.$('send').disabled,true);
  assert.deepEqual(sent[0].payload,{senderId:3,receiverId:0,content:'Cần hỗ trợ đặt phòng'});
  assert.equal(sent[0].options.retryOnNetworkError,false);
  rows=[...rows,{senderId:3,content:'Cần hỗ trợ đặt phòng'}];finish({});await tick();
  assert.equal(f.$('input').value,'');assert.match(f.$('messages').textContent,/Bạn/);
  assert.match(f.$('feedback').textContent,/Đã gửi/);
});

test('offline history can be retried and failed send retains the draft',async t=>{
  const f=fixture(t);api.get=async()=>{throw Error('offline');};
  await renderSupport(f.root,{userId:3});assert.equal(f.$('input').disabled,true);assert.equal(f.$('notice').hidden,false);
  api.get=async()=>[];f.$('refresh').click();await tick();
  assert.equal(f.$('input').disabled,false);assert.match(f.$('messages').textContent,/Bắt đầu trò chuyện/);
  api.post=async()=>{throw Error('offline');};
  f.$('input').value='Giữ lại nội dung';f.$('input').dispatchEvent(f.event('input'));f.$('form').dispatchEvent(f.event('submit'));await tick();
  assert.equal(f.$('input').value,'Giữ lại nội dung');assert.equal(f.$('send').disabled,false);assert.match(f.$('feedback').textContent,/giữ lại/);
});

test('navigation aborts pending history and cannot render a previous account response',async t=>{
  const f=fixture(t);let finish,signal;
  api.get=(path,options)=>{signal=options.signal;return new Promise(resolve=>{finish=resolve;});};
  const rendering=renderSupport(f.root,{userId:3});
  f.root.replaceChildren();await tick();assert.equal(signal.aborted,true);
  finish([{senderId:3,content:'private response'}]);await rendering;
  assert.equal(f.root.textContent,'');
});
