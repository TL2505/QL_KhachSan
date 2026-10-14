import { renderNotice } from "../shared/ui.js";

export function renderDashboard(container) {
    renderNotice(container, "Tạm dừng bảng điều khiển cá nhân", "API đặt phòng và lịch sử điểm hiện nhận customerId từ request mà chưa xác minh đầy đủ quyền sở hữu. Màn hình sẽ chỉ hiển thị dữ liệu cá nhân sau khi backend liên kết Customer với danh tính JWT.");
}
