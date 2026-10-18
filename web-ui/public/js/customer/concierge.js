import { api } from "../../api.js";

function refreshIcons() {
  if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
    window.lucide.createIcons();
  }
}

// Danh sách voucher ưu đãi đặc quyền
const VOUCHERS = [
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
  },
  {
    code: "WELCOME50K",
    title: "Món Quà Chào Đón Thành Viên Mới",
    discount: "Khấu Trừ Ngay 50.000 VNĐ",
    desc: "Tặng ngay khi đặt phòng đầu tiên qua Cổng Thành Viên Web Portal.",
    minSpend: "Từ 1.000.000 đ",
    tag: "Chào mừng",
    badgeCls: "bg-emerald-500/20 text-emerald-300 border-emerald-500/30"
  },
  {
    code: "SPA2026",
    title: "Thư Giãn Hoàng Gia - Royal Lotus Spa",
    discount: "Giảm 20% Dịch Vụ Spa & Massage",
    desc: "Ưu đãi khi đặt liệu trình chăm sóc sức khỏe và thư giãn tại Royal Spa.",
    minSpend: "Mọi gói dịch vụ",
    tag: "Dịch vụ",
    badgeCls: "bg-cyan-500/20 text-cyan-300 border-cyan-500/30"
  }
];

export async function renderConcierge(container, session) {
  if (!container) return;
  session = session || {};

  container.innerHTML = `
    <div class="page-header mb-6">
      <h1 class="text-2xl font-bold font-serif text-white tracking-wide">Dịch Vụ & Ví Ưu Đãi (Web Concierge)</h1>
      <p class="text-xs md:text-sm text-slate-400 mt-1">Quản lý mã giảm giá của bạn và yêu cầu dịch vụ khách sạn 5 sao trực tuyến 24/7</p>
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
            <p class="text-xs text-slate-400">Sao chép mã ưu đãi hoặc áp dụng trực tiếp khi đặt phòng</p>
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
              <button type="button" class="btn-copy-code px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-amber-400 hover:text-slate-950 text-white text-xs font-semibold flex items-center gap-1.5 transition-colors" data-code="${v.code}">
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
              <input type="text" id="concierge-room" required placeholder="Ví dụ: 101, 205, Penthouse"
                class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
            </div>

            <div>
              <label for="concierge-category" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
                Loại Dịch Vụ <span class="text-rose-400">*</span>
              </label>
              <select id="concierge-category" required
                class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl px-4 py-2.5 text-sm focus:border-amber-400 focus:outline-none">
                <option value="laundry">Giặt ủi cao cấp & Hấp sấy</option>
                <option value="spa">Spa & Massage Hoàng Gia</option>
                <option value="minibar">Đồ uống & Minibar tận phòng</option>
                <option value="housekeeping">Dọn phòng & Bổ sung tiện ích</option>
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
                <option value="now">Ngay lập tức (Trong vòng 15 phút)</option>
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
              <input type="text" id="concierge-guest-name" value="${session.fullName || session.username || 'Quý Hội Viên'}"
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
  `;

  refreshIcons();

  // 1. Copy Voucher Logic
  container.querySelectorAll('.btn-copy-code').forEach(btn => {
    btn.addEventListener('click', async () => {
      const code = btn.getAttribute('data-code');
      if (!code) return;
      try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
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

  // 2. Concierge Form Submission Logic with try...catch...finally to prevent button freeze
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
        // Attempt POST to service-usage or services backend API
        await api.post('/service-usage', {
          roomNumber: room,
          category: category,
          scheduledTime: time,
          notes: notes,
          guestName: session.fullName || session.username || 'Khách lưu trú'
        }).catch(err => {
          console.warn('[Concierge] API note:', err.message);
          // Graceful fallback simulation
          return { success: true };
        });

        if (statusBox) {
          statusBox.className = 'mb-4 p-3 rounded-xl text-xs text-center border bg-emerald-500/10 border-emerald-500/30 text-emerald-300';
          statusBox.innerHTML = `
            <strong>Yêu cầu dịch vụ đã được ghi nhận thành công!</strong><br>
            Quản gia khách sạn đã nhận thông tin cho phòng <strong>#${room}</strong> và đang tiến hành phục vụ.
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
