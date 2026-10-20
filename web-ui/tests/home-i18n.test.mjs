import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { t, tx, setLanguage, setTranslatedText, translateUI } from '../public/js/shared/home-i18n.js';

const root = { documentElement: { setAttribute() {} }, querySelectorAll: () => [] };

test('English covers the hero, guest options, account controls and interpolated feedback', () => {
  setLanguage('en', root);
  assert.equal(t('Khẳng Định'), 'Experience');
  assert.equal(t('2 Người lớn (Tiêu chuẩn)'), '2 Adults (Standard)');
  assert.equal(t('Cổng thành viên'), 'Member Portal');
  assert.equal(tx`Tìm thấy ${3} phòng phù hợp.`, 'Found 3 matching rooms.');
  assert.equal(t('Ngày {0} – {1} · Tổng {2} phòng', ['20/10/2026', '21/10/2026', 16]), '20/10/2026–21/10/2026 · 16 rooms in total');
  assert.equal(t('Nguyễn Văn An'), 'Nguyễn Văn An');
  assert.equal(t('Bữa sáng rất ngon, cảm ơn các bạn.'), 'Bữa sáng rất ngon, cảm ơn các bạn.');
});

test('changing language updates tracked feedback without losing interpolation values', () => {
  const label = { textContent: '', dataset: {}, isConnected: true };
  setLanguage('vi', root);
  setTranslatedText(label, 'Tìm thấy {0} phòng phù hợp.', [4]);
  assert.equal(label.textContent, 'Tìm thấy 4 phòng phù hợp.');
  setLanguage('en', root);
  assert.equal(label.textContent, 'Found 4 matching rooms.');
  setLanguage('vi', root);
  assert.equal(label.textContent, 'Tìm thấy 4 phòng phù hợp.');
  label.isConnected = false;
});

test('translating field attributes preserves the actual input value', () => {
  const attributes = new Map([['placeholder', 'Nhập tên đăng nhập hoặc email'], ['data-i18n-placeholder', 'Nhập tên đăng nhập hoặc email']]);
  const field = { value: 'guest@example.com', dataset: {}, getAttribute: key => attributes.get(key) ?? null, setAttribute: (key, value) => attributes.set(key, value) };
  const form = { ...root, querySelectorAll: () => [field] };
  setLanguage('en', form);
  assert.equal(attributes.get('placeholder'), 'Enter your username or email');
  assert.equal(field.value, 'guest@example.com');
  setLanguage('vi', form);
  assert.equal(attributes.get('placeholder'), 'Nhập tên đăng nhập hoặc email');
  assert.equal(field.value, 'guest@example.com');
});

test('every marked homepage label and accessibility attribute has an English translation', () => {
  const html = fs.readFileSync(new URL('../public/index.html', import.meta.url), 'utf8');
  const decode = value => value.replaceAll('&amp;', '&').replaceAll('&quot;', '"').replaceAll('&#x27;', "'").replaceAll('&#39;', "'");
  for (const [, encoded] of html.matchAll(/data-i18n(?:-placeholder|-title|-aria-label)?="([^"]+)"/g)) {
    const source = decode(encoded);
    const english = t(source, {}, 'en');
    assert.ok(!/[ăâđêôơưĂÂĐÊÔƠƯ\u1EA0-\u1EF9]/u.test(english), `Missing translation: ${source}`);
  }
});
