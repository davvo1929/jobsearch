package com.jobseeker.controller;

import com.jobseeker.model.Draft;
import com.jobseeker.service.DraftService;
import com.jobseeker.service.EmailService;
import com.jobseeker.service.PipelineService;
import com.jobseeker.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "JobSeeker", description = "Resume upload and autonomous outreach pipeline")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

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

    @Operation(summary = "Run pipeline", description = "Finds jobs, writes cover letters, and sends emails. Returns when complete.")
    @PostMapping("/pipeline/run")
    public ResponseEntity<?> runPipeline() {
        String resume = resumeService.get();
        if (resume == null || resume.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No resume saved."));
        }

        List<Draft> drafts = pipelineService.run(resume, e -> {});
        draftService.setAllDrafts(drafts);

        int sent = 0;
        for (Draft draft : drafts) {
            try {
                emailService.send(draft);
                draft.setStatus("SENT");
                sent++;
            } catch (Exception e) {
                draft.setStatus("FAILED: " + e.getMessage());
                log.error("FAILED to send to {} - {}", draft.getEmail(), e.getMessage());
            }
        }

        log.info("=== Pipeline done: {}/{} sent ===", sent, drafts.size());
        return ResponseEntity.ok(Map.of("sent", sent, "total", drafts.size()));
    }

    @Operation(summary = "Remove a draft")
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
}
