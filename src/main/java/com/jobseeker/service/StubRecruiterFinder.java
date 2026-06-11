package com.jobseeker.service;

import com.jobseeker.model.JobListing;
import com.jobseeker.model.RecruiterContact;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Kept as reference; ClaudeRecruiterFinder is the active implementation.
public class StubRecruiterFinder implements RecruiterFinder {

    // TODO: Replace this entire stub with an Anthropic LLM call (e.g., claude-sonnet-4-6) that
    //       parses the job posting via structured output to extract real recruiter details.
    //       IMPORTANT: Verified email lookup requires a dedicated data provider (e.g., Hunter.io,
    //       Apollo.io, or LinkedIn Recruiter API) — LLMs cannot reliably produce current, accurate
    //       contact emails and must NOT be used as a source of verified addresses.

    private static final Pattern EMAIL =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    // Proper name adjacent to an email address: "Sarah Johnson at <email>"
    private static final Pattern NAME_NEAR_EMAIL =
            Pattern.compile("([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)\\s+(?:at\\s+)?[a-zA-Z0-9._%+\\-]+@");

    // Keyword (case-insensitive) followed by a ProperName: "Contact Sarah Johnson"
    // (?i:...) scopes case-insensitivity to the keyword only, so [A-Z] stays case-sensitive.
    private static final Pattern RECRUITER_MENTION =
            Pattern.compile("(?i:contact|reach out to|recruiter)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)");

    @Override
    public RecruiterContact find(JobListing job) {
        String desc = job.description();

        // 1. Email directly present in the posting — extract it plus any adjacent name.
        //    Try RECRUITER_MENTION first ("Contact Sarah Johnson at ...") so we capture just the
        //    name and not the leading keyword; fall back to proximity regex.
        Matcher em = EMAIL.matcher(desc);
        if (em.find()) {
            String email = em.group();
            String name  = findRecruiterMention(desc);
            if (name == null) name = findNameNearEmail(desc);
            return new RecruiterContact(
                    name != null ? name : "Recruiter",
                    email,
                    false,
                    "extracted-from-posting"
            );
        }

        // 2. Recruiter name mentioned by keyword, no email
        String recruiterName = findRecruiterMention(desc);

        // 3. Resolve domain (explicit > derived from company name)
        String domain = (job.companyDomain() != null && !job.companyDomain().isBlank())
                ? job.companyDomain()
                : deriveDomain(job.company());

        // 4. Build a guessed email
        String email;
        if (recruiterName != null) {
            email = nameToEmail(recruiterName, domain);
        } else {
            recruiterName = "Recruiting Team";
            email = "careers@" + domain;
        }

        return new RecruiterContact(recruiterName, email, false, "guessed");
    }

    private String findNameNearEmail(String desc) {
        Matcher m = NAME_NEAR_EMAIL.matcher(desc);
        return m.find() ? m.group(1) : null;
    }

    private String findRecruiterMention(String desc) {
        Matcher m = RECRUITER_MENTION.matcher(desc);
        return m.find() ? m.group(1) : null;
    }

    private String nameToEmail(String fullName, String domain) {
        String[] parts = fullName.trim().toLowerCase().split("\\s+");
        return parts.length >= 2
                ? parts[0] + "." + parts[parts.length - 1] + "@" + domain
                : parts[0] + "@" + domain;
    }

    private String deriveDomain(String company) {
        return company.toLowerCase().replaceAll("[^a-z0-9]", "") + ".com";
    }
}
