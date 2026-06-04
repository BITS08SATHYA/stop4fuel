"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { GlassCard } from "@/components/ui/glass-card";
import { StyledSelect } from "@/components/ui/styled-select";
import { PermissionGate } from "@/components/permission-gate";
import {
    getTanks,
    getDipCharts,
    uploadDipChart,
    deleteDipChart,
    convertDip,
    Tank,
    DipChartSummary,
    DipChartType,
} from "@/lib/api/station";
import { Ruler, Upload, Trash2, ArrowLeft, AlertTriangle, CheckCircle2, FileText, Calculator } from "lucide-react";

export default function DipChartsPage() {
    const [tanks, setTanks] = useState<Tank[]>([]);
    const [charts, setCharts] = useState<DipChartSummary[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    // Upload form
    const [tankId, setTankId] = useState("");
    const [type, setType] = useState<DipChartType>("PER_CM");
    const [volumeCol, setVolumeCol] = useState("Dip_Volume");
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState("");
    const [result, setResult] = useState<DipChartSummary | null>(null);

    // Dip → stock lookup
    const [lookupTankId, setLookupTankId] = useState("");
    const [lookupDip, setLookupDip] = useState("");
    const [lookupVolume, setLookupVolume] = useState<number | null>(null);
    const [lookupNote, setLookupNote] = useState("");
    const [lookupBusy, setLookupBusy] = useState(false);

    const handleLookup = async (e: React.FormEvent) => {
        e.preventDefault();
        setLookupVolume(null);
        setLookupNote("");
        if (!lookupTankId) { setLookupNote("Select a tank."); return; }
        const d = parseFloat(lookupDip);
        if (isNaN(d) || d < 0) { setLookupNote("Enter a valid dip in cm."); return; }
        setLookupBusy(true);
        try {
            const res = await convertDip(Number(lookupTankId), d);
            if (res.hasChart && res.volume != null) {
                setLookupVolume(res.volume);
            } else {
                setLookupNote("This tank has no dip chart — upload one above first.");
            }
        } catch (err) {
            setLookupNote(err instanceof Error ? err.message : "Lookup failed");
        }
        setLookupBusy(false);
    };

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [t, c] = await Promise.all([getTanks(), getDipCharts()]);
            setTanks(t.filter((x) => x.active));
            setCharts(c);
        } catch (err) {
            console.error("Failed to load dip charts", err);
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleUpload = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");
        setResult(null);
        if (!tankId) { setError("Select a tank."); return; }
        if (!file) { setError("Choose a CSV file."); return; }
        setUploading(true);
        try {
            const summary = await uploadDipChart(
                Number(tankId),
                type,
                type === "PER_CM" ? volumeCol.trim() : undefined,
                file
            );
            setResult(summary);
            setFile(null);
            await loadData();
        } catch (err) {
            console.error("Upload failed", err);
            setError(err instanceof Error ? err.message : "Upload failed");
        }
        setUploading(false);
    };

    const handleDelete = async (id: number, tankName: string) => {
        if (!confirm(`Delete the dip chart for ${tankName}? Dip readings will fall back to manual entry.`)) return;
        try {
            await deleteDipChart(id);
            await loadData();
        } catch (err) {
            console.error("Delete failed", err);
        }
    };

    return (
        <div className="p-6 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-5xl mx-auto">
                <Link href="/operations/inventory/tanks" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-4">
                    <ArrowLeft className="w-4 h-4" /> Back to Tank Dip Readings
                </Link>
                <div className="mb-8">
                    <h1 className="text-4xl font-bold text-foreground tracking-tight">
                        Tank <span className="text-gradient">Dip Charts</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        Upload a calibration (strapping) chart per tank so dip readings auto-convert to stock litres.
                    </p>
                </div>

                <PermissionGate permission="INVENTORY_CREATE">
                    <GlassCard className="p-6 mb-8">
                        <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
                            <Upload className="w-5 h-5 text-primary" /> Upload / Replace a Chart
                        </h2>
                        <form onSubmit={handleUpload} className="space-y-4">
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-foreground mb-1.5">Tank</label>
                                    <StyledSelect
                                        value={tankId}
                                        onChange={setTankId}
                                        options={[
                                            { value: "", label: "Select Tank..." },
                                            ...tanks.map((t) => ({ value: String(t.id), label: `${t.name} (${t.product?.name})` })),
                                        ]}
                                        placeholder="Select Tank..."
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-foreground mb-1.5">Chart Format</label>
                                    <StyledSelect
                                        value={type}
                                        onChange={(v) => setType(v as DipChartType)}
                                        options={[
                                            { value: "PER_CM", label: "Per-cm (Dip_Value, Volume, Diff)" },
                                            { value: "GRID", label: "Grid (DipValue, t_0…t_9)" },
                                        ]}
                                    />
                                </div>
                                {type === "PER_CM" && (
                                    <div>
                                        <label className="block text-sm font-medium text-foreground mb-1.5">Volume Column</label>
                                        <input
                                            type="text"
                                            value={volumeCol}
                                            onChange={(e) => setVolumeCol(e.target.value)}
                                            placeholder="Dip_Volume / Dip_Stock"
                                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                        />
                                    </div>
                                )}
                                <div>
                                    <label className="block text-sm font-medium text-foreground mb-1.5">CSV File</label>
                                    <input
                                        type="file"
                                        accept=".csv,text/csv"
                                        onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                                        className="w-full text-sm text-muted-foreground file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-primary/10 file:text-primary file:text-sm file:font-medium hover:file:bg-primary/20"
                                    />
                                </div>
                            </div>

                            {error && (
                                <div className="flex items-center gap-2 text-sm text-red-500 bg-red-500/10 rounded-xl px-4 py-3">
                                    <AlertTriangle className="w-4 h-4 shrink-0" /> {error}
                                </div>
                            )}

                            <button
                                type="submit"
                                disabled={uploading}
                                className="btn-gradient px-6 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all disabled:opacity-50"
                            >
                                {uploading ? "Importing…" : "Import Chart"}
                            </button>
                        </form>

                        {result && (
                            <div className="mt-4 rounded-xl border border-border p-4 bg-black/5 dark:bg-white/5">
                                <div className="flex items-center gap-2 text-sm font-medium text-green-600 dark:text-green-400 mb-2">
                                    <CheckCircle2 className="w-4 h-4" /> Imported for {result.tankName}: {result.pointCount.toLocaleString()} points, max dip {((result.maxDipMm ?? 0) / 10).toFixed(1)} cm.
                                </div>
                                {result.hadGlitches ? (
                                    <details className="text-xs text-muted-foreground">
                                        <summary className="cursor-pointer text-amber-600 dark:text-amber-400 flex items-center gap-1.5">
                                            <AlertTriangle className="w-3.5 h-3.5" /> {result.glitchesRepaired} glitched row(s) auto-repaired — review
                                        </summary>
                                        <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap font-mono text-[11px] leading-relaxed">{result.glitchLog}</pre>
                                    </details>
                                ) : (
                                    <p className="text-xs text-muted-foreground">No glitches detected — chart is clean.</p>
                                )}
                            </div>
                        )}
                    </GlassCard>
                </PermissionGate>

                <GlassCard className="p-6 mb-8">
                    <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
                        <Calculator className="w-5 h-5 text-primary" /> Dip → Stock Lookup
                    </h2>
                    <form onSubmit={handleLookup} className="flex flex-col sm:flex-row sm:items-end gap-4">
                        <div className="flex-1">
                            <label className="block text-sm font-medium text-foreground mb-1.5">Tank</label>
                            <StyledSelect
                                value={lookupTankId}
                                onChange={(v) => { setLookupTankId(v); setLookupVolume(null); setLookupNote(""); }}
                                options={[
                                    { value: "", label: "Select Tank..." },
                                    ...tanks.map((t) => ({ value: String(t.id), label: `${t.name} (${t.product?.name})` })),
                                ]}
                                placeholder="Select Tank..."
                            />
                        </div>
                        <div className="flex-1">
                            <label className="block text-sm font-medium text-foreground mb-1.5">Dip (cm)</label>
                            <input
                                type="number"
                                step="0.1"
                                min="0"
                                value={lookupDip}
                                onChange={(e) => { setLookupDip(e.target.value); setLookupVolume(null); setLookupNote(""); }}
                                placeholder="e.g. 150.5"
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                            />
                        </div>
                        <button
                            type="submit"
                            disabled={lookupBusy}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all disabled:opacity-50"
                        >
                            {lookupBusy ? "Looking up…" : "Find Stock"}
                        </button>
                    </form>

                    {lookupVolume != null && (
                        <div className="mt-4 rounded-xl border border-border p-4 bg-black/5 dark:bg-white/5 flex items-baseline gap-2">
                            <span className="text-sm text-muted-foreground">Stock at {parseFloat(lookupDip)} cm:</span>
                            <span className="text-2xl font-bold text-gradient font-mono">{lookupVolume.toLocaleString(undefined, { maximumFractionDigits: 2 })}</span>
                            <span className="text-sm text-muted-foreground">litres</span>
                        </div>
                    )}
                    {lookupNote && (
                        <div className="mt-4 flex items-center gap-2 text-sm text-amber-600 dark:text-amber-400 bg-amber-500/10 rounded-xl px-4 py-3">
                            <AlertTriangle className="w-4 h-4 shrink-0" /> {lookupNote}
                        </div>
                    )}
                </GlassCard>

                <h2 className="text-lg font-bold text-foreground mb-3">Installed Charts</h2>
                {isLoading ? (
                    <div className="flex items-center justify-center py-16 text-muted-foreground">
                        <div className="w-10 h-10 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
                    </div>
                ) : charts.length === 0 ? (
                    <div className="text-center py-16 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Ruler className="w-14 h-14 mx-auto text-muted-foreground mb-3 opacity-50" />
                        <p className="text-muted-foreground">No dip charts uploaded yet.</p>
                    </div>
                ) : (
                    <GlassCard className="overflow-hidden border-none p-0">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        <th className="px-6 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Tank / Product</th>
                                        <th className="px-6 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Points</th>
                                        <th className="px-6 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Max Dip</th>
                                        <th className="px-6 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Quality</th>
                                        <th className="px-6 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {charts.map((c) => (
                                        <tr key={c.id} className="hover:bg-white/5 transition-colors">
                                            <td className="px-6 py-4">
                                                <div className="text-sm font-bold text-foreground">{c.tankName}</div>
                                                <div className="text-[10px] text-muted-foreground">{c.productName}</div>
                                            </td>
                                            <td className="px-6 py-4 text-right font-mono text-sm">{c.pointCount.toLocaleString()}</td>
                                            <td className="px-6 py-4 text-right font-mono text-sm">{((c.maxDipMm ?? 0) / 10).toFixed(1)} cm</td>
                                            <td className="px-6 py-4 text-center">
                                                {c.hadGlitches ? (
                                                    <span className="inline-flex items-center gap-1 text-[11px] text-amber-600 dark:text-amber-400" title={c.glitchLog ?? ""}>
                                                        <FileText className="w-3.5 h-3.5" /> {c.glitchesRepaired} repaired
                                                    </span>
                                                ) : (
                                                    <span className="inline-flex items-center gap-1 text-[11px] text-green-600 dark:text-green-400">
                                                        <CheckCircle2 className="w-3.5 h-3.5" /> clean
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4 text-center">
                                                <PermissionGate permission="INVENTORY_DELETE">
                                                    <button
                                                        onClick={() => handleDelete(c.id, c.tankName)}
                                                        className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors"
                                                        title="Delete chart"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </PermissionGate>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </GlassCard>
                )}
            </div>
        </div>
    );
}
