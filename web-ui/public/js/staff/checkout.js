import { api } from "../../api.js";

const currency = amount => new Intl.NumberFormat("vi-VN", {style: "currency", currency: "VND", maximumFractionDigits: 0}).format(Number(amount) || 0);
const date = value => value ? new Date(value).toLocaleDateString("vi-VN") : "Chưa có";
const parseBalance = value => {
    const amount = typeof value === "number" ? value : typeof value === "string" && value.trim() ? Number(value) : NaN;
    return Number.isFinite(amount) && amount >= 0 ? amount : null;
};

function idempotencyKey(scope, payload) {
    const storeKey = `hotel:pending-operation:${scope}`;
    const fingerprint = JSON.stringify(payload);
    try {
        const saved = JSON.parse(sessionStorage.getItem(storeKey) || "null");
        if (saved?.fingerprint === fingerprint && saved.key) return saved.key;
        const key = crypto.randomUUID();
        sessionStorage.setItem(storeKey, JSON.stringify({fingerprint, key}));
        return key;
    } catch {
        return crypto.randomUUID();
    }
}

function clearIdempotencyKey(scope) {
    try { sessionStorage.removeItem(`hotel:pending-operation:${scope}`); } catch { /* Storage is optional. */ }
}

export async function renderCheckout(container) {
    container.replaceChildren();
    const page = document.createElement("section");
    page.innerHTML = `<header class="page-header flex-row justify-between"><div><h1>Folio và trả phòng</h1><p>Xem số dư do máy chủ cung cấp, ghi nhận thanh toán và xác nhận trả phòng riêng biệt.</p></div><button class="btn btn-secondary" id="checkout-refresh" type="button">Làm mới</button></header><div class="grid-cards" id="checkout-grid" aria-live="polite"></div>`;
    container.append(page);
    const grid = page.querySelector("#checkout-grid");
    let generation = 0;

    async function loadData() {
        const current = ++generation;
        grid.textContent = "Đang tải các booking đang lưu trú…";
        try {
            const [bookings, rooms, customers] = await Promise.all([
                api.get("/bookings"), api.get("/rooms"), api.get("/customers")
            ]);
            if (current !== generation || !container.contains(page)) return;
            if (![bookings, rooms, customers].every(Array.isArray)) throw new Error("Dữ liệu booking, phòng hoặc khách hàng không đúng định dạng.");
            const active = bookings.filter(booking => booking.status === "checked_in");
            const records = await Promise.all(active.map(async booking => ({
                booking,
                room: rooms.find(room => String(room.id) === String(booking.roomId)),
                customer: customers.find(customer => String(customer.id) === String(booking.customerId)),
                folios: await api.get(`/folios/booking/${encodeURIComponent(booking.id)}`)
            })));
            if (current !== generation || !container.contains(page)) return;
            if (records.some(record => !Array.isArray(record.folios))) throw new Error("Không thể đọc các folio của booking.");
            grid.replaceChildren();
            if (!records.length) {
                const empty = document.createElement("p");
                empty.className = "card";
                empty.textContent = "Hiện không có khách đang lưu trú cần xử lý.";
                grid.append(empty);
                return;
            }
            for (const record of records) renderBooking(record, grid, loadData, container);
        } catch (error) {
            if (current !== generation || !container.contains(page)) return;
            const message = document.createElement("p");
            message.className = "card form-error";
            message.setAttribute("role", "alert");
            message.textContent = error.message || "Không thể tải checkout. Vui lòng thử lại.";
            const retry = document.createElement("button");
            retry.className = "btn btn-secondary";
            retry.type = "button";
            retry.textContent = "Thử lại";
            retry.addEventListener("click", loadData);
            grid.replaceChildren(message, retry);
        }
    }

    page.querySelector("#checkout-refresh").addEventListener("click", loadData);
    await loadData();
}

