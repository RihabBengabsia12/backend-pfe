package tn.rihab.projectservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.projectservice.model.entity.Delegation;
import tn.rihab.projectservice.repository.DelegationRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelegationService {

    private final DelegationRepository delegationRepository;

    @PostConstruct
    public void initDelegations() {
        if (delegationRepository.count() == 0) {
            log.info("Initialisation des délégations par défaut...");
            delegationRepository.save(Delegation.builder().roleName("DO").displayName("Direction de l'Offre").email("do@st2i.tn").threshold(null).build());
            delegationRepository.save(Delegation.builder().roleName("DDA").displayName("Direction du Développement des Affaires").email("dda@st2i.tn").threshold(500000.0).build());
            delegationRepository.save(Delegation.builder().roleName("DGA").displayName("Direction Générale Adjointe").email("dga@st2i.tn").threshold(2000000.0).build());
            delegationRepository.save(Delegation.builder().roleName("PDG").displayName("Président Directeur Général").email("pdg@st2i.tn").threshold(10000000.0).build());
        }
    }

    public List<Delegation> getAllDelegations() {
        return delegationRepository.findAll();
    }

    @Transactional
    public Delegation updateDelegation(UUID id, Delegation updatedData) {
        Delegation existing = delegationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Délégation non trouvée avec l'id: " + id));
        
        if (updatedData.getEmail() != null && !updatedData.getEmail().isEmpty()) {
            existing.setEmail(updatedData.getEmail());
        }
        if (updatedData.getThreshold() != null) {
            existing.setThreshold(updatedData.getThreshold());
        }
        return delegationRepository.save(existing);
    }
}
