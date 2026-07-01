import { api } from "../../api.js";

function renderStars(rating) {
    let html = '<span class="star-rating">';
    for (let i = 1; i <= 5; i++) {
        html += `<span class="star ${i <= rating ? 'filled' : 'empty'}">★</span>`;
    }
    html += '</span>';
    return html;
}

function starBadge(rating) {
    return `<span class="star-badge star-badge-${rating}">${rating}★</span>`;
}

function truncateComment(text, maxLen = 80) {
    if (!text) return '<span style="color:var(--text-muted);font-style:italic;">Không có nội dung</span>';
    if (text.length <= maxLen) return `<span class="comment-text">${text}</span>`;
    const short = text.substring(0, maxLen);
    const full  = text;
    return `
        <span class="comment-text">
            <span class="comment-short">${short}...</span>
            <span class="comment-full" style="display:none;">${full}</span>
            <span class="comment-more" onclick="
                const s = this.previousElementSibling;
                const p = s.previousElementSibling;
                if (p.style.display === 'none') { p.style.display=''; s.style.display='none'; this.textContent='Thu gọn'; }
                else { p.style.display='none'; s.style.display=''; this.textContent='Xem thêm'; }
            ">Xem thêm</span>
        </span>`;
}

export async function renderReviews(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Quản Lý Đánh Giá Khách Hàng</h1>
                <p>Kiểm duyệt các ý kiến đóng góp và xếp hạng sao phòng nghỉ.</p>
            </div>
            <div style="display:flex;align-items:center;gap:8px;background:var(--bg-card);border:1px solid var(--border);border-radius:var(--radius-sm);padding:8px 16px;">
                <i data-lucide="star" style="width:16px;height:16px;color:var(--primary);"></i>
                <span style="font-size:13px;color:var(--text-muted);">Tổng quan đánh giá</span>
            </div>
        </div>

        <!-- Summary stats -->
        <div id="reviews-summary-wrap"></div>

        <!-- Table -->
        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th style="width:50px;">#</th>
                            <th>Phòng</th>
                            <th>Xếp hạng</th>
                            <th>Nội dung bình luận</th>
                            <th style="text-align:right;">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="admin-reviews-body">
                        <tr><td colspan="5" style="text-align:center;padding:40px;color:var(--text-muted);">
                            <i data-lucide="loader" style="width:16px;height:16px;"></i> Đang tải đánh giá...
                        </td></tr>
                    </tbody>
                </table>
            </div>
            <div id="pagination-admin-reviews"></div>
        </div>
    `;
    lucide.createIcons();

    const loadReviews = async () => {
        const body = document.getElementById("admin-reviews-body");
        try {
            const list = await api.get("/reviews");

            // Build summary
            const summaryWrap = document.getElementById("reviews-summary-wrap");
            if (list && list.length > 0) {
                const avg = (list.reduce((s, r) => s + r.rating, 0) / list.length).toFixed(1);
                const dist = [5, 4, 3, 2, 1].map(s => ({
                    star: s,
                    count: list.filter(r => r.rating === s).length,
                    pct: Math.round(list.filter(r => r.rating === s).length / list.length * 100)
                }));
                summaryWrap.innerHTML = `
                    <div class="reviews-summary">
                        <div style="text-align:center;min-width:90px;">
                            <div class="reviews-avg-score">${avg}</div>
                            <div style="margin:4px 0 2px;">${renderStars(Math.round(parseFloat(avg)))}</div>
                            <div class="reviews-avg-label">${list.length} đánh giá</div>
                        </div>
                        <div style="width:1px;background:var(--border);align-self:stretch;"></div>
                        <div class="reviews-dist">
                            ${dist.map(d => `
                                <div class="reviews-dist-row">
                                    <span style="width:14px;text-align:right;color:var(--text-muted);">${d.star}</span>
                                    <span style="color:#fbbf24;font-size:11px;">★</span>
                                    <div class="reviews-dist-bar-wrap">
                                        <div class="reviews-dist-bar-fill" style="width:0%" data-target="${d.pct}"></div>
                                    </div>
                                    <span style="width:28px;text-align:right;color:var(--text-muted);">${d.count}</span>
                                </div>
                            `).join('')}
                        </div>
                        <div style="flex:1;display:flex;flex-direction:column;gap:8px;min-width:120px;">
                            <div style="font-size:13px;font-weight:600;">Phân phối</div>
                            ${dist.map(d => `
                                <div style="display:flex;align-items:center;gap:8px;font-size:12px;">
                                    ${starBadge(d.star)}
                                    <span style="color:var(--text-muted);">${d.pct}%</span>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                `;
                // Animate bars
                setTimeout(() => {
                    summaryWrap.querySelectorAll('.reviews-dist-bar-fill').forEach(el => {
                        el.style.width = el.dataset.target + '%';
                    });
                }, 100);
            } else {
                summaryWrap.innerHTML = '';
            }

            allReviews = list;
            currentPage = 1;
            renderTable();
        } catch (e) {
            const body = document.getElementById("admin-reviews-body");
            body.innerHTML = `<tr><td colspan="5" style="color:var(--danger);padding:20px;">${e.message}</td></tr>`;
        }
    };

    let allReviews = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = () => {
        const body = document.getElementById("admin-reviews-body");
        body.innerHTML = "";

        if (!allReviews || allReviews.length === 0) {
            body.innerHTML = `
                <tr><td colspan="5">
                    <div class="empty-state">
                        <div class="empty-state-icon"><i data-lucide="message-square" style="width:28px;height:28px;"></i></div>
                        <p>Chưa có đánh giá nào</p>
                        <small>Các đánh giá từ khách hàng sẽ hiện ở đây.</small>
                    </div>
                </td></tr>`;
            lucide.createIcons();
            const paginationContainer = document.getElementById("pagination-admin-reviews");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }

        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const paginatedList = allReviews.slice(start, end);

        paginatedList.forEach((r, i) => {
            const tr = document.createElement("tr");
                tr.className = "table-row-animate";
                tr.style.animationDelay = `${i * 0.04}s`;
                tr.innerHTML = `
                    <td style="color:var(--text-muted);font-size:13px;">${r.id}</td>
                    <td>
                        <span class="room-pill">
                            <i data-lucide="door-open" style="width:12px;height:12px;"></i>
                            Phòng #${r.roomId}
                        </span>
                    </td>
                    <td>
                        <div style="display:flex;align-items:center;gap:8px;">
                            ${renderStars(r.rating)}
                            ${starBadge(r.rating)}
                        </div>
                    </td>
                    <td style="max-width:320px;">${truncateComment(r.comment)}</td>
                    <td style="text-align:right;">
                        <div class="action-btns" style="justify-content:flex-end;">
                            <button class="btn-icon danger btn-delete-review" data-id="${r.id}" title="Xóa đánh giá">
                                <i data-lucide="trash-2" style="width:14px;height:14px;"></i>
                            </button>
                        </div>
                    </td>
                `;

                tr.querySelector(".btn-delete-review").addEventListener("click", async () => {
                    const modal   = document.getElementById("global-modal");
                    const content = document.getElementById("global-modal-content");
                    modal.classList.add("active");
                    content.innerHTML = `
                        <div class="modal-header">
                            <h2 style="color:var(--danger);">Xác nhận xóa đánh giá</h2>
                            <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
                        </div>
                        <div style="text-align:center;padding:12px 0 24px;">
                            <div style="width:56px;height:56px;border-radius:50%;background:rgba(239,68,68,0.15);display:flex;align-items:center;justify-content:center;margin:0 auto 16px;">
                                <i data-lucide="message-square-x" style="width:24px;height:24px;color:var(--danger);"></i>
                            </div>
                            <p style="font-size:15px;font-weight:600;margin-bottom:8px;">Xóa đánh giá ${renderStars(r.rating)}?</p>
                            <p style="font-size:13px;color:var(--text-muted);">Hành động này không thể hoàn tác và sẽ xóa bình luận công khai.</p>
                        </div>
                        <div class="flex-row" style="justify-content:flex-end;">
                            <button class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy bỏ</button>
                            <button class="btn btn-danger" id="confirm-delete-review">Xóa đánh giá</button>
                        </div>
                    `;
                    lucide.createIcons();
                    document.getElementById("confirm-delete-review").addEventListener("click", async () => {
                        try {
                            await api.delete(`/reviews/${r.id}`);
                            closeModal("global-modal");
                            loadReviews();
                        } catch (err) {
                            window.showCustomAlert("Lỗi xóa đánh giá: " + err.message);
                        }
                    });
                });

                body.appendChild(tr);
            });
            lucide.createIcons();

            if (window.renderPaginationComponent) {
                window.renderPaginationComponent(
                    "pagination-admin-reviews",
                    allReviews.length,
                    itemsPerPage,
                    currentPage,
                    (newPage) => {
                        currentPage = newPage;
                        renderTable();
                    },
                    (newItemsPerPage) => {
                        itemsPerPage = newItemsPerPage;
                        currentPage = 1;
                        renderTable();
                    }
                );
            }
    };
    
    loadReviews();
}
