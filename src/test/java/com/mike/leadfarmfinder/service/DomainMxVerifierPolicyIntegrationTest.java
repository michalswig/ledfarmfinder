package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailProperties;
import com.mike.leadfarmfinder.service.emailextractor.DomainMxVerifier;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainMxVerifierPolicyIntegrationTest {

    @Test
    void shouldAllowUnknownWhenPolicyAllow() {
        EmailProperties props = new EmailProperties(
                "ses",
                true,
                EmailProperties.MxUnknownPolicy.ALLOW,
                5000L,
                Set.of("com", "de")
        );

        MxLookUp mxLookUp = domain -> MxLookUp.MxStatus.UNKNOWN;

        DomainMxVerifier verifier = new DomainMxVerifier(mxLookUp, props);

        assertTrue(verifier.isDomainAllowed("unknown-domain.com", "test@unknown-domain.com"));
    }

    @Test
    void shouldDropUnknownWhenPolicyDrop() {
        EmailProperties props = new EmailProperties(
                "ses",
                true,
                EmailProperties.MxUnknownPolicy.DROP,
                5000L,
                Set.of("com", "de")
        );

        MxLookUp mxLookUp = domain -> MxLookUp.MxStatus.UNKNOWN;

        DomainMxVerifier verifier = new DomainMxVerifier(mxLookUp, props);

        assertFalse(verifier.isDomainAllowed("unknown-domain.com", "test@unknown-domain.com"));
    }
}