import { renderNotice } from "../shared/ui.js";

export function renderLoyalty(container) {
    renderNotice(container, "Lịch sử và đổi điểm đang tạm dừng", "API loyalty hiện cần customerId trong request và chưa xác minh quyền sở hữu từ danh tính đăng nhập. Hồ sơ điểm cá nhân và đổi điểm sẽ tiếp tục sau khi backend áp dụng kiểm tra sở hữu.");
}
