package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Tank calibration (dip / strapping) chart. Maps a physical dip reading to a
 * stock volume in litres. Calibration is physical to a single tank's geometry,
 * so a chart belongs to exactly one {@link Tank}. Points are stored at
 * millimetre resolution (see {@link DipChartPoint}).
 */
@Entity
@Table(name = "dip_chart", indexes = {
    @Index(name = "idx_dip_chart_tank_id", columnList = "tank_id")
})
@Getter
@Setter
public class DipChart extends BaseEntity {

    @NotNull(message = "Tank is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tank_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Tank tank;

    /** Owning product, for labelling/audit only. The lookup key is the tank. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    /** Source CSV file name the chart was imported from, e.g. "hsd.csv". */
    private String sourceFile;

    /** Highest dip in millimetres that the chart covers. */
    private Integer maxDipMm;

    @Column(nullable = false)
    private boolean active = true;

    /** True when the importer detected and repaired one or more bad rows. */
    @Column(nullable = false)
    private boolean hadGlitches = false;

    /** Human-readable audit of every repaired/flagged point. */
    @Column(columnDefinition = "TEXT")
    private String glitchLog;

    @JsonIgnore
    @OneToMany(mappedBy = "chart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DipChartPoint> points = new ArrayList<>();
}
