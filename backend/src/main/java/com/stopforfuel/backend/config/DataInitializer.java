package com.stopforfuel.backend.config;

import com.stopforfuel.backend.entity.BillSequence;
import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.entity.Party;
import com.stopforfuel.backend.entity.Permission;
import com.stopforfuel.backend.entity.RolePermission;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.enums.BillType;
import com.stopforfuel.backend.repository.GroupRepository;
import com.stopforfuel.backend.repository.PartyRepository;
import com.stopforfuel.backend.repository.PermissionRepository;
import com.stopforfuel.backend.repository.RolePermissionRepository;
import com.stopforfuel.backend.repository.RolesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
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
    private final CacheManager cacheManager;
    private final com.stopforfuel.backend.repository.BillSequenceRepository billSequenceRepository;
    private final com.stopforfuel.backend.repository.StatementRepository statementRepository;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedParties();
        seedCustomerGroups();
        seedPermissions();
        migrateManagePermissions();
        seedRolePermissions();
        patchMissingPermissions();
        patchCashierPermissions();
        patchStatementSequence();
        backfillStatementQuantity();
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
            {"VEHICLE_VIEW", "View vehicles", "VEHICLE", "VIEW"},
            {"VEHICLE_CREATE", "Create vehicles", "VEHICLE", "CREATE"},
            {"VEHICLE_UPDATE", "Update vehicles", "VEHICLE", "UPDATE"},
            {"VEHICLE_DELETE", "Delete vehicles", "VEHICLE", "DELETE"},
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

    /**
     * Ensure CASHIER role has CUSTOMER_VIEW (needed for invoice customer search).
     * Runs idempotently on every startup to patch existing deployments.
     */
    /**
     * Ensure all required permissions exist (handles adding new permissions to existing deployments).
     * Uses the same full list as seedPermissions so any new permission added there auto-creates in prod.
     */
    private void patchMissingPermissions() {
        String[][] required = {
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
            {"VEHICLE_VIEW", "View vehicles", "VEHICLE", "VIEW"},
            {"VEHICLE_CREATE", "Create vehicles", "VEHICLE", "CREATE"},
            {"VEHICLE_UPDATE", "Update vehicles", "VEHICLE", "UPDATE"},
            {"VEHICLE_DELETE", "Delete vehicles", "VEHICLE", "DELETE"},
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
        for (String[] p : required) {
            if (permissionRepository.findByCode(p[0]).isEmpty()) {
                Permission perm = new Permission(p[0], p[1], p[2], p[3], true);
                permissionRepository.save(perm);
                log.info("Created missing permission: {}", p[0]);
            }
        }
    }

    private void patchCashierPermissions() {
        Roles cashier = rolesRepository.findByRoleType("CASHIER").orElse(null);
        if (cashier == null) return;
        boolean patched = false;
        // Full set of CASHIER permissions — ensures any new additions reach prod
        for (String code : List.of(
                "DASHBOARD_VIEW",
                "CUSTOMER_VIEW", "VEHICLE_VIEW", "PRODUCT_VIEW", "STATION_VIEW",
                "SHIFT_VIEW", "SHIFT_CREATE", "SHIFT_UPDATE",
                "INVOICE_VIEW", "INVOICE_CREATE", "INVOICE_UPDATE",
                "PAYMENT_VIEW", "PAYMENT_CREATE", "PAYMENT_UPDATE",
                "FINANCE_VIEW", "FINANCE_CREATE"
        )) {
            if (!rolePermissionRepository.existsByRoleIdAndPermissionCode(cashier.getId(), code)) {
                permissionRepository.findByCode(code).ifPresent(p -> {
                    RolePermission rp = new RolePermission();
                    rp.setRole(cashier);
                    rp.setPermission(p);
                    rolePermissionRepository.save(rp);
                    log.info("Patched CASHIER: added {}", code);
                });
                patched = true;
            }
        }
        // Always clear permission caches on startup to ensure consistency
        var permCache = cacheManager.getCache("permissions");
        if (permCache != null) permCache.clear();
        var roleCache = cacheManager.getCache("rolePermissions");
        if (roleCache != null) roleCache.clear();
        if (patched) {
            log.info("Patched CASHIER permissions and cleared caches");
        }
    }

    /**
     * Ensure the global STMT sequence continues from the max new-system (2025+) statement number.
     * Old-format statements (S26/1, etc.) are left as-is — no renaming.
     */
    private void patchStatementSequence() {
        long maxNewSystem = 0;
        for (var stmt : statementRepository.findAll()) {
            String no = stmt.getStatementNo();
            if (no == null || !no.startsWith("S-")) continue;
            try {
                long num = Long.parseLong(no.substring(2));
                // Only consider statements from 2025+ as new-system
                if (stmt.getStatementDate() != null && stmt.getStatementDate().getYear() >= 2025) {
                    if (num > maxNewSystem) maxNewSystem = num;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (maxNewSystem > 0) {
            var existing = billSequenceRepository.findByTypeAndFyYear(BillType.STMT, 0);
            BillSequence seq = existing.orElseGet(() -> {
                BillSequence newSeq = new BillSequence();
                newSeq.setType(BillType.STMT);
                newSeq.setFyYear(0);
                return newSeq;
            });
            if (seq.getLastNumber() == null || seq.getLastNumber() != maxNewSystem) {
                log.info("Resetting STMT sequence from {} to {}", seq.getLastNumber(), maxNewSystem);
                seq.setLastNumber(maxNewSystem);
                billSequenceRepository.save(seq);
            }
        }
    }

    private void seedRolePermissions() {
        Map<String, Set<String>> defaults = Map.of(
            "CASHIER", Set.of(
                "DASHBOARD_VIEW",
                "CUSTOMER_VIEW", "VEHICLE_VIEW", "PRODUCT_VIEW", "STATION_VIEW",
                "SHIFT_VIEW", "SHIFT_CREATE", "SHIFT_UPDATE",
                "INVOICE_VIEW", "INVOICE_CREATE", "INVOICE_UPDATE",
                "PAYMENT_VIEW", "PAYMENT_CREATE", "PAYMENT_UPDATE",
                "FINANCE_VIEW", "FINANCE_CREATE"
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

    private void backfillStatementQuantity() {
        int updated = entityManager.createNativeQuery(
                "UPDATE statement s SET total_quantity = (" +
                "SELECT COALESCE(SUM(ip.quantity), 0) " +
                "FROM invoice_product ip JOIN invoice_bill ib ON ip.invoice_bill_id = ib.id " +
                "WHERE ib.statement_id = s.id" +
                ") WHERE s.total_quantity IS NULL")
                .executeUpdate();
        if (updated > 0) {
            log.info("Backfilled total_quantity for {} statements", updated);
        }
    }
}