function renderBooking({booking, room, customer, folios}, grid, reload, container) {
    const card = document.createElement("article");
    card.className = "checkout-card card";
    const heading = document.createElement("h2");
    heading.textContent = `Phòng ${room?.roomNumber ?? "—"} · Booking #${booking.id}`;
    const details = document.createElement("p");
    details.textContent = `${customer?.fullName || "Khách lưu trú"} · Nhận phòng ${date(booking.checkInDate)} · Dự kiến trả ${date(booking.checkOutDate)}`;
    card.append(heading, details);
    let outstanding = false;
    let invalidBalance = false;
    for (const folio of folios) {
        const row = document.createElement("section");
        row.className = "folio-balance-row";
        const summary = document.createElement("p");
        const type = folio.folio_type === "guest" ? "Folio khách" : "Folio phòng";
        const balance = parseBalance(folio.balance);
        summary.textContent = `${type} · ${folio.status || "chưa rõ"} · Số dư ${balance === null ? "không đọc được" : currency(balance)}`;
        row.append(summary);
        if (balance === null) invalidBalance = true;
        if (folio.status === "open" && balance !== null && balance > 0) {
            outstanding = true;
            for (const [method, label] of [["cash", "Thu tiền mặt"], ["card", "Ghi nhận thẻ"]]) {
                const button = document.createElement("button");
                button.type = "button";
                button.className = "btn btn-secondary";
                button.textContent = label;
                button.addEventListener("click", () => settleFolio(folio, method, button, reload).catch(error => window.showCustomAlert(error.message || "Không thể kiểm tra số dư.")));
                row.append(button);
            }
            const qrButton = document.createElement("button");
            qrButton.type = "button";
            qrButton.className = "btn btn-secondary";
            qrButton.textContent = "Tạo VietQR";
            qrButton.addEventListener("click", () => showVietQr(folio, row, container, reload));
            row.append(qrButton);
        }
        card.append(row);
    }
    const canCheckout = folios.length > 0 && !outstanding && !invalidBalance && folios.every(folio => parseBalance(folio.balance) === 0);
    if (canCheckout) {
        const checkout = document.createElement("button");
        checkout.type = "button";
        checkout.className = "btn btn-primary";
        checkout.textContent = "Xác nhận trả phòng";
        checkout.addEventListener("click", () => checkoutBooking(booking, folios[0], checkout, reload).catch(error => window.showCustomAlert(error.message || "Không thể xác minh các folio trước khi trả phòng.")));
        card.append(checkout);
    } else if (outstanding) {
        const note = document.createElement("p");
        note.className = "text-muted";
        note.textContent = "Cần thanh toán hết các folio trước khi xác nhận trả phòng.";
        card.append(note);
    } else if (folios.length && (invalidBalance || folios.some(folio => parseBalance(folio.balance) !== 0))) {
        const note = document.createElement("p");
        note.className = "text-muted";
        note.textContent = "Có số dư ở trạng thái chưa thể thanh toán từ màn hình này. Vui lòng kiểm tra với quản lý.";
        card.append(note);
    }
    if (!folios.length) {
        const note = document.createElement("p");
        note.className = "text-muted";
        note.textContent = "Booking chưa có folio để đối soát.";
        card.append(note);
    }
    grid.append(card);
}

async function settleFolio(folio, method, button, reload) {
    const status = await api.get(`/folios/${folio.id}/payment-status`);
    const amount = parseBalance(status.balance);
    if (status.status !== "open" || amount === null || amount <= 0) {
        await window.showCustomAlert("Số dư đã thay đổi. Danh sách sẽ được làm mới trước khi tiếp tục.");
        await reload();
        return;
    }
    if (!await window.showCustomConfirm(`Ghi nhận ${currency(amount)} bằng ${method === "cash" ? "tiền mặt" : "thẻ"}? Số tiền lấy từ số dư hiện tại của máy chủ.`)) return;
    const payload = {amount, payment_method: method, checkout: false};
    const scope = `folio-payment:${folio.id}`;
    const key = idempotencyKey(scope, payload);
    button.disabled = true;
    try {
        await api.post(`/folios/${folio.id}/pay`, payload, {headers: {"Idempotency-Key": key}});
        clearIdempotencyKey(scope);
        await window.showCustomAlert("Máy chủ đã ghi nhận thanh toán. Trả phòng vẫn cần xác nhận riêng.");
        await reload();
    } catch (error) {
        await window.showCustomAlert(error.message || "Chưa thể ghi nhận thanh toán. Có thể thử lại cùng yêu cầu này.");
    } finally { button.disabled = false; }
}

