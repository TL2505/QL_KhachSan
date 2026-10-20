const THEME_KEY = 'theme';
const LEGACY_CUSTOMER_THEME_KEY = 'aurelia_customer_theme';
const THEMES = ['system', 'light', 'dark'];

export function savedTheme(storage = globalThis.localStorage) {
  try {
    const saved = storage?.getItem(THEME_KEY);
    if (THEMES.includes(saved)) return saved;
    const legacy = storage?.getItem(LEGACY_CUSTOMER_THEME_KEY);
    return THEMES.includes(legacy) ? legacy : 'system';
  } catch {
    return 'system';
  }
}

function systemTheme(media = globalThis.matchMedia?.('(prefers-color-scheme: dark)')) {
  return media?.matches ? 'dark' : 'light';
}

export function applyTheme(theme, {
  root = document.documentElement,
  storage = globalThis.localStorage,
  media,
  persist = true
} = {}) {
  const preference = THEMES.includes(theme) ? theme : 'system';
  const resolved = preference === 'system' ? systemTheme(media) : preference;
  root.dataset.theme = resolved;
  root.dataset.themePreference = preference;
  root.classList.toggle('light-theme', resolved === 'light');
  root.style.colorScheme = resolved;
  const themeColor = root.ownerDocument?.querySelector('meta[name="theme-color"]');
  if (themeColor) themeColor.content = resolved === 'dark' ? '#0b0f17' : '#f6f3ed';
  if (persist) {
    try {
      storage?.setItem(THEME_KEY, preference);
      storage?.removeItem(LEGACY_CUSTOMER_THEME_KEY);
    } catch {
      // Keep the selected theme active for this page when storage is unavailable.
    }
  }
  return preference;
}

export function nextTheme(theme) {
  const index = THEMES.indexOf(theme);
  return THEMES[(index + 1) % THEMES.length];
}

export function initTheme({
  documentRef = document,
  storage = globalThis.localStorage,
  media = globalThis.matchMedia?.('(prefers-color-scheme: dark)')
} = {}) {
  const root = documentRef.documentElement;
  const toggles = [...documentRef.querySelectorAll('[data-theme-toggle]')];
  const update = preference => {
    const current = preference || root.dataset.themePreference || 'system';
    const resolved = root.dataset.theme;
    const icon = current === 'system' ? 'sun-moon' : current === 'dark' ? 'moon' : 'sun';
    const next = nextTheme(current);
    const labels = {
      system: 'tự động theo thiết bị',
      light: 'sáng',
      dark: 'tối'
    };
    for (const toggle of toggles) {
      const english = root.lang === 'en';
      const englishLabels = { system: 'system', light: 'light', dark: 'dark' };
      toggle.setAttribute('aria-label', english
        ? `Current theme: ${englishLabels[current]}. Switch to ${englishLabels[next]} theme.`
        : `Giao diện hiện tại: ${labels[current]}. Bấm để chuyển sang ${labels[next]}.`);
      toggle.setAttribute('title', english ? `Theme: ${englishLabels[current]}` : `Giao diện: ${labels[current]}`);
      toggle.dataset.themePreference = current;
      const iconNode = toggle.querySelector('[data-theme-icon]');
      if (iconNode) iconNode.setAttribute('data-lucide', icon);
    }
    documentRef.documentElement.dataset.theme = resolved;
    globalThis.lucide?.createIcons();
  };

  documentRef.addEventListener?.('aurelia:languagechange', () => update());
  const current = savedTheme(storage);
  applyTheme(current, { root, storage, media, persist: false });
  update(current);
  for (const toggle of toggles) {
    toggle.addEventListener('click', () => {
      const preference = nextTheme(root.dataset.themePreference || 'system');
      applyTheme(preference, { root, storage, media });
      update(preference);
    });
  }

  const onPreferenceChange = () => {
    if (root.dataset.themePreference !== 'system') return;
    applyTheme('system', { root, storage, media, persist: false });
    update('system');
  };
  media?.addEventListener?.('change', onPreferenceChange);
  if (media?.addEventListener == null) media?.addListener?.(onPreferenceChange);

  documentRef.defaultView?.addEventListener('storage', event => {
    if (event.key !== THEME_KEY && event.key !== LEGACY_CUSTOMER_THEME_KEY) return;
    const preference = savedTheme(storage);
    applyTheme(preference, { root, storage, media, persist: false });
    update(preference);
  });
}

if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => initTheme(), { once: true });
  else initTheme();
}
