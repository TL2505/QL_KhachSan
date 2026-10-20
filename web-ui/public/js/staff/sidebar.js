const STORAGE_KEY = 'aurelia_staff_sidebar_collapsed';

export function createSidebarState(storage) {
    let collapsed = false;
    try { collapsed = storage?.getItem(STORAGE_KEY) === 'true'; } catch {}
    return {
        get collapsed() { return collapsed; },
        toggle() {
            collapsed = !collapsed;
            try { storage?.setItem(STORAGE_KEY, String(collapsed)); } catch {}
            return collapsed;
        }
    };
}

let active = null;

export function refreshStaffSidebar() {
    active?.refresh();
}

/** Enhance the existing sidebar only after the portal has resolved a Staff role. */
export function configureStaffSidebar(isStaff, documentRef = globalThis.document) {
    if (!documentRef?.body) return;
    if (!isStaff) {
        active?.dispose();
        active = null;
        documentRef.body.classList.remove('staff-sidebar-collapsed');
        return;
    }
    const sidebar = documentRef.getElementById('portal-sidebar');
    const caption = documentRef.getElementById('sidebar-role');
    const nav = documentRef.getElementById('portal-nav');
    // Some non-browser consumers render only auth scaffolding.
    if (!sidebar?.insertBefore || !caption || !nav) return;
    if (active?.sidebar === sidebar) { active.refresh(); return; }
    active?.dispose();
    const windowRef = documentRef.defaultView;
    let storage;
    try { storage = windowRef?.localStorage; } catch {}
    const state = createSidebarState(storage);
    const existingHeading = caption.parentElement?.classList.contains('admin-sidebar-heading') ? caption.parentElement : null;
    const existingToggle = documentRef.getElementById('admin-sidebar-toggle');
    const heading = existingHeading || documentRef.createElement('div');
    heading.classList.add('staff-sidebar-heading');
    const toggle = existingToggle || documentRef.createElement('button');
    const originalToggle = { html: toggle.innerHTML, click: toggle.onclick, attributes: [...toggle.attributes].map(attribute => [attribute.name, attribute.value]) };
    toggle.type = 'button'; toggle.classList.add('staff-sidebar-toggle');
    toggle.setAttribute('data-staff-sidebar-toggle', ''); toggle.onclick = null;
    toggle.setAttribute('aria-controls', 'portal-nav');
    if (!existingHeading) { sidebar.insertBefore(heading, caption); heading.append(caption); }
    if (!existingToggle) heading.append(toggle);
    const tooltip = documentRef.createElement('div');
    tooltip.id = 'staff-sidebar-tooltip'; tooltip.className = 'staff-sidebar-tooltip'; tooltip.hidden = true;
    tooltip.setAttribute('role', 'tooltip'); documentRef.body.appendChild(tooltip);
    const existingAvatar = documentRef.getElementById('account-avatar');
    const account = documentRef.getElementById('portal-account');
    const avatar = existingAvatar || (account ? documentRef.createElement('div') : null);
    if (avatar && !existingAvatar) {
        avatar.id = 'account-avatar'; avatar.className = 'account-avatar';
        account.prepend(avatar);
    }
    const originalAvatarTabIndex = avatar?.getAttribute('tabindex');
    const originalAvatarLabel = avatar?.getAttribute('aria-label');
    let current = null;
    let originalDescription = null;
    const hideTooltip = () => {
        tooltip.hidden = true;
        if (current) {
            if (originalDescription === null) current.removeAttribute('aria-describedby');
            else current.setAttribute('aria-describedby', originalDescription);
        }
        current = null;
    };
    const showTooltip = target => {
        if (!target || (target !== toggle && !state.collapsed && windowRef.innerWidth > 1000)) return;
        const label = target === avatar
            ? [documentRef.getElementById('user-display-name')?.textContent, documentRef.getElementById('account-role')?.textContent].filter(Boolean).join(' · ')
            : target.getAttribute('aria-label');
        if (!label) return;
        hideTooltip(); current = target; originalDescription = target.getAttribute('aria-describedby');
        target.setAttribute('aria-describedby', tooltip.id);
        tooltip.textContent = label; tooltip.hidden = false;
        const rect = target.getBoundingClientRect();
        const size = tooltip.getBoundingClientRect();
        let left = rect.right + 10, top = rect.top + (rect.height - size.height) / 2;
        if (windowRef.innerWidth <= 1000) {
            left = rect.left + (rect.width - size.width) / 2;
            top = rect.bottom + 8;
        }
        if (left + size.width > windowRef.innerWidth - 8) left = rect.left - size.width - 10;
        tooltip.style.left = `${Math.max(8, Math.min(left, windowRef.innerWidth - size.width - 8))}px`;
        tooltip.style.top = `${Math.max(8, Math.min(top, windowRef.innerHeight - size.height - 8))}px`;
    };
    const targetOf = event => event.target.closest?.('#portal-nav button, #logout-button, #account-avatar, [data-staff-sidebar-toggle]');
    const onEnter = event => { const target = targetOf(event); if (target && target !== current) showTooltip(target); };
    const onLeave = event => { if (current && !current.contains(event.relatedTarget)) hideTooltip(); };
    const onKey = event => { if (event.key === 'Escape') hideTooltip(); };
    const onScroll = () => {
        // Keyboard focus may scroll the horizontal/vertical nav into view.
        if (current && documentRef.activeElement === current) showTooltip(current);
        else hideTooltip();
    };
    const refresh = () => {
        hideTooltip();
        nav.querySelectorAll('button[data-view]').forEach(button => {
            const label = button.querySelector('span')?.textContent?.trim();
            if (label) button.setAttribute('aria-label', label);
            button.removeAttribute('title'); // Use the positioned tooltip rather than duplicate native bubbles.
        });
        if (avatar) {
            avatar.textContent = String(documentRef.getElementById('user-display-name')?.textContent || 'A').trim().charAt(0).toUpperCase();
            avatar.tabIndex = 0;
            avatar.setAttribute('aria-label', [documentRef.getElementById('user-display-name')?.textContent,
                documentRef.getElementById('account-role')?.textContent].filter(Boolean).join(' · '));
        }
    };
    const apply = () => {
        documentRef.body.classList.toggle('staff-sidebar-collapsed', state.collapsed);
        toggle.hidden = false; toggle.removeAttribute('title');
        toggle.setAttribute('aria-expanded', String(!state.collapsed));
        toggle.setAttribute('aria-label', state.collapsed ? 'Mở rộng menu' : 'Thu gọn menu');
        toggle.innerHTML = `<i data-lucide="${state.collapsed ? 'panel-left-open' : 'panel-left-close'}" aria-hidden="true"></i>`;
        refresh(); windowRef.lucide?.createIcons();
    };
    const onToggle = () => { state.toggle(); apply(); };
    toggle.addEventListener('click', onToggle);
    sidebar.addEventListener('pointerover', onEnter); sidebar.addEventListener('pointerout', onLeave);
    sidebar.addEventListener('focusin', onEnter); sidebar.addEventListener('focusout', onLeave);
    documentRef.addEventListener('keydown', onKey); documentRef.addEventListener('scroll', onScroll, true);
    windowRef.addEventListener('resize', hideTooltip);
    active = {
        sidebar, refresh,
        dispose() {
            hideTooltip();
            toggle.removeEventListener('click', onToggle);
            sidebar.removeEventListener('pointerover', onEnter); sidebar.removeEventListener('pointerout', onLeave);
            sidebar.removeEventListener('focusin', onEnter); sidebar.removeEventListener('focusout', onLeave);
            documentRef.removeEventListener('keydown', onKey); documentRef.removeEventListener('scroll', onScroll, true);
            windowRef.removeEventListener('resize', hideTooltip);
            nav.querySelectorAll('button[data-view]').forEach(button => {
                const label = button.querySelector('span')?.textContent?.trim();
                if (label) { button.setAttribute('aria-label', label); button.setAttribute('title', label); }
            });
            if (avatar) {
                if (originalAvatarTabIndex === null) avatar.removeAttribute('tabindex'); else avatar.setAttribute('tabindex', originalAvatarTabIndex);
                if (originalAvatarLabel === null) avatar.removeAttribute('aria-label'); else avatar.setAttribute('aria-label', originalAvatarLabel);
            }
            if (!existingAvatar) avatar?.remove();
            if (existingToggle) {
                for (const attribute of [...toggle.attributes]) toggle.removeAttribute(attribute.name);
                for (const [name, value] of originalToggle.attributes) toggle.setAttribute(name, value);
                toggle.innerHTML = originalToggle.html; toggle.onclick = originalToggle.click;
            } else toggle.remove();
            if (existingHeading) heading.classList.remove('staff-sidebar-heading');
            else { sidebar.insertBefore(caption, heading); heading.remove(); }
            tooltip.remove();
            documentRef.body.classList.remove('staff-sidebar-collapsed');
        }
    };
    apply();
}
