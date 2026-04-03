package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Group;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupDTO {
    private Long id;
    private String groupName;
    private String description;

    public static GroupDTO from(Group g) {
        return GroupDTO.builder()
                .id(g.getId())
                .groupName(g.getGroupName())
                .description(g.getDescription())
                .build();
    }
}
