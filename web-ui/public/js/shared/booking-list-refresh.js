// Reuse each view's loader on return to the tab. No polling, cached rows or push channel.
export function refreshBookingListOnReturn(element, refresh) {
    const documentRef = element.ownerDocument;
    const windowRef = documentRef?.defaultView;
    if (!windowRef) return () => {};
    let pending = false;
    let stopped = false;
    const stop = () => {
        stopped = true;
        windowRef.removeEventListener('focus', onReturn);
        documentRef.removeEventListener('visibilitychange', onReturn);
        observer?.disconnect();
    };
    const onReturn = async () => {
        if (stopped) return;
        if (!element.isConnected) { stop(); return; }
        if (documentRef.visibilityState === 'hidden' || pending) return;
        pending = true;
        try { await refresh(); }
        finally { pending = false; }
    };
    const observer = windowRef.MutationObserver ? new windowRef.MutationObserver(() => {
        if (!element.isConnected) stop();
    }) : null;
    windowRef.addEventListener('focus', onReturn);
    documentRef.addEventListener('visibilitychange', onReturn);
    observer?.observe(documentRef.documentElement, { childList: true, subtree: true });
    return stop;
}
