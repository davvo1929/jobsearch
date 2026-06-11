package com.jobseeker.service;

import com.jobseeker.model.JobListing;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class JobService {

    private volatile List<JobListing> jobs = new ArrayList<>();

    public List<JobListing> getAll() {
        return Collections.unmodifiableList(jobs);
    }

    public Optional<JobListing> findById(String id) {
        return jobs.stream().filter(j -> j.id().equals(id)).findFirst();
    }

    public void setJobs(List<JobListing> newJobs) {
        this.jobs = new ArrayList<>(newJobs);
    }
}
