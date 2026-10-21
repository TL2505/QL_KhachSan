/**
 * Aurelia Grand Palace - Legacy Membership Tier Mapping Engine
 * Aligns 100% with Legacy System (LoyaltyService.java, PaymentForm.java, customers table)
 * Formula: 1 Point = 1,000 VND spent.
 * Tiers: Member (<500 pts), Silver (500-999 pts), Gold (1000-4999 pts), VIP (>=5000 pts).
 */

export const MEMBERSHIP_TIERS = Object.freeze([
  Object.freeze({ name: 'Member', minPoints: 0 }),
  Object.freeze({ name: 'Silver', minPoints: 500 }),
  Object.freeze({ name: 'Gold', minPoints: 1000 }),
  Object.freeze({ name: 'VIP', minPoints: 5000 })
]);

// Accept the current Java DTO and the older web history field names.
export function normalizeLoyaltyHistory(history) {
  return (Array.isArray(history) ? history : []).map(log => {
    const raw = Number(log.pointsChange ?? log.pointsChanged ?? log.points ?? 0);
    const amount = Number.isFinite(raw) ? Math.abs(raw) : 0;
    const type = String(log.type ?? log.changeType ?? '').toLowerCase();
    return {
      ...log,
      changeType: type || (raw < 0 ? 'redeem' : 'earn'),
      pointsChanged: amount,
      changeDate: log.createdAt ?? log.changeDate ?? 'N/A',
      reason: log.description ?? log.reason ?? ''
    };
  });
}

export function summarizeLoyaltyHistory(history) {
  let currentPoints = 0;
  let totalPoints = 0;
  for (const log of normalizeLoyaltyHistory(history)) {
    if (log.changeType === 'earn') {
      totalPoints += log.pointsChanged;
      currentPoints += log.pointsChanged;
    } else {
      currentPoints -= log.pointsChanged;
    }
  }
  return { currentPoints: Math.max(0, currentPoints), totalPoints };
}

export function getLegacyTierInfo(totalPoints = 0, customSpent = null) {
  const pts = Math.max(0, Number(totalPoints) || 0);
  const spent = customSpent != null ? Number(customSpent) : pts * 1000;

  if (pts >= MEMBERSHIP_TIERS[3].minPoints) {
    return {
      tier: 'VIP',
      name: 'VIP',
      badge: '👑 VIP',
      cls: 'tier-vip',
      color: '#22d3ee',
      accent: '#06b6d4',
      bgGradient: 'linear-gradient(135deg, #09203f 0%, #1e1b4b 45%, #311042 100%)',
      cardBorder: 'rgba(34, 211, 238, 0.45)',
      chipColor: '#22d3ee',
      discountPct: 15,
      nextTier: null,
      nextMinPts: 5000,
      remainingPts: 0,
      remainingSpend: 0,
      progressPct: 100,
      totalSpent: spent,
      description: 'Hội Viên Thượng Hạng (Đặc quyền VIP, Butler 24/7 & Giảm 15%)'
    };
  }

  if (pts >= MEMBERSHIP_TIERS[2].minPoints) {
    const nextPts = MEMBERSHIP_TIERS[3].minPoints;
    const remaining = nextPts - pts;
    const progress = Math.min(100, Math.max(0, Math.round(((pts - 1000) / 4000) * 100)));
    return {
      tier: 'Gold',
      name: 'Gold',
      badge: '⭐ Gold',
      cls: 'tier-gold',
      color: '#fbbf24',
      accent: '#f59e0b',
      bgGradient: 'linear-gradient(135deg, #2b1f08 0%, #4a320d 50%, #1c1505 100%)',
      cardBorder: 'rgba(245, 158, 11, 0.45)',
      chipColor: '#d4af37',
      discountPct: 10,
      nextTier: 'VIP',
      nextMinPts: nextPts,
      remainingPts: remaining,
      remainingSpend: remaining * 1000,
      progressPct: progress,
      totalSpent: spent,
      description: 'Hội Viên Vàng Hoàng Gia (Giảm 10% & Đổi điểm voucher)'
    };
  }

  if (pts >= MEMBERSHIP_TIERS[1].minPoints) {
    const nextPts = MEMBERSHIP_TIERS[2].minPoints;
    const remaining = nextPts - pts;
    const progress = Math.min(100, Math.max(0, Math.round(((pts - 500) / 500) * 100)));
    return {
      tier: 'Silver',
      name: 'Silver',
      badge: '🥈 Silver',
      cls: 'tier-silver',
      color: '#cbd5e1',
      accent: '#9ca3af',
      bgGradient: 'linear-gradient(135deg, #1e293b 0%, #334155 50%, #0f172a 100%)',
      cardBorder: 'rgba(203, 213, 225, 0.4)',
      chipColor: '#cbd5e1',
      discountPct: 5,
      nextTier: 'Gold',
      nextMinPts: nextPts,
      remainingPts: remaining,
      remainingSpend: remaining * 1000,
      progressPct: progress,
      totalSpent: spent,
      description: 'Hội Viên Bạc Ánh Kim (Giảm 5% & Ưu tiên nhận phòng)'
    };
  }

  // Member Tier (0 - 499 pts)
  const nextPts = MEMBERSHIP_TIERS[1].minPoints;
  const remaining = nextPts - pts;
  const progress = Math.min(100, Math.max(0, Math.round((pts / 500) * 100)));
  return {
    tier: 'Member',
    name: 'Member',
    badge: '💎 Member',
    cls: 'tier-member',
    color: '#94a3b8',
    accent: '#64748b',
    bgGradient: 'linear-gradient(135deg, #131b2e 0%, #1a233a 50%, #0b0f17 100%)',
    cardBorder: 'rgba(148, 163, 184, 0.3)',
    chipColor: '#94a3b8',
    discountPct: 5,
    nextTier: 'Silver',
    nextMinPts: nextPts,
    remainingPts: remaining,
    remainingSpend: remaining * 1000,
    progressPct: progress,
    totalSpent: spent,
    description: 'Hội Viên Chuẩn (Tích lũy 1 điểm mỗi 1.000 VNĐ chi tiêu)'
  };
}
