import test from 'node:test';
import assert from 'node:assert/strict';
import { validateImageFile } from '../public/js/shared/media-upload.js';

test('accepts JPEG and PNG files up to the 5 MB limit', () => {
  assert.doesNotThrow(() => validateImageFile({ type: 'image/jpeg', size: 1 }));
  assert.doesNotThrow(() => validateImageFile({ type: 'image/png', size: 5 * 1024 * 1024 }));
});

test('rejects empty, oversized, and unsupported image files', () => {
  for (const file of [
    { type: 'image/jpeg', size: 0 },
    { type: 'image/png', size: 5 * 1024 * 1024 + 1 },
    { type: 'image/svg+xml', size: 12 }
  ]) {
    assert.throws(() => validateImageFile(file), /JPEG hoặc PNG.*5 MB/);
  }
  assert.throws(() => validateImageFile(null), /JPEG hoặc PNG.*5 MB/);
});
