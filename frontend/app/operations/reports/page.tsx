"use client";

import { useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    downloadDailySalesReport,
    downloadTankInventoryReport,
    downloadCustomerBalanceReport,
} from "@/lib/api/station";
import {
    FileText,
    FileSpreadsheet,
    Download,
    BarChart3,
    Droplets,
    Users,
    Calendar,
    Loader2,
} from "lucide-react";

function getCurrentMonthRange() {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, "0");
    return {
        fromDate: `${y}-${m}-01`,
        toDate: `${y}-${m}-${String(now.getDate()).padStart(2, "0")}`,
    };
}

function triggerDownload(blob: Blob, filename: string) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
}

type ReportType = "daily-sales" | "tank-inventory" | "customer-balance";

interface ReportConfig {
    key: ReportType;
    title: string;
    description: string;
    icon: typeof BarChart3;
    color: string;
    needsDates: boolean;
}

const reports: ReportConfig[] = [
    {
        key: "daily-sales",
        title: "Daily Sales Summary",
        description:
            "Product-wise sales, payment mode breakdown, cash vs credit split, and top customers for a date range.",
        icon: BarChart3,
        color: "text-orange-500",
        needsDates: true,
    },
    {
        key: "tank-inventory",
        title: "Tank Inventory Report",
        description:
            "Current tank status with stock levels, threshold alerts, and stock movement summary with income/sales/closing for the period.",
        icon: Droplets,
        color: "text-blue-500",
        needsDates: true,
    },
    {
        key: "customer-balance",
        title: "Customer Balance Report",
        description:
            "All credit customers with outstanding balances, credit limits, utilization, aging analysis, and account status.",
        icon: Users,
        color: "text-green-500",
        needsDates: false,
    },
];

export default function ReportsPage() {
    const { fromDate: defaultFrom, toDate: defaultTo } = getCurrentMonthRange();
    const [fromDate, setFromDate] = useState(defaultFrom);
    const [toDate, setToDate] = useState(defaultTo);
    const [downloading, setDownloading] = useState<string | null>(null);

    const handleDownload = async (
        report: ReportType,
        format: "pdf" | "excel"
    ) => {
        const key = `${report}-${format}`;
        setDownloading(key);
        try {
            let blob: Blob;
            const ext = format === "pdf" ? "pdf" : "xlsx";

            switch (report) {
                case "daily-sales":
                    blob = await downloadDailySalesReport(fromDate, toDate, format);
                    triggerDownload(blob, `DailySales_${fromDate}_to_${toDate}.${ext}`);
                    break;
                case "tank-inventory":
                    blob = await downloadTankInventoryReport(fromDate, toDate, format);
                    triggerDownload(blob, `TankInventory_${fromDate}_to_${toDate}.${ext}`);
                    break;
                case "customer-balance":
                    blob = await downloadCustomerBalanceReport(format);
                    triggerDownload(
                        blob,
                        `CustomerBalance_${new Date().toISOString().split("T")[0]}.${ext}`
                    );
                    break;
            }
        } catch {
            alert("Failed to generate report. Please try again.");
        } finally {
            setDownloading(null);
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold">Reports</h1>
                    <p className="text-muted-foreground text-sm mt-1">
                        Generate and download business reports in PDF or Excel format
                    </p>
                </div>
            </div>

            {/* Date range selector */}
            <GlassCard>
                <div className="flex items-center gap-4 flex-wrap">
                    <Calendar className="w-5 h-5 text-muted-foreground" />
                    <span className="text-sm font-medium text-muted-foreground">
                        Report Period:
                    </span>
                    <input
                        type="date"
                        value={fromDate}
                        onChange={(e) => setFromDate(e.target.value)}
                        className="px-3 py-1.5 rounded-md border border-input bg-background text-sm"
                    />
                    <span className="text-sm text-muted-foreground">to</span>
                    <input
                        type="date"
                        value={toDate}
                        onChange={(e) => setToDate(e.target.value)}
                        className="px-3 py-1.5 rounded-md border border-input bg-background text-sm"
                    />
                    <button
                        onClick={() => {
                            const { fromDate: f, toDate: t } = getCurrentMonthRange();
                            setFromDate(f);
                            setToDate(t);
                        }}
                        className="px-3 py-1.5 text-xs rounded-md border border-input hover:bg-muted transition-colors"
                    >
                        This Month
                    </button>
                    <button
                        onClick={() => {
                            const today = new Date().toISOString().split("T")[0];
                            setFromDate(today);
                            setToDate(today);
                        }}
                        className="px-3 py-1.5 text-xs rounded-md border border-input hover:bg-muted transition-colors"
                    >
                        Today
                    </button>
                </div>
            </GlassCard>

            {/* Report cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                {reports.map((report) => {
                    const Icon = report.icon;
                    const pdfKey = `${report.key}-pdf`;
                    const excelKey = `${report.key}-excel`;

                    return (
                        <GlassCard key={report.key}>
                            <div className="flex flex-col h-full">
                                <div className="flex items-start gap-3 mb-4">
                                    <div
                                        className={`p-2.5 rounded-lg bg-muted ${report.color}`}
                                    >
                                        <Icon className="w-5 h-5" />
                                    </div>
                                    <div className="flex-1">
                                        <h3 className="font-semibold text-base">
                                            {report.title}
                                        </h3>
                                        <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
                                            {report.description}
                                        </p>
                                    </div>
                                </div>

                                {!report.needsDates && (
                                    <p className="text-xs text-muted-foreground mb-3 italic">
                                        Real-time snapshot — no date range needed
                                    </p>
                                )}

                                <div className="flex gap-2 mt-auto pt-3 border-t border-border">
                                    <button
                                        onClick={() =>
                                            handleDownload(report.key, "pdf")
                                        }
                                        disabled={downloading === pdfKey}
                                        className="flex-1 flex items-center justify-center gap-2 px-3 py-2 text-sm font-medium rounded-md bg-red-500/10 text-red-600 dark:text-red-400 hover:bg-red-500/20 transition-colors disabled:opacity-50"
                                    >
                                        {downloading === pdfKey ? (
                                            <Loader2 className="w-4 h-4 animate-spin" />
                                        ) : (
                                            <FileText className="w-4 h-4" />
                                        )}
                                        PDF
                                    </button>
                                    <button
                                        onClick={() =>
                                            handleDownload(report.key, "excel")
                                        }
                                        disabled={downloading === excelKey}
                                        className="flex-1 flex items-center justify-center gap-2 px-3 py-2 text-sm font-medium rounded-md bg-green-500/10 text-green-600 dark:text-green-400 hover:bg-green-500/20 transition-colors disabled:opacity-50"
                                    >
                                        {downloading === excelKey ? (
                                            <Loader2 className="w-4 h-4 animate-spin" />
                                        ) : (
                                            <FileSpreadsheet className="w-4 h-4" />
                                        )}
                                        Excel
                                    </button>
                                </div>
                            </div>
                        </GlassCard>
                    );
                })}
            </div>
        </div>
    );
}
