import { fetchWithAuth } from './fetch-with-auth';
import { API_BASE_URL, handleResponse } from './common';
import type { Product } from './products';

// --- Types ---
export interface Tank {
    id: number;
    name: string;
    capacity: number;
    availableStock: number;
    thresholdStock?: number;
    product: Product;
    active: boolean;
}

export interface Pump {
    id: number;
    name: string;
    active: boolean;
}

export interface Nozzle {
    id: number;
    nozzleName: string;
    nozzleNumber?: string;
    nozzleCompany?: string;
    stampingExpiryDate?: string;
    tank: Tank;
    pump: Pump;
    active: boolean;
}

export interface StockAlert {
    id: number;
    tank: Tank;
    availableStock: number;
    thresholdStock: number;
    message: string;
    active: boolean;
    acknowledgedAt?: string;
    acknowledgedBy?: string;
    notifiedVia?: string;
    createdAt: string;
}

export interface NotificationConfig {
    id?: number;
    alertType: string;
    enabled: boolean;
    notifyRoles: string[];
    channels: string[];
}

export interface RoleOption {
    id: number;
    roleType: string;
}

// --- Tanks ---
export const getTanks = (): Promise<Tank[]> =>
    fetchWithAuth(`${API_BASE_URL}/tanks`).then(handleResponse);

export const createTank = (tank: Partial<Tank>): Promise<Tank> =>
    fetchWithAuth(`${API_BASE_URL}/tanks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tank),
    }).then(handleResponse);

export const updateTank = (id: number, tank: Partial<Tank>): Promise<Tank> =>
    fetchWithAuth(`${API_BASE_URL}/tanks/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tank),
    }).then(handleResponse);

export const deleteTank = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/tanks/${id}`, { method: 'DELETE' }).then(handleResponse);

export const getLowStockTanks = (): Promise<Tank[]> =>
    fetchWithAuth(`${API_BASE_URL}/tanks/low-stock`).then(handleResponse);

// --- Stock Alerts ---
export const getActiveStockAlerts = (): Promise<StockAlert[]> =>
    fetchWithAuth(`${API_BASE_URL}/stock-alerts`).then(handleResponse);

export const checkStockAlerts = (): Promise<StockAlert[]> =>
    fetchWithAuth(`${API_BASE_URL}/stock-alerts/check`, { method: 'POST' }).then(handleResponse);

export const acknowledgeStockAlert = (id: number): Promise<StockAlert> =>
    fetchWithAuth(`${API_BASE_URL}/stock-alerts/${id}/acknowledge`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
    }).then(handleResponse);

export const acknowledgeAllStockAlerts = (): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/stock-alerts/acknowledge-all`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
    }).then(handleResponse);

// --- Notification Config ---
export const getNotificationConfigs = (): Promise<NotificationConfig[]> =>
    fetchWithAuth(`${API_BASE_URL}/notification-config`).then(handleResponse);

export const getNotificationConfigByType = (alertType: string): Promise<NotificationConfig> =>
    fetchWithAuth(`${API_BASE_URL}/notification-config/${alertType}`).then(handleResponse);

export const saveNotificationConfig = (config: NotificationConfig): Promise<NotificationConfig> =>
    fetchWithAuth(`${API_BASE_URL}/notification-config`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
    }).then(handleResponse);

export const getAvailableRoles = (): Promise<RoleOption[]> =>
    fetchWithAuth(`${API_BASE_URL}/notification-config/roles`).then(handleResponse);

// --- Pumps ---
export const getPumps = (): Promise<Pump[]> =>
    fetchWithAuth(`${API_BASE_URL}/pumps`).then(handleResponse);

export const createPump = (pump: Partial<Pump>): Promise<Pump> =>
    fetchWithAuth(`${API_BASE_URL}/pumps`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(pump),
    }).then(handleResponse);

export const updatePump = (id: number, pump: Partial<Pump>): Promise<Pump> =>
    fetchWithAuth(`${API_BASE_URL}/pumps/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(pump),
    }).then(handleResponse);

export const deletePump = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/pumps/${id}`, { method: 'DELETE' }).then(handleResponse);

// --- Nozzles ---
export const getNozzles = (): Promise<Nozzle[]> =>
    fetchWithAuth(`${API_BASE_URL}/nozzles`).then(handleResponse);

export const createNozzle = (nozzle: Partial<Nozzle>): Promise<Nozzle> =>
    fetchWithAuth(`${API_BASE_URL}/nozzles`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(nozzle),
    }).then(handleResponse);

export const updateNozzle = (id: number, nozzle: Partial<Nozzle>): Promise<Nozzle> =>
    fetchWithAuth(`${API_BASE_URL}/nozzles/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(nozzle),
    }).then(handleResponse);

export const deleteNozzle = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/nozzles/${id}`, { method: 'DELETE' }).then(handleResponse);
