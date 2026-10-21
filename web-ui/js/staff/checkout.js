
import { api, getApiBaseUrl } from "../../api.js";
import { escapeHtml } from "../shared/ui.js";
import { renderPaginationComponent } from "../shared/pagination.js";

export async function renderCheckout(container, session) {
    container.innerHTML = `
        <div class="checkout-view">
            <div class="page-header">
                <div>
                    <h1>Thanh toán & trả phòng</h1>
                    <p>Tra cứu khách đang lưu trú để thực hiện thanh toán và trả phòng.</p>
                </div>
                <span id="checkout-count" class="badge checkout-count" role="status">Đang tải danh sách</span>
            </div>
            <section class="card checkout-toolbar" aria-label="Tra cứu phòng đang lưu trú">
                <div class="card-body">
                    <div class="checkout-search-field">
                        <label for="checkout-search">Tìm phòng hoặc khách hàng</label>
                        <div class="checkout-search-input">
                            <i data-lucide="search" aria-hidden="true"></i>
                            <input id="checkout-search" class="input form-input" type="search" placeholder="Số phòng, tên khách, mã đặt phòng…" autocomplete="off">
                        </div>
                    </div>
                    <button id="checkout-refresh" class="btn btn-secondary" type="button">
                        <i data-lucide="refresh-cw" aria-hidden="true"></i><span>Làm mới</span>
                    </button>
                </div>
            </section>
            <div id="checkout-grid" class="checkout-grid" aria-label="Danh sách phòng đang lưu trú" aria-busy="true"></div>
            <div id="pagination-checkout"></div>
        </div>
    `;

    const grid = container.querySelector('#checkout-grid');
    const search = container.querySelector('#checkout-search');
    const refresh = container.querySelector('#checkout-refresh');
    const count = container.querySelector('#checkout-count');
    const pagination = container.querySelector('#pagination-checkout');
    const refreshIcons = () => window.lucide?.createIcons?.();
    let activeBookings = [];
    let currentProcessBooking = null;
    let currentPage = 1;
    let itemsPerPage = 10;

    const formatCurrency = (amount) => {
        return amount != null ? Number(amount).toLocaleString('vi-VN') + ' đ' : '0 đ';
    };

    const formatDate = (dateStr) => {
        const date = new Date(dateStr || '');
        if (!Number.isFinite(date.getTime())) return 'Chưa có thông tin';
        return date.toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' }) + ' · ' + date.toLocaleTimeString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', hour12: false });
    };

    const renderCards = () => {
        const q = search.value.trim().toLocaleLowerCase('vi-VN');
        const filtered = activeBookings.filter(b => [b.roomNumber, b.customerName, b.id].some(value => String(value ?? '').toLocaleLowerCase('vi-VN').includes(q)));
        count.textContent = q ? `${filtered.length} / ${activeBookings.length} phòng` : `${activeBookings.length} phòng đang lưu trú`;
        grid.replaceChildren();
        pagination.replaceChildren();
        if (!filtered.length) {
            grid.innerHTML = `
                <section class="card checkout-empty">
                    <div class="card-body">
                        <i data-lucide="${q ? 'search-x' : 'door-open'}" aria-hidden="true"></i>
                        <h2 class="card-title">${q ? 'Không tìm thấy phòng phù hợp' : 'Chưa có phòng đang lưu trú'}</h2>
                        <p>${q ? 'Thử tìm theo số phòng, tên khách hoặc mã đặt phòng khác.' : 'Các phòng có khách đang ở sẽ xuất hiện tại đây.'}</p>
                        ${q ? '<button class="btn btn-secondary" id="checkout-clear-search" type="button">Xóa tìm kiếm</button>' : ''}
                    </div>
                </section>`;
            container.querySelector('#checkout-clear-search')?.addEventListener('click', () => { search.value = ''; currentPage = 1; renderCards(); search.focus(); });
            refreshIcons();
            return;
        }
        currentPage = Math.min(currentPage, Math.max(1, Math.ceil(filtered.length / itemsPerPage)));
        filtered.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage).forEach(b => {
            const card = document.createElement('article');
            card.className = 'card checkout-card';
            card.innerHTML = `
                <div class="card-body">
                    <div class="checkout-card-heading">
                        <div class="checkout-room-heading">
                            <span class="checkout-room-icon"><i data-lucide="door-open" aria-hidden="true"></i></span>
                            <div><h2 class="card-title">Phòng ${escapeHtml(b.roomNumber || 'N/A')}</h2><span class="checkout-booking-id">Đặt phòng #${escapeHtml(b.id)}</span></div>
                        </div>
                        <span class="badge room-badge badge-occupied">Đang lưu trú</span>
                    </div>
                    <dl class="checkout-details">
                        <div class="checkout-guest"><dt>Khách hàng</dt><dd>${escapeHtml(b.customerName || 'Khách vãng lai')}</dd></div>
                        <div><dt>Nhận phòng</dt><dd>${formatDate(b.checkInDate)}</dd></div>
                        <div><dt>Trả phòng dự kiến</dt><dd>${formatDate(b.checkOutDate)}</dd></div>
                    </dl>
                    <div class="card-actions checkout-card-actions">
                        <div class="checkout-amount"><span>Giá trị đặt phòng</span><strong>${formatCurrency(b.totalPrice)}</strong></div>
                        <button class="btn btn-primary btn-pay" type="button" data-id="${escapeHtml(b.id)}" aria-label="Thanh toán phòng ${escapeHtml(b.roomNumber || 'N/A')}, đơn ${escapeHtml(b.id)}">
                            <span>Thanh toán</span><i data-lucide="arrow-right" aria-hidden="true"></i>
                        </button>
                    </div>
                </div>`;
            card.querySelector('.btn-pay').addEventListener('click', e => openModal(e.currentTarget.dataset.id));
            grid.appendChild(card);
        });
        renderPaginationComponent('pagination-checkout', filtered.length, itemsPerPage, currentPage,
            page => { currentPage = page; renderCards(); },
            size => { itemsPerPage = size; currentPage = 1; renderCards(); });
        refreshIcons();
    };

    const loadData = async () => {
        refresh.disabled = true;
        search.disabled = true;
        grid.setAttribute('aria-busy', 'true');
        count.textContent = 'Đang tải danh sách';
        pagination.replaceChildren();
        grid.innerHTML = '<div class="checkout-loading" role="status"><span class="loading loading-spinner" aria-hidden="true"></span><span>Đang tải phòng đang lưu trú…</span></div>';
        try {
            const [response, rooms, customers] = await Promise.all([api.get('/bookings'), api.get('/rooms'), api.get('/customers')]);
            if (!grid.isConnected) return;
            activeBookings = response.filter(b => b.status === 'checked_in').map(b => {
                const room = rooms.find(r => r.id === b.roomId);
                const customer = customers.find(c => c.id === b.customerId);
                return { ...b, roomNumber: room?.roomNumber || 'N/A', customerName: customer?.fullName || 'Khách vãng lai' };
            });
            currentPage = 1;
            renderCards();
            search.disabled = false;
        } catch (error) {
            if (!grid.isConnected) return;
            count.textContent = 'Chưa tải được danh sách';
            grid.innerHTML = '<div class="alert alert-error checkout-load-error" role="alert"><div><strong>Không thể tải danh sách phòng</strong><p>Vui lòng kiểm tra kết nối và thử lại.</p></div><button class="btn btn-secondary" id="checkout-retry" type="button">Thử lại</button></div>';
            container.querySelector('#checkout-retry').addEventListener('click', loadData);
        } finally {
            if (grid.isConnected) {
                refresh.disabled = false;
                grid.setAttribute('aria-busy', 'false');
            }

        }
    };
    search.addEventListener('input', () => { currentPage = 1; renderCards(); });
    refresh.addEventListener('click', loadData);
    refreshIcons();

    const openModal = bookingId => {
        const b = activeBookings.find(booking => String(booking.id) === String(bookingId));
        if (!b) return;
        currentProcessBooking = b;
        const modal = byId('global-modal'), content = byId('global-modal-content');
        const close = () => { modal.classList.remove('active'); modal.setAttribute('aria-hidden', 'true'); };
        content.innerHTML = `<section class="sc-dialog">
            <div class="sc-modal-head"><div><h2>Xác nhận thanh toán</h2><p>Kiểm tra thông tin và số tiền trước khi xác nhận</p></div><button type="button" class="sc-close" id="sc-modal-close" aria-label="Đóng">×</button></div>
            <div class="sc-modal-guest"><div><strong>${escapeHtml(b.customerName)}</strong><small>Đặt phòng #${escapeHtml(b.id)}</small></div><div><strong>Phòng ${escapeHtml(b.roomNumber)}</strong><small>Trả dự kiến: ${dateLabel(b.checkOutDate)}</small></div></div>
            <div id="modal-checkout-form">
                <div class="sc-balance"><label for="pay-amount">Số tiền thu (VNĐ)</label><input class="sc-amount-input" id="pay-amount" type="number" min="0" value="${Number(b.totalPrice) || 0}" required></div>
                <label for="pay-method">Phương thức thanh toán</label><select id="pay-method"><option value="Cash">Tiền mặt</option><option value="Credit Card">Thẻ tín dụng (POS)</option><option value="Chuyển khoản">Chuyển khoản ngân hàng</option></select>
                <div id="qr-code-section" class="sc-qr" style="display:none"><p class="sc-note">Quét mã VietQR để thanh toán</p><img id="qr-image" src="" alt="Mã VietQR thanh toán" width="200" height="200"></div>
                <p class="sc-note">Đối chiếu số tiền và giao dịch của khách trước khi hoàn tất thanh toán.</p>
            </div>
            <div id="modal-success-screen" class="sc-success" style="display:none"><i data-lucide="circle-check"></i><h3>Đã ghi nhận thanh toán</h3><p>Bạn có thể xem hoặc in hóa đơn cho khách.</p><div class="sc-modal-actions"><button type="button" class="sc-secondary" id="btn-print-invoice"><i data-lucide="printer"></i>In hóa đơn PDF</button><button type="button" class="sc-primary" id="sc-finish">Hoàn tất</button></div></div>
            <div class="sc-modal-actions" id="modal-footer-form"><button type="button" class="sc-secondary" id="sc-cancel-pay">Quay lại</button><button type="button" class="sc-primary" id="btn-confirm-pay">Hoàn tất thanh toán</button></div>
        </section>`;
        byId('sc-modal-close').addEventListener('click', close);
        byId('sc-cancel-pay').addEventListener('click', close);
        byId('sc-finish').addEventListener('click', () => { close(); loadData(); });
        modal.classList.add('active'); modal.setAttribute('aria-hidden','false');
        byId('sc-modal-close').focus();
        icons();
        // Bind events inside modal
        const payMethodSelect = document.getElementById("pay-method");
        const payAmountInput = document.getElementById("pay-amount");
        const qrCodeSection = document.getElementById("qr-code-section");
        const qrImage = document.getElementById("qr-image");
        const btnConfirm = document.getElementById("btn-confirm-pay");
        
        const updateQRCode = () => {
            if (payMethodSelect.value === "Chuyển khoản") {
                const amount = payAmountInput.value || 0;
                const bookingId = currentProcessBooking ? currentProcessBooking.id : '';
                qrImage.src = `https://img.vietqr.io/image/970436-1012345678-compact2.png?amount=${amount}&addInfo=Thanh toan don ${bookingId}&accountName=QUAN LY KHACH SAN`;
                qrCodeSection.style.display = "block";
            } else {
                qrCodeSection.style.display = "none";
            }
        };

        payMethodSelect.addEventListener("change", updateQRCode);
        payAmountInput.addEventListener("input", updateQRCode);
        
        btnConfirm.addEventListener('click', async () => {
            if (!currentProcessBooking) return;
            
            const amount = document.getElementById("pay-amount").value;
            const method = document.getElementById("pay-method").value;
            
            if (!amount || amount < 0) {
                alert("Vui lòng nhập số tiền hợp lệ!");
                return;
            }

            const payload = {
                bookingId: currentProcessBooking.id,
                customerId: currentProcessBooking.customerId,
                amount: parseFloat(amount),
                paymentMethod: method
            };

            try {
                btnConfirm.disabled = true;
                btnConfirm.textContent = "Đang xử lý...";
                
                await api.post("/payments", payload);
                
                // Handle success
                document.getElementById("modal-checkout-form").style.display = 'none';
                document.getElementById("modal-footer-form").style.display = 'none';
                document.getElementById("modal-success-screen").style.display = 'block';
                if (window.lucide) lucide.createIcons();

                document.getElementById("btn-print-invoice").onclick = async () => {
                    try {
                        const invoices = await api.get("/invoices");
                        const invoice = invoices.find(i => i.bookingId == currentProcessBooking.id);
                        if (invoice) {
                            window.open(getApiBaseUrl() + "/invoices/" + invoice.id + "/pdf", "_blank");
                        } else {
                            alert("Không tìm thấy hóa đơn cho đơn đặt phòng này!");
                        }
                    } catch (err) {
                        console.error(err);
                    }
                    document.getElementById('global-modal').classList.remove('active');
                    loadData();
                };
            } catch (e) {
                console.error(e);
                alert("Lỗi thanh toán: " + e.message);
            } finally {
                btnConfirm.disabled = false;
                btnConfirm.textContent = "Hoàn tất Thanh toán";
            }
        });
    };

    byId('sc-refresh').addEventListener('click', loadData);
    byId('sc-search').addEventListener('input', renderList);
    for (const key of ['all','today','overdue']) {
        byId(`sc-filter-${key}`).addEventListener('click', () => { filter = key; renderList(); });
    }
    container.querySelectorAll?.('.sc-stat').forEach(button => button.addEventListener('click', () => { filter = button.dataset.filter; renderList(); }));
    icons();
    await loadData();
}
