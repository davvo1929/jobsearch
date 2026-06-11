package com.jobseeker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseeker.model.JobListing;
import com.jobseeker.model.RecruiterContact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeRecruiterFinder implements RecruiterFinder {

    private static final Logger log = LoggerFactory.getLogger(ClaudeRecruiterFinder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestClient deepSeek;

    public ClaudeRecruiterFinder(RestClient deepSeekRestClient) {
        this.deepSeek = deepSeekRestClient;
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

        String content = call(prompt, 256);
        return parseContact(content, job);
    }

    private RecruiterContact parseContact(String content, JobListing job) {
        try {
            JsonNode root = MAPPER.readTree(content);
            return new RecruiterContact(
                    root.path("recruiterName").asText("Recruiting Team"),
                    root.path("email").asText("careers@" + deriveDomain(job)),
                    root.path("verified").asBoolean(false),
                    root.path("source").asText("estimated")
            );
        } catch (Exception e) {
            return new RecruiterContact("Recruiting Team", "careers@" + deriveDomain(job), false, "estimated");
        }
    }

    private String call(String prompt, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "deepseek-chat");
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("max_tokens", maxTokens);
        body.put("response_format", Map.of("type", "json_object"));

        try {
            String raw = deepSeek.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return MAPPER.readTree(raw).path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("DeepSeek recruiter call failed: {}", e.getMessage());
            return "{}";
        }
    }

    private String deriveDomain(JobListing job) {
        if (job.companyDomain() != null && !job.companyDomain().isBlank()) return job.companyDomain();
        return job.company().toLowerCase().replaceAll("[^a-z0-9]", "") + ".com";
    }
}
