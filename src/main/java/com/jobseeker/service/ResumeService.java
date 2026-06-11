package com.jobseeker.service;

import org.springframework.stereotype.Service;

@Service
public class ResumeService {
    private String resumeText = "";

    public void save(String text) {
        resumeText = text != null ? text : "";
    }

    public String get() {
        return resumeText;
    }
}
