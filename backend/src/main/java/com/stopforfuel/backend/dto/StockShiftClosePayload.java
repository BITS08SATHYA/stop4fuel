package com.stopforfuel.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload broadcast at shift-close: today's stock availability, sales, and prices.
 * Identical shape across SSE, push, and email channels.
 */
public class StockShiftClosePayload {

    public static final String TYPE = "STOCK_SHIFT_CLOSE_SUMMARY";

    private String type = TYPE;
    private Long shiftId;
    private Long scid;
    private String companyName;
    private LocalDateTime closedAt;
    private List<TankRow> tanks;
    private List<ProductRow> products;
    private int lowStockCount;

    public String getType() { return type; }
    public Long getShiftId() { return shiftId; }
    public Long getScid() { return scid; }
    public String getCompanyName() { return companyName; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public List<TankRow> getTanks() { return tanks; }
    public List<ProductRow> getProducts() { return products; }
    public int getLowStockCount() { return lowStockCount; }

    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public void setScid(Long scid) { this.scid = scid; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public void setTanks(List<TankRow> tanks) { this.tanks = tanks; }
    public void setProducts(List<ProductRow> products) { this.products = products; }
    public void setLowStockCount(int lowStockCount) { this.lowStockCount = lowStockCount; }

    public static class TankRow {
        private String tankName;
        private String productName;
        private double currentLiters;
        private double soldLiters;
        private double pricePerLiter;
        private boolean lowStock;

        public TankRow() {}
        public TankRow(String tankName, String productName, double currentLiters, double soldLiters,
                       double pricePerLiter, boolean lowStock) {
            this.tankName = tankName;
            this.productName = productName;
            this.currentLiters = currentLiters;
            this.soldLiters = soldLiters;
            this.pricePerLiter = pricePerLiter;
            this.lowStock = lowStock;
        }
        public String getTankName() { return tankName; }
        public String getProductName() { return productName; }
        public double getCurrentLiters() { return currentLiters; }
        public double getSoldLiters() { return soldLiters; }
        public double getPricePerLiter() { return pricePerLiter; }
        public boolean isLowStock() { return lowStock; }
    }

    public static class ProductRow {
        private String productName;
        private String unit;
        private double currentUnits;
        private double soldUnits;
        private double priceEach;
        private boolean lowStock;

        public ProductRow() {}
        public ProductRow(String productName, String unit, double currentUnits, double soldUnits,
                          double priceEach, boolean lowStock) {
            this.productName = productName;
            this.unit = unit;
            this.currentUnits = currentUnits;
            this.soldUnits = soldUnits;
            this.priceEach = priceEach;
            this.lowStock = lowStock;
        }
        public String getProductName() { return productName; }
        public String getUnit() { return unit; }
        public double getCurrentUnits() { return currentUnits; }
        public double getSoldUnits() { return soldUnits; }
        public double getPriceEach() { return priceEach; }
        public boolean isLowStock() { return lowStock; }
    }
}
