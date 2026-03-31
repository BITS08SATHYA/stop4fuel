package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class UserListDTO {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private String role;
    private String designation;
    private String userType; // EMPLOYEE, CUSTOMER
    private String status;
    private LocalDate joinDate;
    private String employeeCode;

    public static UserListDTO from(User user) {
        UserListDTOBuilder builder = UserListDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhoneNumbers() != null && !user.getPhoneNumbers().isEmpty()
                        ? user.getPhoneNumbers().iterator().next() : null)
                .email(user.getEmails() != null && !user.getEmails().isEmpty()
                        ? user.getEmails().iterator().next() : null)
                .role(user.getRole() != null ? user.getRole().getRoleType() : null)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .joinDate(user.getJoinDate());

        if (user instanceof Employee employee) {
            builder.userType("EMPLOYEE");
            builder.employeeCode(employee.getEmployeeCode());
            if (employee.getDesignationEntity() != null) {
                builder.designation(employee.getDesignationEntity().getName());
            }
        } else if (user instanceof Customer) {
            builder.userType("CUSTOMER");
        } else {
            builder.userType("USER");
        }

        return builder.build();
    }
}
