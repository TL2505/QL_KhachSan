export function savedTheme(storage = globalThis.localStorage) {
  try { return storage?.getItem('theme') === 'dark' ? 'dark' : 'light'; }
  catch { return 'light'; }
}
export function applyTheme(theme, {root = document.documentElement, storage = globalThis.localStorage} = {}) {
  const value = theme === 'dark' ? 'dark' : 'light';
  root.dataset.theme = value;
  root.classList.toggle('light-theme', value === 'light');
  root.style.colorScheme = value;
  try { storage?.setItem('theme', value); } catch { /* Theme remains usable without storage. */ }
  return value;
}
export function initTheme() {
  applyTheme(savedTheme());
  const toggles = [...document.querySelectorAll('[data-theme-toggle], #theme-toggle')];
  const update = () => {
    const dark = document.documentElement.dataset.theme === 'dark';
    for (const toggle of toggles) {
      toggle.setAttribute('aria-label',dark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối');
      toggle.setAttribute('aria-pressed',String(dark));
      if(toggle.dataset.textLabel === 'true') toggle.textContent=dark ? 'Giao diện sáng' : 'Giao diện tối';
    }
    const icon=document.getElementById('theme-icon');
    if(icon)icon.setAttribute('data-lucide',dark ? 'sun' : 'moon');
    globalThis.lucide?.createIcons();
  };
  for(const toggle of toggles)toggle.addEventListener('click',()=>{
    applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');update();
  });
  update();
}
if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initTheme, {once:true});
  else initTheme();
}
