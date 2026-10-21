import test from 'node:test';
import assert from 'node:assert/strict';
for(const base of ['../','../public/']){
 const {orderRoomsNewest}=await import(base+'js/admin/rooms.js');
 test(base+' room order uses creation time with stable ID ties and preserves source rows',()=>{
  const rooms=[{id:99,createdAt:'2025-10-21T12:00:00'},{id:3,createdAt:'2026-10-21T12:00:00'},{id:7,createdAt:'2026-10-21T12:00:00'},{id:100,createdAt:null}];
  assert.deepEqual(orderRoomsNewest(rooms).map(r=>r.id),[7,3,99,100]);assert.deepEqual(rooms.map(r=>r.id),[99,3,7,100]);
 });
 test(base+' legacy room responses without dates use descending insert IDs',()=>{
  assert.deepEqual(orderRoomsNewest([{id:1},{id:18},{id:19},{id:2}]).map(r=>r.id),[19,18,2,1]);
  assert.deepEqual(orderRoomsNewest([]),[]);
 });
}
