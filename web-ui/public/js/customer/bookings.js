import { api } from "../../api.js";

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
              <th class="py-3 px-4 text-center">Hành Động</th>
            </tr>
          </thead>
          <tbody id="bookings-table-body">
            <tr>
              <td colspan="7" class="text-center py-12 text-slate-400 text-xs">
                <div class="inline-block w-6 h-6 border-2 border-amber-400 border-t-transparent rounded-full animate-spin mb-2"></div>
                <p>Đang đồng bộ dữ liệu đặt phòng từ máy chủ...</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div id="bookings-pagination" class="mt-6 flex items-center justify-between text-xs text-slate-400 border-t border-slate-800/80 pt-4"></div>
    </div>

    <!-- Express Check-in QR Pass Modal -->
    <div id="qr-pass-modal" class="fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-4 hidden">
      <div class="bg-slate-900 border border-amber-500/30 rounded-3xl p-6 sm:p-8 max-w-sm w-full text-center relative shadow-2xl">
        <button type="button" class="close-modal-btn absolute top-4 right-4 w-8 h-8 rounded-full bg-slate-800 text-slate-400 hover:text-white flex items-center justify-center">
          <i data-lucide="x" class="w-4 h-4"></i>
        </button>
        <div class="w-12 h-12 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 text-cyan-400 flex items-center justify-center mx-auto mb-3">
          <i data-lucide="qr-code" class="w-6 h-6"></i>
        </div>
        <h3 class="font-serif text-lg font-bold text-white mb-1">Express Check-in Pass</h3>
        <p class="text-xs text-slate-400 mb-4">Aurelia Grand Palace 5★ Priority Access</p>
        
        <div class="p-4 bg-white rounded-2xl mx-auto w-52 h-52 flex flex-col items-center justify-center mb-4 shadow-inner">
          <img id="qr-pass-img" src="" alt="Express Check-in QR" class="w-44 h-44 object-contain">
        </div>

        <div class="text-xs text-slate-300 space-y-1 mb-4 text-left bg-slate-950/60 p-3 rounded-xl border border-slate-800">
          <div>Mã đặt phòng: <strong id="qr-pass-code" class="text-amber-400 font-mono">---</strong></div>
          <div>Khách hàng: <span id="qr-pass-guest" class="text-white font-medium">---</span></div>
          <div>Ngày nhận phòng: <span id="qr-pass-date" class="text-slate-300">---</span></div>
        </div>
        <p class="text-[11px] text-amber-300/80">Xuất trình mã này tại quầy lễ tân để nhận phòng ưu tiên không cần chờ đợi.</p>
      </div>
    </div>

    <!-- E-Folio Electronic Invoice Modal -->
    <div id="e-folio-modal" class="fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-4 hidden">
      <div class="bg-slate-900 border border-amber-500/30 rounded-3xl p-6 sm:p-8 max-w-lg w-full relative shadow-2xl max-h-[90vh] overflow-y-auto">
        <button type="button" class="close-modal-btn absolute top-4 right-4 w-8 h-8 rounded-full bg-slate-800 text-slate-400 hover:text-white flex items-center justify-center">
          <i data-lucide="x" class="w-4 h-4"></i>
        </button>
        <div class="text-center mb-6 border-b border-slate-800 pb-4">
          <div class="w-12 h-12 rounded-2xl bg-amber-500/10 border border-amber-500/30 text-amber-400 flex items-center justify-center mx-auto mb-2">
            <i data-lucide="receipt" class="w-6 h-6"></i>
          </div>
          <h3 class="font-serif text-xl font-bold text-white">Hóa Đơn Điện Tử E-Folio</h3>
          <p class="text-xs text-amber-400 mt-0.5">AURELIA GRAND PALACE LUXURY RESORT</p>
          <p id="folio-booking-ref" class="text-xs font-mono text-slate-400 mt-1">#BK-00000</p>
        </div>

        <div class="space-y-4 text-xs text-slate-300 mb-6" id="folio-details-body"></div>

        <div class="flex items-center justify-between pt-4 border-t border-slate-800 gap-3">
          <button type="button" class="close-modal-btn px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-medium">
            Đóng
          </button>
          <button type="button" onclick="window.print()" class="btn portal-primary px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5">
            <i data-lucide="printer" class="w-3.5 h-3.5"></i>
            <span>In Hóa Đơn</span>
          </button>
        </div>
      </div>
    </div>

    <!-- TASK 15: REVIEW-TO-REWARD 5-STAR RATING MODAL -->
    <div id="review-modal" class="fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-4 hidden">
      <div class="bg-slate-900 border border-amber-500/30 rounded-3xl p-6 sm:p-8 max-w-lg w-full relative shadow-2xl max-h-[90vh] overflow-y-auto text-left">
        <button type="button" class="close-review-modal-btn absolute top-4 right-4 w-8 h-8 rounded-full bg-slate-800 text-slate-400 hover:text-white flex items-center justify-center">
          <i data-lucide="x" class="w-4 h-4"></i>
        </button>

        <div class="text-center mb-6 border-b border-slate-800 pb-4">
          <div class="w-12 h-12 rounded-2xl bg-gradient-to-tr from-amber-600 to-amber-300 text-slate-950 flex items-center justify-center mx-auto mb-2 shadow-lg">
            <i data-lucide="award" class="w-6 h-6"></i>
          </div>
          <h3 class="font-serif text-xl font-bold text-white">Đánh Giá Kỳ Nghỉ & Nhận Thưởng</h3>
          <p class="text-xs text-amber-300 font-semibold mt-0.5">⭐ Nhận ngay 200 điểm thưởng hội viên tích lũy vào tài khoản</p>
          <div class="mt-2 text-xs text-slate-400" id="review-booking-summary">Kỳ nghỉ: ---</div>
        </div>

        <form id="stay-review-form" class="space-y-4">
          <input type="hidden" id="review-booking-id" value="">

          <!-- 4 Interactive Star-Rating Rows -->
          <div class="space-y-3">
            <!-- 1. Room Quality -->
            <div class="star-rating-row flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800">
              <div>
                <span class="text-xs font-bold text-white block">1. Phòng Nghỉ & Tiện Nghi</span>
                <span class="text-[10px] text-slate-400">Độ êm ái của giường, tiện ích phòng, view ban công</span>
              </div>
              <div class="flex items-center gap-1.5" id="stars-room" data-score="5">
                ${[1, 2, 3, 4, 5].map(i => `
                  <button type="button" class="star-btn p-1 text-amber-400" data-value="${i}">
                    <i data-lucide="star" class="w-4 h-4 fill-amber-400 text-amber-400"></i>
                  </button>
                `).join('')}
                <span class="score-label font-mono text-xs font-bold text-amber-300 ml-1">5/5</span>
              </div>
            </div>

            <!-- 2. Dining & F&B -->
            <div class="star-rating-row flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800">
              <div>
                <span class="text-xs font-bold text-white block">2. Ẩm Thực & Thức Uống</span>
                <span class="text-[10px] text-slate-400">Bữa sáng buffet, dịch vụ gọi món tại phòng, quầy bar</span>
              </div>
              <div class="flex items-center gap-1.5" id="stars-fnb" data-score="5">
                ${[1, 2, 3, 4, 5].map(i => `
                  <button type="button" class="star-btn p-1 text-amber-400" data-value="${i}">
                    <i data-lucide="star" class="w-4 h-4 fill-amber-400 text-amber-400"></i>
                  </button>
                `).join('')}
                <span class="score-label font-mono text-xs font-bold text-amber-300 ml-1">5/5</span>
              </div>
            </div>

            <!-- 3. Service Quality -->
            <div class="star-rating-row flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800">
              <div>
                <span class="text-xs font-bold text-white block">3. Dịch Vụ & Thái Độ Phục Vụ</span>
                <span class="text-[10px] text-slate-400">Thái độ chuyên nghiệp, chu đáo của lễ tân & quản gia</span>
              </div>
              <div class="flex items-center gap-1.5" id="stars-service" data-score="5">
                ${[1, 2, 3, 4, 5].map(i => `
                  <button type="button" class="star-btn p-1 text-amber-400" data-value="${i}">
                    <i data-lucide="star" class="w-4 h-4 fill-amber-400 text-amber-400"></i>
                  </button>
                `).join('')}
                <span class="score-label font-mono text-xs font-bold text-amber-300 ml-1">5/5</span>
              </div>
            </div>

            <!-- 4. Cleanliness -->
            <div class="star-rating-row flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800">
              <div>
                <span class="text-xs font-bold text-white block">4. Độ Sạch Sẽ & Không Gian</span>
                <span class="text-[10px] text-slate-400">Vệ sinh khăn ga gối, phòng tắm, hương thơm tinh dầu</span>
              </div>
              <div class="flex items-center gap-1.5" id="stars-cleanliness" data-score="5">
                ${[1, 2, 3, 4, 5].map(i => `
                  <button type="button" class="star-btn p-1 text-amber-400" data-value="${i}">
                    <i data-lucide="star" class="w-4 h-4 fill-amber-400 text-amber-400"></i>
                  </button>
                `).join('')}
                <span class="score-label font-mono text-xs font-bold text-amber-300 ml-1">5/5</span>
              </div>
            </div>
          </div>

          <!-- Overall Rating Display -->
          <div class="p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-between">
            <span class="text-xs font-semibold text-amber-200">Điểm tổng thể kỳ nghỉ:</span>
            <div class="flex items-center gap-2">
              <span class="text-lg font-bold font-serif text-amber-400" id="review-overall-display">5.0 / 5.0</span>
              <span class="text-xs px-2 py-0.5 rounded-md bg-amber-500/20 text-amber-300 font-bold" id="review-overall-text">Xuất sắc</span>
            </div>
          </div>

          <!-- Detailed Comment Input -->
          <div>
            <label for="review-comment" class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1">
              Nhận Xét Chi Tiết Của Quý Khách <span class="text-rose-400">*</span>
            </label>
            <textarea id="review-comment" rows="3" required placeholder="Chia sẻ cảm nhận chân thực của Quý khách về phòng nghỉ, đồ ăn và cung cách phục vụ..."
              class="w-full bg-slate-950/80 border border-slate-700 text-white rounded-xl p-3 text-sm focus:border-amber-400 focus:outline-none"></textarea>
          </div>

          <div id="review-status-box" class="hidden p-3 rounded-xl text-xs text-center border" role="status" aria-live="polite"></div>

          <div class="flex items-center justify-between pt-2 gap-3">
            <button type="button" class="close-review-modal-btn px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-medium">Đóng</button>
            <button type="submit" id="btn-submit-review"
              class="btn portal-primary flex-1 py-2.5 rounded-xl text-xs font-bold uppercase tracking-wider flex items-center justify-center gap-2 shadow-lg disabled:opacity-50">
              <i data-lucide="star" class="w-4 h-4 fill-slate-950"></i>
              <span>Gửi Đánh Giá & Nhận 200 Điểm</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  `;
  refreshIcons();

  // Close modals handling
  container.querySelectorAll('.close-modal-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.getElementById('qr-pass-modal')?.classList.add('hidden');
      document.getElementById('e-folio-modal')?.classList.add('hidden');
    });
  });

  const reviewModal = document.getElementById('review-modal');
  container.querySelectorAll('.close-review-modal-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      if (reviewModal) reviewModal.classList.add('hidden');
    });
  });

  try {
    const rawList = await api.get(`/bookings/customer/${customerId}`).catch(() => []);
    let bookings = Array.isArray(rawList) ? rawList : (rawList?.data && Array.isArray(rawList.data) ? rawList.data : []);

    // Load set of already reviewed booking IDs from local storage or session
    let reviewedBookingIds = new Set();
    try {
      const storedReviewed = JSON.parse(localStorage.getItem('aurelia_reviewed_bookings') || '[]');
      if (Array.isArray(storedReviewed)) {
        storedReviewed.forEach(id => reviewedBookingIds.add(Number(id)));
      }
    } catch {}

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
            <td colspan="7" class="text-center py-16">
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
        const isCheckedOut = String(b.status || '').toLowerCase() === 'checked_out';
        const isAlreadyReviewed = b.isReviewed || reviewedBookingIds.has(Number(b.id));

        return `
          <tr class="border-b border-slate-800/60 hover:bg-slate-800/30 transition-colors text-xs text-slate-200">
            <td class="py-4 px-4 font-mono font-bold text-amber-300">#${bookingCode}</td>
            <td class="py-4 px-4">
              <div class="flex items-center gap-2.5">
                <span class="w-7 h-7 rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-400 flex items-center justify-center shrink-0">
                  <i data-lucide="door-open" class="w-3.5 h-3.5"></i>
                </span>
                <span class="font-semibold text-white">${escapeHtml(roomName)}</span>
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
            <td class="py-4 px-4 text-center">
              <div class="flex items-center justify-center gap-1.5 flex-wrap">
                ${b.status === 'pending' ? `
                  <button type="button" class="btn-cancel-booking px-2 py-1 rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 border border-rose-500/30 text-[10px] font-semibold transition-colors flex items-center gap-1" data-id="${b.id}" title="Hủy đặt phòng này">
                    <i data-lucide="x-circle" class="w-3 h-3"></i>
                    <span>Hủy phòng</span>
                  </button>
                ` : ''}
                ${['confirmed', 'reserved', 'checked_in'].includes(b.status) ? `
                  <button type="button" class="btn-qr-pass px-2 py-1 rounded-lg bg-cyan-500/10 hover:bg-cyan-500/20 text-cyan-300 border border-cyan-500/30 text-[10px] font-semibold transition-colors flex items-center gap-1" data-id="${b.id}" title="Xem mã nhận phòng nhanh">
                    <i data-lucide="qr-code" class="w-3 h-3"></i>
                    <span>QR Pass</span>
                  </button>
                ` : ''}
                <button type="button" class="btn-view-folio px-2 py-1 rounded-lg bg-amber-500/10 hover:bg-amber-500/20 text-amber-300 border border-amber-500/30 text-[10px] font-semibold transition-colors flex items-center gap-1" data-id="${b.id}" title="Xem chi tiết hóa đơn điện tử">
                  <i data-lucide="receipt" class="w-3 h-3"></i>
                  <span>E-Folio</span>
                </button>

                <!-- TASK 15: REVIEW-TO-REWARD BUTTON FOR CHECKED_OUT BOOKINGS -->
                ${isCheckedOut ? (
                  isAlreadyReviewed ? `
                    <span class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-300 border border-emerald-500/30 text-[10px] font-semibold">
                      <i data-lucide="check" class="w-3 h-3 text-emerald-400"></i>
                      <span>Đã đánh giá</span>
                    </span>
                  ` : `
                    <button type="button" class="btn-review-booking px-2.5 py-1 rounded-lg bg-gradient-to-r from-amber-500 to-amber-400 text-slate-950 font-bold text-[11px] uppercase tracking-wider hover:brightness-110 shadow-md flex items-center gap-1"
                      data-id="${b.id}" data-room="${escapeHtml(roomName)}" data-dates="${b.checkInDate || ''} - ${b.checkOutDate || ''}" title="Đánh giá kỳ nghỉ nhận ngay 200 điểm thưởng">
                      <i data-lucide="star" class="w-3.5 h-3.5 fill-slate-950"></i>
                      <span>⭐ Đánh giá nhận 200 điểm</span>
                    </button>
                  `
                ) : ''}
              </div>
            </td>
          </tr>
        `;
      }).join('');

      refreshIcons();

      // Attach row action handlers
      // 1. Cancel Booking Handler
      tableBody.querySelectorAll('.btn-cancel-booking').forEach(btn => {
        btn.addEventListener('click', async () => {
          const id = btn.getAttribute('data-id');
          if (!id) return;
          if (!confirm(`Quý khách có chắc chắn muốn hủy đơn đặt phòng #${id} không?`)) return;

          btn.disabled = true;
          const origHtml = btn.innerHTML;
          btn.innerHTML = `<span class="inline-block w-3 h-3 border border-rose-400 border-t-transparent rounded-full animate-spin"></span>`;

          try {
            await api.post(`/bookings/${id}/cancel`, {}).catch(() => api.delete(`/bookings/${id}`));
            const found = bookings.find(x => String(x.id) === String(id));
            if (found) found.status = 'cancelled';
            renderRows(bookings);
            alert(`Đã hủy đặt phòng #${id} thành công.`);
          } catch (err) {
            console.error('Lỗi khi hủy đặt phòng:', err);
            alert('Không thể hủy đặt phòng. Vui lòng liên hệ quầy lễ tân để được hỗ trợ.');
          } finally {
            btn.disabled = false;
            btn.innerHTML = origHtml;
            refreshIcons();
          }
        });
      });

      // 2. Express QR Pass Handler
      tableBody.querySelectorAll('.btn-qr-pass').forEach(btn => {
        btn.addEventListener('click', () => {
          const id = btn.getAttribute('data-id');
          const booking = bookings.find(x => String(x.id) === String(id));
          if (!booking) return;

          const modal = document.getElementById('qr-pass-modal');
          const qrImg = document.getElementById('qr-pass-img');
          const qrCode = document.getElementById('qr-pass-code');
          const qrGuest = document.getElementById('qr-pass-guest');
          const qrDate = document.getElementById('qr-pass-date');

          const codeStr = `BK-${String(booking.id || '').padStart(5, '0')}`;
          if (qrCode) qrCode.textContent = `#${codeStr}`;
          if (qrGuest) qrGuest.textContent = session.fullName || session.username || 'Quý Hội Viên';
          if (qrDate) qrDate.textContent = `${booking.checkInDate || 'Hôm nay'} (Check-in: 14:00)`;

          const qrData = encodeURIComponent(`AURELIA-PMS-CHECKIN:${booking.id}:${booking.roomId || 101}:${booking.checkInDate || ''}`);
          if (qrImg) {
            qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${qrData}`;
          }

          if (modal) modal.classList.remove('hidden');
        });
      });

      // 3. E-Folio Electronic Invoice Handler
      tableBody.querySelectorAll('.btn-view-folio').forEach(btn => {
        btn.addEventListener('click', () => {
          const id = btn.getAttribute('data-id');
          const booking = bookings.find(x => String(x.id) === String(id));
          if (!booking) return;

          const modal = document.getElementById('e-folio-modal');
          const bookingRef = document.getElementById('folio-booking-ref');
          const body = document.getElementById('folio-details-body');

          const codeStr = `BK-${String(booking.id || '').padStart(5, '0')}`;
          if (bookingRef) bookingRef.textContent = `Mã đặt phòng: #${codeStr}`;

          const total = Number(booking.totalPrice || 0);
          const roomCharge = Math.round(total * 0.87);
          const vat = Math.round(roomCharge * 0.08);
          const serviceFee = Math.round(roomCharge * 0.05);

          if (body) {
            body.innerHTML = `
              <div class="flex justify-between py-2 border-b border-slate-800">
                <span class="text-slate-400">Khách lưu trú:</span>
                <span class="font-bold text-white">${escapeHtml(session.fullName || session.username || 'Quý khách')}</span>
              </div>
              <div class="flex justify-between py-2 border-b border-slate-800">
                <span class="text-slate-400">Hạng phòng:</span>
                <span class="font-medium text-amber-300">${escapeHtml(booking.roomTypeName || booking.roomName || 'Phòng #' + booking.roomId)}</span>
              </div>
              <div class="flex justify-between py-2 border-b border-slate-800">
                <span class="text-slate-400">Thời gian lưu trú:</span>
                <span>${booking.checkInDate || '---'} đến ${booking.checkOutDate || '---'}</span>
              </div>
              <div class="flex justify-between py-2 border-b border-slate-800">
                <span class="text-slate-400">Tiền phòng (Room Subtotal):</span>
                <span>${roomCharge.toLocaleString('vi-VN')} đ</span>
              </div>
              <div class="flex justify-between py-2 border-b border-slate-800">
                <span class="text-slate-400">Thuế GTGT (VAT 8%):</span>
                <span>${vat.toLocaleString('vi-VN')} đ</span>
              </div>
              <div class="flex justify-between py-2 border-b border-slate-800">
                <span class="text-slate-400">Phí phục vụ 5★ (Service Charge 5%):</span>
                <span>${serviceFee.toLocaleString('vi-VN')} đ</span>
              </div>
              <div class="flex justify-between py-3 text-sm font-bold border-t border-amber-500/30">
                <span class="text-amber-300">Tổng cộng (Total Due):</span>
                <span class="text-amber-400 font-serif text-base">${total.toLocaleString('vi-VN')} đ</span>
              </div>
              <div class="flex justify-between py-2 bg-slate-950/70 p-3 rounded-xl border border-slate-800">
                <span class="text-slate-400">Trạng thái thanh toán:</span>
                <span class="font-bold uppercase tracking-wider text-emerald-400">ĐÃ XÁC NHẬN</span>
              </div>
            `;
          }

          if (modal) modal.classList.remove('hidden');
        });
      });

      // 4. TASK 15: Review-to-Reward Button Handler
      tableBody.querySelectorAll('.btn-review-booking').forEach(btn => {
        btn.addEventListener('click', () => {
          const id = btn.getAttribute('data-id');
          const room = btn.getAttribute('data-room');
          const dates = btn.getAttribute('data-dates');

          const modal = document.getElementById('review-modal');
          const bookingIdInput = document.getElementById('review-booking-id');
          const summaryEl = document.getElementById('review-booking-summary');
          const statusBox = document.getElementById('review-status-box');

          if (bookingIdInput) bookingIdInput.value = id;
          if (summaryEl) summaryEl.textContent = `Kỳ nghỉ tại ${room} (${dates})`;
          if (statusBox) statusBox.classList.add('hidden');

          // Reset scores to 5
          ['room', 'fnb', 'service', 'cleanliness'].forEach(cat => {
            const row = document.getElementById(`stars-${cat}`);
            if (row) {
              row.setAttribute('data-score', '5');
              row.querySelectorAll('.star-btn').forEach(s => {
                const icon = s.querySelector('svg');
                if (icon) {
                  icon.classList.add('fill-amber-400', 'text-amber-400');
                  icon.classList.remove('text-slate-600');
                }
              });
              const label = row.querySelector('.score-label');
              if (label) label.textContent = '5/5';
            }
          });

          updateOverallDisplay();
          if (modal) modal.classList.remove('hidden');
          refreshIcons();
        });
      });
    };

    renderRows(bookings);

    // Interactive Star Hover & Click Logic
    function updateOverallDisplay() {
      const room = Number(document.getElementById('stars-room')?.getAttribute('data-score') || 5);
      const fnb = Number(document.getElementById('stars-fnb')?.getAttribute('data-score') || 5);
      const service = Number(document.getElementById('stars-service')?.getAttribute('data-score') || 5);
      const cleanliness = Number(document.getElementById('stars-cleanliness')?.getAttribute('data-score') || 5);

      const avg = ((room + fnb + service + cleanliness) / 4.0).toFixed(1);
      const display = document.getElementById('review-overall-display');
      const text = document.getElementById('review-overall-text');

      if (display) display.textContent = `${avg} / 5.0`;
      if (text) {
        if (avg >= 4.8) text.textContent = 'Xuất sắc';
        else if (avg >= 4.0) text.textContent = 'Rất tốt';
        else if (avg >= 3.0) text.textContent = 'Hài lòng';
        else text.textContent = 'Cần cải thiện';
      }
    }

    ['room', 'fnb', 'service', 'cleanliness'].forEach(cat => {
      const row = document.getElementById(`stars-${cat}`);
      if (!row) return;

      row.querySelectorAll('.star-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          const val = Number(btn.getAttribute('data-value') || 5);
          row.setAttribute('data-score', String(val));

          row.querySelectorAll('.star-btn').forEach(s => {
            const sVal = Number(s.getAttribute('data-value') || 0);
            const icon = s.querySelector('svg');
            if (icon) {
              if (sVal <= val) {
                icon.classList.add('fill-amber-400', 'text-amber-400');
                icon.classList.remove('text-slate-600');
              } else {
                icon.classList.remove('fill-amber-400', 'text-amber-400');
                icon.classList.add('text-slate-600');
              }
            }
          });

          const label = row.querySelector('.score-label');
          if (label) label.textContent = `${val}/5`;

          updateOverallDisplay();
        });
      });
    });

    // Submit Review Form
    const reviewForm = document.getElementById('stay-review-form');
    const submitReviewBtn = document.getElementById('btn-submit-review');
    const reviewStatusBox = document.getElementById('review-status-box');

    if (reviewForm && submitReviewBtn) {
      reviewForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const bookingId = Number(document.getElementById('review-booking-id')?.value);
        const ratingRoom = Number(document.getElementById('stars-room')?.getAttribute('data-score') || 5);
        const ratingFnb = Number(document.getElementById('stars-fnb')?.getAttribute('data-score') || 5);
        const ratingService = Number(document.getElementById('stars-service')?.getAttribute('data-score') || 5);
        const ratingCleanliness = Number(document.getElementById('stars-cleanliness')?.getAttribute('data-score') || 5);
        const ratingOverall = Number(((ratingRoom + ratingFnb + ratingService + ratingCleanliness) / 4.0).toFixed(1));
        const comment = document.getElementById('review-comment')?.value?.trim();

        if (!comment) {
          if (reviewStatusBox) {
            reviewStatusBox.className = 'p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
            reviewStatusBox.textContent = 'Vui lòng nhập lời nhận xét chi tiết về kỳ nghỉ của Quý khách.';
            reviewStatusBox.classList.remove('hidden');
          }
          return;
        }

        submitReviewBtn.disabled = true;
        const origHtml = submitReviewBtn.innerHTML;
        submitReviewBtn.innerHTML = `
          <span class="inline-block w-4 h-4 border-2 border-slate-950 border-t-transparent rounded-full animate-spin"></span>
          <span>Đang gửi đánh giá...</span>
        `;

        try {
          const resp = await api.post('/reviews', {
            booking_id: bookingId,
            rating_room: ratingRoom,
            rating_fnb: ratingFnb,
            rating_service: ratingService,
            rating_cleanliness: ratingCleanliness,
            rating_overall: ratingOverall,
            comment: comment
          });

          // Save to reviewed bookings
          reviewedBookingIds.add(bookingId);
          try {
            localStorage.setItem('aurelia_reviewed_bookings', JSON.stringify([...reviewedBookingIds]));
          } catch {}

          // Update loyalty point balance across UI elements
          try {
            const pointEls = document.querySelectorAll('#user-points, #member-points, #dashboard-points, .loyalty-points-val, [data-points-balance]');
            pointEls.forEach(el => {
              const current = parseInt(el.textContent.replace(/\D/g, ''), 10) || 0;
              el.textContent = (current + 200).toLocaleString('vi-VN');
            });
            const sessionData = localStorage.getItem('auth_session');
            if (sessionData) {
              const parsed = JSON.parse(sessionData);
              parsed.points = (parsed.points || 0) + 200;
              localStorage.setItem('auth_session', JSON.stringify(parsed));
            }
          } catch {}

          if (reviewStatusBox) {
            reviewStatusBox.className = 'p-3 rounded-xl text-xs text-center border bg-emerald-500/10 border-emerald-500/30 text-emerald-300';
            reviewStatusBox.textContent = '🎉 Cảm ơn Quý khách đã gửi đánh giá! Quý khách đã được cộng ngay 200 điểm thưởng hội viên.';
            reviewStatusBox.classList.remove('hidden');
          }

          if (typeof alert !== 'undefined') {
            alert('🎉 Chúc mừng Quý khách! Đánh giá kỳ nghỉ thành công và đã nhận ngay 200 điểm thưởng hội viên.');
          }

          // Re-render table rows with updated Reviewed status
          renderRows(bookings);

          setTimeout(() => {
            if (reviewModal) reviewModal.classList.add('hidden');
          }, 1200);

        } catch (err) {
          console.error('Lỗi gửi đánh giá:', err);
          if (reviewStatusBox) {
            reviewStatusBox.className = 'p-3 rounded-xl text-xs text-center border bg-rose-500/10 border-rose-500/30 text-rose-300';
            reviewStatusBox.textContent = err?.message || 'Không thể gửi đánh giá lúc này. Vui lòng thử lại sau.';
            reviewStatusBox.classList.remove('hidden');
          }
        } finally {
          submitReviewBtn.disabled = false;
          submitReviewBtn.innerHTML = origHtml;
          refreshIcons();
        }
      });
    }

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
