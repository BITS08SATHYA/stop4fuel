export interface Employee {
    id: number;
    name: string;
    designation: string;
    email: string;
    phone: string;
    salary: number;
    salaryDay: number; // Day of month salary is due (1-31)
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
    panNumber: string;
    department: string;
    employeeCode: string;
    emergencyContact: string;
    emergencyPhone: string;
    bloodGroup: string;
    dateOfBirth: string;
    gender: string;
    maritalStatus: string;
    aadharDocUrl: string;
    panDocUrl: string;
}

export interface SalaryHistory {
    id: number;
    oldSalary: number;
    newSalary: number;
    effectiveDate: string;
    reason: string;
}

export interface EmployeeAdvance {
    id: number;
    amount: number;
    advanceDate: string;
    advanceType: string;
    remarks: string;
    status: string;
}

export const emptyEmployee: Omit<Employee, "id"> = {
    name: "", designation: "", email: "", phone: "", salary: 0, salaryDay: 1, joinDate: "",
    status: "Active", aadharNumber: "", additionalPhones: "", address: "",
    city: "", state: "", pincode: "", photoUrl: "", bankAccountNumber: "",
    bankName: "", bankIfsc: "", bankBranch: "", panNumber: "", department: "",
    employeeCode: "", emergencyContact: "", emergencyPhone: "", bloodGroup: "",
    dateOfBirth: "", gender: "", maritalStatus: "", aadharDocUrl: "", panDocUrl: "",
};

export const inputClass =
    "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50";

export const formatRupees = (val: number) => `₹${val.toLocaleString("en-IN")}`;

export const advanceTypeBadge: Record<string, string> = {
    SALARY_ADVANCE: "bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300",
    HOME_ADVANCE: "bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-300",
    NIGHT_ADVANCE: "bg-indigo-100 text-indigo-800 dark:bg-indigo-900/40 dark:text-indigo-300",
};

export const advanceStatusBadge: Record<string, string> = {
    PENDING: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/40 dark:text-yellow-300",
    DEDUCTED: "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300",
    WAIVED: "bg-gray-100 text-gray-800 dark:bg-gray-900/40 dark:text-gray-300",
};

export const formatLabel = (s: string) => s.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());

import { API_BASE_URL } from "@/lib/api/station";
export const API_BASE = `${API_BASE_URL}/employees`;
