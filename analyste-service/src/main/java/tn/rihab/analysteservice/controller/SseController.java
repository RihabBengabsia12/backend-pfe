package tn.rihab.analysteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.rihab.analysteservice.service.SseService;

import java.util.UUID;

@RestController
@RequestMapping("/api/analyses/stream")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SseController {

    private final SseService sseService;

    @GetMapping(value = "/{dossierId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable UUID dossierId) {
        return sseService.subscribe(dossierId);
    }
}
