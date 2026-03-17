"use client";

import { useState, useMemo, useEffect } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface TablePaginationProps {
    page: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
    onPageChange: (page: number) => void;
}

export function TablePagination({ page, totalPages, totalElements, pageSize, onPageChange }: TablePaginationProps) {
    if (totalElements === 0) return null;

    const start = page * pageSize + 1;
    const end = Math.min((page + 1) * pageSize, totalElements);

    return (
        <div className="flex items-center justify-between p-4 border-t border-border">
            <p className="text-sm text-muted-foreground">
                Showing {start}-{end} of {totalElements}
            </p>
            <div className="flex items-center gap-2">
                <button
                    onClick={() => onPageChange(Math.max(0, page - 1))}
                    disabled={page === 0}
                    className="px-3 py-1.5 text-sm rounded-md border border-border text-muted-foreground hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-1"
                >
                    <ChevronLeft className="w-4 h-4" />
                    Previous
                </button>
                <span className="text-sm text-foreground font-medium px-2">
                    {page + 1} / {totalPages}
                </span>
                <button
                    onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
                    disabled={page >= totalPages - 1}
                    className="px-3 py-1.5 text-sm rounded-md border border-border text-muted-foreground hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-1"
                >
                    Next
                    <ChevronRight className="w-4 h-4" />
                </button>
            </div>
        </div>
    );
}

/** Hook for client-side pagination — resets to page 0 when data changes */
export function useClientPagination<T>(data: T[], pageSize: number = 7) {
    const [page, setPage] = useState(0);

    const totalElements = data.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));

    // Reset to page 0 when filtered data changes
    useEffect(() => {
        setPage(0);
    }, [totalElements]);

    const safePage = Math.min(page, totalPages - 1);

    const paginatedData = useMemo(
        () => data.slice(safePage * pageSize, (safePage + 1) * pageSize),
        [data, safePage, pageSize]
    );

    return {
        page: safePage,
        setPage,
        totalPages,
        totalElements,
        pageSize,
        paginatedData,
    };
}
