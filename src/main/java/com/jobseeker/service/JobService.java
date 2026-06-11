package com.jobseeker.service;

import com.jobseeker.model.JobListing;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class JobService {

    private volatile List<JobListing> jobs = new ArrayList<>(List.of(
            new JobListing(
                    "1",
                    "Senior Software Engineer",
                    "Acme Corp",
                    "San Francisco, CA",
                    "We are hiring a Senior Software Engineer to join our platform engineering team. "
                    + "You will architect scalable distributed systems, mentor junior engineers, and collaborate "
                    + "closely with product leadership. Requires 5+ years of experience in Java or Go.",
                    "acmecorp.com"
            ),
            new JobListing(
                    "2",
                    "Product Manager",
                    "TechNova",
                    "New York, NY",
                    "TechNova is looking for a seasoned Product Manager to own our flagship SaaS platform. "
                    + "Define the product roadmap, run discovery with customers, and ship features fast. "
                    + "Contact Sarah Johnson at sarah.johnson@technova.io — she leads our talent team.",
                    "technova.io"
            ),
            new JobListing(
                    "3",
                    "Data Scientist",
                    "Bright Analytics",
                    "Remote",
                    "Join Bright Analytics as a Data Scientist. Build ML models, design A/B experiments, "
                    + "and turn raw data into client insights. "
                    + "Reach out to our recruiter Marcus Chen to learn more about the team. "
                    + "Strong Python, SQL, and statistical modelling skills required.",
                    "brightanalytics.com"
            ),
            new JobListing(
                    "4",
                    "Frontend Developer",
                    "Startup Labs",
                    "Austin, TX",
                    "Startup Labs is a fast-growing early-stage company seeking a Frontend Developer "
                    + "who loves crafting pixel-perfect, accessible UIs. We move fast and ship often. "
                    + "Experience with modern JavaScript and a strong eye for design preferred. Remote-friendly.",
                    null
            )
    ));

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
