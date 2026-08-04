package tn.rihab.adminservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.adminservice.repository.AppUserRepository;
import tn.rihab.adminservice.repository.RoleRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private final AppUserRepository userRepo;
    private final RoleRepository    roleRepo;

    public Map<String, Object> getStats() {
        long total    = userRepo.count();
        long actifs   = userRepo.findAll().stream()
                .filter(u -> "ACTIVE".equals(u.getStatus())).count();
        long desactives = total - actifs;

        // Répartition par rôle
        Map<String, Long> parRole = userRepo.findAll().stream()
                .collect(Collectors.groupingBy(
                        u -> u.getRoleCode(), Collectors.counting()));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUtilisateurs", total);
        stats.put("utilisateursActifs", actifs);
        stats.put("utilisateursDesactives", desactives);
        stats.put("parRole", parRole);
        stats.put("totalRoles", roleRepo.count());

        return stats;
    }
}