package com.jobseeker.service;

import com.jobseeker.model.Draft;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class DraftService {

    private Draft current;
    private List<Draft> allDrafts = new ArrayList<>();

    public List<Draft> getAllDrafts()              { return Collections.unmodifiableList(allDrafts); }
    public void setAllDrafts(List<Draft> drafts)  { allDrafts = new ArrayList<>(drafts); }
    public void removeByJobId(String jobId)       { allDrafts.removeIf(d -> jobId.equals(d.getJobId())); }

    public Draft getCurrent()              { return current; }
    public void   setCurrent(Draft draft)  { current = draft; }

    public Draft update(Map<String, String> fields) {
        if (current == null) return null;
        fields.forEach((key, value) -> {
            switch (key) {
                case "recruiterName" -> current.setRecruiterName(value);
                case "email"         -> current.setEmail(value);
                case "coverLetter"   -> current.setCoverLetter(value);
                case "emailSubject"  -> current.setEmailSubject(value);
                case "emailBody"     -> current.setEmailBody(value);
            }
        });
        return current;
    }

    public Draft approve() {
        if (current != null) current.setStatus("APPROVED");
        return current;
    }

    public Draft markSent() {
        if (current != null) current.setStatus("SENT");
        return current;
    }
}
