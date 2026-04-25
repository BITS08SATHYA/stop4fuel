"use client";

const COLORS = [
    "bg-blue-500",
    "bg-emerald-500",
    "bg-purple-500",
    "bg-orange-500",
    "bg-pink-500",
    "bg-teal-500",
    "bg-indigo-500",
    "bg-rose-500",
];

function initialsOf(name: string): string {
    return name
        .split(" ")
        .filter(Boolean)
        .map(w => w[0])
        .slice(0, 2)
        .join("")
        .toUpperCase();
}

function colorOf(name: string): string {
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    return COLORS[Math.abs(hash) % COLORS.length];
}

export function InitialsAvatar({ name, size = "md" }: { name: string; size?: "sm" | "md" }) {
    const sizeClass = size === "sm" ? "w-8 h-8 text-xs" : "w-10 h-10 text-sm";
    return (
        <div
            className={`${sizeClass} ${colorOf(name || "?")} rounded-full flex items-center justify-center text-white font-semibold flex-shrink-0`}
        >
            {initialsOf(name || "?")}
        </div>
    );
}
