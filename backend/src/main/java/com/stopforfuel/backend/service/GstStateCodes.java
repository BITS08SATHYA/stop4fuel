package com.stopforfuel.backend.service;

import java.util.Map;

/**
 * GSTIN state-code → state-name lookup. The first two characters of a GSTIN
 * are the GST state code. StateOfSupply is not stored on Supplier/Company, so
 * the purchase register derives it from the supplier GSTIN.
 */
public final class GstStateCodes {

    private GstStateCodes() {}

    private static final Map<String, String> BY_CODE = Map.ofEntries(
            Map.entry("01", "Jammu and Kashmir"),
            Map.entry("02", "Himachal Pradesh"),
            Map.entry("03", "Punjab"),
            Map.entry("04", "Chandigarh"),
            Map.entry("05", "Uttarakhand"),
            Map.entry("06", "Haryana"),
            Map.entry("07", "Delhi"),
            Map.entry("08", "Rajasthan"),
            Map.entry("09", "Uttar Pradesh"),
            Map.entry("10", "Bihar"),
            Map.entry("11", "Sikkim"),
            Map.entry("12", "Arunachal Pradesh"),
            Map.entry("13", "Nagaland"),
            Map.entry("14", "Manipur"),
            Map.entry("15", "Mizoram"),
            Map.entry("16", "Tripura"),
            Map.entry("17", "Meghalaya"),
            Map.entry("18", "Assam"),
            Map.entry("19", "West Bengal"),
            Map.entry("20", "Jharkhand"),
            Map.entry("21", "Odisha"),
            Map.entry("22", "Chhattisgarh"),
            Map.entry("23", "Madhya Pradesh"),
            Map.entry("24", "Gujarat"),
            Map.entry("25", "Daman and Diu"),
            Map.entry("26", "Dadra and Nagar Haveli"),
            Map.entry("27", "Maharashtra"),
            Map.entry("28", "Andhra Pradesh"),
            Map.entry("29", "Karnataka"),
            Map.entry("30", "Goa"),
            Map.entry("31", "Lakshadweep"),
            Map.entry("32", "Kerala"),
            Map.entry("33", "TamilNadu"),
            Map.entry("34", "Puducherry"),
            Map.entry("35", "Andaman and Nicobar Islands"),
            Map.entry("36", "Telangana"),
            Map.entry("37", "Andhra Pradesh"),
            Map.entry("38", "Ladakh")
    );

    /** State name for a GSTIN, or "" when the GSTIN is blank/too short/unknown. */
    public static String stateName(String gstin) {
        if (gstin == null || gstin.trim().length() < 2) return "";
        return BY_CODE.getOrDefault(gstin.trim().substring(0, 2), "");
    }
}
