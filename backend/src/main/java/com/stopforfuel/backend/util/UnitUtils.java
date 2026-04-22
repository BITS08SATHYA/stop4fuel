package com.stopforfuel.backend.util;

import java.util.Set;

/**
 * Unit classification — tells whether a product's unit counts discrete items
 * (Pieces, Box, Packets, ...) as opposed to measured quantities (Liters, Kg, ...).
 * Used to round stock fields to integers on save so piece-count stock doesn't drift
 * into fractional values.
 */
public final class UnitUtils {

    private static final Set<String> WHOLE_COUNT = Set.of(
            "PIECES", "PIECE", "PCS", "PC",
            "BOX", "BOXES",
            "PACKET", "PACKETS", "PKT", "PKTS",
            "BOTTLE", "BOTTLES",
            "CAN", "CANS",
            "NOS", "NO",
            "EACH", "UNIT", "UNITS"
    );

    private UnitUtils() {}

    public static boolean isWholeCount(String unit) {
        if (unit == null) return false;
        return WHOLE_COUNT.contains(unit.trim().toUpperCase());
    }

    /** Rounds the value to the nearest integer if the unit is whole-count; returns the input otherwise (null-safe). */
    public static Double roundIfWholeCount(String unit, Double val) {
        if (val == null) return null;
        return isWholeCount(unit) ? (double) Math.round(val) : val;
    }
}
