package com.jobseeker.service;

import com.jobseeker.model.Draft;
import com.jobseeker.model.JobListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final DraftGenerator       draftGenerator;
    private final JobService           jobService;

    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    public PipelineService(ClaudeJobSearchAgent jobSearchAgent,
                           DraftGenerator draftGenerator,
                           JobService jobService) {
        this.jobSearchAgent  = jobSearchAgent;
        this.draftGenerator  = draftGenerator;
        this.jobService      = jobService;
    }

    public List<Draft> run(String resumeText, Consumer<Map<String, Object>> onEvent) {
        log.info("=== Pipeline started ===");
        onEvent.accept(Map.of("type", "status", "message", "Finding matching jobs and recruiters…"));

        List<JobListing> jobs = jobSearchAgent.findJobsForResume(resumeText);
        jobService.setJobs(jobs);
        log.info("Found {} jobs", jobs.size());
        jobs.forEach(j -> log.info("  . {} at {} — recruiter: {} <{}>",
                j.title(), j.company(), j.recruiterName(), j.recruiterEmail()));

        onEvent.accept(Map.of("type", "jobs_found", "count", jobs.size()));

        log.info("Writing drafts for {} jobs in parallel…", jobs.size());
        List<CompletableFuture<Draft>> futures = jobs.stream()
                .map(job -> CompletableFuture.supplyAsync(() -> buildDraft(resumeText, job, onEvent), executor))
                .collect(Collectors.toList());

        List<Draft> drafts = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        log.info("=== Pipeline complete — {} drafts ready ===", drafts.size());
        return drafts;
    }

    private Draft buildDraft(String resumeText, JobListing job, Consumer<Map<String, Object>> onEvent) {
        log.info("[{}] Writing cover letter for recruiter {} <{}>…", job.company(), job.recruiterName(), job.recruiterEmail());
        onEvent.accept(Map.of("type", "processing", "message", "Writing cover letter for " + job.company() + "…"));

        DraftGenerator.GeneratedContent content = draftGenerator.generate(resumeText, job);
        log.info("[{}] Draft ready — subject: \"{}\"", job.company(), content.emailSubject());

        Draft draft = new Draft();
        draft.setJobId(job.id());
        draft.setJobTitle(job.title());
        draft.setCompany(job.company());
        draft.setRecruiterName(job.recruiterName());
        draft.setEmail(job.recruiterEmail());
        draft.setVerified(false);
        draft.setCoverLetter(content.coverLetter());
        draft.setEmailSubject(content.emailSubject());
        draft.setEmailBody(content.emailBody());
        draft.setStatus("DRAFT");
        return draft;
    }
}
