import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

// --- Types ---
export interface ChatMessage {
    role: 'user' | 'assistant';
    content: string;
}

export interface ChatResponse {
    answer: string;
}

export interface Insight {
    category: string;
    title: string;
    detail: string;
    severity: 'positive' | 'info' | 'warning' | 'critical';
}

export interface InsightsResponse {
    insights: Insight[];
    generatedAt: string;
}

// --- API Calls ---
export const chat = (
    message: string,
    history: ChatMessage[]
): Promise<ChatResponse> =>
    fetchWithAuth(`${API_BASE_URL}/ai-analytics/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message, history }),
    }).then(handleResponse);

export const fetchInsights = (): Promise<InsightsResponse> =>
    fetchWithAuth(`${API_BASE_URL}/ai-analytics/insights`).then(handleResponse);
