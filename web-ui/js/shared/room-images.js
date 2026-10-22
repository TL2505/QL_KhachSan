// Attach before checking cached failures, which may precede listener registration.
// Replace only room photos; payment QR images must keep their own error handling.
export function bindRoomImageFallbacks(root) {
  root.querySelectorAll('img').forEach(img => {
    const replace = () => {
      const placeholder = img.ownerDocument.createElement('div');
      placeholder.className = img.classList.contains('discovery-detail-image')
        ? 'discovery-detail-no-image' : 'room-image-placeholder';
      const emblem = img.ownerDocument.createElement('span');
      emblem.setAttribute('aria-hidden', 'true');
      emblem.textContent = 'A';
      const caption = img.ownerDocument.createElement('small');
      caption.textContent = 'Ảnh phòng đang cập nhật';
      placeholder.append(emblem, caption);
      img.replaceWith(placeholder);
    };
    img.addEventListener('error', replace, { once: true });
    if (img.complete && img.naturalWidth === 0) replace();
  });
}
