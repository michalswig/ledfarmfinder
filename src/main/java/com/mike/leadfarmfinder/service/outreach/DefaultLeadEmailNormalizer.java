package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultLeadEmailNormalizer implements LeadEmailNormalizer {

    private final FarmLeadRepository farmLeadRepository;

    private static final Pattern EMAIL_REGEX =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final String[] TELEKOM_DOMAINS = {
            "@t-online.de",
            "@t-mobile.de",
            "@magenta.de",
            "@telekom.de"
            // opcjonalnie: "@telekom.de"
    };

    @Override
    public String normalizeAndValidateOrDeactivate(FarmLead lead) {
        String to = normalizeEmail(lead.getEmail());
        if (to.isBlank()) {
            log.warn("OutreachService: empty email after normalize, deactivating lead (id={})", lead.getId());
            deactivateAsInvalid(lead, "EMPTY_EMAIL");
            return null;
        }

        // blokada na śmieciowe/sklejone adresy
        if (!isValidEmail(to)) {
            log.warn("OutreachService: invalid email format, deactivating lead (id={}, rawEmail={}, normalized={})",
                    lead.getId(), lead.getEmail(), to);
            deactivateAsInvalid(lead, "INVALID_EMAIL_FORMAT");
            return null;
        }

        // tymczasowa blokada Telekom (T-Online/T-Mobile) – 550 5.7.0 IP reputation
        if (isBlockedProviderDomain(to)) {
            log.info("OutreachService: skipping blocked provider domain (Telekom) for now: {}", to);
            return null;
        }
        return to;
    }

    private boolean isBlockedProviderDomain(String email) {
        if (email == null) return false;
        String e = email.toLowerCase();
        for (String d : TELEKOM_DOMAINS) {
            if (e.endsWith(d)) return true;
        }
        return false;
    }

    private void deactivateAsInvalid(FarmLead lead, String reason) {
        lead.setActive(false);
        lead.setBounce(true); // traktuj jak “nieużywalne”
        farmLeadRepository.save(lead);
        log.info("OutreachService: lead deactivated as invalid (id={}, reason={})", lead.getId(), reason);
    }


    private boolean isValidEmail(String email) {
        return EMAIL_REGEX.matcher(email).matches();
    }

    private String normalizeEmail(String email) {
        if (email == null) return "";

        String e = email.trim();

        // "email@domena.dewww.domena.de" -> utnij od www
        int at = e.indexOf('@');
        int www = e.indexOf("www.");
        if (at > 0 && www > at) {
            e = e.substring(0, www);
        }

        // usuń przecinki/średniki na końcu
        e = e.replaceAll("[,;]+$", "");

        return e.trim().toLowerCase();
    }


}
