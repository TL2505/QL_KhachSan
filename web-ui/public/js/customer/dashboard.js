import { api } from "../../api.js";
import { getLegacyTierInfo, summarizeLoyaltyHistory } from "./tier-helper.js";

function refreshIcons() {
  if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
    window.lucide.createIcons();
  }
}

export async function renderDashboard(container, session) {
  if (!container) return;
  document.body.dataset.customerTier = "member";
  session = session || {};
  const displayName = session.fullName || session.username || 'Quý Khách';
  const customerId = session.customerId || session.userId || session.id || 1;
  const memberIdFormatted = `AURELIA-${String(customerId).padStart(6, '0')}`;

  const initials = (() => {
    const parts = String(displayName).trim().split(' ');
    return parts.length >= 2
      ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
      : (displayName[0] || 'A').toUpperCase();
  })();

  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Chào buổi sáng' : hour < 18 ? 'Chào buổi chiều' : 'Chào buổi tối';

  // Render initial skeleton / layout immediately to avoid hanging
  container.innerHTML = `
    <!-- Top Greeting Banner -->
    <div class="greeting-banner mb-6 p-6 rounded-2xl bg-gradient-to-r from-slate-900/90 via-slate-900/70 to-slate-950/90 border border-amber-500/20 flex items-center justify-between shadow-xl backdrop-blur-md">
      <div class="flex items-center gap-4">
        <div class="greeting-avatar w-14 h-14 rounded-2xl font-bold flex items-center justify-center text-xl shadow-lg shrink-0">
          ${initials}
        </div>
        <div class="greeting-text">
          <h2 class="text-xl md:text-2xl font-bold font-serif text-white tracking-wide">${greeting}, ${displayName}! 👋</h2>
          <p class="text-xs md:text-sm text-slate-400 mt-1">Cổng thành viên đặc quyền Aurelia Grand Palace. Chúc bạn có kỳ nghỉ trọn vẹn.</p>
        </div>
      </div>
      <div class="hidden sm:flex items-center gap-2">
        <a href="index.html#rooms" class="btn btn-primary customer-booking-action">
          <i data-lucide="calendar" class="w-4 h-4"></i>
          <span>Đặt phòng mới</span>
        </a>
      </div>
    </div>

    <!-- Main Dashboard Grid: Digital Member Card + Progress & Summary -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 mb-8">
      <!-- Digital Member Card Container (5 cols) -->
      <div class="lg:col-span-5" id="member-card-wrapper">
        <div class="card p-6 h-full flex flex-col justify-between rounded-3xl relative overflow-hidden bg-gradient-to-br from-slate-900 via-slate-950 to-slate-900 border border-amber-500/30 shadow-2xl">
          <div class="animate-pulse flex flex-col gap-4">
            <div class="h-6 w-1/3 bg-slate-800 rounded"></div>
            <div class="h-10 w-2/3 bg-slate-800 rounded"></div>
            <div class="h-20 w-full bg-slate-800 rounded-xl"></div>
          </div>
        </div>
      </div>

      <!-- Tier Progress & Stats (7 cols) -->
      <div class="lg:col-span-7 flex flex-col gap-6" id="member-stats-wrapper">
        <div class="card p-6 rounded-3xl bg-slate-900/80 border border-amber-500/20 shadow-xl animate-pulse">
          <div class="h-6 w-1/2 bg-slate-800 rounded mb-4"></div>
          <div class="h-4 w-full bg-slate-800 rounded mb-2"></div>
          <div class="h-4 w-2/3 bg-slate-800 rounded"></div>
        </div>
      </div>
    </div>

    <!-- Quick Actions / Highlights -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6" id="member-summary-stats">
      <!-- Skeleton Stats -->
      ${[1, 2, 3].map(() => `
        <div class="card p-5 rounded-2xl bg-slate-900/60 border border-slate-800 animate-pulse">
          <div class="h-4 w-1/2 bg-slate-800 rounded mb-3"></div>
          <div class="h-8 w-1/3 bg-slate-800 rounded"></div>
        </div>
      `).join('')}
    </div>
  `;
  refreshIcons();

  try {
    const [bookings, loyaltyHistory] = await Promise.all([
      api.get(`/bookings/customer/${customerId}`).catch(() => []),
      api.get(`/loyalty/history/${customerId}`).catch(() => [])
    ]);

    const allBookings = Array.isArray(bookings) ? bookings : [];

    // Tier uses lifetime earnings; redemption only changes the available balance.
    const { currentPoints, totalPoints } = summarizeLoyaltyHistory(loyaltyHistory);

    // Calculate total cumulative spending from completed/checked_in bookings
    let totalSpent = 0;
    let totalNights = 0;
    allBookings.forEach(b => {
      const price = Number(b.totalPrice || b.priceAtBooking || 0);
      if (b.status !== 'cancelled') {
        totalSpent += price;
      }
      if (b.checkInDate && b.checkOutDate) {
        try {
          const inD = new Date(b.checkInDate);
          const outD = new Date(b.checkOutDate);
          const diffTime = outD.getTime() - inD.getTime();
          const nights = Math.max(1, Math.round(diffTime / (1000 * 60 * 60 * 24)));
          if (!isNaN(nights) && b.status !== 'cancelled') totalNights += nights;
        } catch {}
      }
    });

    // If totalSpent is 0, estimate from points * 1000 according to legacy formula
    if (totalSpent === 0 && currentPoints > 0) {
      totalSpent = currentPoints * 1000;
    }

    const tier = getLegacyTierInfo(totalPoints, totalSpent);
    document.body.dataset.customerTier = tier.name.toLowerCase();

    // Render Digital Member Card (VIP luxury design)
    const cardWrapper = document.getElementById('member-card-wrapper');
    if (cardWrapper) {
      cardWrapper.innerHTML = `
        <div class="relative rounded-3xl p-6 text-white shadow-2xl overflow-hidden border transition-all duration-300 hover:shadow-amber-500/10 hover:border-amber-400/50 flex flex-col justify-between min-h-[260px]"
             style="background: ${tier.bgGradient}; border-color: ${tier.cardBorder};">
          <!-- Background Decorative Shimmer / Watermark -->
          <div class="absolute -right-8 -bottom-8 w-44 h-44 rounded-full bg-amber-400/5 blur-2xl pointer-events-none"></div>
          <div class="absolute right-4 top-4 opacity-10 text-white pointer-events-none">
            <i data-lucide="crown" class="w-32 h-32"></i>
          </div>

          <!-- Top Row: Brand & Tier Badge -->
          <div class="flex items-center justify-between z-10">
            <div class="flex items-center gap-2.5">
              <span class="w-8 h-8 rounded-full border border-amber-400/40 bg-slate-900/80 flex items-center justify-center text-amber-300 shadow">
                <i data-lucide="crown" class="w-4 h-4"></i>
              </span>
              <div>
                <strong class="font-serif tracking-wider text-sm block">AURELIA PALACE</strong>
                <span class="text-[9px] uppercase tracking-widest text-amber-400/80 font-semibold block">VIP MEMBER CLUB</span>
              </div>
            </div>
            <span class="px-3 py-1 rounded-full text-xs font-bold border flex items-center gap-1.5 shadow-sm"
                  style="background: rgba(15, 23, 42, 0.7); border-color: ${tier.accent}; color: ${tier.color};">
              ${tier.badge}
            </span>
          </div>

          <!-- Middle: Chip & Card ID -->
          <div class="my-5 z-10 flex items-center justify-between">
            <!-- Metallic Smart Chip -->
            <div class="w-11 h-8 rounded-md bg-gradient-to-tr from-amber-200 via-amber-400 to-amber-600 border border-amber-300/60 shadow-inner flex items-center justify-center">
              <div class="w-8 h-5 border border-amber-700/40 rounded-sm grid grid-cols-2 gap-0.5 opacity-60">
                <div class="border-b border-amber-800/40"></div>
                <div class="border-b border-amber-800/40"></div>
                <div></div><div></div>
              </div>
            </div>
            <span class="font-mono text-sm tracking-widest text-slate-300 font-bold">${memberIdFormatted}</span>
          </div>

          <!-- Bottom: Member Name, Points & Spending -->
          <div class="z-10 pt-3 border-t border-white/10 grid grid-cols-3 gap-2">
            <div>
              <p class="text-[9px] uppercase tracking-wider text-slate-400">Chủ thẻ</p>
              <p class="text-xs font-bold text-white truncate mt-0.5">${displayName}</p>
            </div>
            <div>
              <p class="text-[9px] uppercase tracking-wider text-slate-400">Điểm thưởng</p>
              <p class="text-xs font-bold text-amber-300 mt-0.5">${currentPoints.toLocaleString('vi-VN')} <span class="text-[10px] font-normal">pts</span></p>
            </div>
            <div class="text-right">
              <p class="text-[9px] uppercase tracking-wider text-slate-400">Tổng chi tiêu</p>
              <p class="text-xs font-bold text-emerald-300 mt-0.5">${totalSpent.toLocaleString('vi-VN')} <span class="text-[10px] font-normal">đ</span></p>
            </div>
          </div>
        </div>
      `;
    }

    // Render Tier Progress Card
    const statsWrapper = document.getElementById('member-stats-wrapper');
    if (statsWrapper) {
      statsWrapper.innerHTML = `
        <div class="card p-6 rounded-3xl bg-slate-900/85 border border-amber-500/25 shadow-xl flex flex-col justify-between h-full">
          <div>
            <div class="flex items-center justify-between mb-3">
              <div class="flex items-center gap-2">
                <i data-lucide="award" class="w-5 h-5 text-amber-400"></i>
                <h3 class="text-base font-bold text-white">Tiến Độ Nâng Hạng Thẻ</h3>
              </div>
              <span class="text-xs font-bold text-amber-300 px-2.5 py-1 rounded-full bg-amber-500/10 border border-amber-500/30">
                ${tier.nextTier ? `${totalPoints.toLocaleString('vi-VN')} / ${tier.nextMinPts.toLocaleString('vi-VN')} Điểm` : 'Hạng tối cao'}
              </span>
            </div>

            <p class="text-xs text-slate-300 leading-relaxed mb-4">
              ${tier.description}
            </p>

            <!-- Custom Progress Bar -->
            <div class="w-full bg-slate-950/80 rounded-full h-3.5 p-0.5 border border-slate-800 overflow-hidden mb-3">
              <div class="customer-tier-progress h-full rounded-full transition-all duration-1000 ease-out"
                   style="width: ${tier.progressPct}%; background: var(--portal-gold);"></div>
            </div>

            <div class="flex items-center justify-between text-[11px] text-slate-400">
              <span>Hạng hiện tại: <strong style="color:${tier.color};">${tier.name}</strong></span>
              <span>${tier.nextTier ? `Hạng tiếp theo: <strong class="text-amber-400">${tier.nextTier}</strong>` : 'Đã đạt đỉnh VIP'}</span>
            </div>
          </div>

          <div class="mt-5 p-3.5 rounded-xl bg-slate-950/60 border border-amber-500/15 text-xs text-slate-300 flex items-start gap-3">
            <i data-lucide="sparkles" class="w-4 h-4 text-amber-400 shrink-0 mt-0.5"></i>
            <div>
              ${tier.nextTier ? `
                Tích lũy thêm <strong class="text-amber-300">${tier.remainingPts.toLocaleString('vi-VN')} điểm</strong> (tương đương <strong class="text-white">${tier.remainingSpend.toLocaleString('vi-VN')} đ</strong> chi tiêu) để tự động thăng hạng <strong class="text-amber-400">${tier.nextTier}</strong> và nhận đặc quyền giảm ${tier.nextTier === 'VIP' ? '15%' : '10%'} toàn hệ thống.
              ` : `
                Chúc mừng bạn đã là <strong class="text-cyan-300">Hội viên VIP</strong>. Mọi đặc quyền cao cấp nhất bao gồm ưu tiên nhận phòng, miễn phí bữa sáng và giảm 15% đều đã được kích hoạt.
              `}
            </div>
          </div>
        </div>
      `;
    }

    // Render Summary Statistics (3 cards)
    const summaryStats = document.getElementById('member-summary-stats');
    if (summaryStats) {
      summaryStats.innerHTML = `
        <div class="card p-5 rounded-2xl bg-slate-900/70 border border-amber-500/20 hover:border-amber-400/40 transition-all flex items-center gap-4 shadow-lg">
          <div class="w-12 h-12 rounded-xl bg-blue-500/10 border border-blue-500/30 flex items-center justify-center text-blue-400 shrink-0">
            <i data-lucide="moon" class="w-6 h-6"></i>
          </div>
          <div>
            <p class="text-xs uppercase tracking-wider text-slate-400 font-semibold">Số đêm lưu trú</p>
            <h4 class="text-2xl font-bold font-serif text-white mt-0.5">${totalNights} <span class="text-xs font-normal text-slate-400">đêm</span></h4>
          </div>
        </div>

        <div class="card p-5 rounded-2xl bg-slate-900/70 border border-amber-500/20 hover:border-amber-400/40 transition-all flex items-center gap-4 shadow-lg">
          <div class="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400 shrink-0">
            <i data-lucide="calendar-check" class="w-6 h-6"></i>
          </div>
          <div>
            <p class="text-xs uppercase tracking-wider text-slate-400 font-semibold">Lượt đặt phòng</p>
            <h4 class="text-2xl font-bold font-serif text-white mt-0.5">${allBookings.length} <span class="text-xs font-normal text-slate-400">lượt</span></h4>
          </div>
        </div>

        <div class="card p-5 rounded-2xl bg-slate-900/70 border border-amber-500/20 hover:border-amber-400/40 transition-all flex items-center gap-4 shadow-lg">
          <div class="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0">
            <i data-lucide="percent" class="w-6 h-6"></i>
          </div>
          <div>
            <p class="text-xs uppercase tracking-wider text-slate-400 font-semibold">Ưu đãi hạng thẻ</p>
            <h4 class="text-2xl font-bold font-serif text-emerald-400 mt-0.5">Giảm ${tier.discountPct}% <span class="text-xs font-normal text-slate-400">giá phòng</span></h4>
          </div>
        </div>
      `;
    }

    refreshIcons();
  } catch (err) {
    console.error('Lỗi khi tải bảng điều khiển thành viên:', err);
  }
}
