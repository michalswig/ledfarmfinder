package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LeadController {

    private final FarmLeadRepository farmLeadRepository;

    @GetMapping("/unsubscribe/{token}")
    public ResponseEntity<String> unsubscribeGet(@PathVariable String token) {
        return doUnsubscribe(token);
    }

    @PostMapping("/unsubscribe/{token}")
    public ResponseEntity<String> unsubscribePost(@PathVariable String token) {
        return doUnsubscribe(token);
    }

    private ResponseEntity<String> doUnsubscribe(String token) {
        return farmLeadRepository.findByUnsubscribeToken(token)
                .map(lead -> {
                    if (lead.isActive()) {
                        lead.setActive(false);
                        farmLeadRepository.save(lead);
                    }
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body(successHtml());
                })
                .orElse(ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_HTML)
                        .body(errorHtml()));
    }

    private String successHtml() {
        return """
                <!DOCTYPE html>
                <html lang="de">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Abmeldung bestätigt</title>
                  <style>
                    body { font-family: Arial, sans-serif; background: #f9f9f9; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }
                    .card { background: white; border-radius: 8px; padding: 48px 40px; max-width: 480px; width: 100%; box-shadow: 0 2px 12px rgba(0,0,0,0.08); text-align: center; }
                    .icon { font-size: 48px; margin-bottom: 16px; }
                    h1 { color: #1a1a1a; font-size: 22px; margin: 0 0 12px; }
                    p { color: #555; font-size: 15px; line-height: 1.6; margin: 0 0 24px; }
                    .brand { color: #888; font-size: 13px; margin-top: 32px; }
                    a { color: #2e7d32; text-decoration: none; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">✓</div>
                    <h1>Abmeldung bestätigt</h1>
                    <p>Sie erhalten von uns keine weiteren Nachrichten.<br>Wir respektieren Ihre Entscheidung.</p>
                    <p>Bei Fragen erreichen Sie uns unter<br><a href="mailto:office@o1jobs.de">office@o1jobs.de</a></p>
                    <div class="brand">o1jobs &middot; <a href="https://www.o1jobs.de">www.o1jobs.de</a></div>
                  </div>
                </body>
                </html>
                """;
    }

    private String errorHtml() {
        return """
                <!DOCTYPE html>
                <html lang="de">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Link ungültig</title>
                  <style>
                    body { font-family: Arial, sans-serif; background: #f9f9f9; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }
                    .card { background: white; border-radius: 8px; padding: 48px 40px; max-width: 480px; width: 100%; box-shadow: 0 2px 12px rgba(0,0,0,0.08); text-align: center; }
                    .icon { font-size: 48px; margin-bottom: 16px; }
                    h1 { color: #1a1a1a; font-size: 22px; margin: 0 0 12px; }
                    p { color: #555; font-size: 15px; line-height: 1.6; margin: 0 0 24px; }
                    .brand { color: #888; font-size: 13px; margin-top: 32px; }
                    a { color: #2e7d32; text-decoration: none; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">✗</div>
                    <h1>Link ungültig</h1>
                    <p>Der Abmeldelink ist leider ungültig oder bereits verwendet.</p>
                    <p>Bei Fragen erreichen Sie uns unter<br><a href="mailto:office@o1jobs.de">office@o1jobs.de</a></p>
                    <div class="brand">o1jobs &middot; <a href="https://www.o1jobs.de">www.o1jobs.de</a></div>
                  </div>
                </body>
                </html>
                """;
    }
}