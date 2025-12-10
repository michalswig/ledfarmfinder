package com.mike.leadfarmfinder.controller;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test-mail")
public class TestMailController {

    private final JavaMailSender mailSender;

    @GetMapping
    public String sendTestMail() throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

        helper.setFrom("Patrycja Swigost <office@o1jobs.de>");
        helper.setTo("TWÓJ_MAIL_TESTOWY@gmail.com"); // tu wstaw swój adres
        helper.setSubject("Test SMTP o1jobs.de");
        helper.setText("To jest testowy mail z LeadFarmFinder.", false);

        mailSender.send(message);
        return "OK";
    }
}

