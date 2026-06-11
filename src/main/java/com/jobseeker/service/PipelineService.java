package com.jobseeker.service;

import com.jobseeker.model.Draft;
import com.jobseeker.model.JobListing;
import com.jobseeker.model.RecruiterContact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final ClaudeJobSearchAgent jobSearchAgent;
    private final RecruiterFinder      recruiterFinder;
    private final DraftGenerator       draftGenerator;
    private final JobService           jobService;

    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    public PipelineService(ClaudeJobSearchAgent jobSearchAgent,
                           RecruiterFinder recruiterFinder,
                           DraftGenerator draftGenerator,
                           JobService jobService) {
        this.jobSearchAgent  = jobSearchAgent;
        this.recruiterFinder = recruiterFinder;
        this.draftGenerator  = draftGenerator;
        this.jobService      = jobService;
    }

    public List<Draft> run(String resumeText, Consumer<Map<String, Object>> onEvent) {
        log.info("=== Pipeline started ===");
        onEvent.accept(msg("status", "Asking DeepSeek to find matching jobs..."));

        log.info("Step 1/3 - Asking DeepSeek to find matching jobs...");
        long t0 = System.currentTimeMillis();

        List<JobListing> jobs = jobSearchAgent.findJobsForResume(resumeText);
        jobService.setJobs(jobs);
        log.info("Step 1/3 - Found {} jobs in {}ms", jobs.size(), System.currentTimeMillis() - t0);
        jobs.forEach(j -> log.info("  . {} at {} ({})", j.title(), j.company(), j.location()));

        List<Map<String, String>> jobSummaries = jobs.stream()
                .map(j -> Map.of("title", j.title(), "company", j.company(), "location", j.location()))
                .collect(Collectors.toList());

        Map<String, Object> jobsFoundEvent = new HashMap<>();
        jobsFoundEvent.put("type", "jobs_found");
        jobsFoundEvent.put("count", jobs.size());
        jobsFoundEvent.put("jobs", jobSummaries);
        onEvent.accept(jobsFoundEvent);

        log.info("Step 2/3 - Finding recruiters and writing drafts for {} jobs in parallel...", jobs.size());
        long t1 = System.currentTimeMillis();

        List<CompletableFuture<Draft>> futures = jobs.stream()
                .map(job -> CompletableFuture.supplyAsync(
                        () -> processJob(resumeText, job, onEvent), executor))
                .collect(Collectors.toList());

        List<Draft> drafts = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        log.info("Step 2/3 - All {} drafts ready in {}ms", drafts.size(), System.currentTimeMillis() - t1);
        log.info("=== Pipeline complete - {} drafts generated ===", drafts.size());
        return drafts;
    }

    private Draft processJob(String resumeText, JobListing job, Consumer<Map<String, Object>> onEvent) {
        log.info("[{}] Finding recruiter...", job.company());
        onEvent.accept(msg("processing", "Finding recruiter for " + job.company() + "..."));

        RecruiterContact contact = recruiterFinder.find(job);
        log.info("[{}] Recruiter: {} <{}>", job.company(), contact.recruiterName(), contact.email());

        log.info("[{}] Writing cover letter and email...", job.company());
        onEvent.accept(msg("processing", "Writing cover letter for " + job.company() + "..."));

        DraftGenerator.GeneratedContent content = draftGenerator.generate(resumeText, job, contact);
        log.info("[{}] Draft ready - subject: \"{}\"", job.company(), content.emailSubject());

        Map<String, Object> draftEvent = new HashMap<>();
        draftEvent.put("type", "draft_ready");
        draftEvent.put("company", job.company());
        draftEvent.put("jobId", job.id());
        onEvent.accept(draftEvent);

        Draft draft = new Draft();
        draft.setJobId(job.id());
        draft.setJobTitle(job.title());
        draft.setCompany(job.company());
        draft.setRecruiterName(contact.recruiterName());
        draft.setEmail(contact.email());
        draft.setVerified(contact.verified());
        draft.setCoverLetter(content.coverLetter());
        draft.setEmailSubject(content.emailSubject());
        draft.setEmailBody(content.emailBody());
        draft.setStatus("DRAFT");
        return draft;
    }

    private static Map<String, Object> msg(String type, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("message", message);
        return m;
    }
}
