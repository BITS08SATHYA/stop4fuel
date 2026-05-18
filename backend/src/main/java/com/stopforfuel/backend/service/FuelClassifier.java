package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Product;

/**
 * Single source of truth for mapping a {@link Product} to its fuel label
 * (XP / MS / HSD) or OTHER (non-fuel). Previously duplicated inside
 * VatReportExcelService and VatReportPdfGenerator — both now delegate here.
 */
public final class FuelClassifier {

    private FuelClassifier() {}

    public enum FuelLabel {
        XP("XP", "XTRA PREMIUM"),
        MS("MS", "PETROL"),
        HSD("HSD", "DIESEL"),
        OTHER("OTHER", null);

        private final String label;
        private final String section;

        FuelLabel(String label, String section) {
            this.label = label;
            this.section = section;
        }

        /** Short label (XP/MS/HSD/OTHER) used in the VAT register. */
        public String label() {
            return label;
        }

        /** Daily-Sales-Register sheet name: "XTRA PREMIUM" / "PETROL" / "DIESEL"; null for OTHER. */
        public String section() {
            return section;
        }
    }

    public static FuelLabel classify(Product p) {
        if (p == null) return FuelLabel.OTHER;
        String fuelFamily = p.getFuelFamily() != null ? p.getFuelFamily().toUpperCase() : "";
        String name = p.getName() != null ? p.getName().toUpperCase() : "";
        String gradeName = p.getGrade() != null && p.getGrade().getName() != null
                ? p.getGrade().getName().toUpperCase() : "";
        if (fuelFamily.equals("DIESEL") || name.contains("DIESEL") || name.equals("HSD")) {
            return FuelLabel.HSD;
        }
        boolean petrol = fuelFamily.equals("PETROL") || name.contains("PETROL")
                || name.equals("MS") || name.contains("XTRA");
        if (!petrol) return FuelLabel.OTHER;
        boolean isPremium = name.contains("XTRA") || name.contains("PREMIUM") || name.equals("XP")
                || gradeName.contains("XTRA") || gradeName.contains("PREMIUM");
        return isPremium ? FuelLabel.XP : FuelLabel.MS;
    }
}
