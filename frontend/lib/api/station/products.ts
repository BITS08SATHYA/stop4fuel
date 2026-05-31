import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

// --- Types ---
export interface Supplier {
    id: number;
    name: string;
    contactPerson?: string;
    phone?: string;
    email?: string;
    active: boolean;
}

export interface OilType {
    id: number;
    name: string;
    description?: string;
    active: boolean;
}

export interface GradeType {
    id: number;
    oilType?: { id: number; name: string };
    name: string;
    description?: string;
    active: boolean;
}

export interface Product {
    id: number;
    name: string;
    hsnCode: string;
    price: number;
    category: string;
    unit: string;
    volume?: number;
    brand?: string;
    gstRate?: number;
    fuelFamily?: string;
    discountRate?: number;
    active: boolean;
    supplier?: { id: number; name: string };
    oilType?: { id: number; name: string };
    gradeType?: { id: number; name: string };
    scid?: number;
}

/**
 * Mirror of the backend {@code FuelClassifier} — the single source of truth for
 * deciding whether a product is fuel (XP / MS / HSD) or non-fuel (OTHER).
 * Keep in sync with backend/.../service/FuelClassifier.java.
 */
export type FuelLabel = "XP" | "MS" | "HSD" | "OTHER";

export function classifyFuel(p: Pick<Product, "name" | "fuelFamily" | "gradeType">): FuelLabel {
    const fuelFamily = (p.fuelFamily || "").toUpperCase();
    const name = (p.name || "").toUpperCase();
    const gradeName = (p.gradeType?.name || "").toUpperCase();
    if (fuelFamily === "DIESEL" || name.includes("DIESEL") || name === "HSD") return "HSD";
    const petrol =
        fuelFamily === "PETROL" || name.includes("PETROL") || name === "MS" || name.includes("XTRA");
    if (!petrol) return "OTHER";
    const isPremium =
        name.includes("XTRA") ||
        name.includes("PREMIUM") ||
        name === "XP" ||
        gradeName.includes("XTRA") ||
        gradeName.includes("PREMIUM");
    return isPremium ? "XP" : "MS";
}

export const isFuelProduct = (p: Pick<Product, "name" | "fuelFamily" | "gradeType">): boolean =>
    classifyFuel(p) !== "OTHER";

// Products
export const getActiveProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/active`).then(handleResponse);

export const getActiveNonFuelProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/active/non-fuel`).then(handleResponse);

export const getFuelProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/category/Fuel`).then(handleResponse);

export const getTopSellingProducts = (days = 30, limit = 8): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/top-selling?days=${days}&limit=${limit}`).then(handleResponse);

export const getProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products`).then(handleResponse);

export const createProduct = (product: Partial<Product>): Promise<Product> =>
    fetchWithAuth(`${API_BASE_URL}/products`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(product),
    }).then(handleResponse);

export const updateProduct = (id: number, product: Partial<Product>): Promise<Product> =>
    fetchWithAuth(`${API_BASE_URL}/products/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(product),
    }).then(handleResponse);

export const deleteProduct = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/products/${id}`, { method: 'DELETE' }).then(handleResponse);

// Product Price History
export interface ProductPriceHistoryEntry {
    id: number;
    effectiveDate: string;
    productId: number;
    productName: string;
    price: number;
    createdAt?: string;
    updatedAt?: string;
}

export const getProductPriceHistory = (params?: {
    productId?: number;
    from?: string;
    to?: string;
}): Promise<ProductPriceHistoryEntry[]> => {
    const q = new URLSearchParams();
    if (params?.productId != null) q.set('productId', String(params.productId));
    if (params?.from) q.set('from', params.from);
    if (params?.to) q.set('to', params.to);
    const qs = q.toString() ? `?${q.toString()}` : '';
    return fetchWithAuth(`${API_BASE_URL}/product-price-history${qs}`).then(handleResponse);
};

export const createProductPriceHistory = (entry: {
    product: { id: number };
    effectiveDate: string;
    price: number;
}): Promise<ProductPriceHistoryEntry> =>
    fetchWithAuth(`${API_BASE_URL}/product-price-history`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(entry),
    }).then(handleResponse);

export const deleteProductPriceHistory = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/product-price-history/${id}`, { method: 'DELETE' }).then(handleResponse);

// Suppliers
export const getSuppliers = (): Promise<Supplier[]> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers`).then(handleResponse);

export const getActiveSuppliers = (): Promise<Supplier[]> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers/active`).then(handleResponse);

export const createSupplier = (supplier: Partial<Supplier>): Promise<Supplier> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(supplier),
    }).then(handleResponse);

export const updateSupplier = (id: number, supplier: Partial<Supplier>): Promise<Supplier> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(supplier),
    }).then(handleResponse);

export const deleteSupplier = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers/${id}`, { method: 'DELETE' }).then(handleResponse);

export const toggleSupplierStatus = (id: number): Promise<Supplier> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers/${id}/toggle-status`, { method: 'PATCH' }).then(handleResponse);

// Oil Types
export const getOilTypes = (): Promise<OilType[]> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types`).then(handleResponse);

export const getActiveOilTypes = (): Promise<OilType[]> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types/active`).then(handleResponse);

export const createOilType = (oilType: Partial<OilType>): Promise<OilType> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(oilType),
    }).then(handleResponse);

export const updateOilType = (id: number, oilType: Partial<OilType>): Promise<OilType> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(oilType),
    }).then(handleResponse);

export const deleteOilType = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types/${id}`, { method: 'DELETE' }).then(handleResponse);

export const toggleOilTypeStatus = (id: number): Promise<OilType> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types/${id}/toggle-status`, { method: 'PATCH' }).then(handleResponse);

// Grade Types
export const getGradeTypes = (): Promise<GradeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/grades`).then(handleResponse);

export const getActiveGradeTypes = (): Promise<GradeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/grades/active`).then(handleResponse);

export const createGradeType = (grade: Partial<GradeType>): Promise<GradeType> =>
    fetchWithAuth(`${API_BASE_URL}/grades`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(grade),
    }).then(handleResponse);

export const updateGradeType = (id: number, grade: Partial<GradeType>): Promise<GradeType> =>
    fetchWithAuth(`${API_BASE_URL}/grades/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(grade),
    }).then(handleResponse);

export const deleteGradeType = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/grades/${id}`, { method: 'DELETE' }).then(handleResponse);

export const toggleGradeStatus = (id: number): Promise<GradeType> =>
    fetchWithAuth(`${API_BASE_URL}/grades/${id}/toggle-status`, { method: 'PATCH' }).then(handleResponse);

export const getGradesByOilType = (oilTypeId: number): Promise<GradeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/grades/oil-type/${oilTypeId}`).then(handleResponse);
