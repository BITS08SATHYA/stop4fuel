"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Settings2, Printer, RotateCcw, RefreshCw, CheckCircle2, AlertCircle } from "lucide-react";
import {
    DOT_MATRIX_FORM_DEFAULTS,
    generateDotMatrixTestSlip,
    getDotMatrixForm,
    resetDotMatrixForm,
    setDotMatrixForm,
    type DotMatrixForm,
} from "@/lib/escp-dotmatrix";
import { getDotMatrixPrinter, refreshPrintAgentState, type CompanyInfo } from "@/lib/invoice-print";
import { getPrintAgentHealth, listPrintAgentPrinters, sendToPrintAgent, type PrintAgentHealth } from "@/lib/print-agent";
import { showToast } from "@/components/ui/toast";

// Alignment controls for the MSP 250's pre-printed slip.
//
// These live at the counter rather than in the source because every station
// parks its stationery slightly differently and each stationery batch has its
// own tractor inset — the values that make the slip land inside the pre-printed
// box are a property of the machine, not of the app. The test slip prints a
// column ruler and edge markers so a cashier can dial them in without a dev.
//
// Mirrors PrintMenuButton's click-outside + absolute z-[9999] popover so it
// behaves like the other dropdowns on the page.
interface Props {
    company?: CompanyInfo | null;
    /** Lets a successful recheck hand the page a printer list it failed to get on mount. */
    onPrintersFound?: (names: string[]) => void;
}

const NUMBERS: { key: keyof DotMatrixForm; label: string; hint: string; min: number; max: number }[] = [
    { key: "leftMargin", label: "Left margin", hint: "Columns of inset. Raise if the first letter of each line is cut off.", min: 0, max: 40 },
    { key: "width", label: "Width", hint: "Columns of content. Lower if the Amount column is cut off on the right.", min: 24, max: 120 },
    { key: "topLines", label: "Top lines", hint: "Blank lines before the header. Raise if the header prints on the perforation.", min: 0, max: 10 },
    { key: "pageLines", label: "Page lines", hint: "Form height in lines. 4.5in at 6 LPI = 27.", min: 10, max: 72 },
];

