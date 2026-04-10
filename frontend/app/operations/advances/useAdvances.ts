"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { showToast } from "@/components/ui/toast";
import {
    OperationalAdvance,
    Employee,
    fetchActiveShift,
    fetchEmployees,
    fetchAdvancesByShift,
    fetchAdvancesByDateRange,
    cancelAdvance,
    deleteAdvance,
    formatCurrency,
} from "./advances-api";

export interface AdvanceSummary {
    totalGiven: number;
    outstanding: number;
    returned: number;
    utilized: number;
    count: number;
}

export function useAdvances() {
    const [advances, setAdvances] = useState<OperationalAdvance[]>([]);
    const [activeShift, setActiveShift] = useState<{ id: number; startTime?: string } | null>(null);
    const [employees, setEmployees] = useState<Employee[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    // Filters
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [typeFilter, setTypeFilter] = useState("ALL");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [viewMode, setViewMode] = useState<"shift" | "dates">("shift");

    // Modal state
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [isReturnModalOpen, setIsReturnModalOpen] = useState(false);
    const [returnTarget, setReturnTarget] = useState<OperationalAdvance | null>(null);
    const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
    const [detailAdvance, setDetailAdvance] = useState<OperationalAdvance | null>(null);

    const loadAdvances = useCallback(async (mode: "shift" | "dates", shiftId?: number | null, from?: string, to?: string) => {
        setIsLoading(true);
        try {
            let data: OperationalAdvance[];
            if (mode === "dates" && from && to) {
                data = await fetchAdvancesByDateRange(from, to);
            } else if (mode === "shift" && shiftId) {
                data = await fetchAdvancesByShift(shiftId);
            } else {
                data = [];
            }
            setAdvances(data);
        } catch (err) {
            console.error("Failed to load advances", err);
        } finally {
            setIsLoading(false);
        }
    }, []);

    const loadData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [shift, emps] = await Promise.all([
                fetchActiveShift(),
                fetchEmployees(),
            ]);
            setActiveShift(shift);
            setEmployees(emps);
            if (shift?.id) {
                const advs = await fetchAdvancesByShift(shift.id);
                setAdvances(advs);
                if (shift.startTime) {
                    setFromDate(shift.startTime.split("T")[0]);
                    setToDate(new Date().toISOString().split("T")[0]);
                }
            }
        } catch (err) {
            console.error("Failed to load data", err);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const handleDateSearch = () => {
        if (fromDate && toDate) {
            setViewMode("dates");
            loadAdvances("dates", null, fromDate, toDate);
        }
    };

    const handleShowCurrentShift = () => {
        setViewMode("shift");
        setFromDate("");
        setToDate("");
        if (activeShift?.id) {
            loadAdvances("shift", activeShift.id);
        } else {
            setAdvances([]);
        }
    };

    const reloadCurrentView = useCallback(async () => {
        if (viewMode === "dates" && fromDate && toDate) {
            loadAdvances("dates", null, fromDate, toDate);
        } else if (activeShift?.id) {
            loadAdvances("shift", activeShift.id);
        }
    }, [viewMode, fromDate, toDate, activeShift, loadAdvances]);

    // Summary
    const summary: AdvanceSummary = useMemo(() => {
        const totalGiven = advances
            .filter((a) => a.status !== "CANCELLED")
            .reduce((sum, a) => sum + a.amount, 0);
        const outstanding = advances
            .filter((a) => a.status === "GIVEN" || a.status === "PARTIALLY_RETURNED")
            .reduce((sum, a) => sum + (a.amount - (a.returnedAmount || 0) - (a.utilizedAmount || 0)), 0);
        const returned = advances.reduce((sum, a) => sum + (a.returnedAmount || 0), 0);
        const utilized = advances.reduce((sum, a) => sum + (a.utilizedAmount || 0), 0);
        return { totalGiven, outstanding, returned, utilized, count: advances.length };
    }, [advances]);

    // Filtering
    const filtered = useMemo(() => {
        return advances.filter((a) => {
            const matchStatus = statusFilter === "ALL" || a.status === statusFilter;
            const matchType = typeFilter === "ALL" || a.advanceType === typeFilter;
            const q = searchQuery.toLowerCase();
            const matchSearch =
                !searchQuery ||
                a.recipientName?.toLowerCase().includes(q) ||
                a.purpose?.toLowerCase().includes(q) ||
                a.remarks?.toLowerCase().includes(q) ||
                a.employee?.name?.toLowerCase().includes(q);
            return matchStatus && matchType && matchSearch;
        });
    }, [advances, statusFilter, typeFilter, searchQuery]);

    // Handlers
    const handleOpenAddModal = () => {
        if (!activeShift) return;
        setIsAddModalOpen(true);
    };

    const handleOpenReturnModal = (adv: OperationalAdvance) => {
        setReturnTarget(adv);
        setIsReturnModalOpen(true);
    };

    const handleCancel = async (adv: OperationalAdvance) => {
        if (!confirm(`Cancel advance of Rs.${formatCurrency(adv.amount)} to ${adv.recipientName}?`)) return;
        try {
            await cancelAdvance(adv.id);
            await reloadCurrentView();
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : "Failed to cancel advance";
            showToast.error(message);
        }
    };

    const handleDelete = async (adv: OperationalAdvance) => {
        if (!confirm(`Delete this advance record permanently?`)) return;
        try {
            await deleteAdvance(adv.id);
            await reloadCurrentView();
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : "Failed to delete advance";
            showToast.error(message);
        }
    };

    const handleOpenDetail = (adv: OperationalAdvance) => {
        setDetailAdvance(adv);
        setIsDetailModalOpen(true);
    };

    return {
        // Data
        advances,
        activeShift,
        employees,
        isLoading,
        summary,
        filtered,

        // Filters
        searchQuery, setSearchQuery,
        statusFilter, setStatusFilter,
        typeFilter, setTypeFilter,
        fromDate, setFromDate,
        toDate, setToDate,
        viewMode,

        // Filter actions
        handleDateSearch,
        handleShowCurrentShift,

        // Modal state
        isAddModalOpen, setIsAddModalOpen,
        isReturnModalOpen, setIsReturnModalOpen,
        returnTarget,
        isDetailModalOpen, setIsDetailModalOpen,
        detailAdvance, setDetailAdvance,

        // Actions
        handleOpenAddModal,
        handleOpenReturnModal,
        handleCancel,
        handleDelete,
        handleOpenDetail,
        reloadCurrentView,
    };
}
