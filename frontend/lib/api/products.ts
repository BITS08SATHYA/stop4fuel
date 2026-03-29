import { fetchWithAuth } from './fetch-with-auth';
import { API_BASE_URL, handleResponse } from './common';

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
    oilType?: OilType;
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
    active: boolean;
    supplier?: Supplier;
    oilType?: OilType;
    gradeType?: GradeType;
    fuelFamily?: string;
}

// --- Products ---
export const getActiveProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/active`).then(handleResponse);

export const getActiveNonFuelProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/active/non-fuel`).then(handleResponse);

export const getFuelProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/category/Fuel`).then(handleResponse);

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

// --- Suppliers ---
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

// --- Oil Types ---
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

// --- Grade Types ---
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
