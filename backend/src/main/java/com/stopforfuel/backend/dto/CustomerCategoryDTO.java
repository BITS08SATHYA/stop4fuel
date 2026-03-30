package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.CustomerCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerCategoryDTO {
    private Long id;
    private String categoryName;
    private String categoryType;
    private String description;

    public static CustomerCategoryDTO from(CustomerCategory cc) {
        return CustomerCategoryDTO.builder()
                .id(cc.getId())
                .categoryName(cc.getCategoryName())
                .categoryType(cc.getCategoryType())
                .description(cc.getDescription())
                .build();
    }
}
