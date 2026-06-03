package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A single calibrated point of a {@link DipChart}: the stock volume in litres
 * at an exact dip of {@code dipMm} millimetres. Charts are normalised to one
 * point per millimetre so lookup is a direct indexed read (with linear
 * interpolation only for sub-millimetre fractions).
 */
@Entity
@Table(name = "dip_chart_point",
    uniqueConstraints = @UniqueConstraint(name = "uk_dip_point_chart_mm", columnNames = {"chart_id", "dip_mm"}),
    indexes = @Index(name = "idx_dip_point_chart_mm", columnList = "chart_id, dip_mm"))
@Getter
@Setter
public class DipChartPoint extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chart_id", nullable = false)
    private DipChart chart;

    @Column(name = "dip_mm", nullable = false)
    private int dipMm;

    @Column(name = "volume_litres", nullable = false)
    private double volumeLitres;
}
