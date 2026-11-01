import { api } from "../../api.js";

const STATUS = {
  pending: { label: "Chờ xác nhận", cls: "badge-booked" },
  confirmed: { label: "Đã xác nhận", cls: "badge-available" },
  checked_in: { label: "Đang lưu trú", cls: "badge-occupied" },
  checked_out: { label: "Đã trả phòng", cls: "badge-cleaning" },
  cancelled: { label: "Đã hủy", cls: "badge-maintenance" },
  no_show: { label: "Không đến", cls: "badge-maintenance" }
};

const esc = value => String(value ?? "").replace(/[&<>'"]/g, c => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", "'":"&#39;", '"':"&quot;" })[c]);
const dateText = value => value ? new Date(value).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" }) : "—";
const statusInfo = status => STATUS[String(status || "").toLowerCase()] || { label: "Chưa xác định", cls: "badge-maintenance" };

export async function renderAdminBookings(container) {
  container.innerHTML = `
    <div class="page-header admin-bookings-header">
      <div><h1>Quản lý đặt phòng</h1><p>Xác nhận hoặc hủy đơn trước ngày nhận phòng. Nhận/trả phòng vẫn do Lễ tân xử lý.</p></div>
      <button type="button" id="admin-bookings-refresh" class="btn btn-secondary"><i data-lucide="refresh-cw"></i>Làm mới</button>
    </div>
    <div class="admin-bookings-toolbar card">
      <label class="admin-bookings-search"><i data-lucide="search"></i><input id="admin-bookings-search" class="form-input" aria-label="Tìm đơn đặt phòng" placeholder="Tìm mã đơn, phòng hoặc khách hàng"></label>
      <select id="admin-bookings-filter" class="form-input" aria-label="Lọc theo trạng thái">
        <option value="">Tất cả trạng thái</option><option value="pending">Chờ xác nhận</option><option value="confirmed">Đã xác nhận</option><option value="checked_in">Đang lưu trú</option><option value="checked_out">Đã trả phòng</option><option value="cancelled">Đã hủy</option>
      </select>
    </div>
    <div class="card admin-bookings-card"><div class="table-container"><table class="table admin-bookings-table">
      <thead><tr><th>Mã đơn</th><th>Phòng</th><th>Khách hàng</th><th>Nhận phòng</th><th>Trả phòng</th><th>Tổng tiền</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody id="admin-bookings-body"><tr><td colspan="8" class="admin-bookings-loading">Đang tải đơn đặt phòng…</td></tr></tbody>
    </table></div></div>`;
  window.lucide?.createIcons();
  let bookings = [];
  const body = document.getElementById("admin-bookings-body");

  const renderRows = () => {
    const query = document.getElementById("admin-bookings-search").value.trim().toLowerCase();
    const filter = document.getElementById("admin-bookings-filter").value;
    const list = bookings.filter(b => {
      const state = String(b.status || "").toLowerCase();
      return (!filter || state === filter) && (!query || [b.id, b.roomId, b.customerId, statusInfo(state).label].join(" ").toLowerCase().includes(query));
    });
    if (!list.length) { body.innerHTML = `<tr><td colspan="8" class="admin-bookings-empty">Không tìm thấy đơn đặt phòng.</td></tr>`; return; }
    body.innerHTML = list.map(b => {
      const state = String(b.status || "").toLowerCase();
      const info = statusInfo(state);
      const actions = state === "pending"
        ? `<button class="btn btn-primary btn-sm" data-action="confirmed" data-id="${b.id}">Xác nhận</button><button class="btn btn-danger btn-sm" data-action="cancelled" data-id="${b.id}">Từ chối</button>`
        : state === "confirmed"
          ? `<button class="btn btn-danger btn-sm" data-action="cancelled" data-id="${b.id}">Hủy đơn</button>`
          : '<span class="admin-bookings-na">Không có thao tác</span>';
      return `<tr><td><strong>#${esc(b.id)}</strong></td><td>Phòng ${esc(b.roomId)}</td><td>KH #${esc(b.customerId)}</td><td>${esc(dateText(b.checkInDate))}</td><td>${esc(dateText(b.checkOutDate))}</td><td class="admin-bookings-price">${Number(b.totalPrice || 0).toLocaleString("vi-VN")} đ</td><td><span class="room-badge ${info.cls}">${esc(info.label)}</span></td><td><div class="admin-bookings-actions">${actions}</div></td></tr>`;
    }).join("");
  };

  const load = async () => {
    body.innerHTML = `<tr><td colspan="8" class="admin-bookings-loading">Đang tải đơn đặt phòng…</td></tr>`;
    try { bookings = await api.get("/bookings") || []; renderRows(); }
    catch (error) { body.innerHTML = `<tr><td colspan="8" class="admin-bookings-error">${esc(error.message)}</td></tr>`; }
  };
  body.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const confirm = button.dataset.action === "confirmed";
    const message = confirm ? "Xác nhận đơn đặt phòng này?" : "Hủy đơn này? Chỉ các đơn chưa đến ngày nhận phòng và chưa phát sinh thanh toán hoặc hóa đơn mới được hủy.";
    if (!await window.showCustomConfirm(message, { title: confirm ? "Xác nhận đơn đặt phòng" : "Hủy đơn đặt phòng", confirmLabel: confirm ? "Xác nhận" : "Hủy đơn", kind: confirm ? "primary" : "danger" })) return;
    button.disabled = true;
    try { await api.put(`/bookings/${encodeURIComponent(button.dataset.id)}/admin-status`, { status: button.dataset.action }); await load(); }
    catch (error) { window.showCustomAlert?.(error.message); button.disabled = false; }
  });
  document.getElementById("admin-bookings-search").addEventListener("input", renderRows);
  document.getElementById("admin-bookings-filter").addEventListener("change", renderRows);
  document.getElementById("admin-bookings-refresh").addEventListener("click", load);
  await load();
}
