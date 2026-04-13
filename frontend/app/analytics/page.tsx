"use client";

import { useState, useEffect, useRef } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    chat,
    fetchInsights,
    ChatMessage,
    Insight,
} from "@/lib/api/station/ai-analytics";
import {
    Brain,
    Send,
    RefreshCw,
    Info,
    AlertTriangle,
    CheckCircle,
    Loader2,
    Sparkles,
    MessageSquare,
} from "lucide-react";

const SEVERITY_STYLES: Record<
    string,
    { border: string; icon: typeof Info; color: string; badge: string }
> = {
    positive: {
        border: "border-l-emerald-500",
        icon: CheckCircle,
        color: "text-emerald-500",
        badge: "bg-emerald-500/15 text-emerald-600 dark:text-emerald-300",
    },
    info: {
        border: "border-l-blue-500",
        icon: Info,
        color: "text-blue-500",
        badge: "bg-blue-500/15 text-blue-600 dark:text-blue-300",
    },
    warning: {
        border: "border-l-amber-500",
        icon: AlertTriangle,
        color: "text-amber-500",
        badge: "bg-amber-500/15 text-amber-600 dark:text-amber-300",
    },
    critical: {
        border: "border-l-red-500",
        icon: AlertTriangle,
        color: "text-red-500",
        badge: "bg-red-500/15 text-red-600 dark:text-red-300",
    },
};

const SUGGESTED_QUESTIONS = [
    "What was today's revenue?",
    "Which tanks are low on stock?",
    "Who owes the most right now?",
    "Top 3 customers this month?",
];

