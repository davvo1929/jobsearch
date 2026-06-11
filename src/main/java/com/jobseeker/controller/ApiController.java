package com.jobseeker.controller;

import com.jobseeker.model.Draft;
import com.jobseeker.model.JobListing;
import com.jobseeker.model.RecruiterContact;
import com.jobseeker.service.ClaudeJobSearchAgent;
import com.jobseeker.service.DraftGenerator;
import com.jobseeker.service.DraftService;
import com.jobseeker.service.EmailService;
import com.jobseeker.service.JobService;
import com.jobseeker.service.PipelineService;
import com.jobseeker.service.RecruiterFinder;
import com.jobseeker.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag(name = "JobSeeker", description = "Resume, job listing, and outreach draft management")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
@SuppressWarnings("unused")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    private final ResumeService        resumeService;
    private final JobService           jobService;
    private final DraftService         draftService;
    private final RecruiterFinder      recruiterFinder;
    private final DraftGenerator       draftGenerator;
    private final ClaudeJobSearchAgent jobSearchAgent;
    private final PipelineService      pipelineService;
    private final EmailService         emailService;

    public ApiController(ResumeService resumeService, JobService jobService,
                         DraftService draftService, RecruiterFinder recruiterFinder,
                         DraftGenerator draftGenerator, ClaudeJobSearchAgent jobSearchAgent,
                         PipelineService pipelineService, EmailService emailService) {
        this.resumeService   = resumeService;
        this.jobService      = jobService;
        this.draftService    = draftService;
        this.recruiterFinder = recruiterFinder;
        this.draftGenerator  = draftGenerator;
        this.jobSearchAgent  = jobSearchAgent;
        this.pipelineService = pipelineService;
        this.emailService    = emailService;
    }

    @Operation(summary = "Save resume", description = "Stores the plain-text resume used when generating cover letters and email drafts.")
    @ApiResponse(responseCode = "200", description = "Resume saved")
    @PostMapping("/resume")
    public ResponseEntity<Map<String, String>> saveResume(@RequestBody Map<String, String> body) {
        resumeService.save(body.getOrDefault("text", ""));
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    @Operation(summary = "List job listings", description = "Returns all job listings currently loaded in the system.")
    @ApiResponse(responseCode = "200", description = "List of job listings")
    @GetMapping("/jobs")
    public List<JobListing> getJobs() {
        return jobService.getAll();
    }

    @Operation(summary = "AI job scan", description = "Analyzes the saved resume and uses Claude AI with web search to find real, currently open job positions that match the candidate's profile.")
    @ApiResponse(responseCode = "200", description = "Job listings found by AI agent")
    @ApiResponse(responseCode = "400", description = "No resume saved")
    @PostMapping("/agent/scan")
    public ResponseEntity<?> agentScan() {
        String resume = resumeService.get();
        if (resume == null || resume.isBlank()) {
            return ResponseEntity.badRequest().body("No resume saved. Please save your resume first.");
        }
        List<JobListing> jobs = jobSearchAgent.findJobsForResume(resume);
        jobService.setJobs(jobs);
        return ResponseEntity.ok(jobs);
    }

    @Operation(summary = "Generate draft", description = "Finds the recruiter for a job listing, then uses AI to generate a cover letter, email subject, and email body tailored to that job and your resume.")
    @ApiResponse(responseCode = "200", description = "Draft created and set as current")
    @ApiResponse(responseCode = "404", description = "Job listing not found")
    @PostMapping("/jobs/{id}/draft")
    public ResponseEntity<?> generateDraft(
            @Parameter(description = "ID of the job listing to generate a draft for") @PathVariable String id) {
        Optional<JobListing> jobOpt = jobService.findById(id);
        if (jobOpt.isEmpty()) return ResponseEntity.notFound().build();

        JobListing      job     = jobOpt.get();
        String          resume  = resumeService.get();
        RecruiterContact contact = recruiterFinder.find(job);
        DraftGenerator.GeneratedContent content = draftGenerator.generate(resume, job, contact);

        Draft draft = new Draft();
        draft.setJobId(id);
        draft.setRecruiterName(contact.recruiterName());
        draft.setEmail(contact.email());
        draft.setVerified(contact.verified());
        draft.setCoverLetter(content.coverLetter());
        draft.setEmailSubject(content.emailSubject());
        draft.setEmailBody(content.emailBody());
        draft.setStatus("DRAFT");

        draftService.setCurrent(draft);
        return ResponseEntity.ok(draft);
    }

    @Operation(summary = "Update draft", description = "Edits fields on the current active draft. Accepted keys: recruiterName, email, coverLetter, emailSubject, emailBody.")
    @ApiResponse(responseCode = "200", description = "Draft updated")
    @ApiResponse(responseCode = "400", description = "No active draft")
    @PutMapping("/draft")
    public ResponseEntity<?> updateDraft(@RequestBody Map<String, String> body) {
        Draft updated = draftService.update(body);
        if (updated == null) return ResponseEntity.badRequest().body("No active draft");
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Approve draft", description = "Marks the current draft as APPROVED, confirming it is ready to send.")
    @ApiResponse(responseCode = "200", description = "Draft approved")
    @ApiResponse(responseCode = "400", description = "No active draft")
    @PostMapping("/draft/approve")
    public ResponseEntity<?> approveDraft() {
        Draft approved = draftService.approve();
        if (approved == null) return ResponseEntity.badRequest().body("No active draft");
        return ResponseEntity.ok(approved);
    }

    @Operation(summary = "Send draft", description = "Outputs the email to the console and marks the draft as SENT.")
    @ApiResponse(responseCode = "200", description = "Draft sent")
    @ApiResponse(responseCode = "400", description = "No active draft")
    @PostMapping("/draft/send")
    public ResponseEntity<?> sendDraft() {
        Draft draft = draftService.getCurrent();
        if (draft == null) return ResponseEntity.badRequest().body("No active draft");

        System.out.println("\n========== SENDING EMAIL ==========");
        System.out.println("To:      " + draft.getRecruiterName() + " <" + draft.getEmail() + ">");
        System.out.println("Subject: " + draft.getEmailSubject());
        System.out.println("------------------------------------");
        System.out.println(draft.getEmailBody());
        System.out.println("====================================\n");

        return ResponseEntity.ok(draftService.markSent());
    }

    // ── Email test ───────────────────────────────────────────────────

    @Operation(summary = "Send test email", description = "Sends a plain test email to verify SMTP is working.")
    @PostMapping("/test/email")
    public ResponseEntity<?> testEmail() {
        try {
            log.info("Sending test email to dawit.tilahun.tech@gmail.com...");
            com.jobseeker.model.Draft test = new com.jobseeker.model.Draft();
            test.setRecruiterName("Test");
            test.setEmail("dawit.tilahun.tech@gmail.com");
            test.setEmailSubject("JobSeeker — SMTP test");
            test.setEmailBody("This is a test email from your JobSeeker app.\n\nIf you received this, Gmail SMTP is working correctly.");
            test.setCoverLetter("");
            emailService.send(test);
            log.info("Test email sent successfully.");
            return ResponseEntity.ok(Map.of("status", "sent", "to", "dawit.tilahun.tech@gmail.com"));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            log.error("Test email FAILED — {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Full pipeline ────────────────────────────────────────────────

    @Operation(summary = "Run full pipeline", description = "Finds jobs, writes cover letters, and sends all emails automatically in one shot.")
    @ApiResponse(responseCode = "200", description = "Emails sent")
    @ApiResponse(responseCode = "400", description = "No resume saved")
    @PostMapping("/pipeline/run")
    public ResponseEntity<?> runPipeline() {
        String resume = resumeService.get();
        if (resume == null || resume.isBlank()) {
            return ResponseEntity.badRequest().body("No resume saved.");
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
                log.error("FAILED to send email to {} <{}> — {}: {}",
                        draft.getRecruiterName(), draft.getEmail(),
                        e.getClass().getSimpleName(), e.getMessage());
                draft.setStatus("FAILED: " + e.getMessage());
            }
        }
        log.info("=== Email phase done: {}/{} sent ===", sent, drafts.size());
        return ResponseEntity.ok(Map.of("sent", sent, "total", drafts.size(), "drafts", drafts));
    }

    @Operation(summary = "Pipeline SSE stream", description = "Runs the full pipeline and streams real-time progress events.")
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
                    Map<String, Object> sendingEvent = new java.util.HashMap<>();
                    sendingEvent.put("type", "sending");
                    sendingEvent.put("company", draft.getCompany());
                    sendingEvent.put("email", draft.getEmail());
                    onEvent.accept(sendingEvent);

                    try {
                        emailService.send(draft);
                        draft.setStatus("SENT");
                        sent++;
                        Map<String, Object> sentEvent = new java.util.HashMap<>();
                        sentEvent.put("type", "email_sent");
                        sentEvent.put("company", draft.getCompany());
                        onEvent.accept(sentEvent);
                    } catch (Exception e) {
                        draft.setStatus("FAILED: " + e.getMessage());
                        log.error("FAILED to send to {} - {}", draft.getEmail(), e.getMessage());
                        Map<String, Object> failEvent = new java.util.HashMap<>();
                        failEvent.put("type", "email_failed");
                        failEvent.put("company", draft.getCompany());
                        failEvent.put("error", e.getMessage());
                        onEvent.accept(failEvent);
                    }
                }

                log.info("=== Email phase done: {}/{} sent ===", sent, drafts.size());
                Map<String, Object> doneEvent = new java.util.HashMap<>();
                doneEvent.put("type", "done");
                doneEvent.put("sent", sent);
                doneEvent.put("total", drafts.size());
                onEvent.accept(doneEvent);
                emitter.complete();

            } catch (Exception e) {
                log.error("Pipeline stream error: {}", e.getMessage());
                sseEvent(emitter, Map.of("type", "error", "message", e.getMessage()));
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sseEvent(SseEmitter emitter, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().data(MAPPER.writeValueAsString(data)));
        } catch (Exception ignored) {
            // client disconnected
        }
    }

    @Operation(summary = "Remove pipeline draft", description = "Removes a draft from the review list before sending.")
    @DeleteMapping("/pipeline/drafts/{jobId}")
    public ResponseEntity<?> removeDraft(@PathVariable String jobId) {
        draftService.removeByJobId(jobId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Send all pipeline drafts", description = "Sends emails for all remaining drafts via Gmail SMTP.")
    @ApiResponse(responseCode = "200", description = "Emails sent")
    @PostMapping("/pipeline/send-all")
    public ResponseEntity<?> sendAll() {
        List<Draft> drafts = draftService.getAllDrafts();
        int sent = 0;
        for (Draft draft : drafts) {
            try {
                emailService.send(draft);
                draft.setStatus("SENT");
                sent++;
            } catch (Exception e) {
                draft.setStatus("FAILED: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("sent", sent, "total", drafts.size(), "drafts", drafts));
    }
}
