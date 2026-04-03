import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

// --- Types ---
export interface EmployeeType {
    id: number;
    name: string;
    designation: string;
    email: string;
    phone: string;
    salary: number;
    salaryDay?: number; // Day of month salary is due (1-31)
    joinDate: string;
    status: string;
    aadharNumber: string;
    additionalPhones: string;
    address: string;
    city: string;
    state: string;
    pincode: string;
    photoUrl: string;
    bankAccountNumber: string;
    bankName: string;
    bankIfsc: string;
    bankBranch: string;
    panNumber?: string;
    department?: string;
    employeeCode?: string;
    emergencyContact?: string;
    emergencyPhone?: string;
    bloodGroup?: string;
    dateOfBirth?: string;
    gender?: string;
    maritalStatus?: string;
    aadharDocUrl?: string;
    panDocUrl?: string;
}

// Leave Types
export interface LeaveType {
    id: number;
    name: string;
    maxDaysPerYear: number;
    carryForward: boolean;
    maxCarryForwardDays: number;
}

export interface LeaveBalance {
    id: number;
    employee: EmployeeType;
    leaveType: LeaveType;
    year: number;
    totalAllotted: number;
    used: number;
    remaining: number;
}

export interface LeaveRequest {
    id: number;
    employee: EmployeeType;
    leaveType: LeaveType;
    fromDate: string;
    toDate: string;
    numberOfDays: number;
    reason: string;
    status: string; // PENDING, APPROVED, REJECTED
    approvedBy?: string;
    remarks?: string;
    createdAt?: string;
}

// Attendance
export interface Attendance {
    id: number;
    employee: EmployeeType;
    date: string;
    checkInTime?: string;
    checkOutTime?: string;
    totalHoursWorked?: number;
    shiftRefId?: number;
    status: string; // PRESENT, ABSENT, HALF_DAY, ON_LEAVE
    source: string; // MANUAL, GPS
    remarks?: string;
}

// Salary
export interface SalaryPayment {
    id: number;
    employee: EmployeeType;
    month: number;
    year: number;
    baseSalary: number;
    advanceDeduction: number;
    lopDays: number;
    lopDeduction: number;
    incentiveAmount: number;
    otherDeductions: number;
    netPayable: number;
    paymentDate?: string;
    paymentMode?: string;
    status: string; // DRAFT, PAID
    remarks?: string;
}

// Employees
export const getEmployees = (): Promise<EmployeeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/employees?size=1000`).then(handleResponse).then(data => data.content ?? data);

export const getActiveEmployees = (): Promise<EmployeeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/employees/active`).then(handleResponse);

export const uploadEmployeePhoto = (id: number, file: File): Promise<EmployeeType> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/employees/${id}/upload-photo`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const uploadEmployeeAadharDoc = (id: number, file: File): Promise<EmployeeType> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/employees/${id}/upload-aadhar-doc`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const uploadEmployeePanDoc = (id: number, file: File): Promise<EmployeeType> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/employees/${id}/upload-pan-doc`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const getEmployeeFileUrl = (id: number, type: string): Promise<{ url: string }> =>
    fetchWithAuth(`${API_BASE_URL}/employees/${id}/file-url?type=${type}`).then(handleResponse);

// Leave Types
export const getLeaveTypes = (): Promise<LeaveType[]> =>
    fetchWithAuth(`${API_BASE_URL}/leave-types`).then(handleResponse);

export const createLeaveType = (lt: Partial<LeaveType>): Promise<LeaveType> =>
    fetchWithAuth(`${API_BASE_URL}/leave-types`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(lt),
    }).then(handleResponse);

export const updateLeaveType = (id: number, lt: Partial<LeaveType>): Promise<LeaveType> =>
    fetchWithAuth(`${API_BASE_URL}/leave-types/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(lt),
    }).then(handleResponse);

export const deleteLeaveType = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/leave-types/${id}`, { method: 'DELETE' }).then(handleResponse);

export const getEmployeeLeaveBalances = (employeeId: number, year?: number): Promise<LeaveBalance[]> => {
    const params = year ? `?year=${year}` : '';
    return fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/leave-balances${params}`).then(handleResponse);
};

export const initializeLeaveBalances = (employeeId: number, year?: number): Promise<LeaveBalance[]> => {
    const params = year ? `?year=${year}` : '';
    return fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/leave-balances/initialize${params}`, {
        method: 'POST',
    }).then(handleResponse);
};

export const getLeaveRequests = (status?: string): Promise<LeaveRequest[]> => {
    const params = status ? `?status=${status}` : '';
    return fetchWithAuth(`${API_BASE_URL}/leave-requests${params}`).then(handleResponse);
};

export const createLeaveRequest = (employeeId: number, request: Partial<LeaveRequest>): Promise<LeaveRequest> =>
    fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/leave-requests`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
    }).then(handleResponse);

export const approveLeaveRequest = (id: number, body?: { approvedBy?: string; remarks?: string }): Promise<LeaveRequest> =>
    fetchWithAuth(`${API_BASE_URL}/leave-requests/${id}/approve`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body || {}),
    }).then(handleResponse);

export const rejectLeaveRequest = (id: number, body?: { remarks?: string }): Promise<LeaveRequest> =>
    fetchWithAuth(`${API_BASE_URL}/leave-requests/${id}/reject`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body || {}),
    }).then(handleResponse);

// Attendance
export const getDailyAttendance = (date: string): Promise<Attendance[]> =>
    fetchWithAuth(`${API_BASE_URL}/attendance/daily?date=${date}`).then(handleResponse);

export const getEmployeeAttendance = (employeeId: number, month: number, year: number): Promise<Attendance[]> =>
    fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/attendance?month=${month}&year=${year}`).then(handleResponse);

export const markAttendance = (employeeId: number, attendance: Partial<Attendance>): Promise<Attendance> =>
    fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/attendance`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(attendance),
    }).then(handleResponse);

export const markBulkAttendance = (attendances: Partial<Attendance>[]): Promise<Attendance[]> =>
    fetchWithAuth(`${API_BASE_URL}/attendance/bulk`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(attendances),
    }).then(handleResponse);

// Salary
export const getMonthlyPayments = (month: number, year: number): Promise<SalaryPayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/salary?month=${month}&year=${year}`).then(handleResponse);

export const getEmployeeSalaryPayments = (employeeId: number): Promise<SalaryPayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/salary/employee/${employeeId}`).then(handleResponse);

export const processMonthlyPayroll = (month: number, year: number): Promise<SalaryPayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/salary/process?month=${month}&year=${year}`, {
        method: 'POST',
    }).then(handleResponse);

export const markSalaryAsPaid = (id: number, paymentMode?: string): Promise<SalaryPayment> =>
    fetchWithAuth(`${API_BASE_URL}/salary/${id}/pay`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ paymentMode: paymentMode || 'CASH' }),
    }).then(handleResponse);

export const updateSalaryPayment = (id: number, payment: Partial<SalaryPayment>): Promise<SalaryPayment> =>
    fetchWithAuth(`${API_BASE_URL}/salary/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);
