"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import {
    Upload, FileSearch, Loader2, AlertTriangle, Search, IndianRupee, ExternalLink, ArrowRight,
} from "lucide-react";
import {
    parseBankStatement,
    type BankStatementParseResponse,
    type BankStatementRow,
    type BankMatchCandidate,
} from "@/lib/api/station";
import { showToast } from "@/components/ui/toast";

const formatMoney = (v: number | null | undefined) =>
    v == null ? "—" : v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const formatDate = (d: string | null | undefined) => {
    if (!d) return "—";
    const parsed = new Date(d);
    if (Number.isNaN(parsed.getTime())) return d;
    return parsed.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "2-digit" });
};

export default function BankStatementParserPage() {
    const [file, setFile] = useState<File | null>(null);
    const [minAmount, setMinAmount] = useState<string>("");
    const [maxAmount, setMaxAmount] = useState<string>("");
    const [customerName, setCustomerName] = useState<string>("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string>("");
    const [response, setResponse] = useState<BankStatementParseResponse | null>(null);

    // Client-side refinement on top of the server-side filtered rows.
    const [clientName, setClientName] = useState<string>("");
    const [clientMin, setClientMin] = useState<string>("");
    const [clientMax, setClientMax] = useState<string>("");

    const runParse = async () => {
        if (!file) {
            setError("Choose a bank statement PDF first.");
            return;
        }
        setError("");
        setLoading(true);
        try {
            const res = await parseBankStatement(file, {
                minAmount: minAmount ? Number(minAmount) : undefined,
                maxAmount: maxAmount ? Number(maxAmount) : undefined,
                customerNameContains: customerName || undefined,
            });
            setResponse(res);
            setClientName("");
            setClientMin("");
            setClientMax("");
            if (res.rows.length === 0 && res.warnings.length === 0) {
                showToast.info("The PDF parsed but no transaction rows matched your filters.");
            }
        } catch (e: any) {
            setError(e?.message || "Failed to parse the statement.");
            setResponse(null);
        } finally {
            setLoading(false);
        }
    };

    const visibleRows = useMemo(() => {
        if (!response) return [];
        const nameNeedle = clientName.trim().toLowerCase();
        const min = clientMin ? Number(clientMin) : null;
        const max = clientMax ? Number(clientMax) : null;
        return response.rows.filter((row) => {
            if (nameNeedle) {
                const inNarration = row.narration?.toLowerCase().includes(nameNeedle);
                const matches = response.matchesByRow[row.rowIndex] ?? [];
                const inMatch = matches.some((m) => (m.customerName ?? "").toLowerCase().includes(nameNeedle));
                if (!inNarration && !inMatch) return false;
            }
            const amounts = [row.credit, row.debit].filter((v): v is number => v != null);
            if (min != null && !amounts.some((v) => v >= min)) return false;
            if (max != null && !amounts.some((v) => v <= max)) return false;
            return true;
        });
    }, [response, clientName, clientMin, clientMax]);

    return (
        <div className="p-4 sm:p-6 space-y-4 max-w-[1400px] mx-auto">
            <div className="flex items-center gap-3">
                <FileSearch className="w-6 h-6 text-primary" />
                <div>
                    <h1 className="text-xl font-semibold">Bank Statement Parser</h1>
                    <p className="text-sm text-muted-foreground">
                        Upload a bank statement PDF. Search by customer name or amount range. Matching invoices and statements are suggested automatically.
                    </p>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-[360px_1fr] gap-4">
                <GlassCard className="space-y-4">
                    <div>
                        <label className="text-sm font-medium block mb-1.5">Statement PDF</label>
                        <input
                            type="file"
                            accept="application/pdf,.pdf"
                            onChange={(e) => {
                                const f = e.target.files?.[0] ?? null;
                                setFile(f);
                                setError("");
                            }}
                            className="block w-full text-sm file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border file:border-border file:bg-muted/30 file:text-sm hover:file:bg-muted/60"
                        />
                        {file && (
                            <p className="text-xs text-muted-foreground mt-1">
                                {file.name} · {(file.size / 1024).toFixed(0)} KB
                            </p>
                        )}
                    </div>

                    <div className="border-t border-border pt-3 space-y-3">
                        <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Pre-filter (server)</div>
                        <div>
                            <label className="text-xs text-muted-foreground block mb-1">Customer name contains</label>
                            <div className="relative">
                                <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
                                <input
                                    type="text"
                                    value={customerName}
                                    onChange={(e) => setCustomerName(e.target.value)}
                                    placeholder="e.g. sundaram"
                                    className="w-full pl-8 pr-2 py-1.5 text-sm rounded-md border border-border bg-background"
                                />
                            </div>
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                            <div>
                                <label className="text-xs text-muted-foreground block mb-1">Min amount</label>
                                <div className="relative">
                                    <IndianRupee className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
                                    <input
                                        type="number"
                                        inputMode="decimal"
                                        value={minAmount}
                                        onChange={(e) => setMinAmount(e.target.value)}
                                        placeholder="0"
                                        className="w-full pl-7 pr-2 py-1.5 text-sm rounded-md border border-border bg-background"
                                    />
                                </div>
                            </div>
                            <div>
                                <label className="text-xs text-muted-foreground block mb-1">Max amount</label>
                                <div className="relative">
                                    <IndianRupee className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
                                    <input
                                        type="number"
                                        inputMode="decimal"
                                        value={maxAmount}
                                        onChange={(e) => setMaxAmount(e.target.value)}
                                        placeholder="0"
                                        className="w-full pl-7 pr-2 py-1.5 text-sm rounded-md border border-border bg-background"
                                    />
                                </div>
                            </div>
                        </div>
                    </div>

                    <button
                        type="button"
                        onClick={runParse}
                        disabled={!file || loading}
                        className="w-full inline-flex items-center justify-center gap-2 px-3 py-2 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 disabled:opacity-50"
                    >
                        {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                        {loading ? "Parsing…" : "Parse & match"}
                    </button>

                    {error && (
                        <div className="text-sm text-red-500 flex items-start gap-1.5">
                            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                            <span>{error}</span>
                        </div>
                    )}

                    {response && (
                        <div className="border-t border-border pt-3 space-y-3">
                            <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Refine results (instant)</div>
                            <input
                                type="text"
                                value={clientName}
                                onChange={(e) => setClientName(e.target.value)}
                                placeholder="Filter rows by name…"
                                className="w-full px-2.5 py-1.5 text-sm rounded-md border border-border bg-background"
                            />
                            <div className="grid grid-cols-2 gap-2">
                                <input
                                    type="number"
                                    inputMode="decimal"
                                    value={clientMin}
                                    onChange={(e) => setClientMin(e.target.value)}
                                    placeholder="Min ₹"
                                    className="px-2.5 py-1.5 text-sm rounded-md border border-border bg-background"
                                />
                                <input
                                    type="number"
                                    inputMode="decimal"
                                    value={clientMax}
                                    onChange={(e) => setClientMax(e.target.value)}
                                    placeholder="Max ₹"
                                    className="px-2.5 py-1.5 text-sm rounded-md border border-border bg-background"
                                />
                            </div>
                        </div>
                    )}
                </GlassCard>

                <GlassCard className="min-h-[360px]">
                    {!response && !loading && (
                        <div className="flex flex-col items-center justify-center h-80 text-center text-muted-foreground gap-2">
                            <FileSearch className="w-10 h-10 opacity-40" />
                            <p className="text-sm">Upload a statement PDF on the left to see parsed transactions here.</p>
                        </div>
                    )}

                    {loading && (
                        <div className="flex items-center justify-center h-80 text-muted-foreground gap-2">
                            <Loader2 className="w-5 h-5 animate-spin" />
                            <span className="text-sm">Parsing statement…</span>
                        </div>
                    )}

                    {response && (
                        <>
                            <div className="flex flex-wrap items-center gap-2 mb-3">
                                <span className="text-sm">
                                    <span className="font-semibold">{visibleRows.length}</span>
                                    <span className="text-muted-foreground"> of {response.rows.length} parsed rows</span>
                                </span>
                                {response.unparsedLineCount > 0 && (
                                    <Badge variant="warning">
                                        {response.unparsedLineCount} lines skipped
                                    </Badge>
                                )}
                                {Object.keys(response.matchesByRow).length > 0 && (
                                    <Badge variant="default">
                                        {Object.keys(response.matchesByRow).length} rows with matches
                                    </Badge>
                                )}
                            </div>

                            {response.warnings.length > 0 && (
                                <div className="mb-3 rounded-md border border-yellow-500/30 bg-yellow-500/10 px-3 py-2 text-xs text-yellow-700 dark:text-yellow-300">
                                    {response.warnings.map((w, i) => (
                                        <div key={i} className="flex items-start gap-1.5">
                                            <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                                            <span>{w}</span>
                                        </div>
                                    ))}
                                </div>
                            )}

                            <div className="overflow-auto rounded-md border border-border">
                                <table className="w-full text-sm">
                                    <thead className="bg-muted/40 text-xs uppercase text-muted-foreground">
                                        <tr>
                                            <th className="text-left px-2 py-2 font-medium">Date</th>
                                            <th className="text-left px-2 py-2 font-medium">Narration</th>
                                            <th className="text-right px-2 py-2 font-medium">Debit</th>
                                            <th className="text-right px-2 py-2 font-medium">Credit</th>
                                            <th className="text-right px-2 py-2 font-medium">Balance</th>
                                            <th className="text-left px-2 py-2 font-medium">Suggested match</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {visibleRows.length === 0 && (
                                            <tr>
                                                <td colSpan={6} className="text-center py-8 text-muted-foreground text-sm">
                                                    No rows match the current filters.
                                                </td>
                                            </tr>
                                        )}
                                        {visibleRows.map((row) => (
                                            <BankRow
                                                key={row.rowIndex}
                                                row={row}
                                                matches={response.matchesByRow[row.rowIndex] ?? []}
                                            />
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </>
                    )}
                </GlassCard>
            </div>
        </div>
    );
}

function BankRow({ row, matches }: { row: BankStatementRow; matches: BankMatchCandidate[] }) {
    return (
        <tr className="border-t border-border/60 hover:bg-muted/30 align-top">
            <td className="px-2 py-2 whitespace-nowrap">{formatDate(row.txnDate)}</td>
            <td className="px-2 py-2">
                <div className="max-w-[320px]">
                    <div className="text-sm">{row.narration || <span className="text-muted-foreground">—</span>}</div>
                </div>
            </td>
            <td className="px-2 py-2 text-right tabular-nums text-red-500/90">{formatMoney(row.debit)}</td>
            <td className="px-2 py-2 text-right tabular-nums text-emerald-500/90">{formatMoney(row.credit)}</td>
            <td className="px-2 py-2 text-right tabular-nums text-muted-foreground">{formatMoney(row.balance)}</td>
            <td className="px-2 py-2">
                {matches.length === 0 ? (
                    <span className="text-xs text-muted-foreground">—</span>
                ) : (
                    <div className="flex flex-col gap-1">
                        {matches.slice(0, 3).map((m) => (
                            <MatchChip key={`${m.type}-${m.id}`} match={m} />
                        ))}
                    </div>
                )}
            </td>
        </tr>
    );
}

function MatchChip({ match }: { match: BankMatchCandidate }) {
    const href =
        match.type === "INVOICE"
            ? `/operations/invoices?billId=${match.id}`
            : `/payments/statements?statementId=${match.id}`;
    return (
        <Link
            href={href}
            className="inline-flex items-center gap-1.5 text-xs px-2 py-1 rounded-md border border-border bg-muted/30 hover:bg-muted/60 max-w-full"
        >
            <Badge variant={match.type === "INVOICE" ? "default" : "outline"} className="text-[10px] py-0 px-1">
                {match.type === "INVOICE" ? "Invoice" : "Statement"}
            </Badge>
            <span className="truncate font-medium">{match.customerName ?? "Unknown"}</span>
            <span className="text-muted-foreground">·</span>
            <span className="tabular-nums">{formatMoney(match.amount)}</span>
            {match.matchReason === "amount+name" && (
                <Badge variant="success" className="text-[10px] py-0 px-1">name</Badge>
            )}
            <ArrowRight className="w-3 h-3 text-muted-foreground flex-shrink-0" />
            <ExternalLink className="w-3 h-3 text-muted-foreground flex-shrink-0" />
        </Link>
    );
}
