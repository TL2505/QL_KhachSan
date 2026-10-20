import test from 'node:test';
import assert from 'node:assert/strict';
import { renderPaginationComponent } from '../js/shared/pagination.js';

class Element {
  constructor(tagName) {
    this.tagName = tagName;
    this.children = [];
    this.listeners = new Map();
    this.attributes = new Map();
    this.textContent = '';
    this.value = '';
    this.disabled = false;
    this.className = '';
  }
  append(...elements) { this.children.push(...elements); }
  replaceChildren(...elements) { this.children = [...elements]; }
  setAttribute(name, value) { this.attributes.set(name, String(value)); }
  addEventListener(type, handler) { this.listeners.set(type, handler); }
}

test('pagination shows range, keeps controls available for one page and dispatches changes', () => {
  const container = new Element('div');
  const previousDocument = globalThis.document;
  globalThis.document = {
    getElementById: id => id === 'pagination-test' ? container : null,
    createElement: tagName => new Element(tagName)
  };

  try {
    let changedPage;
    let changedPageSize;
    renderPaginationComponent('pagination-test', 26, 10, 2, page => { changedPage = page; }, size => { changedPageSize = size; });

    const nav = container.children[0];
    const [summary, controls] = nav.children;
    assert.equal(summary.textContent, 'Hiển thị 11–20 trên 26 mục');
    assert.equal(controls.children[2].textContent, 'Trang 2 / 3');
    controls.children[3].listeners.get('click')();
    assert.equal(changedPage, 3);

    const sizeLabel = controls.children[0];
    const sizeSelect = sizeLabel.children[0];
    sizeSelect.value = '25';
    sizeSelect.listeners.get('change')();
    assert.equal(changedPageSize, 25);

    renderPaginationComponent('pagination-test', 0, 10, 1, () => {}, () => {});
    const [emptySummary, emptyControls] = container.children[0].children;
    assert.equal(emptySummary.textContent, 'Hiển thị 0–0 trên 0 mục');
    assert.equal(emptyControls.children[1].disabled, true);
    assert.equal(emptyControls.children[3].disabled, true);
  } finally {
    if (previousDocument === undefined) delete globalThis.document;
    else globalThis.document = previousDocument;
  }
});
