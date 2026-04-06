package com.stopforfuel.backend.config;

import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.entity.Party;
import com.stopforfuel.backend.entity.Permission;
import com.stopforfuel.backend.entity.RolePermission;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.repository.GroupRepository;
import com.stopforfuel.backend.repository.PartyRepository;
import com.stopforfuel.backend.repository.PermissionRepository;
import com.stopforfuel.backend.repository.RolePermissionRepository;
import com.stopforfuel.backend.repository.RolesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final RolesRepository rolesRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PartyRepository partyRepository;
    private final GroupRepository groupRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedParties();
        seedCustomerGroups();
        seedPermissions();
        migrateManagePermissions();
        seedRolePermissions();
    }

    private void seedRoles() {
        for (String roleType : List.of("CUSTOMER", "EMPLOYEE", "DEALER", "OWNER", "ADMIN", "CASHIER")) {
            if (rolesRepository.findByRoleType(roleType).isEmpty()) {
                Roles role = new Roles();
                role.setRoleType(roleType);
                rolesRepository.save(role);
                log.info("Seeded role: {}", roleType);
            }
        }
    }

    private void seedParties() {
        if (partyRepository.count() == 0) {
            for (String type : List.of("Local", "Statement")) {
                Party party = new Party();
                party.setPartyType(type);
                partyRepository.save(party);
            }
            log.info("Seeded parties");
        }
    }

    private void seedCustomerGroups() {
        if (groupRepository.findByGroupName("Default").isEmpty()) {
            Group group = new Group();
            group.setGroupName("Default");
            group.setDescription("Default customer group");
            groupRepository.save(group);
            log.info("Seeded default customer group");
        }
    }

    private void seedPermissions() {
        if (permissionRepository.count() > 0) return;

        String[][] perms = {
            {"DASHBOARD_VIEW", "View dashboard", "DASHBOARD", "VIEW"},
            {"CUSTOMER_VIEW", "View customers", "CUSTOMER", "VIEW"},
            {"CUSTOMER_CREATE", "Create customers", "CUSTOMER", "CREATE"},
            {"CUSTOMER_UPDATE", "Update customers", "CUSTOMER", "UPDATE"},
            {"CUSTOMER_DELETE", "Delete customers", "CUSTOMER", "DELETE"},
            {"EMPLOYEE_VIEW", "View employees", "EMPLOYEE", "VIEW"},
            {"EMPLOYEE_CREATE", "Create employees", "EMPLOYEE", "CREATE"},
            {"EMPLOYEE_UPDATE", "Update employees", "EMPLOYEE", "UPDATE"},
            {"EMPLOYEE_DELETE", "Delete employees", "EMPLOYEE", "DELETE"},
            {"PRODUCT_VIEW", "View products", "PRODUCT", "VIEW"},
            {"PRODUCT_CREATE", "Create products", "PRODUCT", "CREATE"},
            {"PRODUCT_UPDATE", "Update products", "PRODUCT", "UPDATE"},
            {"PRODUCT_DELETE", "Delete products", "PRODUCT", "DELETE"},
            {"STATION_VIEW", "View station layout", "STATION", "VIEW"},
            {"STATION_CREATE", "Create station layout", "STATION", "CREATE"},
            {"STATION_UPDATE", "Update station layout", "STATION", "UPDATE"},
            {"STATION_DELETE", "Delete station layout", "STATION", "DELETE"},
            {"INVENTORY_VIEW", "View inventory", "INVENTORY", "VIEW"},
            {"INVENTORY_CREATE", "Create inventory", "INVENTORY", "CREATE"},
            {"INVENTORY_UPDATE", "Update inventory", "INVENTORY", "UPDATE"},
            {"INVENTORY_DELETE", "Delete inventory", "INVENTORY", "DELETE"},
            {"SHIFT_VIEW", "View shifts", "SHIFT", "VIEW"},
            {"SHIFT_CREATE", "Create shifts", "SHIFT", "CREATE"},
            {"SHIFT_UPDATE", "Update shifts", "SHIFT", "UPDATE"},
            {"SHIFT_DELETE", "Delete shifts", "SHIFT", "DELETE"},
            {"INVOICE_VIEW", "View invoices", "INVOICE", "VIEW"},
            {"INVOICE_CREATE", "Create invoices", "INVOICE", "CREATE"},
            {"INVOICE_UPDATE", "Update invoices", "INVOICE", "UPDATE"},
            {"INVOICE_DELETE", "Delete invoices", "INVOICE", "DELETE"},
            {"PAYMENT_VIEW", "View payments", "PAYMENT", "VIEW"},
            {"PAYMENT_CREATE", "Create payments", "PAYMENT", "CREATE"},
            {"PAYMENT_UPDATE", "Update payments", "PAYMENT", "UPDATE"},
            {"PAYMENT_DELETE", "Delete payments", "PAYMENT", "DELETE"},
            {"FINANCE_VIEW", "View finance", "FINANCE", "VIEW"},
            {"FINANCE_CREATE", "Create finance", "FINANCE", "CREATE"},
            {"FINANCE_UPDATE", "Update finance", "FINANCE", "UPDATE"},
            {"FINANCE_DELETE", "Delete finance", "FINANCE", "DELETE"},
            {"PURCHASE_VIEW", "View purchases", "PURCHASE", "VIEW"},
            {"PURCHASE_CREATE", "Create purchases", "PURCHASE", "CREATE"},
            {"PURCHASE_UPDATE", "Update purchases", "PURCHASE", "UPDATE"},
            {"PURCHASE_DELETE", "Delete purchases", "PURCHASE", "DELETE"},
            {"REPORT_VIEW", "View reports", "REPORT", "VIEW"},
            {"REPORT_GENERATE", "Generate reports", "REPORT", "GENERATE"},
            {"SETTINGS_VIEW", "View settings", "SETTINGS", "VIEW"},
            {"SETTINGS_CREATE", "Create settings", "SETTINGS", "CREATE"},
            {"SETTINGS_UPDATE", "Update settings", "SETTINGS", "UPDATE"},
            {"SETTINGS_DELETE", "Delete settings", "SETTINGS", "DELETE"},
            {"USER_VIEW", "View users", "USER", "VIEW"},
            {"USER_CREATE", "Create users", "USER", "CREATE"},
            {"USER_UPDATE", "Update users", "USER", "UPDATE"},
            {"USER_DELETE", "Delete users", "USER", "DELETE"},
        };

        for (String[] p : perms) {
            Permission perm = new Permission(p[0], p[1], p[2], p[3], true);
            permissionRepository.save(perm);
        }
        log.info("Seeded {} permissions", perms.length);
    }

    /**
     * Migrates old _MANAGE permissions to fine-grained _CREATE/_UPDATE/_DELETE.
     * Transfers role assignments and removes the old _MANAGE entries.
     */
    private void migrateManagePermissions() {
        List<Permission> managePerms = permissionRepository.findAll().stream()
                .filter(p -> p.getCode().endsWith("_MANAGE"))
                .toList();

        if (managePerms.isEmpty()) return;

        for (Permission manage : managePerms) {
            String module = manage.getModule();
            List<RolePermission> existingAssignments = rolePermissionRepository.findByPermissionId(manage.getId());

            for (String action : List.of("CREATE", "UPDATE", "DELETE")) {
                String newCode = module + "_" + action;
                Permission newPerm = permissionRepository.findByCode(newCode).orElseGet(() -> {
                    Permission p = new Permission(newCode, action.substring(0, 1) + action.substring(1).toLowerCase() + " " + module.toLowerCase(), module, action, true);
                    return permissionRepository.save(p);
                });

                for (RolePermission rp : existingAssignments) {
                    if (!rolePermissionRepository.existsByRoleIdAndPermissionCode(rp.getRole().getId(), newCode)) {
                        RolePermission newRp = new RolePermission();
                        newRp.setRole(rp.getRole());
                        newRp.setPermission(newPerm);
                        rolePermissionRepository.save(newRp);
                    }
                }
            }

            rolePermissionRepository.deleteByPermissionId(manage.getId());
            permissionRepository.delete(manage);
            log.info("Migrated {}_MANAGE to fine-grained permissions", module);
        }
    }

    private void seedRolePermissions() {
        Map<String, Set<String>> defaults = Map.of(
            "CASHIER", Set.of(
                "DASHBOARD_VIEW",
                "SHIFT_VIEW", "SHIFT_CREATE", "SHIFT_UPDATE",
                "INVOICE_VIEW", "INVOICE_CREATE", "INVOICE_UPDATE",
                "PAYMENT_VIEW", "PAYMENT_CREATE", "PAYMENT_UPDATE",
                "FINANCE_VIEW"
            ),
            "ADMIN", Set.of(), // gets all except SETTINGS_DELETE, USER_DELETE — handled below
            "EMPLOYEE", Set.of(
                "DASHBOARD_VIEW", "SHIFT_VIEW", "INVENTORY_VIEW"
            )
        );

        for (var entry : defaults.entrySet()) {
            String roleType = entry.getKey();
            Roles role = rolesRepository.findByRoleType(roleType).orElse(null);
            if (role == null) continue;

            // Skip if role already has assignments
            if (!rolePermissionRepository.findByRoleId(role.getId()).isEmpty()) continue;

            if ("ADMIN".equals(roleType)) {
                // Admin gets everything except SETTINGS_DELETE and USER_DELETE
                List<Permission> allPerms = permissionRepository.findAll();
                Set<String> excluded = Set.of("SETTINGS_DELETE", "USER_DELETE");
                for (Permission p : allPerms) {
                    if (!excluded.contains(p.getCode())) {
                        RolePermission rp = new RolePermission();
                        rp.setRole(role);
                        rp.setPermission(p);
                        rolePermissionRepository.save(rp);
                    }
                }
            } else {
                for (String code : entry.getValue()) {
                    permissionRepository.findByCode(code).ifPresent(p -> {
                        RolePermission rp = new RolePermission();
                        rp.setRole(role);
                        rp.setPermission(p);
                        rolePermissionRepository.save(rp);
                    });
                }
            }
            log.info("Seeded role permissions for {}", roleType);
        }
    }
}
