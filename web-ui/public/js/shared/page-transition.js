const transitionKey = 'aurelia-page-transition';
const root = document.documentElement;
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// Clear the gallery lightbox before the index enters the back-forward cache.
// A pageshow-only cleanup runs after the browser may already have painted it.
const dismissGalleryLightbox = () => {
  const lightbox = document.getElementById('lightbox-modal');
  if (!lightbox) return;

  lightbox.classList.remove('active');
  lightbox.hidden = true;
  const image = document.getElementById('lightbox-image');
  if (image) {
    image.removeAttribute('src');
    image.classList.remove('is-zoomed');
  }
  const zoomButton = document.getElementById('lightbox-zoom');
  if (zoomButton) zoomButton.setAttribute('aria-pressed', 'false');
};

window.addEventListener('pagehide', dismissGalleryLightbox);
window.addEventListener('pageshow', dismissGalleryLightbox);

try {
  const incomingDirection = sessionStorage.getItem(transitionKey);
  if (incomingDirection) {
    sessionStorage.removeItem(transitionKey);
    root.classList.add(incomingDirection === 'from-left' ? 'page-transition-enter-left' : 'page-transition-enter-right');
    window.setTimeout(() => root.classList.remove('page-transition-enter-left', 'page-transition-enter-right'), 320);
  }
} catch {
  // Page navigation still works when session storage is unavailable.
}

if (!reducedMotion) {
  document.addEventListener('click', event => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const link = event.target instanceof Element ? event.target.closest('a[href]') : null;
    if (!link || link.target || link.hasAttribute('download')) return;

    const destination = new URL(link.href, window.location.href);
    if (destination.origin !== window.location.origin) return;

    const currentPage = window.location.pathname.split('/').pop() || 'index.html';
    const destinationPage = destination.pathname.split('/').pop() || 'index.html';
    let incomingDirection;

    if (currentPage === 'index.html' && destinationPage === 'portal.html') {
      incomingDirection = 'from-right';
    } else if (currentPage === 'portal.html' && destinationPage === 'index.html') {
      incomingDirection = 'from-left';
    } else {
      return;
    }

    try {
      sessionStorage.setItem(transitionKey, incomingDirection);
    } catch {
      // Continue with normal navigation if the entry animation cannot be stored.
    }
  });
}
