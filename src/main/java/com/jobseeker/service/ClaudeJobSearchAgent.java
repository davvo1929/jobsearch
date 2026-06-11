package com.jobseeker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseeker.model.JobListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ClaudeJobSearchAgent {

    private static final Logger log = LoggerFactory.getLogger(ClaudeJobSearchAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient chatClient;

    public ClaudeJobSearchAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public List<JobListing> findJobsForResume(String resumeText) {
        log.info("Calling DeepSeek to find jobs and recruiter contacts (resume: {} chars)...", resumeText.length());

        String prompt = """
                Analyze the following resume and generate 1 realistic, currently in-demand job listing \
                that this candidate should apply for. For each job also identify the most likely person \
                who posted or manages that listing — this could be a recruiter, HR manager, or hiring manager. \
                Use real company names. Generate a plausible contact email based on the company's email format.

                Resume:
                %s

                Respond with ONLY a valid JSON object in this exact format:
                {"jobs": [
                  {
                    "title": "Job Title",
                    "company": "Company Name",
                    "location": "City, State or Remote",
                    "description": "Job description with key requirements...",
                    "companyDomain": "company.com",
                    "recruiterName": "Full Name",
                    "recruiterEmail": "name@company.com"
                  }
                ]}
                """.formatted(resumeText);

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        List<JobListing> jobs = parseJobs(content);
        log.info("Found {} job listings with recruiter contacts", jobs.size());
        return jobs;
    }

    private List<JobListing> parseJobs(String content) {
        try {
            String json = content.replaceAll("(?s)```[a-z]*\\n?", "").replaceAll("```", "").trim();
            JsonNode arr = MAPPER.readTree(json).path("jobs");
            List<JobListing> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonNode j = arr.get(i);
                String domain = j.path("companyDomain").asText("");
                String company = j.path("company").asText("");
                result.add(new JobListing(
                        String.valueOf(i + 1),
                        j.path("title").asText(""),
                        company,
                        j.path("location").asText(""),
                        j.path("description").asText(""),
                        domain.isBlank() ? deriveDomain(company) : domain,
                        j.path("recruiterName").asText("Recruiting Team"),
                        j.path("recruiterEmail").asText("careers@" + (domain.isBlank() ? deriveDomain(company) : domain))
                ));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse jobs response: {}", e.getMessage());
            return List.of();
        }
    }

    private String deriveDomain(String company) {
        return company.toLowerCase().replaceAll("[^a-z0-9]", "") + ".com";
    }
}
