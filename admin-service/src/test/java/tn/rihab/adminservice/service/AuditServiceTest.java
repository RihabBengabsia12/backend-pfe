package tn.rihab.adminservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rihab.adminservice.entity.DataEvent;
import tn.rihab.adminservice.repository.DataEventRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AuditServiceTest {

    @Mock
    private DataEventRepository dataEventRepository;

    @InjectMocks
    private AuditService auditService;

    // --- TEST NON-FONCTIONNEL : TNF-06 - Traçabilité / Audit métier ---

    @Test
    void TNF_06_01_Creation_Evenement_Audit_DataEvent() {
        // Préparation des données de l'audit
        UUID actorId = UUID.randomUUID();
        String entity = "AppUser";
        String action = "UPDATE_ROLE";
        String oldVal = "ROLE_GUEST";
        String newVal = "ROLE_ANALYST";

        // Exécution de la méthode d'audit
        auditService.saveAudit(actorId, entity, action, oldVal, newVal);

        // VÉRIFICATION : On capture l'objet DataEvent sauvegardé par le repository
        ArgumentCaptor<DataEvent> eventCaptor = ArgumentCaptor.forClass(DataEvent.class);
        verify(dataEventRepository, times(1)).saveAndFlush(eventCaptor.capture());

        DataEvent savedEvent = eventCaptor.getValue();

        // On vérifie que toutes les données sont correctement tracées
        assertNotNull(savedEvent);
        assertEquals(actorId, savedEvent.getActorUserId());
        assertEquals(entity, savedEvent.getEntityName());
        assertEquals(action, savedEvent.getAction());
        assertEquals(oldVal, savedEvent.getOldData());
        assertEquals(newVal, savedEvent.getNewData());
    }
}
