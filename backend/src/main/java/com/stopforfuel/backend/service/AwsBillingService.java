package com.stopforfuel.backend.service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class AwsBillingService {

    @Value("${aws.billing.enabled:false}")
    private boolean enabled;

    @Value("${aws.billing.region:us-east-1}")
    private String region;

    @Cacheable(value = "awsBilling", key = "'mtd'")
    public BillingDto getMonthToDate() {
        BillingDto dto = new BillingDto();

        if (!enabled) {
            dto.setAvailable(false);
            return dto;
        }

        try {
            CostExplorerClient client = CostExplorerClient.builder()
                    .region(Region.of(region))
                    .build();

            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            String start = monthStart.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String end = today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

            GetCostAndUsageResponse response = client.getCostAndUsage(
                    GetCostAndUsageRequest.builder()
                            .timePeriod(DateInterval.builder().start(start).end(end).build())
                            .granularity(Granularity.MONTHLY)
                            .metrics("UnblendedCost")
                            .build()
            );

            if (!response.resultsByTime().isEmpty()) {
                MetricValue cost = response.resultsByTime().get(0).total().get("UnblendedCost");
                dto.setMonthToDateCost(new BigDecimal(cost.amount()));
                dto.setCurrency(cost.unit());
            }

            dto.setPeriodStart(start);
            dto.setPeriodEnd(end);
            dto.setAvailable(true);
            client.close();

        } catch (Exception e) {
            log.warn("Failed to fetch AWS billing: {}", e.getMessage());
            dto.setAvailable(false);
        }

        return dto;
    }

    @Getter
    @Setter
    public static class BillingDto {
        private boolean available;
        private BigDecimal monthToDateCost;
        private String currency;
        private String periodStart;
        private String periodEnd;
    }
}
