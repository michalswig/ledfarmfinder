package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.config.AwsConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig
@Import({AwsConfiguration.class, SesMailSenderGateway.class})
@TestPropertySource(properties = {
        "leadfinder.email.provider=ses",
        "aws.ses.region=eu-central-1"
})
@Disabled("Manual integration test - sends real email via AWS SES")
class SesMailSenderGatewayIT {

    @Autowired
    private MailSenderGateway mailSenderGateway;

    @Test
    void shouldSendTestEmailViaSes() {

        PreparedMail mail = new PreparedMail(
                "Patrycja Swigost <patrycja@office.o1jobs.de>",
                "m.swigost@gmail.com",
                "TEST SES - LeadFarmFinder",
                """
                        Cześć,
                                        
                        to jest testowy mail wysłany z LeadFarmFinder przez AWS SES.
                                        
                        Jeśli to czytasz, integracja działa poprawnie.
                                        
                        Pozdrawiam
                        LeadFarmFinder
                        """,
                null,
                1L,
                "TEST"
        );

        SendResult result = mailSenderGateway.send(mail);

        assertTrue(result.sent(), "Expected SES email to be accepted by AWS SES");
    }
}