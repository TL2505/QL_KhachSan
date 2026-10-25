import { api } from "../../api.js";
import { uploadMedia, validateImageFile } from "../shared/media-upload.js";

const ROOM_STATUSES = {
    vacant_clean: { label: "Trống - sạch", badge: "badge-available" },
    vacant_dirty: { label: "Cần vệ sinh", badge: "badge-cleaning" },
    occupied_clean: { label: "Đang có khách", badge: "badge-occupied" },
    occupied_dirty: { label: "Đang ở - cần vệ sinh", badge: "badge-occupied" },
    reserved: { label: "Đã đặt", badge: "badge-booked" },
    out_of_order: { label: "Đang bảo trì", badge: "badge-maintenance" },
    out_of_service: { label: "Ngừng phục vụ", badge: "badge-maintenance" }
};

const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
})[character]);

export async function renderRooms(container) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Quản lý phòng</h1>
                <p>Quản lý từng phòng vật lý, trạng thái vận hành và hình ảnh phòng.</p>
            </div>
            <div class="admin-rooms-header-actions">
                <button id="btn-add-room-admin" class="btn btn-primary">
                    <i data-lucide="plus" style="width:14px;height:14px;"></i> Thêm phòng mới
                </button>
            </div>
        </div>
        <div id="rooms-feedback" class="rooms-feedback" role="status" aria-live="polite" hidden></div>
        <div class="card">
            <div class="rooms-filters">
                <label class="rooms-filter">
                    <span>Tìm phòng</span>
                    <input id="rooms-search" type="search" class="input" placeholder="Nhập số phòng hoặc hạng phòng">
                </label>
                <label class="rooms-filter">
                    <span>Hạng phòng</span>
                    <select id="rooms-type-filter" class="select"><option value="">Tất cả hạng phòng</option></select>
                </label>
                <label class="rooms-filter">
                    <span>Trạng thái</span>
                    <select id="rooms-status-filter" class="select">
                        <option value="">Tất cả trạng thái</option>
                        ${Object.entries(ROOM_STATUSES).map(([value, item]) =>
                            `<option value="${value}">${escapeHtml(item.label)}</option>`).join("")}
                    </select>
                </label>
                <div id="rooms-count" class="rooms-count" aria-live="polite"></div>
            </div>
            <div class="table-container">
                <table class="table">
                    <thead><tr>
                        <th>Mã ID</th><th>Số phòng</th><th>Hạng phòng</th>
                        <th>Đơn giá / đêm</th><th>Trạng thái</th><th>Thao tác</th>
                    </tr></thead>
                    <tbody id="admin-rooms-body">
                        <tr><td colspan="6" class="rooms-empty">Đang tải danh sách phòng...</td></tr>
                    </tbody>
                </table>
            </div>
            <div id="pagination-admin-rooms"></div>
        </div>
    `;
    if (window.lucide?.createIcons) window.lucide.createIcons();

    let allRooms = [];
    let roomTypes = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const feedback = (message, kind = "error") => {
        const node = document.getElementById("rooms-feedback");
        node.textContent = message;
        node.className = `rooms-feedback ${kind}`;
        node.hidden = !message;
    };

    const filteredRooms = () => {
        const search = document.getElementById("rooms-search").value.trim().toLocaleLowerCase("vi");
        const typeId = document.getElementById("rooms-type-filter").value;
        const status = document.getElementById("rooms-status-filter").value;
        return allRooms.filter(room => {
            const matchesSearch = !search
                || String(room.roomNumber ?? "").toLocaleLowerCase("vi").includes(search)
                || String(room.typeName ?? "").toLocaleLowerCase("vi").includes(search);
            return matchesSearch
                && (!typeId || String(room.roomTypeId) === typeId)
                && (!status || room.status === status);
        });
    };

    const renderTable = () => {
        const body = document.getElementById("admin-rooms-body");
        const pagination = document.getElementById("pagination-admin-rooms");
        const count = document.getElementById("rooms-count");
        const filtered = filteredRooms();
        count.textContent = `Hiển thị ${filtered.length} / ${allRooms.length} phòng`;
        body.replaceChildren();

        if (!filtered.length) {
            const row = body.insertRow();
            const cell = row.insertCell();
            cell.colSpan = 6;
            cell.className = "rooms-empty";
            cell.textContent = allRooms.length ? "Không có phòng phù hợp bộ lọc." : "Chưa có dữ liệu phòng.";
            pagination.replaceChildren();
            return;
        }

        const pageCount = Math.max(1, Math.ceil(filtered.length / itemsPerPage));
        currentPage = Math.min(currentPage, pageCount);
        const visible = filtered.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);
        for (const room of visible) {
            const row = body.insertRow();
            const id = row.insertCell();
            id.textContent = String(room.id);
            const number = row.insertCell();
            const strong = document.createElement("strong");
            strong.textContent = `Phòng ${room.roomNumber}`;
            number.append(strong);
            row.insertCell().textContent = room.typeName || "Chưa phân hạng";
            row.insertCell().textContent = `${Number(room.price).toLocaleString("vi-VN")} đ`;

            const statusCell = row.insertCell();
            const status = ROOM_STATUSES[room.status];
            const badge = document.createElement("span");
            badge.className = `room-badge ${status?.badge || "badge-maintenance"}`;
            badge.textContent = status?.label || "Chưa xác định";
            badge.title = status ? "" : `Mã trạng thái: ${room.status || "trống"}`;
            statusCell.append(badge);

            const actions = row.insertCell();
            actions.className = "rooms-actions";
            const edit = document.createElement("button");
            edit.type = "button";
            edit.className = "btn btn-secondary btn-sm";
            edit.textContent = "Sửa";
            edit.title = "Cập nhật thông tin phòng và giá chung của hạng phòng";
            edit.addEventListener("click", () => openRoomForm(room));
            const remove = document.createElement("button");
            remove.type = "button";
            remove.className = "btn btn-danger btn-sm";
            remove.textContent = "Xóa";
            remove.addEventListener("click", () => deleteRoom(room));
            actions.append(edit, remove);
        }

        if (window.renderPaginationComponent) {
            window.renderPaginationComponent(
                "pagination-admin-rooms",
                filtered.length,
                itemsPerPage,
                currentPage,
                page => { currentPage = page; renderTable(); },
                pageSize => { itemsPerPage = pageSize; currentPage = 1; renderTable(); }
            );
        } else {
            pagination.replaceChildren();
        }
    };

    const loadRooms = async () => {
        const body = document.getElementById("admin-rooms-body");
        body.innerHTML = '<tr><td colspan="6" class="rooms-empty">Đang tải danh sách phòng...</td></tr>';
        feedback("");
        try {
            const [rooms, types] = await Promise.all([
                api.get("/rooms"),
                api.get("/rooms/types")
            ]);
            if (!Array.isArray(rooms) || !Array.isArray(types)) {
                throw new Error("Phản hồi danh sách phòng không đúng định dạng.");
            }
            allRooms = rooms;
            roomTypes = types;
            const typeFilter = document.getElementById("rooms-type-filter");
            typeFilter.innerHTML = '<option value="">Tất cả hạng phòng</option>' + types.map(type =>
                `<option value="${escapeHtml(type.id)}">${escapeHtml(type.name)}</option>`).join("");
            currentPage = 1;
            renderTable();
        } catch (error) {
            body.replaceChildren();
            const row = body.insertRow();
            const cell = row.insertCell();
            cell.colSpan = 6;
            cell.className = "rooms-empty";
            cell.textContent = error.message || "Không thể tải dữ liệu phòng.";
            feedback(error.message || "Không thể tải dữ liệu phòng.");
        }
    };

    const openRoomForm = room => {
        const modal = document.getElementById("global-modal");
        const content = document.getElementById("global-modal-content");
        if (!modal || !content) {
            feedback("Không thể mở biểu mẫu phòng. Vui lòng tải lại trang.");
            return;
        }
        const editing = Boolean(room);
        const closeRoomModal = () => {
            releaseObjectPreview();
            window.closeModal?.("global-modal");
        };
        modal.classList.add("active");
        content.innerHTML = `
            <div class="modal-header">
                <h2>${editing ? "Cập nhật phòng" : "Thêm phòng mới"}</h2>
                <button type="button" class="modal-close" data-close-room-modal aria-label="Đóng">&times;</button>
            </div>
            <form id="form-room-admin-action">
                <div class="form-group">
                    <label class="form-label" for="ar-number">Số phòng *</label>
                    <input class="form-input" type="text" id="ar-number" maxlength="20" pattern="[A-Za-z0-9][A-Za-z0-9-]{0,19}"
                        value="${escapeHtml(room?.roomNumber || "")}" required placeholder="Ví dụ: 101">
                </div>
                <div class="form-group">
                    <label class="form-label" for="ar-type">Hạng phòng *</label>
                    <select class="form-input" id="ar-type" required>
                        <option value="">Chọn hạng phòng</option>
                        ${roomTypes.map(type => `<option value="${escapeHtml(type.id)}" ${Number(room?.roomTypeId) === Number(type.id) ? "selected" : ""}>${escapeHtml(type.name)}</option>`).join("")}
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label" for="ar-image-file">Ảnh phòng (không bắt buộc)</label>
                    <div class="rooms-image-picker">
                        <label class="btn btn-secondary rooms-image-picker-button" for="ar-image-file">Chọn ảnh</label>
                        <input class="rooms-image-file-input" type="file" id="ar-image-file" accept="image/jpeg,image/png"
                            aria-describedby="ar-image-help ar-image-preview-status">
                        <span id="ar-image-file-name">Chưa chọn ảnh</span>
                    </div>
                    <small class="rooms-form-note" id="ar-image-help">Chọn ảnh JPEG hoặc PNG, tối đa 5 MB. Ảnh được tải lên khi lưu phòng.</small>
                    <div class="rooms-image-preview" id="ar-image-preview">
                        <div class="rooms-image-preview-placeholder" id="ar-image-preview-placeholder">
                            <span aria-hidden="true">Ảnh</span>
                            <small>Ảnh phòng sẽ hiển thị ở đây</small>
                        </div>
                        <img id="ar-image-preview-image" alt="Ảnh xem trước phòng" referrerpolicy="no-referrer" hidden>
                    </div>
                    <small class="rooms-form-note rooms-image-preview-status" id="ar-image-preview-status" role="status" aria-live="polite">${room?.imageUrl ? "Đang tải ảnh hiện tại…" : "Chọn tệp ảnh để xem trước trước khi lưu."}</small>
                </div>
                    <div class="form-group">
                        <label class="form-label" for="ar-price">Đơn giá / đêm (VND) *</label>
                    <input class="form-input" type="number" id="ar-price" min="1" max="99999999.99" step="0.01"
                        value="${editing ? escapeHtml(room.price) : ""}" required placeholder="Ví dụ: 500000">
                </div>
                ${editing ? `<p class="rooms-form-note">Trạng thái hiện tại: ${escapeHtml(ROOM_STATUSES[room.status]?.label || "Chưa xác định")}. Trạng thái vận hành được cập nhật qua quy trình lễ tân / buồng phòng.</p>` : `
                <div class="form-group">
                    <label class="form-label" for="ar-status">Trạng thái ban đầu</label>
                    <select class="form-input" id="ar-status">
                        <option value="vacant_dirty">Cần vệ sinh</option>
                        <option value="vacant_clean">Sẵn sàng</option>
                        <option value="out_of_order">Đang bảo trì</option>
                        <option value="out_of_service">Ngừng phục vụ</option>
                    </select>
                </div>`}
                <div class="rooms-form-error" id="room-form-error" role="alert" hidden></div>
            </form>
            <div class="flex-row mt-4" style="justify-content:flex-end;">
                <button type="button" class="btn btn-secondary" data-close-room-modal>Hủy</button>
                <button type="submit" form="form-room-admin-action" class="btn btn-primary">${editing ? "Lưu thông tin phòng" : "Thêm phòng"}</button>
            </div>
        `;
        content.querySelectorAll("[data-close-room-modal]").forEach(button =>
            button.addEventListener("click", closeRoomModal));

        const imageInput = content.querySelector("#ar-image-file");
        const imageFileName = content.querySelector("#ar-image-file-name");
        const previewImage = content.querySelector("#ar-image-preview-image");
        const previewPlaceholder = content.querySelector("#ar-image-preview-placeholder");
        const previewStatus = content.querySelector("#ar-image-preview-status");
        let previewUrl = "";
        let objectPreviewUrl = "";
        let uploadedImageUrl = "";
        const showImagePreview = (src, successMessage) => {
            previewUrl = src;
            previewImage.hidden = true;
            previewPlaceholder.hidden = false;
            previewStatus.textContent = "Đang tải ảnh xem trước…";
            previewImage.onload = () => {
                if (previewUrl !== src) return;
                previewImage.hidden = false;
                previewPlaceholder.hidden = true;
                previewStatus.textContent = successMessage;
            };
            previewImage.onerror = () => {
                if (previewUrl !== src) return;
                previewImage.hidden = true;
                previewPlaceholder.hidden = false;
                previewStatus.textContent = "Không thể hiển thị ảnh này. Hãy chọn một tệp JPEG hoặc PNG khác.";
            };
            previewImage.src = src;
        };
        const releaseObjectPreview = () => {
            if (!objectPreviewUrl) return;
            URL.revokeObjectURL(objectPreviewUrl);
            objectPreviewUrl = "";
        };
        imageInput.addEventListener("change", () => {
            uploadedImageUrl = "";
            const file = imageInput.files?.[0];
            if (!file) {
                releaseObjectPreview();
                imageFileName.textContent = "Chưa chọn ảnh";
                if (room?.imageUrl) {
                    let existingImageUrl;
                    try { existingImageUrl = new URL(room.imageUrl, window.location.origin); }
                    catch { existingImageUrl = null; }
                    if (existingImageUrl && ['http:', 'https:'].includes(existingImageUrl.protocol)) {
                        showImagePreview(existingImageUrl.href, "Ảnh hiện tại của phòng.");
                        return;
                    }
                }
                previewUrl = "";
                previewImage.removeAttribute("src");
                previewImage.hidden = true;
                previewPlaceholder.hidden = false;
                previewStatus.textContent = "Chọn tệp ảnh để xem trước trước khi lưu.";
                return;
            }
            try {
                validateImageFile(file);
            } catch (error) {
                imageInput.value = "";
                imageFileName.textContent = "Chưa chọn ảnh";
                releaseObjectPreview();
                if (room?.imageUrl) {
                    let existingImageUrl;
                    try { existingImageUrl = new URL(room.imageUrl, window.location.origin); }
                    catch { existingImageUrl = null; }
                    if (existingImageUrl && ['http:', 'https:'].includes(existingImageUrl.protocol)) {
                        showImagePreview(existingImageUrl.href, `Ảnh hiện tại của phòng. ${error.message}`);
                        return;
                    }
                }
                previewUrl = "";
                previewImage.removeAttribute("src");
                previewImage.hidden = true;
                previewPlaceholder.hidden = false;
                previewStatus.textContent = error.message;
                return;
            }
            imageFileName.textContent = file.name;
            releaseObjectPreview();
            objectPreviewUrl = URL.createObjectURL(file);
            showImagePreview(objectPreviewUrl, `Đang xem trước ${file.name}. Tệp sẽ được tải lên khi lưu phòng.`);
        });
        if (room?.imageUrl) {
            let existingImageUrl;
            try { existingImageUrl = new URL(room.imageUrl, window.location.origin); }
            catch { existingImageUrl = null; }
            if (existingImageUrl && ['http:', 'https:'].includes(existingImageUrl.protocol)) {
                showImagePreview(existingImageUrl.href, "Ảnh hiện tại của phòng.");
            } else {
                previewStatus.textContent = "Đường dẫn ảnh hiện tại không hợp lệ. Chọn ảnh mới để thay thế.";
            }
        }
        content.querySelector("#form-room-admin-action").addEventListener("submit", async event => {
            event.preventDefault();
            const form = event.currentTarget;
            const submit = content.querySelector('button[form="form-room-admin-action"][type="submit"]');
            const errorNode = form.querySelector("#room-form-error");
            const imageFile = imageInput.files?.[0] || null;
            const payload = {
                roomNumber: form.querySelector("#ar-number").value.trim(),
                roomTypeId: Number(form.querySelector("#ar-type").value),
                imageUrl: room?.imageUrl || null,
                price: Number(form.querySelector("#ar-price").value)
            };
            if (!editing) payload.status = form.querySelector("#ar-status").value;
            submit.disabled = true;
            submit.textContent = "Đang lưu...";
            errorNode.hidden = true;
            try {
                if (imageFile) {
                    if (!uploadedImageUrl) uploadedImageUrl = (await uploadMedia(imageFile)).url;
                    payload.imageUrl = uploadedImageUrl;
                }
                if (editing) await api.put(`/rooms/${encodeURIComponent(room.id)}`, payload);
                else await api.post("/rooms", payload);
                releaseObjectPreview();
                closeRoomModal();
                feedback(editing ? "Đã cập nhật phòng." : "Đã thêm phòng.", "success");
                await loadRooms();
            } catch (error) {
                errorNode.textContent = error.message || "Không thể lưu thông tin phòng.";
                errorNode.hidden = false;
            } finally {
                submit.disabled = false;
                submit.textContent = editing ? "Lưu thông tin phòng" : "Thêm phòng";
            }
        });
    };

    const deleteRoom = async room => {
        const confirmed = await window.showCustomConfirm(`Xóa phòng ${room.roomNumber}? Không thể xóa phòng đã có đặt phòng hoặc dữ liệu liên quan.`, {
            title: 'Xác nhận xóa phòng', confirmLabel: 'Xóa phòng'
        });
        if (!confirmed) return;
        try {
            await api.delete(`/rooms/${encodeURIComponent(room.id)}`);
            feedback(`Đã xóa phòng ${room.roomNumber}.`, "success");
            await loadRooms();
        } catch (error) {
            feedback(error.message || "Không thể xóa phòng.");
        }
    };

    document.getElementById("btn-add-room-admin").addEventListener("click", () => openRoomForm(null));
    for (const id of ["rooms-search", "rooms-type-filter", "rooms-status-filter"]) {
        document.getElementById(id).addEventListener("input", () => { currentPage = 1; renderTable(); });
        document.getElementById(id).addEventListener("change", () => { currentPage = 1; renderTable(); });
    }
    await loadRooms();
}
