export function renderMembershipPlaceholder(container) {
    container.innerHTML = `
        <section class="card" aria-label="Quản lý thành viên">
            <div class="empty-state">
                <span class="empty-state-icon"><i data-lucide="award" aria-hidden="true"></i></span>
                <p>Quản lý thành viên</p>
                <small>Chức năng quản lý thành viên đang được chuẩn bị.</small>
            </div>
        </section>
    `;
    window.lucide?.createIcons?.();
}
