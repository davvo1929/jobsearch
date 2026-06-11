package com.jobseeker.service;

import com.jobseeker.model.JobListing;
import com.jobseeker.model.RecruiterContact;

// Kept as reference; ClaudeDraftGenerator is the active implementation.
public class StubDraftGenerator implements DraftGenerator {

    // TODO: Replace this stub with an Anthropic LLM call, e.g.:
    //
    //   AnthropicClient client = AnthropicOkHttpClient.fromEnv();
    //   MessageCreateParams params = MessageCreateParams.builder()
    //       .model("claude-sonnet-4-6")
    //       .maxTokens(1024)
    //       .addUserMessage(buildPrompt(resumeText, job, contact))
    //       .build();
    //   Message response = client.messages().create(params);
    //   // Parse structured JSON from response.content() into GeneratedContent
    //
    //   The prompt should ask for JSON: { coverLetter, emailSubject, emailBody }
    //   tailored to the candidate's resume and the specific job description.

    @Override
    public GeneratedContent generate(String resumeText, JobListing job, RecruiterContact contact) {
        String recruiterFirst = contact.recruiterName().split("\\s+")[0];
        String candidateName  = extractFirstLine(resumeText);
        String snippet        = extractSnippet(resumeText, 280);

        String coverLetter = buildCoverLetter(candidateName, snippet, job);
        String subject     = "Application: " + job.title() + " at " + job.company();
        String body        = buildEmailBody(recruiterFirst, candidateName, snippet, job);

        return new GeneratedContent(coverLetter, subject, body);
    }

    private String buildCoverLetter(String name, String snippet, JobListing job) {
        return """
                Dear Hiring Manager,

                I am writing to express my strong interest in the %s position at %s (%s).

                A bit about my background: %s

                The opportunity at %s particularly appeals to me because it aligns well with both my technical expertise and my goal of making a meaningful impact. I am confident I can contribute from day one.

                I would welcome the chance to discuss how my experience maps to your team's needs. Thank you for your time and consideration.

                Best regards,
                %s
                """.formatted(job.title(), job.company(), job.location(), snippet, job.company(), name);
    }

    private String buildEmailBody(String recruiterFirst, String name, String snippet, JobListing job) {
        return """
                Hi %s,

                I came across the %s opening at %s and wanted to reach out directly.

                Quick intro: %s

                I think my background is a strong match for what you're looking for. I've put together a cover letter with more detail — happy to share it or jump on a quick call if that's easier.

                Thanks for your time,
                %s
                """.formatted(recruiterFirst, job.title(), job.company(), snippet, name);
    }

    private String extractFirstLine(String text) {
        if (text == null || text.isBlank()) return "Candidate";
        return text.strip().split("\\r?\\n")[0].strip();
    }

    private String extractSnippet(String text, int maxLen) {
        if (text == null || text.isBlank()) return "Experienced professional with a strong background.";
        // Skip the first line (usually the name), grab the next meaningful lines
        String[] lines = text.strip().split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isBlank()) continue;
            if (sb.length() + line.length() > maxLen) break;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(line);
        }
        String result = sb.toString().strip();
        if (result.isEmpty()) {
            String full = text.strip();
            result = full.substring(0, Math.min(maxLen, full.length()));
        }
        return result;
    }
}
