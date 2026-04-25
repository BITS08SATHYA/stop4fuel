import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

export type ConversationType = 'DIRECT';

export interface Contact {
    userId: number;
    name: string;
    role: string | null;
    designation: string | null;
    status: string | null;
}

export interface Conversation {
    id: number;
    type: ConversationType;
    createdAt: string;
    updatedAt: string;
    lastMessageAt: string | null;
    lastMessagePreview: string | null;
    unreadCount: number;
    otherParticipant: Contact | null;
}

export interface Message {
    id: number;
    conversationId: number;
    senderUserId: number;
    senderName: string;
    text: string;
    createdAt: string;
}

export const listConversations = async (): Promise<Conversation[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/messaging/conversations`);
    return handleResponse(res);
};

export const startDirectConversation = async (recipientUserId: number): Promise<Conversation> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/messaging/conversations`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ recipientUserId }),
    });
    return handleResponse(res);
};

export const listMessages = async (
    conversationId: number,
    opts?: { before?: number; size?: number }
): Promise<Message[]> => {
    const params = new URLSearchParams();
    if (opts?.before != null) params.set('before', String(opts.before));
    if (opts?.size != null) params.set('size', String(opts.size));
    const qs = params.toString();
    const url = `${API_BASE_URL}/messaging/conversations/${conversationId}/messages${qs ? `?${qs}` : ''}`;
    const res = await fetchWithAuth(url);
    return handleResponse(res);
};

export const sendMessage = async (conversationId: number, text: string): Promise<Message> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/messaging/conversations/${conversationId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
    });
    return handleResponse(res);
};

export const markConversationRead = async (conversationId: number): Promise<void> => {
    await fetchWithAuth(`${API_BASE_URL}/messaging/conversations/${conversationId}/read`, {
        method: 'POST',
    });
};

export const listContacts = async (): Promise<Contact[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/messaging/contacts`);
    return handleResponse(res);
};

export const getUnreadCount = async (): Promise<number> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/messaging/unread-count`);
    const body = (await handleResponse(res)) as { count?: number } | null;
    return body?.count ?? 0;
};
