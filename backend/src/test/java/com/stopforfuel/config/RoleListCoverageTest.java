package com.stopforfuel.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Role lists in {@code hasRole}/{@code hasAnyRole} are not hierarchical: a rule written as
 * hasAnyRole('OWNER', 'ADMIN') refuses PRIME even though PRIME outranks both. That is how the
 * invoice-move and shift-unfinalize endpoints locked PRIME out after the tier was added. Any
 * rule that admits OWNER must therefore name PRIME too.
 */
class RoleListCoverageTest {

    @Test
    void everyRoleRuleThatAdmitsOwnerAlsoAdmitsPrime() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (var bd : scanner.findCandidateComponents("com.stopforfuel")) {
            Class<?> cls = Class.forName(bd.getBeanClassName());
            List<PreAuthorize> rules = new ArrayList<>();
            PreAuthorize onClass = cls.getAnnotation(PreAuthorize.class);
            if (onClass != null) rules.add(onClass);
            for (Method m : cls.getDeclaredMethods()) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa == null) continue;
                checked++;
                if (admitsOwnerButNotPrime(pa.value())) offenders.add(cls.getSimpleName() + "." + m.getName() + ": " + pa.value());
            }
            if (onClass != null && admitsOwnerButNotPrime(onClass.value())) {
                offenders.add(cls.getSimpleName() + ": " + onClass.value());
            }
        }

        assertTrue(checked > 0, "scanner found no @PreAuthorize methods — scan is broken");
        assertTrue(offenders.isEmpty(), "Role rules admitting OWNER but not PRIME:\n" + String.join("\n", offenders));
    }

    private static boolean admitsOwnerButNotPrime(String expr) {
        return expr.contains("'OWNER'") && !expr.contains("'PRIME'");
    }
}
