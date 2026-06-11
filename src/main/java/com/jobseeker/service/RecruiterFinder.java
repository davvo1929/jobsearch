package com.jobseeker.service;

import com.jobseeker.model.JobListing;
import com.jobseeker.model.RecruiterContact;

public interface RecruiterFinder {
    RecruiterContact find(JobListing job);
}