export function DotMatrixSettings({ company, onPrintersFound }: Props) {
    const [isOpen, setIsOpen] = useState(false);
    const [form, setForm] = useState<DotMatrixForm>(DOT_MATRIX_FORM_DEFAULTS);
    const [testing, setTesting] = useState(false);
    const [health, setHealth] = useState<PrintAgentHealth | null>(null);
    const [checking, setChecking] = useState(false);
    const wrapperRef = useRef<HTMLDivElement>(null);

    // Seeded with the defaults for SSR, then hydrated from this machine's saved
    // geometry on mount (localStorage is not available during render).
    useEffect(() => { setForm(getDotMatrixForm()); }, []);

    // Re-probe the agent and re-read its printer list. The page only does this
    // once on mount, so a miss there (agent still starting, PC busy) otherwise
    // stands until reload — which reads at the counter as "Print stopped working".
    const recheck = useCallback(async () => {
        setChecking(true);
        try {
            const h = await getPrintAgentHealth();
            setHealth(h);
            await refreshPrintAgentState();
            if (h && onPrintersFound) {
                const names = await listPrintAgentPrinters();
                if (names.length) onPrintersFound(names);
            }
        } finally {
            setChecking(false);
        }
    }, [onPrintersFound]);

    // Check whenever the panel is opened: whoever opens this is already
    // wondering why printing is misbehaving.
    useEffect(() => { if (isOpen) void recheck(); }, [isOpen, recheck]);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    const update = (patch: Partial<DotMatrixForm>) => {
        setDotMatrixForm(patch);
        setForm(getDotMatrixForm()); // read back so clamping is reflected in the inputs
    };

    const printTest = async () => {
        setTesting(true);
        try {
            if (!(await refreshPrintAgentState())) {
                showToast.error("Print agent not running on this PC — start it, then try the test slip again.");
                return;
            }
            const info: CompanyInfo = company || { name: "StopForFuel", address: "", phone: "", gstNo: "" };
            await sendToPrintAgent(generateDotMatrixTestSlip(info, getDotMatrixForm()), "Dot-matrix alignment test", getDotMatrixPrinter());
            showToast.success("Test slip sent. Both [ ] markers must land inside the pre-printed box.");
        } catch (e) {
            showToast.error(`Test slip failed: ${e instanceof Error ? e.message : "unknown error"}`);
        } finally {
            setTesting(false);
        }
    };

    return (
        <div ref={wrapperRef} className="relative">
            <button
                onClick={() => setIsOpen(o => !o)}
                title="Dot-matrix alignment & speed"
                className={`p-2 rounded-lg border border-border transition-colors ${
                    isOpen ? "bg-muted text-foreground" : "text-muted-foreground hover:text-foreground hover:bg-muted"
                }`}
            >
                <Settings2 className="w-4 h-4" />
            </button>

            {isOpen && (
                <div className="absolute right-0 top-full z-[9999] mt-1 w-80 p-4 bg-card border border-border rounded-xl shadow-2xl">
                    <div className="text-xs font-bold uppercase tracking-widest text-muted-foreground mb-3">
                        Dot-matrix slip
                    </div>

                    {/* Agent status. Without this the cashier has no way to tell a
                        dead agent from a misaligned form — both just look like
                        "the printer is wrong". */}
                    <div className={`flex items-start gap-2 p-2 mb-3 rounded-lg border text-[11px] ${
                        checking ? "border-border text-muted-foreground"
                            : health ? "border-green-500/30 text-green-600 dark:text-green-400"
                                : "border-red-500/30 text-red-600 dark:text-red-400"
                    }`}>
                        {checking
                            ? <RefreshCw className="w-3.5 h-3.5 mt-px shrink-0 animate-spin" />
                            : health ? <CheckCircle2 className="w-3.5 h-3.5 mt-px shrink-0" />
                                : <AlertCircle className="w-3.5 h-3.5 mt-px shrink-0" />}
                        <div className="flex-1 leading-snug">
                            {checking ? "Checking print agent..."
                                : health ? <>Print agent connected (v{health.version}). Default printer: {health.printer || "Windows default"}.</>
                                    : <>Print agent not reachable. Bills will print through the browser dialog — slowly, and cropped at the edges.</>}
                        </div>
                        <button
                            onClick={recheck}
                            disabled={checking}
                            title="Check the print agent again"
                            className="shrink-0 text-muted-foreground hover:text-foreground disabled:opacity-50"
                        >
                            <RefreshCw className="w-3.5 h-3.5" />
                        </button>
                    </div>

                    <label className="block text-[11px] font-semibold text-foreground mb-1">Print quality</label>
                    <select
                        value={form.quality}
                        onChange={e => update({ quality: e.target.value as DotMatrixForm["quality"] })}
                        className="w-full px-3 py-2 mb-1 text-sm rounded-lg border border-border bg-background text-foreground"
                    >
                        <option value="draft">Draft — one pass, fastest</option>
                        <option value="nlq">Letter quality — 3-4x slower</option>
                    </select>
                    <p className="text-[10px] text-muted-foreground mb-3">
                        Draft strikes each line once. Letter quality strikes it twice for a smoother
                        font, which is what makes a slip take close to a minute.
                    </p>

                    <label className="block text-[11px] font-semibold text-foreground mb-1">Character pitch</label>
                    <select
                        value={form.cpi}
                        onChange={e => update({ cpi: Number(e.target.value) as DotMatrixForm["cpi"] })}
                        className="w-full px-3 py-2 mb-3 text-sm rounded-lg border border-border bg-background text-foreground"
                    >
                        <option value={10}>10 cpi — pica (normal)</option>
                        <option value={12}>12 cpi — elite</option>
                        <option value={17}>17 cpi — condensed</option>
                    </select>

                    <div className="grid grid-cols-2 gap-2 mb-3">
                        {NUMBERS.map(n => (
                            <div key={n.key}>
                                <label className="block text-[11px] font-semibold text-foreground mb-1" title={n.hint}>
                                    {n.label}
                                </label>
                                <input
                                    type="number"
                                    min={n.min}
                                    max={n.max}
                                    value={form[n.key] as number}
                                    onChange={e => update({ [n.key]: Number(e.target.value) } as Partial<DotMatrixForm>)}
                                    title={n.hint}
                                    className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                                />
                            </div>
                        ))}
                    </div>

                    <div className="flex items-center gap-2">
                        <button
                            onClick={printTest}
                            disabled={testing}
                            className="flex-1 px-3 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                        >
                            <Printer className="w-4 h-4" />{testing ? "Sending..." : "Print test slip"}
                        </button>
                        <button
                            onClick={() => { resetDotMatrixForm(); setForm(getDotMatrixForm()); }}
                            title="Back to the default geometry"
                            className="px-3 py-2 text-sm rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors"
                        >
                            <RotateCcw className="w-4 h-4" />
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
