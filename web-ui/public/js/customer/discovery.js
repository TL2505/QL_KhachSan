import { renderNotice } from "../shared/ui.js";

export function renderDiscovery(container) {
    renderNotice(container, "Đặt phòng bằng tài khoản đang chờ backend", "Luồng Customer hiện không có báo giá và liên kết sở hữu được xác minh qua API. Luồng OTP khách vãng lai vẫn là danh tính riêng; tài khoản Customer không tự chuyển sang luồng đó. Vui lòng liên hệ lễ tân để đặt phòng trong thời gian chờ.");
}
