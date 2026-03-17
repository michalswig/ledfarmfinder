package com.mike.leadfarmfinder.service.emailextractor;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import com.mike.leadfarmfinder.service.MxLookUp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DomainMxVerifier {
    private final MxLookUp mxLookUp;
    private final EmailExtractorProperties props;

    public boolean isDomainAllowed(String domain, String rowEmailForLog) {

        if (!props.mxCheckEnabled()) return true;

        MxLookUp.MxStatus mx = mxLookUp.checkDomain(domain);

        if (mx == MxLookUp.MxStatus.INVALID) return false;

        if (mx == MxLookUp.MxStatus.UNKNOWN) {
            if (props.mxUnknownPolicy() == EmailExtractorProperties.MxUnknownPolicy.DROP) return false;
            log.warn("MX check UNKNOWN for domain={}, email={}", domain, rowEmailForLog);
        }
        return true;
    }


}
