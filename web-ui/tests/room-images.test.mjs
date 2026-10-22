import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { bindRoomImageFallbacks } from '../public/js/shared/room-images.js';

test('failed card and detail photos display the existing placeholder without retrying', () => {
  const dom = new JSDOM('<main><img src="missing.jpg"><img class="discovery-detail-image" src="missing-detail.jpg"></main>');
  const root = dom.window.document.querySelector('main');
  bindRoomImageFallbacks(root);
  root.querySelectorAll('img').forEach(img => img.dispatchEvent(new dom.window.Event('error')));
  assert.equal(root.querySelectorAll('img').length, 0);
  assert.equal(root.querySelectorAll('.room-image-placeholder').length, 1);
  assert.equal(root.querySelectorAll('.discovery-detail-no-image').length, 1);
  assert.match(root.textContent, /Ảnh phòng đang cập nhật/);
});

test('cached failures are replaced, while loaded photos remain visible', () => {
  const dom = new JSDOM('<main><img id="failed" src="missing.jpg"><img id="loaded" src="room.jpg"></main>');
  const root = dom.window.document.querySelector('main');
  for (const img of root.querySelectorAll('img')) Object.defineProperty(img, 'complete', { value: true });
  Object.defineProperty(root.querySelector('#loaded'), 'naturalWidth', { value: 1200 });
  bindRoomImageFallbacks(root);
  assert.equal(root.querySelector('#failed'), null);
  assert.ok(root.querySelector('#loaded'));
});
