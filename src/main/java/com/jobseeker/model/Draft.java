package com.jobseeker.model;

public class Draft {
    private String jobId;
    private String jobTitle;
    private String company;
    private String recruiterName;
    private String email;
    private boolean verified;
    private String coverLetter;
    private String emailSubject;
    private String emailBody;
    private String status;   // DRAFT | APPROVED | SENT

    public String getJobId()                  { return jobId; }
    public void   setJobId(String v)          { jobId = v; }

    public String getJobTitle()               { return jobTitle; }
    public void   setJobTitle(String v)       { jobTitle = v; }

    public String getCompany()                { return company; }
    public void   setCompany(String v)        { company = v; }

    public String getRecruiterName()          { return recruiterName; }
    public void   setRecruiterName(String v)  { recruiterName = v; }

    public String getEmail()                  { return email; }
    public void   setEmail(String v)          { email = v; }

    public boolean isVerified()               { return verified; }
    public void    setVerified(boolean v)     { verified = v; }

    public String getCoverLetter()            { return coverLetter; }
    public void   setCoverLetter(String v)    { coverLetter = v; }

    public String getEmailSubject()           { return emailSubject; }
    public void   setEmailSubject(String v)   { emailSubject = v; }

    public String getEmailBody()              { return emailBody; }
    public void   setEmailBody(String v)      { emailBody = v; }

    public String getStatus()                 { return status; }
    public void   setStatus(String v)         { status = v; }
}
