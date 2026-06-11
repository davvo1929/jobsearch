package com.jobseeker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseeker.model.Draft;
import com.jobseeker.service.DraftService;
import com.jobseeker.service.EmailService;
import com.jobseeker.service.PipelineService;
import com.jobseeker.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Tag(name = "JobSeeker", description = "Resume upload and autonomous outreach pipeline")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    private final ResumeService   resumeService;
    private final DraftService    draftService;
    private final PipelineService pipelineService;
    private final EmailService    emailService;

    public ApiController(ResumeService resumeService, DraftService draftService,
                         PipelineService pipelineService, EmailService emailService) {
        this.resumeService   = resumeService;
        this.draftService    = draftService;
        this.pipelineService = pipelineService;
        this.emailService    = emailService;
    }

    @Operation(summary = "Save resume")
    @PostMapping("/resume")
    public ResponseEntity<Map<String, String>> saveResume(@RequestBody Map<String, String> body) {
        resumeService.save(body.getOrDefault("text", ""));
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    @Operation(summary = "Pipeline SSE stream",
               description = "Runs the full pipeline and streams real-time progress events.")
    @GetMapping(value = "/pipeline/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPipeline() {
        SseEmitter emitter = new SseEmitter(180_000L);

        streamExecutor.submit(() -> {
            try {
                String resume = resumeService.get();
                if (resume == null || resume.isBlank()) {
                    sseEvent(emitter, Map.of("type", "error", "message", "No resume saved."));
                    emitter.complete();
                    return;
                }

                Consumer<Map<String, Object>> onEvent = event -> sseEvent(emitter, event);

                List<Draft> drafts = pipelineService.run(resume, onEvent);
                draftService.setAllDrafts(drafts);

                int sent = 0;
                for (Draft draft : drafts) {
                    Map<String, Object> sendingEvent = Map.of(
                            "type", "sending", "company", draft.getCompany(), "email", draft.getEmail());
                    onEvent.accept(sendingEvent);

                    try {
                        emailService.send(draft);
                        draft.setStatus("SENT");
                        sent++;
                        onEvent.accept(Map.of("type", "email_sent", "company", draft.getCompany()));
                    } catch (Exception e) {
                        draft.setStatus("FAILED: " + e.getMessage());
                        log.error("FAILED to send to {} - {}", draft.getEmail(), e.getMessage());
                        onEvent.accept(Map.of("type", "email_failed",
                                "company", draft.getCompany(), "error", e.getMessage()));
                    }
                }

                log.info("=== Email phase done: {}/{} sent ===", sent, drafts.size());
                onEvent.accept(Map.of("type", "done", "sent", sent, "total", drafts.size()));
                emitter.complete();

            } catch (Exception e) {
                log.error("Pipeline stream error: {}", e.getMessage());
                sseEvent(emitter, Map.of("type", "error", "message", e.getMessage()));
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Operation(summary = "Remove a draft before sending")
    @DeleteMapping("/pipeline/drafts/{jobId}")
    public ResponseEntity<Void> removeDraft(@PathVariable String jobId) {
        draftService.removeByJobId(jobId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Send test email", description = "Verifies Gmail SMTP is working.")
    @PostMapping("/test/email")
    public ResponseEntity<?> testEmail() {
        try {
            Draft test = new Draft();
            test.setRecruiterName("Test");
            test.setEmail("dawit.tilahun.tech@gmail.com");
            test.setEmailSubject("JobSeeker — SMTP test");
            test.setEmailBody("This is a test email from your JobSeeker app.\n\nIf you received this, Gmail SMTP is working correctly.");
            test.setCoverLetter("");
            emailService.send(test);
            return ResponseEntity.ok(Map.of("status", "sent", "to", "dawit.tilahun.tech@gmail.com"));
        } catch (Exception e) {
            log.error("Test email FAILED — {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private void sseEvent(SseEmitter emitter, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().data(MAPPER.writeValueAsString(data)));
        } catch (Exception ignored) {}
    }
}
