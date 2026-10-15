import test from 'node:test';
import assert from 'node:assert/strict';
import {applyTheme,nextTheme,savedTheme} from '../public/js/shared/theme.js';
import {escapeHtml,roomStatus,ROOM_STATUSES} from '../public/js/shared/ui.js';
import {presentationRole,isDepartmentalRole} from '../public/js/shared/roles.js';
test('both themes apply independently of storage availability',()=>{
 let light;const root={dataset:{},style:{},classList:{toggle:(name,value)=>{assert.equal(name,'light-theme');light=value;}}};
 const storage={setItem(){throw Error('blocked');},getItem(){throw Error('blocked');}};
 assert.equal(savedTheme(storage),'system');assert.equal(applyTheme('dark',{root,storage}),'dark');assert.equal(light,false);assert.equal(root.style.colorScheme,'dark');
 assert.equal(applyTheme('light',{root,storage}),'light');assert.equal(light,true);
});
test('three theme choices cycle and system resolves to the browser preference',()=>{
 assert.equal(nextTheme('system'),'light');assert.equal(nextTheme('light'),'dark');assert.equal(nextTheme('dark'),'system');
 const themeColor={content:''};const root={dataset:{},style:{},classList:{toggle(){}},ownerDocument:{querySelector:()=>themeColor}};
 assert.equal(applyTheme('system',{root,storage:null,media:{matches:true}}),'system');
 assert.equal(root.dataset.theme,'dark');assert.equal(root.dataset.themePreference,'system');assert.equal(themeColor.content,'#0b0f17');
 assert.equal(applyTheme('system',{root,storage:null,media:{matches:false}}),'system');
 assert.equal(root.dataset.theme,'light');assert.equal(themeColor.content,'#f6f3ed');
});
test('saved global and legacy customer preferences are retained',()=>{
 assert.equal(savedTheme({getItem:key=>key==='theme'?'dark':null}),'dark');
 assert.equal(savedTheme({getItem:key=>key==='aurelia_customer_theme'?'light':null}),'light');
});
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
