"use client";

import { Bar, BarChart, ResponsiveContainer, XAxis, YAxis, Tooltip } from "recharts";

const data = [
    { name: "6am", total: Math.floor(Math.random() * 5000) + 1000 },
    { name: "9am", total: Math.floor(Math.random() * 5000) + 1000 },
    { name: "12pm", total: Math.floor(Math.random() * 5000) + 1000 },
    { name: "3pm", total: Math.floor(Math.random() * 5000) + 1000 },
    { name: "6pm", total: Math.floor(Math.random() * 5000) + 1000 },
    { name: "9pm", total: Math.floor(Math.random() * 5000) + 1000 },
    { name: "12am", total: Math.floor(Math.random() * 5000) + 1000 },
];

export function OverviewChart() {
    return (
        <ResponsiveContainer width="100%" height={350}>
            <BarChart data={data}>
                <XAxis
                    dataKey="name"
                    stroke="#888888"
                    fontSize={12}
                    tickLine={false}
                    axisLine={false}
                />
                <YAxis
                    stroke="#888888"
                    fontSize={12}
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={(value) => `$${value}`}
                />
                <Tooltip
                    contentStyle={{ backgroundColor: '#1f2937', borderColor: '#374151', color: '#f3f4f6' }}
                    itemStyle={{ color: '#f3f4f6' }}
                    cursor={{ fill: '#374151', opacity: 0.4 }}
                />
                <Bar dataKey="total" fill="#3b82f6" radius={[4, 4, 0, 0]} />
            </BarChart>
        </ResponsiveContainer>
    );
}