async function showVietQr(folio, row, container, reload) {
    const modal = document.getElementById("global-modal");
    const content = document.getElementById("global-modal-content");
    content.replaceChildren();
    const title = document.createElement("h2"); title.textContent = "Thanh toán qua VietQR";
    const status = document.createElement("p"); status.setAttribute("role", "status"); status.textContent = "Đang lấy mã từ máy chủ…";
    const close = document.createElement("button"); close.type = "button"; close.className = "btn btn-secondary"; close.textContent = "Đóng";
    content.append(title, status, close); modal.classList.add("active");
    const controller = new AbortController();
    let timer;
    const stop = () => { clearTimeout(timer); controller.abort(); };
    close.addEventListener("click", () => { stop(); modal.classList.remove("active"); });
    window.addEventListener("hotel:session-expired", stop, {once: true});
    try {
        const qr = await api.get(`/folios/${folio.id}/vietqr`, {signal: controller.signal});
        if (controller.signal.aborted || !container.contains(row)) return stop();
        const bytes = String(qr.qr_image_base64 || "").replace(/^data:image\/png;base64,/, "");
        if (!/^[A-Za-z0-9+/]+={0,2}$/.test(bytes)) throw new Error("Máy chủ trả về ảnh QR không hợp lệ.");
        const image = document.createElement("img"); image.src = `data:image/png;base64,${bytes}`; image.alt = "Mã VietQR do máy chủ tạo"; image.width = 220; image.height = 220;
        const amount = document.createElement("p"); amount.textContent = `Số tiền: ${currency(qr.amount)}`;
        const reference = document.createElement("p"); reference.textContent = `Nội dung: ${qr.reference_code}`;
        status.textContent = "Đang chờ máy chủ xác nhận giao dịch ngân hàng…";
        content.insertBefore(image, status); content.append(amount, reference);
        const deadline = Date.now() + Math.min(300, Number(qr.expires_in_seconds) || 300) * 1000;
        const poll = async () => {
            if (controller.signal.aborted || !container.contains(row) || !modal.classList.contains("active")) return stop();
            if (Date.now() >= deadline) { status.textContent = "Chưa nhận được xác nhận. Hãy kiểm tra trạng thái với lễ tân trước khi tạo yêu cầu mới."; return stop(); }
            try {
                const payment = await api.get(`/folios/${folio.id}/payment-status`, {signal: controller.signal});
                if (payment.status === "settled" || Number(payment.balance) === 0) {
                    status.textContent = "Máy chủ đã xác nhận folio được thanh toán.";
                    stop();
                    await reload();
                    return;
                }
            } catch (error) {
                if (error.name === "AbortError" || controller.signal.aborted) return;
                if ([401, 403].includes(error.status)) { status.textContent = "Không thể xác minh trạng thái. Vui lòng đăng nhập lại hoặc liên hệ lễ tân."; return stop(); }
            }
            timer = setTimeout(poll, 2500);
        };
        timer = setTimeout(poll, 2500);
    } catch (error) {
        if (!controller.signal.aborted) status.textContent = error.message || "Không thể tạo mã VietQR. Vui lòng thử lại.";
    }
}

async function checkoutBooking(booking, folio, button, reload) {
    const folios = await api.get(`/folios/booking/${encodeURIComponent(booking.id)}`);
    const latest = await Promise.all(folios.map(item => api.get(`/folios/${item.id}/payment-status`)));
    if (latest.some(item => parseBalance(item.balance) !== 0)) {
        await window.showCustomAlert("Có folio chưa được tất toán. Hãy làm mới số dư trước khi trả phòng.");
        await reload();
        return;
    }
    if (!await window.showCustomConfirm(`Xác nhận trả phòng ${booking.id}? Thao tác này chỉ đổi trạng thái lưu trú sau khi mọi folio đã tất toán.`)) return;
    const payload = {amount: 0, payment_method: "checkout", checkout: true};
    const scope = `booking-checkout:${booking.id}`;
    const key = idempotencyKey(scope, payload);
    button.disabled = true;
    try {
        const result = await api.post(`/folios/${folio.id}/pay`, payload, {headers: {"Idempotency-Key": key}});
        if (!result?.checked_out) {
            clearIdempotencyKey(scope);
            await window.showCustomAlert("Máy chủ chưa xác nhận trả phòng. Tải lại để kiểm tra trạng thái.");
            await reload();
            return;
        }
        clearIdempotencyKey(scope);
        await window.showCustomAlert("Máy chủ đã xác nhận trả phòng.");
        await reload();
    } catch (error) {
        await window.showCustomAlert(error.message || "Chưa thể trả phòng. Hãy tải lại trạng thái trước khi thử lại.");
    } finally { button.disabled = false; }
}
