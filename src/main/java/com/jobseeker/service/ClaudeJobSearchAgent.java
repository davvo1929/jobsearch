package com.jobseeker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseeker.model.JobListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final RestClient geminiRestClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    public ClaudeJobSearchAgent(RestClient geminiRestClient) {
        this.geminiRestClient = geminiRestClient;
    }

    public List<JobListing> findJobsForResume(String resumeText) {
        log.info("Searching real job listings via Gemini + Google Search...");

        String prompt = """
                Search the internet right now for real, currently open job listings that match \
                this candidate's resume. Find actual job postings from company career pages, \
                LinkedIn, Indeed, or other job boards. For each job also find the recruiter, \
                hiring manager, or HR contact email if listed on the posting.

                Candidate Resume:
                %s

                Find 5 real open positions. Return ONLY a JSON object — no extra text before or after:
                {"jobs": [
                  {
                    "title": "Exact job title from the posting",
                    "company": "Real company name",
                    "location": "City, State or Remote",
                    "description": "Key requirements from the actual posting",
                    "companyDomain": "company.com",
                    "recruiterName": "Name if found, otherwise Recruiting Team",
                    "recruiterEmail": "email if found, otherwise careers@company.com",
                    "jobUrl": "Direct URL to the job posting"
                  }
                ]}
                """.formatted(resumeText);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))
        ));
        body.put("tools", List.of(Map.of("google_search", Map.of())));

        try {
            String raw = geminiRestClient.post()
                    .uri("/models/gemini-2.0-flash:generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String text = MAPPER.readTree(raw)
                    .path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            List<JobListing> jobs = parseJobs(text);
            log.info("Found {} real job listings", jobs.size());
            jobs.forEach(j -> log.info("  . {} at {} — {}", j.title(), j.company(), j.recruiterEmail()));
            return jobs;

        } catch (Exception e) {
            log.error("Gemini job search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<JobListing> parseJobs(String content) {
        try {
            String json = content.replaceAll("(?s)```[a-z]*\\n?", "").replaceAll("```", "").trim();
            int start = json.indexOf('{');
            int end   = json.lastIndexOf('}');
            if (start >= 0 && end > start) json = json.substring(start, end + 1);

            JsonNode arr = MAPPER.readTree(json).path("jobs");
            List<JobListing> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonNode j = arr.get(i);
                String domain  = j.path("companyDomain").asText("");
                String company = j.path("company").asText("");
                if (domain.isBlank()) domain = deriveDomain(company);
                result.add(new JobListing(
                        String.valueOf(i + 1),
                        j.path("title").asText(""),
                        company,
                        j.path("location").asText(""),
                        j.path("description").asText(""),
                        domain,
                        j.path("recruiterName").asText("Recruiting Team"),
                        j.path("recruiterEmail").asText("careers@" + domain)
                ));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse job listings: {}", e.getMessage());
            return List.of();
        }
    }

    private String deriveDomain(String company) {
        return company.toLowerCase().replaceAll("[^a-z0-9]", "") + ".com";
    }
}
