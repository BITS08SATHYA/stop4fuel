"use client";

import { useState, useEffect } from "react";
import { getEmployeeFileUrl } from "@/lib/api/station";

interface EmployeeAvatarProps {
    employeeId: number;
    name: string;
    photoUrl?: string;
    size?: "sm" | "md" | "lg";
}

const sizeMap = {
    sm: "w-8 h-8 text-xs",
    md: "w-12 h-12 text-sm",
    lg: "w-24 h-24 text-2xl",
};

const colors = [
    "bg-blue-500",
    "bg-emerald-500",
    "bg-purple-500",
    "bg-orange-500",
    "bg-pink-500",
    "bg-teal-500",
    "bg-indigo-500",
    "bg-rose-500",
];

function getInitials(name: string): string {
    return name
        .split(" ")
        .filter(Boolean)
        .map((w) => w[0])
        .slice(0, 2)
        .join("")
        .toUpperCase();
}

function getColor(name: string): string {
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash) % colors.length];
}

export function EmployeeAvatar({ employeeId, name, photoUrl, size = "md" }: EmployeeAvatarProps) {
    const [imgUrl, setImgUrl] = useState<string | null>(null);
    const [imgError, setImgError] = useState(false);

    useEffect(() => {
        if (!photoUrl) {
            setImgUrl(null);
            setImgError(false);
            return;
        }
        let cancelled = false;
        getEmployeeFileUrl(employeeId, "photo")
            .then((data) => {
                if (!cancelled) setImgUrl(data.url);
            })
            .catch(() => {
                if (!cancelled) setImgError(true);
            });
        return () => { cancelled = true; };
    }, [employeeId, photoUrl]);

    const sizeClass = sizeMap[size];

    if (imgUrl && !imgError) {
        return (
            <img
                src={imgUrl}
                alt={name}
                className={`${sizeClass} rounded-full object-cover flex-shrink-0`}
                onError={() => setImgError(true)}
            />
        );
    }

    return (
        <div
            className={`${sizeClass} ${getColor(name)} rounded-full flex items-center justify-center text-white font-semibold flex-shrink-0`}
        >
            {getInitials(name || "?")}
        </div>
    );
}
