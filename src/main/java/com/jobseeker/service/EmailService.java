package com.jobseeker.service;

import com.jobseeker.model.Draft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(Draft draft) {
        log.info("Step 3/3 — Sending email to {} <{}> | Subject: \"{}\"",
                draft.getRecruiterName(), draft.getEmail(), draft.getEmailSubject());
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(draft.getEmail());
        msg.setSubject(draft.getEmailSubject());
        msg.setText(draft.getEmailBody() + "\n\n---\n" + draft.getCoverLetter());
        mailSender.send(msg);
        log.info("  ✓ Email sent → {}", draft.getEmail());
    }
}
