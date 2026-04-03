"use client";

import {
    ArrowDownLeft,
    ArrowUpRight,
    AlertCircle,
    Hash,
    Plus,
    Receipt,
} from "lucide-react";
import { PermissionGate } from "@/components/permission-gate";
import { formatCurrency } from "./advances-api";
import { useAdvances } from "./useAdvances";
import { AdvanceFilters } from "./AdvanceFilters";
import { AdvanceTable } from "./AdvanceTable";
import { AdvanceAddModal } from "./advance-add-modal";
import { AdvanceReturnModal } from "./advance-return-modal";
import { AdvanceDetailModal } from "./advance-detail-modal";

export default function OperationalAdvancesPage() {
    const {
        activeShift,
        employees,
        isLoading,
        summary,
        filtered,
        searchQuery, setSearchQuery,
        statusFilter, setStatusFilter,
        typeFilter, setTypeFilter,
        fromDate, setFromDate,
        toDate, setToDate,
        viewMode,
        handleDateSearch,
        handleShowCurrentShift,
        isAddModalOpen, setIsAddModalOpen,
        isReturnModalOpen, setIsReturnModalOpen,
        returnTarget,
        isDetailModalOpen, setIsDetailModalOpen,
        detailAdvance, setDetailAdvance,
        handleOpenAddModal,
        handleOpenReturnModal,
        handleCancel,
        handleOpenDetail,
        reloadCurrentView,
    } = useAdvances();

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Operational <span className="text-gradient">Advances</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Track operational advances (cash, salary, management) and their settlements.
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        {!activeShift && (
                            <div className="flex items-center gap-2 px-4 py-2 bg-amber-500/10 border border-amber-500/20 rounded-xl">
                                <AlertCircle className="w-4 h-4 text-amber-500" />
                                <span className="text-sm font-medium text-amber-500">No active shift</span>
                            </div>
                        )}
                        <PermissionGate permission="FINANCE_MANAGE">
                            <button
                                onClick={handleOpenAddModal}
                                title="Record a new advance"
                                className="btn-gradient px-5 py-2.5 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                            >
                                <Plus className="w-4 h-4" />
                                Record Advance
                            </button>
                        </PermissionGate>
                    </div>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading advances...</p>
                    </div>
                ) : (
                    <>
                        {/* Summary Cards */}
                        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 mb-6">
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-blue-500 bg-blue-500/10">
                                    <ArrowUpRight className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Total Given</p>
                                    <p className="text-sm font-bold text-foreground">{formatCurrency(summary.totalGiven)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-red-500 bg-red-500/10">
                                    <AlertCircle className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Outstanding</p>
                                    <p className="text-sm font-bold text-red-500">{formatCurrency(summary.outstanding)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-teal-500 bg-teal-500/10">
                                    <Receipt className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Bill Settled</p>
                                    <p className="text-sm font-bold text-teal-500">{formatCurrency(summary.utilized)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-green-500 bg-green-500/10">
                                    <ArrowDownLeft className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Cash Returned</p>
                                    <p className="text-sm font-bold text-green-500">{formatCurrency(summary.returned)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-purple-500 bg-purple-500/10">
                                    <Hash className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Count</p>
                                    <p className="text-sm font-bold text-foreground">{summary.count}</p>
                                </div>
                            </div>
                        </div>

                        {/* Filter Bar */}
                        <AdvanceFilters
                            searchQuery={searchQuery}
                            onSearchChange={setSearchQuery}
                            statusFilter={statusFilter}
                            onStatusFilterChange={setStatusFilter}
                            typeFilter={typeFilter}
                            onTypeFilterChange={setTypeFilter}
                            fromDate={fromDate}
                            onFromDateChange={setFromDate}
                            toDate={toDate}
                            onToDateChange={setToDate}
                            viewMode={viewMode}
                            onDateSearch={handleDateSearch}
                            onShowCurrentShift={handleShowCurrentShift}
                        />

                        {/* View indicator */}
                        <div className="mb-3">
                            <span className="text-xs text-muted-foreground">
                                {viewMode === "shift"
                                    ? activeShift
                                        ? `Showing current shift #${activeShift.id} entries`
                                        : "No active shift"
                                    : `Showing entries from ${fromDate} to ${toDate}`
                                }
                            </span>
                        </div>

                        {/* Advances Table */}
                        <AdvanceTable
                            filtered={filtered}
                            onOpenDetail={handleOpenDetail}
                            onOpenReturn={handleOpenReturnModal}
                            onCancel={handleCancel}
                        />
                    </>
                )}
            </div>

            {/* Record Advance Modal */}
            <AdvanceAddModal
                isOpen={isAddModalOpen}
                onClose={() => setIsAddModalOpen(false)}
                onSuccess={reloadCurrentView}
                employees={employees}
                activeShift={activeShift}
            />

            {/* Record Return Modal */}
            <AdvanceReturnModal
                isOpen={isReturnModalOpen}
                onClose={() => setIsReturnModalOpen(false)}
                onSuccess={reloadCurrentView}
                advance={returnTarget}
            />

            {/* Detail & Invoice Assignment Modal */}
            <AdvanceDetailModal
                isOpen={isDetailModalOpen}
                onClose={() => { setIsDetailModalOpen(false); setDetailAdvance(null); }}
                advance={detailAdvance}
                onDataChanged={reloadCurrentView}
            />
        </div>
    );
}
