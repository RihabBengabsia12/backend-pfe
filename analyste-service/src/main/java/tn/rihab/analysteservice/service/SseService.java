package tn.rihab.analysteservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SseService {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(() -> {
            emitters.forEach((id, emitter) -> {
                try {
                    emitter.send(SseEmitter.event().name("PING").data("keep-alive"));
                } catch (Exception e) {
                    emitters.remove(id);
                }
            });
        }, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
    }

    public SseEmitter subscribe(UUID dossierId) {
        // Keep connection open for 30 minutes
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(dossierId, emitter);

        emitter.onCompletion(() -> emitters.remove(dossierId));
        emitter.onTimeout(() -> emitters.remove(dossierId));
        emitter.onError((e) -> emitters.remove(dossierId));

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to SSE for dossier " + dossierId));
        } catch (IOException e) {
            emitters.remove(dossierId);
        }

        return emitter;
    }

    public void sendEvent(UUID dossierId, String eventName, Object payload) {
        SseEmitter emitter = emitters.get(dossierId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                emitters.remove(dossierId);
                log.warn("Failed to send SSE event {} to dossier {}", eventName, dossierId);
            }
        }
    }
}
