import { api } from "../../api.js";
import { roomStatus, ROOM_STATUSES } from "../shared/ui.js";

const STATE_COLORS = {
    vacant_clean: "var(--color-vacant-clean)",
    vacant_dirty: "var(--color-vacant-dirty)",
    occupied_clean: "var(--color-occupied-clean)",
    occupied_dirty: "var(--color-occupied-dirty)",
    out_of_order: "var(--color-out-of-order)",
    out_of_service: "var(--color-out-of-service)",
    reserved: "var(--color-reserved)"
};

export async function renderRoomMap(container) {
    container.replaceChildren();
    const header = document.createElement("div");
    header.className = "page-header flex-row justify-between";
    header.innerHTML = `<div><h1>Sơ đồ phòng khách sạn</h1><p>Tổng quan tình trạng phòng. Thao tác đặt phòng, nhận phòng và trả phòng nằm trong các mục nghiệp vụ tương ứng.</p></div><button id="btn-refresh-map" class="btn btn-secondary" type="button">Làm mới sơ đồ</button>`;

    const legend = document.createElement("div");
    legend.className = "room-status-legend mb-4";
    for (const [code, [label]] of Object.entries(ROOM_STATUSES)) {
        const item = document.createElement("span");
        item.className = "room-status-legend-item";
        const marker = document.createElement("span");
        marker.className = "room-status-marker";
        marker.style.backgroundColor = STATE_COLORS[code];
        marker.setAttribute("aria-hidden", "true");
        const text = document.createElement("span");
        text.textContent = label;
        item.append(marker, text);
        legend.append(item);
    }

    const grid = document.createElement("div");
    grid.className = "room-grid";
    grid.id = "staff-rooms-grid";
    grid.setAttribute("aria-live", "polite");
    grid.textContent = "Đang tải sơ đồ phòng…";
    container.append(header, legend, grid);

    let generation = 0;
    const loadMap = async () => {
        const current = ++generation;
        grid.textContent = "Đang tải sơ đồ phòng…";
        try {
            const rooms = await api.get("/rooms");
            if (current !== generation || !container.contains(grid)) return;
            if (!Array.isArray(rooms)) throw new Error("Dữ liệu phòng không đúng định dạng.");
            grid.replaceChildren();
            if (rooms.length === 0) {
                grid.textContent = "Chưa có phòng để hiển thị.";
                return;
            }
            for (const room of rooms) {
                const state = roomStatus(room.status);
                const card = document.createElement("article");
                card.className = `room-card status-${state.code}`;
                card.style.setProperty("--room-state-color", STATE_COLORS[state.code] || "var(--border)");
                const number = document.createElement("div");
                number.className = "room-card-number";
                number.textContent = `Phòng ${room.roomNumber ?? "—"}`;
                const type = document.createElement("div");
                type.className = "room-card-type";
                type.textContent = room.typeName || "Chưa phân loại";
                const badge = document.createElement("span");
                badge.className = "room-badge";
                badge.textContent = state.label;
                const code = document.createElement("small");
                code.className = "room-state-code";
                code.textContent = state.abbreviation;
                card.append(number, type, badge, code);
                grid.append(card);
            }
        } catch (error) {
            if (current !== generation || !container.contains(grid)) return;
            const message = document.createElement("p");
            message.className = "form-error";
            message.setAttribute("role", "alert");
            message.textContent = error.message || "Không thể tải sơ đồ phòng. Vui lòng thử lại.";
            grid.replaceChildren(message);
        }
    };

    document.getElementById("btn-refresh-map").addEventListener("click", loadMap);
    await loadMap();
}
