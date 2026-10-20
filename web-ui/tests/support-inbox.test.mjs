import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeSupportInboxes } from '../public/js/staff/chat-model.js';

test('an outgoing staff reply keeps the original customer as the conversation peer', () => {
    const [row] = normalizeSupportInboxes([{ senderId: 2, receiverId: 3, senderName: 'Khách hàng Test', content: 'Bạn muốn đặt phòng với kinh phí khoảng bao nhiêu?' }], 2);
    assert.equal(row.customerId, 3);
    assert.equal(row.senderId, 2);
    assert.equal(row.senderName, 'Khách hàng Test');
});

test('incoming queue messages and direct replies resolve to the same customer', () => {
    const rows = normalizeSupportInboxes([
        { id: 4, senderId: '2', receiverId: '3', content: 'Staff reply' },
        { id: 3, senderId: 3, receiverId: null, content: 'Queue request' },
        { id: 2, senderId: 4, receiverId: 2, content: 'Another customer' }
    ], 2);
    assert.deepEqual(rows.map(row => row.customerId), [3, 4]);
    assert.equal(rows[0].id, 4);
});

test('unavailable inboxes and invalid or self conversations cannot be selected', () => {
    assert.deepEqual(normalizeSupportInboxes(null, 2), []);
    assert.deepEqual(normalizeSupportInboxes([{ senderId: 2, receiverId: null }, { senderId: 2, receiverId: 2 }, { senderId: 'invalid' }], 2), []);
});
