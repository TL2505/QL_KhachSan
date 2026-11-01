/**
 * WCAG 2.2 AA Compliant Operator Keyboard Acceleration
 * Location: web-ui/js/staff/hotkeys.js
 * 
 * Provides native, collision-free keyboard shortcuts for high-throughput front desk POS operations:
 * - Alt + N: Open Walk-in booking modal
 * - Alt + F: Focus Room/Guest search field
 * - Alt + C: Quickly open Checkout screen
 * - Escape: Close all currently open modals
 */

export function handleStaffHotkey(e, options = {}) {
    // Suppress shortcut collision if operator is typing in text input (unless Alt modifier or Escape)
    const activeEl = typeof document !== 'undefined' ? document.activeElement : null;
    const isTyping = activeEl && ['INPUT', 'TEXTAREA', 'SELECT'].includes(activeEl.tagName);

    // Alt + N: Create New Walk-in Booking
    if (e.altKey && (e.key === 'n' || e.key === 'N')) {
        e.preventDefault();
        const walkinBtn = typeof document !== 'undefined' ? (
            document.getElementById('btn-new-walkin') 
            || document.getElementById('btn-walkin')
            || document.getElementById('btn-add-customer')
            || document.querySelector('[data-action="walk-in"]')
            || document.querySelector('[data-action="new-booking"]')
        ) : null;
            
        if (walkinBtn && typeof walkinBtn.click === 'function') {
            walkinBtn.click();
        } else if (typeof window !== 'undefined' && typeof window.openWalkinModal === 'function') {
            window.openWalkinModal();
        } else if (typeof options.onWalkin === 'function') {
            options.onWalkin();
        } else if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
            window.dispatchEvent(new CustomEvent('staff:open-walkin'));
        }
        return true;
    }

    // Alt + F: Room Matrix Search & Filter
    if (e.altKey && (e.key === 'f' || e.key === 'F')) {
        e.preventDefault();
        const searchInput = typeof document !== 'undefined' ? (
            document.getElementById('staff-search-input')
            || document.getElementById('sc-search') 
            || document.getElementById('staff-cust-search')
            || document.getElementById('room-search')
            || document.getElementById('filter-input')
            || document.querySelector('input[type="search"]')
            || document.querySelector('.search-input')
        ) : null;
            
        if (searchInput && typeof searchInput.focus === 'function') {
            searchInput.focus();
            if (typeof searchInput.select === 'function') {
                searchInput.select();
            }
        } else if (typeof options.onSearchFocus === 'function') {
            options.onSearchFocus();
        } else if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
            window.dispatchEvent(new CustomEvent('staff:focus-search'));
        }
        return true;
    }

    // Alt + C: Express Checkout Shortcut
    if (e.altKey && (e.key === 'c' || e.key === 'C')) {
        e.preventDefault();
        const checkoutNav = typeof document !== 'undefined' ? (
            document.getElementById('btn-checkout')
            || document.querySelector('[data-view="checkout"]')
            || document.querySelector('[data-tab="checkout"]')
            || document.getElementById('nav-checkout')
        ) : null;
            
        if (checkoutNav && typeof checkoutNav.click === 'function') {
            checkoutNav.click();
        } else if (typeof window !== 'undefined' && typeof window.loadStaffView === 'function') {
            window.loadStaffView('checkout');
        } else if (typeof options.onCheckout === 'function') {
            options.onCheckout();
        } else if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
            window.dispatchEvent(new CustomEvent('staff:open-checkout'));
        }
        return true;
    }

    // Escape: Defensive Modal Dismissal
    if (e.key === 'Escape' || e.key === 'Esc') {
        const activeModals = typeof document !== 'undefined' ? document.querySelectorAll(
            '.modal-overlay:not(.hidden), .modal.active, #global-modal.active, .portal-modal.active, [role="dialog"].active, dialog[open]'
        ) : [];
        let closed = false;
        activeModals.forEach(modal => {
            closed = true;
            const closeBtn = modal.querySelector('.modal-close, [data-action="close"], .btn-close');
            if (closeBtn && typeof closeBtn.click === 'function') {
                closeBtn.click();
            } else if (typeof modal.close === 'function') {
                modal.close();
            } else {
                modal.classList.remove('active');
                modal.classList.add('hidden');
                modal.setAttribute('aria-hidden', 'true');
            }
        });
        if (closed || (typeof document !== 'undefined' && document.getElementById('global-modal')?.classList?.contains('active'))) {
            e.preventDefault();
            if (typeof window !== 'undefined' && typeof window.closeModal === 'function') {
                try { window.closeModal('global-modal'); } catch (_) {}
            }
            if (typeof options.onModalClose === 'function') {
                options.onModalClose();
            }
            if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
                window.dispatchEvent(new CustomEvent('staff:modals-closed'));
            }
            return true;
        }
    }

    return false;
}

let hotkeysListener = null;

export function initStaffHotkeys(options = {}) {
    if (hotkeysListener && typeof window !== 'undefined') {
        window.removeEventListener('keydown', hotkeysListener);
    }
    hotkeysListener = (e) => handleStaffHotkey(e, options);
    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
        window.addEventListener('keydown', hotkeysListener);
    }
    return () => {
        if (hotkeysListener && typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
            window.removeEventListener('keydown', hotkeysListener);
            hotkeysListener = null;
        }
    };
}

// Auto-initialize when loaded in browser window
if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    initStaffHotkeys();
}
