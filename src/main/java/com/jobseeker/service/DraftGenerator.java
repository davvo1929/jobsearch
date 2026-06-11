package com.jobseeker.service;

import com.jobseeker.model.JobListing;
import com.jobseeker.model.RecruiterContact;

public interface DraftGenerator {

    record GeneratedContent(String coverLetter, String emailSubject, String emailBody) {}

    GeneratedContent generate(String resumeText, JobListing job, RecruiterContact contact);
}
