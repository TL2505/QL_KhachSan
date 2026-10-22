import test from 'node:test';
import assert from 'node:assert/strict';
import { MEMBERSHIP_TIERS, getLegacyTierInfo, summarizeLoyaltyHistory, normalizeLoyaltyHistory, membershipPoints } from '../public/js/customer/tier-helper.js';

test('staff policy and customer tiers match backend boundaries', () => {
  for (const tier of MEMBERSHIP_TIERS) {
    assert.equal(getLegacyTierInfo(tier.minPoints).name, tier.name);
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

test('stored lifetime totals keep all customer pages on the same tier despite partial history or redemptions',()=>{
  const history=[{type:'earn',pointsChange:200}];
  const result=membershipPoints({totalLoyaltyPoints:1500,loyaltyPoints:100},history);
  assert.deepEqual(result,{totalPoints:1500,currentPoints:100});
  assert.equal(getLegacyTierInfo(result.totalPoints).name,'Gold');
  assert.deepEqual(membershipPoints({totalLoyaltyPoints:0,loyaltyPoints:0},history),{totalPoints:0,currentPoints:0});
  assert.deepEqual(membershipPoints(null,history),{totalPoints:200,currentPoints:200});
});
