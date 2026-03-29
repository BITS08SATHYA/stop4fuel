import { fetchWithAuth } from './fetch-with-auth';
import { API_BASE_URL } from './common';

export const downloadDailySalesReport = (fromDate: string, toDate: string, format: 'pdf' | 'excel'): Promise<Blob> => {
    const endpoint = format === 'pdf' ? 'pdf' : 'excel';
    return fetchWithAuth(`${API_BASE_URL}/reports/daily-sales/${endpoint}?fromDate=${fromDate}&toDate=${toDate}`).then(res => {
        if (!res.ok) throw new Error('Report generation failed');
        return res.blob();
    });
};

export const downloadTankInventoryReport = (fromDate: string, toDate: string, format: 'pdf' | 'excel'): Promise<Blob> => {
    const endpoint = format === 'pdf' ? 'pdf' : 'excel';
    return fetchWithAuth(`${API_BASE_URL}/reports/tank-inventory/${endpoint}?fromDate=${fromDate}&toDate=${toDate}`).then(res => {
        if (!res.ok) throw new Error('Report generation failed');
        return res.blob();
    });
};

export const downloadCustomerBalanceReport = (format: 'pdf' | 'excel'): Promise<Blob> => {
    const endpoint = format === 'pdf' ? 'pdf' : 'excel';
    return fetchWithAuth(`${API_BASE_URL}/reports/customer-balance/${endpoint}`).then(res => {
        if (!res.ok) throw new Error('Report generation failed');
        return res.blob();
    });
};
