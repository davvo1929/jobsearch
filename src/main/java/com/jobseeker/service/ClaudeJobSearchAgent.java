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
        log.info("Calling DeepSeek to find job matches (resume length: {} chars)...", resumeText.length());

        String prompt = """
                Analyze the following resume and generate 6 realistic, currently in-demand job listings \
                that this candidate should apply for. Use real company names known to hire for these roles. \
                Base the listings on common job requirements for this skill set and experience level.

                Resume:
                %s

                Respond with ONLY a valid JSON object in this exact format:
                {"jobs": [
                  {
                    "title": "Job Title",
                    "company": "Company Name",
                    "location": "City, State or Remote",
                    "description": "Detailed job description with requirements...",
                    "companyDomain": "company.com"
                  }
                ]}
                """.formatted(resumeText);

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        List<JobListing> jobs = parseJobs(content);
        log.info("DeepSeek returned {} job listings", jobs.size());
        return jobs;
    }

    private List<JobListing> parseJobs(String content) {
        try {
            String json = content.replaceAll("(?s)```[a-z]*\\n?", "").replaceAll("```", "").trim();
            JsonNode root = MAPPER.readTree(json);
            JsonNode arr = root.path("jobs");
            List<JobListing> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonNode j = arr.get(i);
                result.add(new JobListing(
                        String.valueOf(i + 1),
                        j.path("title").asText(""),
                        j.path("company").asText(""),
                        j.path("location").asText(""),
                        j.path("description").asText(""),
                        j.has("companyDomain") ? j.path("companyDomain").asText() : null
                ));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse job listings from DeepSeek response: {}", e.getMessage());
            return List.of();
        }
    }
}
