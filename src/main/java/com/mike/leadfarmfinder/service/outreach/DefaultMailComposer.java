package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.config.EmailTemplateVariants;
import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultMailComposer implements MailComposer {

    private final OutreachProperties outreachProperties;
    private final EmailTemplateVariants emailTemplateVariants;
    private final MailTemplateVariantSelector templateVariantSelector;
    private final UnsubscribeUrlBuilder unsubscribeUrlBuilder;

    @Override
    public PreparedMail compose(FarmLead lead, String normalizeTo, EmailType type) {
        String from = outreachProperties.getFromAddress();

        String unsubscribeUrl = unsubscribeUrlBuilder.build(lead);
        Map<String, String> vars = buildTemplateVars(normalizeTo, unsubscribeUrl);

        String subject = resolveSubject(type);
        String template = resolveBodyTemplate(lead, type);
        String body = renderTemplate(template, vars);

        return new PreparedMail(
                from,
                normalizeTo,
                subject,
                body,
                unsubscribeUrl,
                lead.getId(),
                type.name()
        );
    }

    private Map<String, String> buildTemplateVars(String email, String unsubscribeUrl) {
        Map<String, String> vars = new HashMap<>();
        vars.put("EMAIL", email);
        vars.put("UNSUBSCRIBE_URL", unsubscribeUrl);
        return vars;
    }

    private String resolveSubject(EmailType type) {
        return type == EmailType.FIRST
                ? outreachProperties.getDefaultSubject()
                : outreachProperties.getFollowUpSubject();
    }

    /**
     * Jeśli skonfigurowano warianty rotacyjne
     * ({@code leadfinder.outreach.templates.first} / {@code .follow-up}) — używa ich.
     * W przeciwnym razie wraca do pojedynczego, statycznego szablonu
     * (zachowanie identyczne jak przed tą zmianą — backward compatible).
     */
    private String resolveBodyTemplate(FarmLead lead, EmailType type) {
        List<String> variants = type == EmailType.FIRST
                ? emailTemplateVariants.getFirst()
                : emailTemplateVariants.getFollowUp();

        if (variants != null && !variants.isEmpty()) {
            return templateVariantSelector.select(lead.getId(), variants);
        }

        return type == EmailType.FIRST
                ? outreachProperties.getFirstEmailBodyTemplate()
                : outreachProperties.getFollowUpEmailBodyTemplate();
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) return "";
        String result = template;
        for (var entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}