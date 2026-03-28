"use client";

import { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

interface CustomerLocation {
    id: number;
    name: string;
    status: string;
    latitude: number;
    longitude: number;
    groupName: string | null;
    address: string | null;
}

interface CustomerMapProps {
    customers: CustomerLocation[];
    stationLat: number;
    stationLng: number;
    statusFilter: string;
    routeCustomerIds: number[];
    showRoute: boolean;
}

const STATUS_COLORS: Record<string, string> = {
    ACTIVE: "#22c55e",
    BLOCKED: "#ef4444",
    INACTIVE: "#eab308",
};

function createMarkerIcon(color: string) {
    return L.divIcon({
        className: "custom-marker",
        html: `<div style="
            width: 28px; height: 28px; border-radius: 50% 50% 50% 0;
            background: ${color}; transform: rotate(-45deg);
            border: 2px solid white; box-shadow: 0 2px 6px rgba(0,0,0,0.3);
            display: flex; align-items: center; justify-content: center;
        "><div style="width: 8px; height: 8px; background: white; border-radius: 50%; transform: rotate(45deg);"></div></div>`,
        iconSize: [28, 28],
        iconAnchor: [14, 28],
        popupAnchor: [0, -30],
    });
}

const stationIcon = L.divIcon({
    className: "station-marker",
    html: `<div style="
        width: 36px; height: 36px; border-radius: 50%;
        background: linear-gradient(135deg, #6366f1, #8b5cf6);
        border: 3px solid white; box-shadow: 0 2px 8px rgba(0,0,0,0.4);
        display: flex; align-items: center; justify-content: center;
        color: white; font-size: 16px; font-weight: bold;
    ">&#9981;</div>`,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
    popupAnchor: [0, -20],
});

export default function CustomerMap({
    customers,
    stationLat,
    stationLng,
    statusFilter,
    routeCustomerIds,
    showRoute,
}: CustomerMapProps) {
    const mapRef = useRef<L.Map | null>(null);
    const mapContainerRef = useRef<HTMLDivElement>(null);
    const markersRef = useRef<L.LayerGroup | null>(null);
    const routeLayerRef = useRef<L.LayerGroup | null>(null);
    const [routeInfo, setRouteInfo] = useState<{ distance: string; duration: string } | null>(null);

    // Initialize map
    useEffect(() => {
        if (!mapContainerRef.current || mapRef.current) return;

        const map = L.map(mapContainerRef.current, {
            center: [stationLat, stationLng],
            zoom: 12,
            zoomControl: true,
        });

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
            maxZoom: 19,
        }).addTo(map);

        markersRef.current = L.layerGroup().addTo(map);
        routeLayerRef.current = L.layerGroup().addTo(map);
        mapRef.current = map;

        return () => {
            map.remove();
            mapRef.current = null;
        };
    }, []);

    // Update markers when customers or filter changes
    useEffect(() => {
        if (!mapRef.current || !markersRef.current) return;

        markersRef.current.clearLayers();

        // Add station marker
        L.marker([stationLat, stationLng], { icon: stationIcon })
            .bindPopup(`<div style="font-weight:600;font-size:14px;">Your Fuel Station</div>`)
            .addTo(markersRef.current);

        const filtered = statusFilter === "ALL"
            ? customers
            : customers.filter(c => c.status === statusFilter);

        const bounds: L.LatLngExpression[] = [[stationLat, stationLng]];

        filtered.forEach(c => {
            const color = STATUS_COLORS[c.status] || "#6b7280";
            const marker = L.marker([c.latitude, c.longitude], { icon: createMarkerIcon(color) });
            marker.bindPopup(`
                <div style="min-width:180px;">
                    <div style="font-weight:600;font-size:14px;margin-bottom:4px;">${c.name}</div>
                    ${c.groupName ? `<div style="font-size:12px;color:#6b7280;">Group: ${c.groupName}</div>` : ""}
                    ${c.address ? `<div style="font-size:12px;color:#6b7280;margin-top:2px;">${c.address}</div>` : ""}
                    <div style="display:flex;align-items:center;gap:4px;margin-top:6px;">
                        <span style="width:8px;height:8px;border-radius:50%;background:${color};display:inline-block;"></span>
                        <span style="font-size:12px;">${c.status}</span>
                    </div>
                    <a href="/customers/${c.id}" style="display:inline-block;margin-top:8px;font-size:12px;color:#6366f1;text-decoration:underline;">View Profile</a>
                </div>
            `);
            marker.addTo(markersRef.current!);
            bounds.push([c.latitude, c.longitude]);
        });

        if (bounds.length > 1) {
            mapRef.current.fitBounds(L.latLngBounds(bounds), { padding: [50, 50] });
        }
    }, [customers, statusFilter, stationLat, stationLng]);

    // Draw route
    useEffect(() => {
        if (!mapRef.current || !routeLayerRef.current) return;
        routeLayerRef.current.clearLayers();
        setRouteInfo(null);

        if (!showRoute || routeCustomerIds.length === 0) return;

        const selectedCustomers = customers.filter(c => routeCustomerIds.includes(c.id));
        if (selectedCustomers.length === 0) return;

        // Build coordinates: station + selected customers
        const coords = [
            `${stationLng},${stationLat}`,
            ...selectedCustomers.map(c => `${c.longitude},${c.latitude}`),
        ].join(";");

        const url = selectedCustomers.length === 1
            ? `https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson`
            : `https://router.project-osrm.org/trip/v1/driving/${coords}?roundtrip=true&source=first&overview=full&geometries=geojson`;

        fetch(url)
            .then(res => res.json())
            .then(data => {
                if (!routeLayerRef.current) return;

                const route = selectedCustomers.length === 1
                    ? data.routes?.[0]
                    : data.trips?.[0];

                if (!route) return;

                const geojson = L.geoJSON(route.geometry, {
                    style: {
                        color: "#6366f1",
                        weight: 4,
                        opacity: 0.8,
                        dashArray: "8, 6",
                    },
                });
                geojson.addTo(routeLayerRef.current);

                const distKm = (route.distance / 1000).toFixed(1);
                const durMin = Math.round(route.duration / 60);
                setRouteInfo({
                    distance: `${distKm} km`,
                    duration: durMin >= 60
                        ? `${Math.floor(durMin / 60)}h ${durMin % 60}m`
                        : `${durMin} min`,
                });

                // Add numbered markers for route stops
                selectedCustomers.forEach((c, i) => {
                    L.marker([c.latitude, c.longitude], {
                        icon: L.divIcon({
                            className: "route-stop",
                            html: `<div style="
                                width: 22px; height: 22px; border-radius: 50%;
                                background: #6366f1; color: white; font-size: 12px;
                                font-weight: bold; display: flex; align-items: center;
                                justify-content: center; border: 2px solid white;
                                box-shadow: 0 1px 4px rgba(0,0,0,0.3);
                            ">${i + 1}</div>`,
                            iconSize: [22, 22],
                            iconAnchor: [11, 11],
                        }),
                    }).addTo(routeLayerRef.current!);
                });
            })
            .catch(err => console.error("OSRM route error:", err));
    }, [showRoute, routeCustomerIds, customers, stationLat, stationLng]);

    return (
        <div className="relative w-full h-full">
            <div ref={mapContainerRef} className="w-full h-full rounded-xl" />
            {routeInfo && (
                <div className="absolute bottom-4 left-4 z-[1000] bg-card border border-border rounded-lg px-4 py-3 shadow-lg">
                    <div className="text-sm font-semibold text-foreground">Route Summary</div>
                    <div className="flex gap-4 mt-1 text-sm text-muted-foreground">
                        <span>Distance: <span className="font-medium text-foreground">{routeInfo.distance}</span></span>
                        <span>Time: <span className="font-medium text-foreground">{routeInfo.duration}</span></span>
                    </div>
                </div>
            )}
        </div>
    );
}
