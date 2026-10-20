import test from 'node:test';
import assert from 'node:assert/strict';

for (const base of ['../', '../public/']) {
    const { memberTier, filterMembers, loyaltyDate } = await import(`${base}js/staff/loyalty.js`);
    const members = [
        { id: 1, fullName: 'Nguyễn Minh Anh', phone: '0901234567', loyaltyLevel: 'Silver', loyaltyPoints: 4000, totalLoyaltyPoints: 9000 },
        { id: 2, fullName: 'Trần Hoàng Nam', phone: '091 234 5678', loyaltyLevel: 'Gold', loyaltyPoints: 42, totalLoyaltyPoints: 42 },
        { id: 3, fullName: 'Lê Thảo Nguyên', phone: '0921234567', loyaltyLevel: 'VIP', loyaltyPoints: 0, totalLoyaltyPoints: 1200 },
        { id: 4, fullName: 'Synthetic Member', phone: null, loyaltyLevel: 'Member', loyaltyPoints: null, totalLoyaltyPoints: null },
        { id: 5, fullName: null, phone: null, loyaltyLevel: null }
    ];
    test(`${base} member tiers preserve stored ranks and do not infer a rank from points`, () => {
        assert.equal(memberTier(' silver '), 'Silver');
        assert.equal(memberTier('VIP'), 'VIP');
        assert.equal(memberTier('Member'), 'Member');
        assert.equal(memberTier(null), 'Chưa xếp hạng');
        assert.equal(memberTier('constructor'), 'constructor');
        assert.equal(memberTier('__proto__'), '__proto__');
        assert.deepEqual(filterMembers(members, '', 'Gold').map(m => m.id), [2]);
        assert.deepEqual(filterMembers(members, '', 'Silver').map(m => m.id), [1]);
        assert.equal(members[0].totalLoyaltyPoints, 9000);
    });
    test(`${base} search and rank filter combine across names, phone formatting and unranked records`, () => {
        assert.deepEqual(filterMembers(members, 'nguyen minh anh').map(m => m.id), [1]);
        assert.deepEqual(filterMembers(members, 'hoang', 'Gold').map(m => m.id), [2]);
        assert.deepEqual(filterMembers(members, 'hoang', 'Silver'), []);
        assert.deepEqual(filterMembers(members, '0912345678').map(m => m.id), [2]);
        assert.deepEqual(filterMembers(members, '090 123 4567').map(m => m.id), [1]);
        assert.deepEqual(filterMembers(members, '', 'other').map(m => m.id), [4, 5]);
        assert.deepEqual(filterMembers(members, '3', 'VIP').map(m => m.id), [3]);
        assert.deepEqual(filterMembers([], 'anything'), []);
    });
    test(`${base} dates use stored hotel calendar values and never replace missing dates with today`, () => {
        assert.equal(loyaltyDate('2026-01-02T14:30'), '02/01/2026');
        assert.equal(loyaltyDate('2026-01-02T14:30:20', true), '02/01/2026 · 14:30');
        assert.equal(loyaltyDate('2026-01-02T23:00:00Z'), '03/01/2026');
        assert.equal(loyaltyDate(null), 'Chưa có dữ liệu');
        assert.equal(loyaltyDate('bad-date'), 'Chưa có dữ liệu');
    });
}
