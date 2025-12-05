package com.mike.leadfarmfinder.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.*;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class EmailExtractor {

    /**
     * Prosty, dość ścisły regex na maile w HTML.
     * Później i tak walidujemy je mocniej w normalizeEmail().
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /**
     * Cloudflare obfuscation: <a href="/cdn-cgi/l/email-protection" data-cfemail="...">
     */
    private static final Pattern CLOUDFLARE_PATTERN =
            Pattern.compile("data-cfemail=\"([0-9a-fA-F]+)\"");

    /**
     * TLD, które akceptujemy. Możesz rozszerzyć o np. .net, .eu, itp.
     */
    private static final Set<String> ALLOWED_TLDS = Set.of(
            "de", "com", "net", "eu"
    );

    /**
     * Dozwolona lokalna część e-maila (przed @):
     * - tylko ASCII: [a-z0-9._%+-]
     * - długość: 2–40 znaków
     */
    private static final Pattern LOCAL_PART_PATTERN =
            Pattern.compile("^[a-z0-9._%+-]{2,40}$");

    /**
     * Wykrywanie pseudo-unicode typu "u00fc" itp. w lokalnej części.
     * Takie rzeczy chcemy odrzucać (u00fcnchuschi@...).
     */
    private static final Pattern SUSPICIOUS_UNICODE_ESCAPE =
            Pattern.compile("u00[0-9a-fA-F]{2}");

    public Set<String> extractEmails(String html) {
        Set<String> results = new LinkedHashSet<>();

        if (html == null || html.isBlank()) {
            return results;
        }

        // 1) Cloudflare data-cfemail
        Matcher cf = CLOUDFLARE_PATTERN.matcher(html);
        while (cf.find()) {
            String encoded = cf.group(1);
            String decoded = CloudflareEmailDecoder.decode(encoded);

            if (decoded == null) {
                continue;
            }

            String normalized = normalizeEmail(decoded);
            if (normalized != null) {
                results.add(normalized);
            }
        }

        // 2) Zwykłe maile w HTML
        Matcher matcher = EMAIL_PATTERN.matcher(html);
        while (matcher.find()) {
            String raw = matcher.group();
            String normalized = normalizeEmail(raw);
            if (normalized != null) {
                results.add(normalized);
            }
        }

        return results;
    }

    /**
     * Normalize email:
     * - trim
     * - rozbicie na local@domain
     * - walidacja lokalnej części (zwykłe ASCII, rozsądna długość, brak "u00xx")
     * - wyłuskanie i „zacięcie” TLD (np. ".deust" -> ".de" jeśli "de" jest dozwolone)
     * - MX check na domenie (musi mieć rekord MX)
     * - wszystko na lowercase
     *
     * Zwraca:
     * - znormalizowany e-mail (np. info@hof-may.de)
     * - albo null, jeśli e-mail jest śmieciem / nie spełnia kryteriów
     */
    private String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }

        String email = raw.trim();
        if (email.isEmpty()) {
            return null;
        }

        int atIndex = email.indexOf('@');
        int lastDot = email.lastIndexOf('.');
        if (atIndex <= 0 || lastDot <= atIndex) {
            return null;
        }

        String localPart = email.substring(0, atIndex);
        String hostWithoutTld = email.substring(atIndex + 1, lastDot);
        String tldPart = email.substring(lastDot + 1);

        // 🔥 Walidacja lokalnej części (przed @)
        if (!isLocalPartAllowed(localPart)) {
            return null;
        }

        // 🔥 Wyłuskanie znanego TLD (np. de, com)
        String tld = extractKnownTld(tldPart);
        if (tld == null) {
            return null;
        }

        String normalizedLocal = localPart.toLowerCase();
        String normalizedHost = hostWithoutTld.toLowerCase();
        String domain = normalizedHost + "." + tld;

        // 🔥 MX check – domena musi mieć rekord MX
        if (!domainHasMxRecord(domain)) {
            return null;
        }

        return normalizedLocal + "@" + domain;
    }

    /**
     * From something like "de", "deust", "DE", "DeUST" etc.
     * return a known TLD if it starts with it (e.g. "deust" -> "de").
     */
    private String extractKnownTld(String tldPart) {
        if (tldPart == null || tldPart.isEmpty()) {
            return null;
        }

        String lower = tldPart.toLowerCase();

        for (String allowed : ALLOWED_TLDS) {
            if (lower.equals(allowed) || lower.startsWith(allowed)) {
                return allowed;
            }
        }

        return null;
    }

    /**
     * Dodatkowa walidacja lokalnej części ("before @"):
     * - tylko ascii [a-z0-9._%+-]
     * - długość 2–40
     * - bez "u00XX" (pseudo-unicode)
     */
    private boolean isLocalPartAllowed(String localPartRaw) {
        if (localPartRaw == null) {
            return false;
        }

        String local = localPartRaw.trim().toLowerCase();

        // odrzucamy pseudo-unicode typu "u00fc"
        if (SUSPICIOUS_UNICODE_ESCAPE.matcher(local).find()) {
            return false;
        }

        // odrzucamy znaki spoza zestawu i dziwne długości
        if (!LOCAL_PART_PATTERN.matcher(local).matches()) {
            return false;
        }

        return true;
    }

    /**
     * Prosty MX check przez JNDI DNS:
     * - true  -> domena ma rekord MX (może odbierać pocztę)
     * - false -> brak MX / domena nie istnieje / błąd DNS
     */
    private boolean domainHasMxRecord(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);

            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute attr = attrs.get("MX");

            return (attr != null && attr.size() > 0);
        } catch (NamingException e) {
            return false;
        }
    }
}
