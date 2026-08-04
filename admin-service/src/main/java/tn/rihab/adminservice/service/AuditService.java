package tn.rihab.adminservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.adminservice.entity.DataEvent;
import tn.rihab.adminservice.repository.DataEventRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuditService {

    private final DataEventRepository eventRepo;

    // --- NOUVELLE MÉTHODE DE SAUVEGARDE ---
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAudit(UUID actorId, String entity, String action, String oldVal, String newVal) {
        try {
            DataEvent event = DataEvent.builder()
                    .actorUserId(actorId)
                    .entityName(entity)
                    .entityId(actorId.toString()) // <--- AJOUTE CETTE LIGNE ICI
                    .action(action)
                    .oldData(oldVal)
                    .newData(newVal)
                    .occurredAt(java.time.OffsetDateTime.now())
                    .createdAt(java.time.OffsetDateTime.now()) // Ajoute aussi le createdAt si nécessaire
                    .build();

            eventRepo.saveAndFlush(event);
            System.out.println("✅ Audit inséré pour l'entité : " + actorId);
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'enregistrement de l'audit : " + e.getMessage());
        }
    }


    public Page<DataEvent> getDataEvents(UUID userId, String entity,
                                         String action, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("occurredAt").descending());

        if (userId != null)
            return eventRepo.findByActorUserId(userId, pageable);
        if (entity != null)
            return eventRepo.findByEntityName(entity, pageable);
        if (action != null)
            return eventRepo.findByAction(action, pageable);

        return eventRepo.findAll(pageable);
    }
}