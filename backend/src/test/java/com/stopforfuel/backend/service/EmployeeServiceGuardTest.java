package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.exception.PrivilegeException;
import com.stopforfuel.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Employee extends User, so the Employees screen writes logins. These pin the seniority rules
 * there too — without them it is a side door around User Management's guards.
 */
class EmployeeServiceGuardTest {

    private EmployeeRepository employeeRepository;
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        service = new EmployeeService(employeeRepository, mock(SalaryHistoryRepository.class),
                mock(OperationalAdvanceRepository.class), mock(S3StorageService.class),
                mock(DesignationRepository.class), mock(RolesRepository.class), mock(EntityManager.class));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Roles role(String type) {
        Roles r = new Roles();
        r.setRoleType(type);
        return r;
    }

    private static Employee employee(long id, String roleType) {
        Employee e = new Employee();
        e.setId(id);
        e.setName("Employee " + id);
        e.setRole(role(roleType));
        e.setStatus(EntityStatus.ACTIVE);
        e.setScid(1L);
        return e;
    }

    private void authenticatedAs(String roleType, long userId) {
        Map<String, Object> principal = Map.of(
                "sub", String.valueOf(userId),
                "name", "Caller",
                "custom:role", roleType,
                "custom:scid", "1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + roleType))));
    }

    private void exists(Employee target) {
        when(employeeRepository.findByIdAndScid(eq(target.getId()), anyLong())).thenReturn(Optional.of(target));
    }

    @Test
    void adminCannotCreateAnEmployeeAboveItsRank() {
        authenticatedAs("ADMIN", 99);
        Employee draft = employee(0, "OWNER");
        draft.setId(null);

        assertThrows(PrivilegeException.class, () -> service.createEmployee(draft));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void adminCannotCreateAPeerAdmin() {
        authenticatedAs("ADMIN", 99);
        Employee draft = employee(0, "ADMIN");
        draft.setId(null);

        assertThrows(PrivilegeException.class, () -> service.createEmployee(draft));
    }

    @Test
    void adminMayCreateACashier() {
        authenticatedAs("ADMIN", 99);
        Employee draft = employee(0, "CASHIER");
        draft.setId(null);

        assertDoesNotThrow(() -> service.createEmployee(draft));
    }

    @Test
    void ownerCannotBlockPrimeThroughTheEmployeeForm() {
        authenticatedAs("OWNER", 99);
        Employee prime = employee(1, "PRIME");
        exists(prime);
        Employee edit = employee(1, "PRIME");
        edit.setStatus(EntityStatus.BLOCKED);

        assertThrows(PrivilegeException.class, () -> service.updateEmployee(1L, edit));
        assertEquals(EntityStatus.ACTIVE, prime.getStatus());
    }

    @Test
    void nobodyMayChangeTheirOwnStatus() {
        authenticatedAs("OWNER", 5);
        Employee self = employee(5, "OWNER");
        exists(self);
        Employee edit = employee(5, "OWNER");
        edit.setStatus(EntityStatus.INACTIVE);

        assertThrows(PrivilegeException.class, () -> service.updateEmployee(5L, edit));
    }

    @Test
    void ownerMayEditTheirOwnDetails() {
        authenticatedAs("OWNER", 5);
        exists(employee(5, "OWNER"));
        Employee edit = employee(5, "OWNER");
        edit.setName("New Name");

        assertEquals("New Name", service.updateEmployee(5L, edit).getName());
    }

    @Test
    void missingStatusInTheEditKeepsTheCurrentOne() {
        authenticatedAs("OWNER", 99);
        Employee cashier = employee(7, "CASHIER");
        cashier.setStatus(EntityStatus.BLOCKED);
        exists(cashier);
        Employee edit = employee(7, "CASHIER");
        edit.setStatus(null);

        assertEquals(EntityStatus.BLOCKED, service.updateEmployee(7L, edit).getStatus());
    }
}
