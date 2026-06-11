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
public class ClaudeDraftGenerator implements DraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClaudeDraftGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestClient deepSeek;

    public ClaudeDraftGenerator(RestClient deepSeekRestClient) {
        this.deepSeek = deepSeekRestClient;
    }

    @Override
    public GeneratedContent generate(String resumeText, JobListing job, RecruiterContact contact) {
        String prompt = """
                Generate a tailored cover letter and recruiter outreach email for this job application. \
                Make the cover letter professional and specific to the job requirements. \
                Make the outreach email friendly, concise, and direct.

                Candidate Resume:
                %s

                Job: %s at %s (%s)
                Job Description:
                %s

                Recruiter: %s <%s>

                Respond with ONLY a valid JSON object:
                {"coverLetter": "Full professional cover letter...", "emailSubject": "Subject line", "emailBody": "Full email body..."}
                """.formatted(
                resumeText,
                job.title(), job.company(), job.location(),
                job.description(),
                contact.recruiterName(), contact.email()
        );

        String content = call(prompt, 2000);
        return parseContent(content, job, contact);
    }

    private GeneratedContent parseContent(String content, JobListing job, RecruiterContact contact) {
        try {
            JsonNode root = MAPPER.readTree(content);
            return new GeneratedContent(
                    root.path("coverLetter").asText(""),
                    root.path("emailSubject").asText("Application: " + job.title() + " at " + job.company()),
                    root.path("emailBody").asText("")
            );
        } catch (Exception e) {
            String firstName = contact.recruiterName().split("\\s+")[0];
            return new GeneratedContent(
                    content,
                    "Application: " + job.title() + " at " + job.company(),
                    "Hi " + firstName + ",\n\nI am interested in the " + job.title()
                    + " role at " + job.company() + ".\n\nBest regards"
            );
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
            log.error("DeepSeek draft call failed: {}", e.getMessage());
            return "{}";
        }
    }
}
