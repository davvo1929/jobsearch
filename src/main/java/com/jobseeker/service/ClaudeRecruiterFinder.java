package com.jobseeker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseeker.model.JobListing;
import com.jobseeker.model.RecruiterContact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ClaudeRecruiterFinder implements RecruiterFinder {

    private static final Logger log = LoggerFactory.getLogger(ClaudeRecruiterFinder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient chatClient;

    public ClaudeRecruiterFinder(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public RecruiterContact find(JobListing job) {
        String prompt = """
                Suggest a realistic recruiter contact for the following job position. \
                Based on typical naming conventions and company email formats, generate a plausible \
                recruiter name and email address for this company. Mark verified as false.

                Job: %s at %s (%s)
                Company domain: %s

                Respond with ONLY a valid JSON object:
                {"recruiterName": "Full Name", "email": "email@company.com", "verified": false, "source": "estimated"}
                """.formatted(
                job.title(), job.company(), job.location(),
                job.companyDomain() != null ? job.companyDomain() : deriveDomain(job)
        );

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return parseContact(content, job);
    }

    private RecruiterContact parseContact(String content, JobListing job) {
        try {
            String json = content.replaceAll("(?s)```[a-z]*\\n?", "").replaceAll("```", "").trim();
            JsonNode root = MAPPER.readTree(json);
            return new RecruiterContact(
                    root.path("recruiterName").asText("Recruiting Team"),
                    root.path("email").asText("careers@" + deriveDomain(job)),
                    root.path("verified").asBoolean(false),
                    root.path("source").asText("estimated")
            );
        } catch (Exception e) {
            log.error("Failed to parse recruiter response: {}", e.getMessage());
            return new RecruiterContact("Recruiting Team", "careers@" + deriveDomain(job), false, "estimated");
        }
    }

    private String deriveDomain(JobListing job) {
        if (job.companyDomain() != null && !job.companyDomain().isBlank()) return job.companyDomain();
        return job.company().toLowerCase().replaceAll("[^a-z0-9]", "") + ".com";
    }
}
