import test from 'node:test';
import assert from 'node:assert/strict';
import {applyTheme,savedTheme} from '../public/js/shared/theme.js';
import {escapeHtml,roomStatus,ROOM_STATUSES} from '../public/js/shared/ui.js';
import {presentationRole,isDepartmentalRole} from '../public/js/shared/roles.js';
test('both themes apply independently of storage availability',()=>{
 let light;const root={dataset:{},style:{},classList:{toggle:(name,value)=>{assert.equal(name,'light-theme');light=value;}}};
 const storage={setItem(){throw Error('blocked');},getItem(){throw Error('blocked');}};
 assert.equal(savedTheme(storage),'light');assert.equal(applyTheme('dark',{root,storage}),'dark');assert.equal(light,false);assert.equal(root.style.colorScheme,'dark');
 assert.equal(applyTheme('light',{root,storage}),'light');assert.equal(light,true);
});
test('saved legacy dark preference is retained',()=>{assert.equal(savedTheme({getItem:()=> 'dark'}),'dark');});
test('departmental roles never become Front Desk and unknown roles fail closed',()=>{
 assert.equal(presentationRole('STAFF'),null);assert.equal(presentationRole('ADMIN'),'admin');assert.equal(presentationRole(' Front Desk '),'front_desk');assert.equal(presentationRole('FRONT_DESK'),'front_desk');
 for(const role of ['housekeeping','maintenance','f&b']){assert.equal(presentationRole(role),role);assert.equal(isDepartmentalRole(role),true);}
 assert.equal(presentationRole('guest'),null);assert.equal(presentationRole('superuser'),null);
});
test('seven room states stay distinct and obsolete statuses do not become available',()=>{
 assert.equal(Object.keys(ROOM_STATUSES).length,7);for(const value of Object.keys(ROOM_STATUSES))assert.equal(roomStatus(value).code,value);
 assert.notEqual(roomStatus('out_of_order').label,roomStatus('out_of_service').label);assert.equal(roomStatus('available').code,'unknown');
});
test('dynamic HTML values cannot escape text or quoted attributes',()=>{
 assert.equal(escapeHtml('<img src=x onerror="attack()">'), '&lt;img src=x onerror=&quot;attack()&quot;&gt;');assert.equal(escapeHtml(0),'0');assert.equal(escapeHtml(null),'');
});
