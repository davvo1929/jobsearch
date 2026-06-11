package com.jobseeker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseeker.model.JobListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeJobSearchAgent {

    private static final Logger log = LoggerFactory.getLogger(ClaudeJobSearchAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestClient deepSeek;

    public ClaudeJobSearchAgent(RestClient deepSeekRestClient) {
        this.deepSeek = deepSeekRestClient;
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

        String content = call(prompt, 4000);
        List<JobListing> jobs = parseJobs(content);
        log.info("DeepSeek returned {} job listings", jobs.size());
        return jobs;
    }

    private List<JobListing> parseJobs(String content) {
        try {
            JsonNode root = MAPPER.readTree(content);
            JsonNode jobs = root.path("jobs");
            List<JobListing> result = new ArrayList<>();
            for (int i = 0; i < jobs.size(); i++) {
                JsonNode j = jobs.get(i);
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

    private String call(String prompt, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "deepseek-chat");
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("max_tokens", maxTokens);
        body.put("response_format", Map.of("type", "json_object"));

        log.debug("Sending request to DeepSeek /chat/completions...");
        try {
            String raw = deepSeek.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            String content = MAPPER.readTree(raw).path("choices").get(0).path("message").path("content").asText();
            log.debug("DeepSeek response received ({} chars)", content.length());
            return content;
        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            return "{}";
        }
    }
}
