// Inbox rows contain the latest message; its sender may be the staff member.
// Resolve the conversation peer without changing the actual message author.
export function normalizeSupportInboxes(rows, staffId) {
    const conversations = new Map();
    for (const item of Array.isArray(rows) ? rows : []) {
        const peer = Number(String(item.senderId) === String(staffId) ? item.receiverId : item.senderId);
        if (!Number.isSafeInteger(peer) || peer <= 0 || String(peer) === String(staffId)) continue;
        if (!conversations.has(peer)) conversations.set(peer, { ...item, customerId: peer });
    }
    return [...conversations.values()];
}
