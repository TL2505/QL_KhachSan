// Placeholder only: this view does not send messages or create support requests.
export function renderSupport(container) {
  if (!container) return;
  container.innerHTML = `
    <section class="card customer-support-placeholder" aria-labelledby="customer-support-title">
      <div class="card-body">
        <span class="customer-support-icon" aria-hidden="true"><i data-lucide="life-buoy"></i></span>
        <span class="badge badge-outline">Sắp ra mắt</span>
        <h2 id="customer-support-title" class="card-title">Hỗ trợ khách hàng</h2>
        <p>Khu vực hỗ trợ đang được chuẩn bị. Bạn sẽ có thể tìm hướng dẫn và theo dõi yêu cầu hỗ trợ tại đây khi tính năng ra mắt.</p>
        <p class="customer-support-hint">Trong thời gian này, vui lòng liên hệ lễ tân khi cần hỗ trợ về kỳ nghỉ.</p>
      </div>
    </section>
  `;
  window.lucide?.createIcons?.();
}
