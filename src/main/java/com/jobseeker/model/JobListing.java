package com.jobseeker.model;

public record JobListing(
        String id,
        String title,
        String company,
        String location,
        String description,
        String companyDomain   // null when unknown
) {}
