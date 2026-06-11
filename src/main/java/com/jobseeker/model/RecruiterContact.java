package com.jobseeker.model;

public record RecruiterContact(
        String recruiterName,
        String email,
        boolean verified,
        String source
) {}
