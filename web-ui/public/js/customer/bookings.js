import { api } from "../../api.js";

function refreshIcons() {
  if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
    window.lucide.createIcons();
  }
}

const statusMap = {
  pending: { text: "Chờ xác nhận", cls: "bg-amber-500/10 text-amber-300 border-amber-500/30", dot: "#f59e0b" },
  confirmed: { text: "Đã đặt cọc / Xác nhận", cls: "bg-emerald-500/10 text-emerald-300 border-emerald-500/30", dot: "#10b981" },
  reserved: { text: "Đã giữ phòng", cls: "bg-emerald-500/10 text-emerald-300 border-emerald-500/30", dot: "#10b981" },
  checked_in: { text: "Đang lưu trú", cls: "bg-cyan-500/10 text-cyan-300 border-cyan-500/30", dot: "#06b6d4" },
  checked_out: { text: "Đã hoàn thành", cls: "bg-slate-500/10 text-slate-300 border-slate-500/30", dot: "#94a3b8" },
  cancelled: { text: "Đã hủy", cls: "bg-rose-500/10 text-rose-300 border-rose-500/30", dot: "#f43f5e" }
};

export async function renderBookings(container, session) {
  if (!container) return;
  session = session || {};
  const customerId = session.customerId || session.userId || session.id || 1;

  container.innerHTML = `
    <div class="page-header mb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold font-serif text-white tracking-wide">Lịch Sử Đặt Phòng</h1>
        <p class="text-xs md:text-sm text-slate-400 mt-1">Danh sách tất cả các kỳ nghỉ và giao dịch phòng của bạn tại Aurelia Grand Palace</p>
      </div>
      <div class="flex items-center gap-3">
        <a href="index.html#rooms" class="btn portal-primary text-xs px-4 py-2.5 rounded-xl font-bold flex items-center gap-2">
          <i data-lucide="plus-circle" class="w-4 h-4"></i>
          <span>Đặt phòng mới</span>
        </a>
      </div>
    </div>

    <!-- Booking List Card -->
    <div class="card p-6 rounded-3xl bg-slate-900/80 border border-amber-500/20 shadow-2xl">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div class="flex items-center gap-2">
          <span class="w-3 h-3 rounded-full bg-amber-400 animate-ping"></span>
          <span class="text-sm font-semibold text-slate-200" id="bookings-count-label">Đang tải lịch sử...</span>
        </div>
        <div class="flex items-center gap-3">
          <input type="text" id="filter-booking-input" placeholder="Tìm theo mã phòng hoặc ngày..." class="input input-sm bg-slate-950/80 border border-slate-700 text-xs px-3 py-1.5 rounded-xl text-white w-64 focus:border-amber-400 outline-none">
        </div>
      </div>

      <div class="table-container overflow-x-auto">
        <table class="table w-full text-left">
          <thead>
            <tr class="border-b border-slate-800 text-[11px] uppercase tracking-wider text-amber-400/90 font-bold">
              <th class="py-3 px-4">Mã Đặt Phòng</th>
              <th class="py-3 px-4">Hạng Phòng</th>
              <th class="py-3 px-4">Ngày Check-in</th>
              <th class="py-3 px-4">Ngày Check-out</th>
              <th class="py-3 px-4 text-right">Tổng Thanh Toán</th>
              <th class="py-3 px-4 text-center">Trạng Thái</th>
            </tr>
          </thead>
          <tbody id="bookings-table-body">
            <tr>
              <td colspan="6" class="text-center py-12 text-slate-400 text-xs">
                <div class="inline-block w-6 h-6 border-2 border-amber-400 border-t-transparent rounded-full animate-spin mb-2"></div>
                <p>Đang đồng bộ dữ liệu đặt phòng từ máy chủ...</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div id="bookings-pagination" class="mt-6 flex items-center justify-between text-xs text-slate-400 border-t border-slate-800/80 pt-4"></div>
    </div>
  `;
  refreshIcons();

  try {
    const rawList = await api.get(`/bookings/customer/${customerId}`).catch(() => []);
    let bookings = Array.isArray(rawList) ? rawList : [];

    const countLabel = document.getElementById('bookings-count-label');
    if (countLabel) {
      countLabel.textContent = `Tổng cộng: ${bookings.length} kỳ nghỉ`;
    }

    const tableBody = document.getElementById('bookings-table-body');
    const filterInput = document.getElementById('filter-booking-input');

    const renderRows = (list) => {
      if (!tableBody) return;
      if (!list || list.length === 0) {
        tableBody.innerHTML = `
          <tr>
            <td colspan="6" class="text-center py-16">
              <div class="w-16 h-16 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-amber-400 flex items-center justify-center mx-auto mb-3 shadow-inner">
                <i data-lucide="calendar-x" class="w-8 h-8"></i>
              </div>
              <h4 class="text-base font-bold text-white mb-1">Chưa có đơn đặt phòng nào</h4>
              <p class="text-xs text-slate-400 max-w-sm mx-auto mb-4">Bạn chưa có lịch sử đặt phòng tại khách sạn. Hãy lựa chọn hạng phòng yêu thích và trải nghiệm dịch vụ nghỉ dưỡng 5 sao ngay hôm nay!</p>
              <a href="index.html#rooms" class="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-amber-500 to-amber-400 text-slate-950 font-bold text-xs uppercase tracking-wider hover:brightness-110 shadow-md">
                <i data-lucide="bed-double" class="w-4 h-4"></i>
                <span>Khám phá phòng ngay</span>
              </a>
            </td>
          </tr>
        `;
        refreshIcons();
        return;
      }

      tableBody.innerHTML = list.map(b => {
        const st = statusMap[b.status] || { text: b.status || "Chờ xác nhận", cls: "bg-slate-700/30 text-slate-300 border-slate-700", dot: "#94a3b8" };
        const price = Number(b.totalPrice || b.priceAtBooking || 0).toLocaleString('vi-VN');
        const roomName = b.roomTypeName || b.roomName || (b.roomId ? `Phòng #${b.roomId}` : 'Hạng phòng cao cấp');
        const bookingCode = `BK-${String(b.id || '').padStart(5, '0')}`;

        return `
          <tr class="border-b border-slate-800/60 hover:bg-slate-800/30 transition-colors text-xs text-slate-200">
            <td class="py-4 px-4 font-mono font-bold text-amber-300">#${bookingCode}</td>
            <td class="py-4 px-4">
              <div class="flex items-center gap-2.5">
                <span class="w-7 h-7 rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-400 flex items-center justify-center shrink-0">
                  <i data-lucide="door-open" class="w-3.5 h-3.5"></i>
                </span>
                <span class="font-semibold text-white">${roomName}</span>
              </div>
            </td>
            <td class="py-4 px-4 text-slate-300 font-medium">${b.checkInDate || '---'}</td>
            <td class="py-4 px-4 text-slate-300 font-medium">${b.checkOutDate || '---'}</td>
            <td class="py-4 px-4 text-right font-bold text-white font-serif text-sm">${price} đ</td>
            <td class="py-4 px-4 text-center">
              <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-semibold border ${st.cls}">
                <span class="w-1.5 h-1.5 rounded-full" style="background:${st.dot};"></span>
                <span>${st.text}</span>
              </span>
            </td>
          </tr>
        `;
      }).join('');

      refreshIcons();
    };

    renderRows(bookings);

    if (filterInput) {
      filterInput.addEventListener('input', (e) => {
        const q = e.target.value.toLowerCase().trim();
        if (!q) {
          renderRows(bookings);
          return;
        }
        const filtered = bookings.filter(b => 
          String(b.id).includes(q) ||
          String(b.roomTypeName || '').toLowerCase().includes(q) ||
          String(b.checkInDate || '').toLowerCase().includes(q) ||
          String(b.checkOutDate || '').toLowerCase().includes(q) ||
          String(b.status || '').toLowerCase().includes(q)
        );
        renderRows(filtered);
      });
    }

  } catch (err) {
    console.error('Lỗi khi tải lịch sử đặt phòng:', err);
  }
}
