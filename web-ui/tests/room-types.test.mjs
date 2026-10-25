import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';
import { api } from '../public/api.js';
import { renderRoomTypes } from '../public/js/admin/room-types.js';

const settle=()=>new Promise(resolve=>setImmediate(resolve));
const type=(overrides={})=>({id:4,name:'Synthetic Deluxe',description:'Test category',basePrice:1500000,capacity:3,imageUrl:null,imageAlt:'',amenities:'Wi-Fi, TV',visible:true,roomCount:0,...overrides});

async function setup(t,initial=[type()]){
  const dom=new JSDOM('<body><main id="view-container"></main><div id="global-modal"><div id="global-modal-content"></div></div></body>',{url:'http://localhost/portal.html'});
  const old={window:globalThis.window,document:globalThis.document,get:api.get,post:api.post,put:api.put,del:api.delete,confirm:globalThis.window?.showCustomConfirm};
  globalThis.window=dom.window;globalThis.document=dom.window.document;dom.window.closeModal=()=>{};dom.window.showCustomConfirm=async()=>true;
  let values=[...initial];const calls=[];
  api.get=async path=>{if(path==='/rooms/types')return values;if(path==='/admin/rental-policy')return values.map(v=>({...v,hourly_base_price:500000,hourly_extra_hour_price:250000,overnight_price:1100000}));throw new Error(path);};
  api.post=async(path,body)=>{calls.push({method:'POST',path,body});values.push(type({id:9,...body}));return values.at(-1);};
  api.put=async(path,body)=>{calls.push({method:'PUT',path,body});values=values.map(v=>String(v.id)===path.split('/').at(-1)?{...v,...body}:v);return values.find(v=>String(v.id)===path.split('/').at(-1));};
  api.delete=async path=>{calls.push({method:'DELETE',path});values=values.filter(v=>String(v.id)!==path.split('/').at(-1));return {};};
  t.after(()=>{api.get=old.get;api.post=old.post;api.put=old.put;api.delete=old.del;dom.window.close();globalThis.window=old.window;globalThis.document=old.document;});
  const root=document.querySelector('#view-container');await renderRoomTypes(root);return {dom,root,calls};
}

test('separate room-type view includes CRUD and the moved shared stay-rate editor',async t=>{
  const {root}=await setup(t);
  assert.equal(root.querySelector('h1').textContent,'Hạng phòng');
  assert.match(root.querySelector('#room-types-body').textContent,/Synthetic Deluxe/);
  assert.equal(root.querySelectorAll('#room-types-rate-editor [data-rate]').length,3);
  assert.ok(root.querySelector('#room-types-rate-editor .room-rates-grid'));
  root.querySelector('#room-type-add').click();
  assert.ok(document.querySelector('#room-type-form'));
  assert.equal(document.querySelector('#global-modal-content [data-rate]'),null);
});

test('room-type navigation has a Lucide icon and rate cards flex to available width',async()=>{
  const portalApp=await readFile(fileURLToPath(new URL('../public/portal-app.js',import.meta.url)),'utf8');
  const adminCss=await readFile(fileURLToPath(new URL('../public/portal-admin.css',import.meta.url)),'utf8');
  assert.match(portalApp,/\['room-types',\s*'Hạng phòng',\s*'layers'\]/);
  assert.match(adminCss,/grid-template-columns:\s*repeat\(auto-fit,\s*minmax\(min\(100%,\s*420px\),\s*1fr\)\)/);
  assert.match(adminCss,/room-rates-grid\s*\{\s*grid-template-columns:\s*minmax\(0,\s*1fr\);/);
});

test('room-type form creates with validated room-class fields then refreshes list and rates',async t=>{
  const {root,calls}=await setup(t,[]);root.querySelector('#room-type-add').click();
  const form=document.querySelector('#room-type-form');
  form.querySelector('#rt-name').value='Synthetic New';form.querySelector('#rt-base-price').value='1200000';form.querySelector('#rt-capacity').value='2';
  form.dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));await settle();await settle();
  assert.equal(calls[0].method,'POST');assert.equal(calls[0].path,'/rooms/types');assert.equal(calls[0].body.name,'Synthetic New');
  assert.match(root.querySelector('#room-types-body').textContent,/Synthetic New/);
});

test('hạng phòng in use cannot be deleted; available type can be removed after custom confirmation',async t=>{
  const {root,calls}=await setup(t,[type({roomCount:2}),type({id:5,name:'Available',roomCount:0})]);
  const rows=[...root.querySelectorAll('#room-types-body tr')];
  assert.equal(rows[0].querySelector('.btn-danger').disabled,true);
  assert.equal(rows[1].querySelector('.btn-danger').disabled,false);
  rows[1].querySelector('.btn-danger').click();await settle();await settle();
  assert.deepEqual(calls.find(call=>call.method==='DELETE'),{method:'DELETE',path:'/rooms/types/5'});
});
