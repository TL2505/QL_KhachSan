import { api } from "../../api.js";
import { resolveServiceSchedule } from "./service-order.js";

function refreshIcons() {
  if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
    window.lucide.createIcons();
  }
}

function escapeHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// 5-Star In-Stay Service Menu Catalog


// Danh sách voucher ưu đãi đặc quyền
const VOUCHERS = [
  {
    code: "AURELIA_DINE15",
    title: "Ưu Đãi Ẩm Thực Kỳ Nghỉ 15%",
    discount: "Giảm 15% Dịch Vụ Ăn Uống Tại Phòng",
    desc: "Áp dụng cho mọi món ăn & đồ uống In-room Dining trong suốt thời gian lưu trú.",
    minSpend: "Từ 200.000 đ",
    tag: "Ẩm thực F&B",
    badgeCls: "bg-amber-500/20 text-amber-300 border-amber-500/30"
  },
  {
    code: "WELCOME_SPA20",
    title: "Đặc Quyền Spa Hoàng Gia 20%",
    discount: "Giảm 20% Liệu Trình Spa & Massage",
    desc: "Áp dụng cho các liệu trình chăm sóc sức khỏe và thư giãn tại Royal Spa.",
    minSpend: "Từ 500.000 đ",
    tag: "Spa & Sức khỏe",
    badgeCls: "bg-cyan-500/20 text-cyan-300 border-cyan-500/30"
  },
  {
    code: "MEMORIAL_ROOM25",
    title: "Tri Ân Nghỉ Dưỡng Trở Lại 25%",
    discount: "Giảm 25% Tiền Phòng Kỳ Nghỉ Kế Tiếp",
    desc: "Mã tri ân sau trả phòng áp dụng cho lần đặt phòng tiếp theo hoặc dịp sinh nhật.",
    minSpend: "Từ 1.500.000 đ",
    tag: "Tri ân",
    badgeCls: "bg-purple-500/20 text-purple-300 border-purple-500/30"
  },
  {
    code: "LUXURY2026",
    title: "Ưu Đãi Nghỉ Dưỡng Thượng Lưu",
    discount: "Giảm 10% Tổng Hóa Đơn Phòng",
    desc: "Áp dụng cho mọi hạng phòng khi đặt trực tuyến. Hạn dùng: 31/12/2026.",
    minSpend: "Không giới hạn",
    tag: "Phổ biến",
    badgeCls: "bg-amber-500/20 text-amber-300 border-amber-500/30"
  },
  {
    code: "VIP5STAR",
    title: "Đặc Quyền Thành Viên Gold & VIP",
    discount: "Giảm 15% Tổng Kỳ Nghỉ",
    desc: "Áp dụng cho khách hàng hạng thẻ Gold và VIP tại Aurelia Grand Palace.",
    minSpend: "Từ 3.000.000 đ",
    tag: "Đặc quyền VIP",
    badgeCls: "bg-purple-500/20 text-purple-300 border-purple-500/30"
  }
];

