import { fetchWithAuth } from '../fetch-with-auth';

// In the browser, derive the API URL from the current hostname so it works
// on any deployment (localhost, EC2 public IP, custom domain) without needing
// NEXT_PUBLIC_API_URL to be baked in at build time.
const getApiBaseUrl = () => {
    if (typeof window !== 'undefined') {
        // Browser: use same hostname, port 8080
        return process.env.NEXT_PUBLIC_API_URL || `${window.location.protocol}//${window.location.hostname}:8080/api`;
    }
    // Server-side (SSR): use env var or fallback
    return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
};

export const API_BASE_URL = getApiBaseUrl();

// --- Page type (Spring Data Page response) ---
export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number; // current page (0-based)
    first: boolean;
    last: boolean;
    empty: boolean;
}

// Shared response handler used by all modules
export const handleResponse = async (res: Response) => {
    if (!res.ok) {
        const error = await res.text();
        throw new Error(error || 'Network response was not ok');
    }
    // Handle 204 No Content for DELETE
    if (res.status === 204 || res.headers.get('content-length') === '0') {
        return null;
    }
    return res.json();
};

// Re-export fetchWithAuth for convenience
export { fetchWithAuth };
