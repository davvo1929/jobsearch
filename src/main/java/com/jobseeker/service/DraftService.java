package com.jobseeker.service;

import com.jobseeker.model.Draft;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DraftService {

    private List<Draft> allDrafts = new ArrayList<>();

    public List<Draft> getAllDrafts()             { return Collections.unmodifiableList(allDrafts); }
    public void setAllDrafts(List<Draft> drafts) { allDrafts = new ArrayList<>(drafts); }
    public void removeByJobId(String jobId)      { allDrafts.removeIf(d -> jobId.equals(d.getJobId())); }
}
