package com.jobseeker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseeker.model.JobListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ClaudeDraftGenerator implements DraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClaudeDraftGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient chatClient;

    public ClaudeDraftGenerator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public GeneratedContent generate(String resumeText, JobListing job) {
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
                job.recruiterName(), job.recruiterEmail()
        );

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return parseContent(content, job);
    }

    private GeneratedContent parseContent(String content, JobListing job) {
        try {
            String json = content.replaceAll("(?s)```[a-z]*\\n?", "").replaceAll("```", "").trim();
            JsonNode root = MAPPER.readTree(json);
            return new GeneratedContent(
                    root.path("coverLetter").asText(""),
                    root.path("emailSubject").asText("Application: " + job.title() + " at " + job.company()),
                    root.path("emailBody").asText("")
            );
        } catch (Exception e) {
            log.error("Failed to parse draft response: {}", e.getMessage());
            String firstName = job.recruiterName().split("\\s+")[0];
            return new GeneratedContent(
                    content,
                    "Application: " + job.title() + " at " + job.company(),
                    "Hi " + firstName + ",\n\nI am interested in the " + job.title()
                    + " role at " + job.company() + ".\n\nBest regards"
            );
        }
    }
}