export default function AiAnalyticsPage() {
    // Insights state
    const [insights, setInsights] = useState<Insight[]>([]);
    const [insightsLoading, setInsightsLoading] = useState(true);
    const [insightsGeneratedAt, setInsightsGeneratedAt] = useState<string>("");

    // Chat state
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [input, setInput] = useState("");
    const [chatLoading, setChatLoading] = useState(false);
    const messagesEndRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        loadInsights();
    }, []);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, chatLoading]);

    const loadInsights = async () => {
        setInsightsLoading(true);
        try {
            const data = await fetchInsights();
            setInsights(data.insights);
            setInsightsGeneratedAt(data.generatedAt);
        } catch {
            setInsights([
                {
                    category: "system",
                    title: "Could not load insights",
                    detail:
                        "AI analytics service is unavailable. Verify that the Anthropic API key is configured.",
                    severity: "warning",
                },
            ]);
            setInsightsGeneratedAt("");
        } finally {
            setInsightsLoading(false);
        }
    };

    const sendMessage = async (text: string) => {
        const trimmed = text.trim();
        if (!trimmed || chatLoading) return;

        const userMsg: ChatMessage = { role: "user", content: trimmed };
        const history = messages;
        const newMessages = [...messages, userMsg];
        setMessages(newMessages);
        setInput("");
        setChatLoading(true);

        try {
            const response = await chat(trimmed, history);
            setMessages([
                ...newMessages,
                { role: "assistant", content: response.answer },
            ]);
        } catch {
            setMessages([
                ...newMessages,
                {
                    role: "assistant",
                    content:
                        "Sorry, I couldn't process that request. Please try again.",
                },
            ]);
        } finally {
            setChatLoading(false);
        }
    };

    const handleSend = () => sendMessage(input);

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    return (
        <div className="p-6 space-y-6">
            {/* Header */}
            <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-purple-500/15 flex items-center justify-center">
                    <Brain className="h-5 w-5 text-purple-500" />
                </div>
                <div>
                    <h1 className="text-2xl font-bold">AI Analytics</h1>
                    <p className="text-sm text-muted-foreground">
                        Ask questions about your station and get AI-generated insights.
                    </p>
                </div>
            </div>

            {/* Two-panel layout */}
            <div
                className="grid grid-cols-1 lg:grid-cols-5 gap-6"
                style={{ minHeight: "calc(100vh - 200px)" }}
            >
                {/* LEFT: Insights (2/5 ≈ 40%) */}
                <div className="lg:col-span-2 flex flex-col space-y-3">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <Sparkles className="h-4 w-4 text-amber-500" />
                            <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                                Insights
                            </h2>
                        </div>
                        <button
                            onClick={loadInsights}
                            disabled={insightsLoading}
                            className="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg border border-border bg-card hover:bg-muted transition-colors disabled:opacity-50"
                        >
                            <RefreshCw
                                className={`h-3 w-3 ${insightsLoading ? "animate-spin" : ""}`}
                            />
                            Refresh
                        </button>
                    </div>

                    {insightsLoading ? (
                        <div className="space-y-3">
                            {[...Array(4)].map((_, i) => (
                                <GlassCard key={i} className="!p-4 animate-pulse">
                                    <div className="h-4 bg-muted rounded w-3/4 mb-2" />
                                    <div className="h-3 bg-muted rounded w-full" />
                                    <div className="h-3 bg-muted rounded w-2/3 mt-1" />
                                </GlassCard>
                            ))}
                        </div>
                    ) : (
                        <div className="space-y-3">
                            {insights.map((insight, i) => {
                                const style =
                                    SEVERITY_STYLES[insight.severity] || SEVERITY_STYLES.info;
                                const Icon = style.icon;
                                return (
                                    <GlassCard
                                        key={i}
                                        className={`!p-4 border-l-4 ${style.border}`}
                                    >
                                        <div className="flex items-start gap-3">
                                            <Icon
                                                className={`h-4 w-4 mt-0.5 shrink-0 ${style.color}`}
                                            />
                                            <div className="min-w-0 flex-1">
                                                <div className="flex items-center gap-2 mb-1">
                                                    <span
                                                        className={`text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded ${style.badge}`}
                                                    >
                                                        {insight.category}
                                                    </span>
                                                </div>
                                                <p className="text-sm font-semibold">
                                                    {insight.title}
                                                </p>
                                                <p className="text-xs text-muted-foreground mt-1">
                                                    {insight.detail}
                                                </p>
                                            </div>
                                        </div>
                                    </GlassCard>
                                );
                            })}
                            {insightsGeneratedAt && (
                                <p className="text-[10px] text-muted-foreground/70 text-center">
                                    Generated: {insightsGeneratedAt}
                                </p>
                            )}
                        </div>
                    )}
                </div>

                {/* RIGHT: Chat (3/5 ≈ 60%) */}
                <div className="lg:col-span-3 flex flex-col">
                    <GlassCard className="flex flex-col flex-1 !p-0 overflow-hidden">
                        {/* Chat header */}
                        <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
                            <MessageSquare className="h-4 w-4 text-blue-500" />
                            <h2 className="text-sm font-semibold">Chat with your data</h2>
                            {messages.length > 0 && (
                                <button
                                    onClick={() => setMessages([])}
                                    className="ml-auto text-[11px] text-muted-foreground hover:text-foreground"
                                >
                                    Clear
                                </button>
                            )}
                        </div>

                        {/* Messages area */}
                        <div
                            className="flex-1 overflow-y-auto px-4 py-4 space-y-4"
                            style={{ minHeight: 300 }}
                        >
                            {messages.length === 0 && !chatLoading && (
                                <div className="flex flex-col items-center justify-center h-full text-center py-12">
                                    <Brain className="h-12 w-12 text-muted-foreground/30 mb-4" />
                                    <p className="text-sm text-muted-foreground">
                                        Ask me anything about your station&apos;s data
                                    </p>
                                    <p className="text-xs text-muted-foreground/70 mt-1">
                                        Revenue, invoices, payments, tanks, customers...
                                    </p>
                                </div>
                            )}

                            {messages.map((msg, i) => (
                                <div
                                    key={i}
                                    className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
                                >
                                    <div
                                        className={`max-w-[85%] rounded-2xl px-4 py-2.5 text-sm ${
                                            msg.role === "user"
                                                ? "bg-purple-500/90 text-white rounded-br-md"
                                                : "bg-muted text-foreground rounded-bl-md"
                                        }`}
                                    >
                                        <div className="whitespace-pre-wrap">{msg.content}</div>
                                    </div>
                                </div>
                            ))}

                            {chatLoading && (
                                <div className="flex justify-start">
                                    <div className="bg-muted rounded-2xl rounded-bl-md px-4 py-3">
                                        <div className="flex items-center gap-2 text-muted-foreground">
                                            <Loader2 className="h-4 w-4 animate-spin" />
                                            <span className="text-sm">Analyzing...</span>
                                        </div>
                                    </div>
                                </div>
                            )}

                            <div ref={messagesEndRef} />
                        </div>

                        {/* Suggested questions (empty state) */}
                        {messages.length === 0 && (
                            <div className="px-4 pb-3">
                                <div className="flex flex-wrap gap-1.5">
                                    {SUGGESTED_QUESTIONS.map((q) => (
                                        <button
                                            key={q}
                                            onClick={() => sendMessage(q)}
                                            className="text-[11px] px-2.5 py-1 rounded-full border border-border bg-card hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
                                        >
                                            {q}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Input bar */}
                        <div className="border-t border-border px-4 py-3">
                            <div className="flex items-center gap-2">
                                <input
                                    type="text"
                                    value={input}
                                    onChange={(e) => setInput(e.target.value)}
                                    onKeyDown={handleKeyDown}
                                    placeholder="Ask about revenue, invoices, tanks, customers..."
                                    className="flex-1 bg-card border border-border rounded-xl px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-purple-500/40"
                                    disabled={chatLoading}
                                />
                                <button
                                    onClick={handleSend}
                                    disabled={!input.trim() || chatLoading}
                                    className="p-2.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white disabled:opacity-40 disabled:hover:bg-purple-600 transition-colors"
                                    aria-label="Send"
                                >
                                    <Send className="h-4 w-4" />
                                </button>
                            </div>
                        </div>
                    </GlassCard>
                </div>
            </div>
        </div>
    );
}
