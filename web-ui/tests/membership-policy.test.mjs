import test from 'node:test';
import assert from 'node:assert/strict';
import { MEMBERSHIP_TIERS, getLegacyTierInfo, summarizeLoyaltyHistory, normalizeLoyaltyHistory } from '../public/js/customer/tier-helper.js';
import { renderLoyalty } from '../public/js/staff/loyalty.js';

test('staff policy and customer tiers match backend boundaries', () => {
  const container = {};
  renderLoyalty(container, {});
  for (const tier of MEMBERSHIP_TIERS) {
    assert.equal(getLegacyTierInfo(tier.minPoints).name, tier.name);
    assert.ok(container.innerHTML.includes(tier.minPoints.toLocaleString('vi-VN')));
  }
  for (const [points, name] of [[0, 'Member'], [299, 'Member'], [499, 'Member'], [999, 'Silver'], [4999, 'Gold']]) {
    assert.equal(getLegacyTierInfo(points).name, name);
  }
});

test('redemption changes balance without reducing membership progress or tier', () => {
  const history = [
    { type: 'earn', pointsChange: 1200 },
    { type: 'redeem', pointsChange: -500 },
    { changeType: 'earn', pointsChanged: '300' },
    { changeType: 'redeem', pointsChanged: 100 }
  ];
  const summary = summarizeLoyaltyHistory(history);
  assert.deepEqual(summary, { currentPoints: 900, totalPoints: 1500 });
  const tier = getLegacyTierInfo(summary.totalPoints);
  assert.equal(tier.name, 'Gold');
  assert.equal(tier.remainingPts, 3500);
  assert.equal(tier.progressPct, 13);
});

test('history normalizes Java DTOs and handles unavailable data', () => {
  assert.deepEqual(summarizeLoyaltyHistory(null), { currentPoints: 0, totalPoints: 0 });
  const [log] = normalizeLoyaltyHistory([{ type: 'redeem', pointsChange: -100, description: 'Voucher', createdAt: '2026-10-21' }]);
  assert.equal(log.pointsChanged, 100);
  assert.equal(log.reason, 'Voucher');
  assert.equal(log.changeDate, '2026-10-21');
  assert.deepEqual(summarizeLoyaltyHistory([{ type: 'earn', pointsChange: 'invalid' }]), { currentPoints: 0, totalPoints: 0 });
});