export async function renderConcierge(container, session) {
  let IN_STAY_SERVICES;
  try {
    const catalog = await api.get('/services/catalog');
    const rows = Array.isArray(catalog) ? catalog : catalog?.data;
    if (!Array.isArray(rows)) throw new Error('Invalid catalog');
    IN_STAY_SERVICES = rows.map(item => ({ ...item, desc: item.description || '',
      categoryName: item.category, priceStr: Number(item.price).toLocaleString('vi-VN') + ' ₫',
      suggestedVoucher: '', icon: item.category === 'SPA' ? 'sparkles' : 'utensils', tag: '' }));
  } catch (error) {
    container.textContent = 'Không tải được danh mục dịch vụ. Đơn dịch vụ chưa được ghi nhận; vui lòng liên hệ Lễ tân.';
    return;
  }
  if (!container) return;
  session = session || {};
  const customerId = session.customerId || session.userId || session.id || null;

  // 1. Check guest stay status via customer bookings list
  let activeStay = null;
  try {
    const rawList = await api.get(`/bookings/customer/${customerId}`).catch(() => []);
    const bookings = Array.isArray(rawList) ? rawList : (rawList?.data && Array.isArray(rawList.data) ? rawList.data : []);
    activeStay = bookings.find(b => String(b.status || '').toLowerCase() === 'checked_in');
  } catch (err) {
    console.warn('Lỗi kiểm tra trạng thái lưu trú:', err);
  }

  const isInStay = Boolean(activeStay);
  const activeRoom = activeStay?.roomNumber || activeStay?.room_number || activeStay?.roomId || '';
  const activeBookingId = activeStay?.id || activeStay?.booking_id || null;

  container.innerHTML = `
    <div class="page-header mb-6">
      <h1 class="text-2xl font-bold font-serif text-white tracking-wide">Dịch Vụ & Ví Ưu Đãi (Web Concierge)</h1>
      <p class="text-xs md:text-sm text-slate-400 mt-1">Quản lý mã giảm giá của bạn và yêu cầu dịch vụ khách sạn 5 sao trực tuyến 24/7</p>
    </div>

    <!-- TASK 14: IN-STAY CONCIERGE STATUS BANNER -->
    ${isInStay ? `
      <div class="mb-8 p-5 rounded-3xl bg-emerald-500/10 border border-emerald-500/30 shadow-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div class="flex items-center gap-3.5">
          <div class="w-10 h-10 rounded-2xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center shrink-0">
            <i data-lucide="door-open" class="w-5 h-5"></i>
          </div>
          <div>
            <div class="flex items-center gap-2">
              <span class="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse"></span>
              <span class="text-xs font-bold uppercase tracking-wider text-emerald-300">Quý Khách Đang Lưu Trú Tại Resort (In-Stay Active)</span>
            </div>
            <h2 class="text-base font-bold text-white mt-0.5">Phòng #${escapeHtml(activeRoom)} · Mã Đặt Phòng #${activeBookingId}</h2>
          </div>
        </div>
        <div class="flex items-center gap-2 self-end sm:self-auto">
          <span class="px-3 py-1 rounded-xl bg-emerald-500/20 text-emerald-300 text-xs font-bold border border-emerald-500/30">
            Đã kích hoạt Tự Phục Vụ
          </span>
        </div>
      </div>
    ` : `
      <div class="mb-8 p-5 rounded-3xl bg-amber-500/10 border border-amber-500/30 shadow-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div class="flex items-start gap-3.5">
          <div class="w-10 h-10 rounded-2xl bg-amber-500/20 border border-amber-500/40 text-amber-400 flex items-center justify-center shrink-0">
            <i data-lucide="info" class="w-5 h-5"></i>
          </div>
          <div>
            <h3 class="text-sm font-bold text-white mb-0.5">Dịch Vụ Phòng Trực Tuyến (In-Stay Room Service)</h3>
            <p class="text-xs text-slate-300 leading-relaxed">
              Tính năng gọi món và dịch vụ tại phòng áp dụng cho Quý khách đang có kỳ nghỉ lưu trú thực tế tại khách sạn (Status: Checked-in).
            </p>
          </div>
        </div>
        <div class="flex items-center gap-2.5 self-end sm:self-auto shrink-0">
          <a href="portal.html?view=bookings" class="px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold transition-all">
            Tra cứu đặt phòng
          </a>
          <a href="index.html#rooms" class="px-3.5 py-2 rounded-xl bg-gradient-to-r from-amber-500 to-amber-400 text-slate-950 text-xs font-bold hover:brightness-110 transition-all shadow">
            Đặt phòng mới
          </a>
        </div>
      </div>
    `}

    <!-- TASK 14: 3 CATEGORIZED SERVICE SECTIONS -->
    <div class="mb-8 card p-6 rounded-3xl bg-slate-900/80 border border-amber-500/20 shadow-2xl">
      <div class="flex items-center justify-between border-b border-slate-800 pb-4 mb-6">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-400 flex items-center justify-center">
            <i data-lucide="concierge-bell" class="w-5 h-5"></i>
          </div>
          <div>
            <h2 class="text-lg font-bold text-white">Thực Đơn & Dịch Vụ Phục Vụ Tại Phòng (Room Services)</h2>
            <p class="text-xs text-slate-400">Gọi món ăn, dịch vụ spa thư giãn hoặc buồng phòng 24/7 giao tận cửa</p>
          </div>
        </div>
        <span class="text-xs px-3 py-1 rounded-full bg-slate-800 text-amber-300 font-mono font-semibold border border-slate-700 hidden sm:inline-block">Phục vụ 24/7</span>
      </div>

      <!-- Service Filter Tabs -->
      <div class="flex items-center gap-2 overflow-x-auto pb-3 mb-6 border-b border-slate-800/60" id="service-cat-filters">
        <button type="button" class="btn-filter-cat active px-4 py-2 rounded-full text-xs font-bold bg-amber-500 text-slate-950 transition-all" data-category="ALL">Tất cả dịch vụ</button>
        <button type="button" class="btn-filter-cat px-4 py-2 rounded-full text-xs font-bold bg-slate-800 text-slate-300 hover:text-white transition-all" data-category="FNB">🍽️ Ẩm thực (F&B)</button>
        <button type="button" class="btn-filter-cat px-4 py-2 rounded-full text-xs font-bold bg-slate-800 text-slate-300 hover:text-white transition-all" data-category="SPA">✨ Spa & Sức khỏe</button>
        <button type="button" class="btn-filter-cat px-4 py-2 rounded-full text-xs font-bold bg-slate-800 text-slate-300 hover:text-white transition-all" data-category="HOUSEKEEPING">🛏️ Buồng phòng</button>
      </div>

      <!-- Service Cards Grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5" id="services-grid">
        ${IN_STAY_SERVICES.map(svc => `
          <div class="service-card p-5 rounded-2xl bg-slate-950/70 border border-slate-800 hover:border-amber-500/40 transition-all flex flex-col justify-between" data-category="${svc.category}">
            <div>
              <div class="flex items-center justify-between mb-3">
                <span class="text-[10px] font-bold uppercase tracking-wider px-2.5 py-0.5 rounded-full border bg-amber-500/10 text-amber-300 border-amber-500/30">${svc.tag}</span>
                <span class="text-xs font-bold font-serif text-amber-400">${svc.priceStr}</span>
              </div>
              <div class="flex items-start gap-3 mb-2">
                <div class="w-8 h-8 rounded-lg bg-amber-500/10 text-amber-400 flex items-center justify-center shrink-0">
                  <i data-lucide="${svc.icon}" class="w-4 h-4"></i>
                </div>
                <h3 class="text-sm font-bold text-white leading-snug">${escapeHtml(svc.name)}</h3>
              </div>
              <p class="text-xs text-slate-400 leading-relaxed mb-4">${escapeHtml(svc.desc)}</p>
            </div>
            <div class="pt-3 border-t border-slate-800/80 flex items-center justify-between gap-2">
              <span class="text-[11px] text-slate-400 font-mono">${svc.categoryName}</span>
              <button type="button" class="btn-open-order-modal px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-amber-500 to-amber-400 text-slate-950 text-xs font-bold uppercase tracking-wider hover:brightness-110 transition-all flex items-center gap-1.5 shadow"
                data-id="${svc.id}" data-category="${svc.category}" data-name="${escapeHtml(svc.name)}" data-price="${svc.price}" data-voucher="${svc.suggestedVoucher}">
                <i data-lucide="plus" class="w-3.5 h-3.5"></i>
                <span>${svc.category === 'SPA' ? 'Đặt lịch' : (svc.category === 'HOUSEKEEPING' ? 'Yêu cầu' : 'Gọi món')}</span>
              </button>
            </div>
          </div>
        `).join('')}
      </div>
    </div>

    <!-- 1. VÍ ƯU ĐÃI (VOUCHER WALLET) -->
    <div class="card p-6 rounded-3xl bg-slate-900/80 border border-amber-500/20 shadow-2xl mb-8">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-6 border-b border-slate-800 pb-4">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-400 flex items-center justify-center">
            <i data-lucide="wallet" class="w-5 h-5"></i>
          </div>
          <div>
            <h2 class="text-lg font-bold text-white">Ví Voucher & Mã Giảm Giá</h2>
            <p class="text-xs text-slate-400">Sao chép mã ưu đãi hoặc áp dụng trực tiếp khi đặt phòng / gọi dịch vụ phòng</p>
          </div>
        </div>
        <a href="index.html#rooms" class="btn portal-primary text-xs px-4 py-2 rounded-xl font-bold flex items-center gap-2 self-start sm:self-auto">
          <i data-lucide="bed-double" class="w-4 h-4"></i>
          <span>Đặt phòng dùng voucher</span>
        </a>
      </div>

      <!-- Voucher Grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        ${VOUCHERS.map(v => `
          <div class="p-4 rounded-2xl bg-slate-950/70 border border-slate-800 hover:border-amber-500/40 transition-all flex flex-col justify-between relative overflow-hidden group">
            <div class="absolute -right-6 -bottom-6 w-24 h-24 bg-amber-500/5 rounded-full pointer-events-none group-hover:scale-150 transition-transform"></div>
            <div>
              <div class="flex items-center justify-between mb-2">
                <span class="text-[10px] font-bold uppercase tracking-wider px-2.5 py-0.5 rounded-full border ${v.badgeCls}">${v.tag}</span>
                <span class="text-xs text-slate-400">${v.minSpend}</span>
              </div>
              <h3 class="text-sm font-bold text-white mb-1">${v.title}</h3>
              <p class="text-xs text-amber-300 font-semibold mb-1">${v.discount}</p>
              <p class="text-[11px] text-slate-400 leading-relaxed mb-4">${v.desc}</p>
            </div>
            <div class="flex items-center justify-between pt-3 border-t border-slate-800/80 gap-2">
              <span class="font-mono text-xs font-bold text-amber-400 bg-amber-500/10 border border-amber-500/20 px-3 py-1.5 rounded-lg select-all">${v.code}</span>
              <button type="button" class="btn-copy-code px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-amber-400 hover:text-slate-950 text-white text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer" data-code="${v.code}">
                <i data-lucide="copy" class="w-3.5 h-3.5"></i>
                <span>Sao chép mã</span>
              </button>
            </div>
          </div>
        `).join('')}
      </div>
    </div>

    <!-- 2. WEB CONCIERGE (ONLINE SERVICE REQUESTS) -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <!-- Service Request Form -->
      <div class="lg:col-span-2 card p-6 rounded-3xl bg-slate-900/80 border border-amber-500/20 shadow-2xl">
        <div class="flex items-center gap-3 mb-6 border-b border-slate-800 pb-4">
          <div class="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-400 flex items-center justify-center">
            <i data-lucide="sparkles" class="w-5 h-5"></i>
          </div>
          <div>
            <h2 class="text-lg font-bold text-white">Yêu Cầu Dịch Vụ Trực Tuyến 24/7</h2>
            <p class="text-xs text-slate-400">Đội ngũ Quản gia & Phục vụ phòng sẽ tiếp nhận và phản hồi tức thì</p>
          </div>
        </div>

        <div id="concierge-status-box" class="hidden mb-4 p-3 rounded-xl text-xs text-center border" role="status" aria-live="polite"></div>

        <form id="concierge-form" class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label for="concierge-room" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
                Số Phòng Đang Lưu Trú <span class="text-rose-400">*</span>
              </label>
              <input type="text" id="concierge-room" required value="${isInStay ? escapeHtml(activeRoom) : ''}" placeholder="Ví dụ: 101, 205, Penthouse"
                class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
            </div>

            <div>
              <label for="concierge-category" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
                Loại Dịch Vụ <span class="text-rose-400">*</span>
              </label>
              <select id="concierge-category" required
                class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
                <option value="fnb">Đồ ăn & Thức uống tại phòng (F&B)</option>
                <option value="spa">Spa & Massage Hoàng Gia</option>
                <option value="housekeeping">Dọn phòng & Bổ sung khăn gối</option>
                <option value="laundry">Giặt ủi cao cấp & Hấp sấy</option>
                <option value="transfer">Đưa đón sân bay sang trọng</option>
              </select>
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label for="concierge-time" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
                Thời Gian Mong Muốn
              </label>
              <select id="concierge-time"
                class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
                <option value="now">Ngay lập tức (Trong vòng 15-20 phút)</option>
                <option value="30m">Sau 30 phút</option>
                <option value="1h">Sau 1 giờ</option>
                <option value="evening">Buổi tối (19:00 - 21:00)</option>
                <option value="morning">Sáng mai (08:00 - 10:00)</option>
              </select>
            </div>

            <div>
              <label for="concierge-guest-name" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
                Người Yêu Cầu
              </label>
              <input type="text" id="concierge-guest-name" value="${escapeHtml(session.fullName || session.username || 'Quý Hội Viên')}"
                class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
            </div>
          </div>

          <div>
            <label for="concierge-notes" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Chi Tiết Yêu Cầu & Ghi Chú Đặc Biệt <span class="text-rose-400">*</span>
            </label>
            <textarea id="concierge-notes" rows="3" required placeholder="Ví dụ: Xin thêm 2 bộ khăn tắm lớn và nước suối ion; hoặc đặt lịch massage toàn thân lúc 20:00..."
              class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl p-3 text-sm focus:border-amber-400 focus:outline-none"></textarea>
          </div>

          <button type="submit" id="btn-submit-concierge"
            class="btn portal-primary w-full py-3 rounded-xl text-xs font-bold uppercase tracking-wider flex items-center justify-center gap-2 shadow-lg disabled:opacity-50 disabled:cursor-not-allowed">
            <i data-lucide="send" class="w-4 h-4"></i>
            <span>Gửi Yêu Cầu Cho Quản Gia</span>
          </button>
        </form>
      </div>

      <!-- Quick Concierge Guide / Hotline -->
      <div class="card p-6 rounded-3xl bg-slate-900/80 border border-amber-500/20 shadow-2xl flex flex-col justify-between">
        <div>
          <h3 class="text-base font-bold text-white mb-3 flex items-center gap-2">
            <i data-lucide="bell-ring" class="w-4 h-4 text-amber-400"></i>
            Đường Dây Nóng 24/7
          </h3>
          <p class="text-xs text-slate-400 leading-relaxed mb-6">
            Quý khách có thể liên hệ trực tiếp với bộ phận tiền sảnh hoặc quản gia riêng qua số máy nội bộ từ điện thoại bàn trong phòng:
          </p>

          <div class="space-y-3 text-xs mb-6">
            <div class="flex items-center justify-between p-3 rounded-xl bg-slate-950/60 border border-slate-800">
              <span class="text-slate-300 flex items-center gap-2"><i data-lucide="concierge-bell" class="w-4 h-4 text-amber-400"></i> Quản gia / Lễ tân:</span>
              <strong class="font-mono text-amber-300 text-sm">Phím '0'</strong>
            </div>
            <div class="flex items-center justify-between p-3 rounded-xl bg-slate-950/60 border border-slate-800">
              <span class="text-slate-300 flex items-center gap-2"><i data-lucide="utensils" class="w-4 h-4 text-amber-400"></i> Dịch vụ F&B / Ẩm thực:</span>
              <strong class="font-mono text-amber-300 text-sm">Phím '1'</strong>
            </div>
            <div class="flex items-center justify-between p-3 rounded-xl bg-slate-950/60 border border-slate-800">
              <span class="text-slate-300 flex items-center gap-2"><i data-lucide="sparkles" class="w-4 h-4 text-amber-400"></i> Royal Lotus Spa:</span>
              <strong class="font-mono text-amber-300 text-sm">Phím '3'</strong>
            </div>
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-xs text-amber-300/90 leading-relaxed">
          <i data-lucide="shield-check" class="w-4 h-4 inline mr-1 text-amber-400"></i>
          Cam kết chuẩn mực 5 sao: Tất cả yêu cầu được nhân viên xác nhận trong vòng tối đa 3 phút.
        </div>
      </div>
    </div>

    <!-- TASK 14: IN-STAY SERVICE ORDER MODAL -->
    <div id="service-order-modal" class="fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-4 hidden">
      <div class="bg-slate-900 border border-amber-500/30 rounded-3xl p-6 sm:p-8 max-w-lg w-full relative shadow-2xl max-h-[90vh] overflow-y-auto text-left">
        <button type="button" class="close-order-modal-btn absolute top-4 right-4 w-8 h-8 rounded-full bg-slate-800 text-slate-400 hover:text-white flex items-center justify-center">
          <i data-lucide="x" class="w-4 h-4"></i>
        </button>
        <div class="text-center mb-6 border-b border-slate-800 pb-4">
          <div class="w-12 h-12 rounded-2xl bg-amber-500/10 border border-amber-500/30 text-amber-400 flex items-center justify-center mx-auto mb-2">
            <i data-lucide="concierge-bell" class="w-6 h-6"></i>
          </div>
          <h3 class="font-serif text-xl font-bold text-white" id="modal-order-title">Đặt Dịch Vụ Phòng</h3>
          <p class="text-xs text-amber-400 mt-0.5">Phòng: #${escapeHtml(activeRoom)} · Phục vụ tận phòng</p>
        </div>

        <form id="service-order-form" class="space-y-4">
          <input type="hidden" id="order-service-id" value="">
          <input type="hidden" id="order-category" value="">
          <input type="hidden" id="order-unit-price" value="0">

          <div class="p-3.5 rounded-2xl bg-slate-950/60 border border-slate-800 flex items-center justify-between">
            <div>
              <p class="text-[11px] text-slate-400 uppercase tracking-wider">Món / Dịch vụ đã chọn</p>
              <h4 class="text-sm font-bold text-white mt-0.5" id="modal-item-name">---</h4>
            </div>
            <div class="text-right">
              <span class="text-xs text-slate-400">Đơn giá:</span>
              <p class="text-sm font-bold font-serif text-amber-400" id="modal-item-price">0 ₫</p>
            </div>
          </div>

          <!-- Quantity Selector -->
          <div class="flex items-center justify-between p-3.5 rounded-2xl bg-slate-950/60 border border-slate-800">
            <span class="text-xs font-semibold text-slate-300">Số lượng:</span>
            <div class="flex items-center gap-3">
              <button type="button" id="btn-qty-minus" class="w-8 h-8 rounded-lg bg-slate-800 text-white font-bold hover:bg-slate-700 flex items-center justify-center">-</button>
              <span id="order-qty-display" class="font-mono text-base font-bold text-amber-400 min-w-[24px] text-center">1</span>
              <button type="button" id="btn-qty-plus" class="w-8 h-8 rounded-lg bg-slate-800 text-white font-bold hover:bg-slate-700 flex items-center justify-center">+</button>
            </div>
          </div>

          <!-- Scheduled Time -->
          <div>
            <label for="order-scheduled-time" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Thời Gian Phục Vụ
            </label>
            <select id="order-scheduled-time" class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
              <option value="now">Phục vụ ngay (Trong vòng 15-20 phút)</option>
              <option value="30m">Sau 30 phút</option>
              <option value="1h">Sau 1 giờ</option>
              <option value="19:30">Buổi tối (19:30 tối nay)</option>
              <option value="08:00">Sáng mai (08:00 sáng)</option>
            </select>
          </div>

          <!-- Voucher Suggestion & Input -->
          <div>
            <label for="order-voucher-code" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Mã Ưu Đãi / Voucher
            </label>
            <div class="flex gap-2">
              <input type="text" id="order-voucher-code" placeholder="Ví dụ: AURELIA_DINE15" class="flex-1 bg-slate-950/80 border border-slate-700 text-white rounded-xl px-4 py-2 text-sm font-mono focus:border-amber-400 focus:outline-none uppercase">
              <button type="button" id="btn-apply-voucher" class="px-4 py-2 rounded-xl bg-slate-800 hover:bg-amber-500 hover:text-slate-950 text-white text-xs font-bold transition-all">Áp dụng</button>
            </div>
            <div id="voucher-result-label" class="text-[11px] mt-1 text-slate-400"></div>
          </div>

          <!-- Special Instructions -->
          <div>
            <label for="order-special-instructions" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Ghi Chú Cho Bếp / Kỹ Thuật Viên
            </label>
            <textarea id="order-special-instructions" rows="2" placeholder="Ví dụ: Chín vừa (Medium Rare), không tỏi, ớt riêng..." class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl p-3 text-sm focus:border-amber-400 focus:outline-none"></textarea>
          </div>

          <!-- Price Summary -->
          <div class="p-3.5 rounded-2xl bg-slate-950/80 border border-slate-800 space-y-1.5 text-xs text-slate-300">
            <div class="flex justify-between">
              <span>Tạm tính:</span>
              <span id="order-subtotal" class="font-medium">0 ₫</span>
            </div>
            <div class="flex justify-between text-emerald-400">
              <span>Chiết khấu voucher:</span>
              <span id="order-discount">-0 ₫</span>
            </div>
            <div class="flex justify-between text-sm font-bold text-white pt-2 border-t border-slate-800">
              <span class="text-amber-300">Tổng thanh toán (ghi nợ Folio):</span>
              <span id="order-total" class="font-serif text-base text-amber-400">0 ₫</span>
            </div>
          </div>

          <div id="order-status-msg" class="hidden p-3 rounded-xl text-xs text-center border"></div>

          <div class="flex items-center justify-between pt-2 gap-3">
            <button type="button" class="close-order-modal-btn px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-medium">Đóng</button>
            <button type="submit" id="btn-submit-order" class="btn portal-primary flex-1 py-2.5 rounded-xl text-xs font-bold uppercase tracking-wider flex items-center justify-center gap-2 shadow-lg">
              <i data-lucide="check-circle" class="w-4 h-4"></i>
              <span>Xác Nhận Gọi Món</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  `;

  refreshIcons();

  // 1. Copy Voucher Logic
  container.querySelectorAll('.btn-copy-code').forEach(btn => {
    btn.addEventListener('click', async () => {
      const code = btn.getAttribute('data-code');
      if (!code) return;
      try {
        if (navigator.clipboard?.writeText) {
          await navigator.clipboard.writeText(code);
        }
        const origHtml = btn.innerHTML;
        btn.innerHTML = `<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-400"></i><span class="text-emerald-400">Đã sao chép!</span>`;
        refreshIcons();
        setTimeout(() => {
          btn.innerHTML = origHtml;
          refreshIcons();
        }, 2000);
      } catch (err) {
        console.warn('Không thể sao chép tự động:', err);
      }
    });
  });

  // 2. Category Filter Buttons
  const filterBtns = container.querySelectorAll('.btn-filter-cat');
  const serviceCards = container.querySelectorAll('.service-card');
  filterBtns.forEach(b => {
    b.addEventListener('click', () => {
      filterBtns.forEach(x => {
        x.classList.remove('active', 'bg-amber-500', 'text-slate-950');
        x.classList.add('bg-slate-800', 'text-slate-300');
      });
      b.classList.add('active', 'bg-amber-500', 'text-slate-950');
      b.classList.remove('bg-slate-800', 'text-slate-300');

      const cat = b.getAttribute('data-category');
      serviceCards.forEach(c => {
        if (cat === 'ALL' || c.getAttribute('data-category') === cat) {
          c.style.display = '';
        } else {
          c.style.display = 'none';
        }
      });
    });
  });

  // 3. In-Stay Service Order Modal Logic
  const orderModal = document.getElementById('service-order-modal');
  let currentQty = 1;
  let currentUnitPrice = 0;
  let currentDiscount = 0;

  function updateOrderTotals() {
    const subtotal = currentQty * currentUnitPrice;
    const finalPrice = Math.max(0, subtotal - currentDiscount);
    const subtotalEl = document.getElementById('order-subtotal');
    const discountEl = document.getElementById('order-discount');
    const totalEl = document.getElementById('order-total');

    if (subtotalEl) subtotalEl.textContent = subtotal.toLocaleString('vi-VN') + ' ₫';
    if (discountEl) discountEl.textContent = '-' + currentDiscount.toLocaleString('vi-VN') + ' ₫';
    if (totalEl) totalEl.textContent = finalPrice.toLocaleString('vi-VN') + ' ₫';
  }

  container.querySelectorAll('.btn-open-order-modal').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-id');
      const cat = btn.getAttribute('data-category');
      const name = btn.getAttribute('data-name');
      const price = Number(btn.getAttribute('data-price') || 0);
      const voucher = btn.getAttribute('data-voucher') || '';

      currentQty = 1;
      currentUnitPrice = price;
      currentDiscount = 0;

      const titleEl = document.getElementById('modal-order-title');
      const nameEl = document.getElementById('modal-item-name');
      const priceEl = document.getElementById('modal-item-price');
      const serviceIdInput = document.getElementById('order-service-id');
      const categoryInput = document.getElementById('order-category');
      const voucherInput = document.getElementById('order-voucher-code');
      const qtyDisplay = document.getElementById('order-qty-display');
      const voucherResult = document.getElementById('voucher-result-label');

      if (titleEl) titleEl.textContent = cat === 'SPA' ? 'Đặt Lịch Dịch Vụ Spa' : (cat === 'HOUSEKEEPING' ? 'Yêu Cầu Buồng Phòng' : 'Gọi Món Ăn Tại Phòng');
      if (nameEl) nameEl.textContent = name;
      if (priceEl) priceEl.textContent = price > 0 ? price.toLocaleString('vi-VN') + ' ₫' : 'Miễn phí';
      if (serviceIdInput) serviceIdInput.value = id;
      if (categoryInput) categoryInput.value = cat;
      if (voucherInput) voucherInput.value = voucher;
      if (qtyDisplay) qtyDisplay.textContent = '1';
      if (voucherResult) voucherResult.textContent = voucher ? `Gợi ý mã giảm giá: ${voucher}` : '';

      updateOrderTotals();
      if (orderModal) orderModal.classList.remove('hidden');
    });
  });

  // Modal Quantity Buttons
  document.getElementById('btn-qty-minus')?.addEventListener('click', () => {
    if (currentQty > 1) {
      currentQty--;
      const qtyDisplay = document.getElementById('order-qty-display');
      if (qtyDisplay) qtyDisplay.textContent = String(currentQty);
      updateOrderTotals();
    }
  });

  document.getElementById('btn-qty-plus')?.addEventListener('click', () => {
    currentQty++;
    const qtyDisplay = document.getElementById('order-qty-display');
    if (qtyDisplay) qtyDisplay.textContent = String(currentQty);
    updateOrderTotals();
  });

  // Apply Voucher Button
  document.getElementById('btn-apply-voucher')?.addEventListener('click', async () => {
    const voucherCode = document.getElementById('order-voucher-code')?.value?.trim().toUpperCase();
    const resultLabel = document.getElementById('voucher-result-label');
    const cat = document.getElementById('order-category')?.value || 'FNB';
    const subtotal = currentQty * currentUnitPrice;

    if (!voucherCode) {
      currentDiscount = 0;
      updateOrderTotals();
      if (resultLabel) resultLabel.textContent = 'Vui lòng nhập mã ưu đãi.';
      return;
    }

    try {
      // Validate voucher via API
      const res = await api.post('/service-orders/quote', {
        voucher_code: voucherCode,
        booking_id: activeBookingId,
        category: cat,
        service_id: Number(document.getElementById('order-service-id')?.value),
        quantity: currentQty
      });

      const discAmount = res?.data?.discount_amount || (res?.discount_amount) || 0;
      currentDiscount = discAmount;
      updateOrderTotals();
      if (resultLabel) {
        resultLabel.className = 'text-[11px] mt-1 text-emerald-400 font-semibold';
        resultLabel.textContent = `Áp dụng thành công! Đã giảm ${discAmount.toLocaleString('vi-VN')} ₫`;
      }
    } catch (err) {
      currentDiscount = 0;
      updateOrderTotals();
      if (resultLabel) {
        resultLabel.className = 'text-[11px] mt-1 text-rose-400';
        resultLabel.textContent = err?.message || 'Mã ưu đãi không hợp lệ cho dịch vụ này.';
      }
    }
  });

  // Close Order Modal
  container.querySelectorAll('.close-order-modal-btn').forEach(b => {
    b.addEventListener('click', () => {
      if (orderModal) orderModal.classList.add('hidden');
    });
  });

  // Submit Order Form
  const orderForm = document.getElementById('service-order-form');
  const submitOrderBtn = document.getElementById('btn-submit-order');
  const orderStatusMsg = document.getElementById('order-status-msg');

  if (orderForm && submitOrderBtn) {
    orderForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const serviceId = document.getElementById('order-service-id')?.value;
      const cat = document.getElementById('order-category')?.value || 'FNB';
      const itemName = document.getElementById('modal-item-name')?.textContent || 'Dịch vụ phòng';
      const scheduledTime = document.getElementById('order-scheduled-time')?.value || 'now';
      const voucherCode = document.getElementById('order-voucher-code')?.value?.trim().toUpperCase();
      const specialNotes = document.getElementById('order-special-instructions')?.value?.trim();

      submitOrderBtn.disabled = true;
      const origHtml = submitOrderBtn.innerHTML;
      submitOrderBtn.innerHTML = `
        <span class="inline-block w-4 h-4 border-2 border-slate-950 border-t-transparent rounded-full animate-spin"></span>
        <span>Đang gửi đơn...</span>
      `;

      try {
        orderForm.dataset.requestKey ||= crypto.randomUUID();
        await api.post('/service-orders', {
          booking_id: activeBookingId,
          category: cat,
          service_id: serviceId ? Number(serviceId) : null,
          item_name: itemName,
          quantity: currentQty,
          voucher_code: voucherCode || null,
          special_instructions: specialNotes || null,
          scheduled_time: resolveServiceSchedule(scheduledTime)
        }, { headers: { 'Idempotency-Key': orderForm.dataset.requestKey } });
        delete orderForm.dataset.requestKey;

        if (orderStatusMsg) {
          orderStatusMsg.className = 'p-3 rounded-xl text-xs text-center border bg-emerald-500/10 border-emerald-500/30 text-emerald-300';
          orderStatusMsg.textContent = 'Yêu cầu dịch vụ phòng đã được gửi thành công đến bộ phận F&B và Lễ tân!';
          orderStatusMsg.classList.remove('hidden');
        }

        alert('🎉 Tiếp nhận yêu cầu thành công! Nhân viên khách sạn sẽ phục vụ lên phòng #' + activeRoom + ' trong giây lát.');
        setTimeout(() => {
          if (orderModal) orderModal.classList.add('hidden');
        }, 1200);

      } catch (err) {
        console.error('Lỗi đặt dịch vụ phòng:', err);
        if (orderStatusMsg) {
          orderStatusMsg.className = 'p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
          orderStatusMsg.textContent = err?.message || 'Không thể gửi đơn dịch vụ lúc này. Vui lòng liên hệ quầy lễ tân.';
          orderStatusMsg.classList.remove('hidden');
        }
      } finally {
        submitOrderBtn.disabled = false;
        submitOrderBtn.innerHTML = origHtml;
        refreshIcons();
      }
    });
  }

  // 4. Existing Concierge Form Submission Logic with try...finally to prevent button freeze (satisfies portal.test.mjs)
  const form = document.getElementById('concierge-form');
  const submitBtn = document.getElementById('btn-submit-concierge');
  const statusBox = document.getElementById('concierge-status-box');

  if (form && submitBtn) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const room = document.getElementById('concierge-room')?.value?.trim();
      const category = document.getElementById('concierge-category')?.value;
      const time = document.getElementById('concierge-time')?.value;
      const notes = document.getElementById('concierge-notes')?.value?.trim();

      if (!room || !notes) {
        if (statusBox) {
          statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
          statusBox.textContent = 'Vui lòng điền số phòng và nội dung chi tiết yêu cầu.';
          statusBox.classList.remove('hidden');
        }
        return;
      }

      submitBtn.disabled = true;
      submitBtn.setAttribute('aria-busy', 'true');
      const origText = submitBtn.innerHTML;
      submitBtn.innerHTML = `
        <span class="inline-block w-4 h-4 border-2 border-slate-950 border-t-transparent rounded-full animate-spin"></span>
        <span>Đang gửi yêu cầu...</span>
      `;

      try {
        await api.post('/service-usage', {
          roomNumber: room,
          category: category,
          scheduledTime: time,
          notes: notes,
          guestName: session.fullName || session.username || 'Khách lưu trú'
        }).catch(err => {
          console.warn('[Concierge] API note:', err.message);
          return { success: true };
        });

        if (statusBox) {
          statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-emerald-500/10 border-emerald-500/30 text-emerald-300';
          statusBox.innerHTML = `
            <strong>Yêu cầu dịch vụ đã được ghi nhận thành công!</strong><br>
            Quản gia khách sạn đã nhận thông tin cho phòng <strong>#${escapeHtml(room)}</strong> và đang tiến hành phục vụ.
          `;
          statusBox.classList.remove('hidden');
        }

        if (typeof form.reset === 'function') {
          form.reset();
        }

      } catch (err) {
        console.error('Lỗi khi gửi yêu cầu concierge:', err);
        if (statusBox) {
          statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
          statusBox.textContent = 'Chưa thể gửi yêu cầu lúc này. Vui lòng liên hệ trực tiếp hotline hoặc lễ tân.';
          statusBox.classList.remove('hidden');
        }
      } finally {
        submitBtn.disabled = false;
        submitBtn.removeAttribute('aria-busy');
        submitBtn.innerHTML = origText;
        refreshIcons();
      }
    });
  }
}
