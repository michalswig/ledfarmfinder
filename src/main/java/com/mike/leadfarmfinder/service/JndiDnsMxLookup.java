package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

@Component
@AllArgsConstructor
@Slf4j
public class JndiDnsMxLookup implements MxLookUp {

    private final EmailExtractorProperties props;

    @Override
    public MxStatus checkDomain(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(props.mxTimeoutMs()));
            env.put("com.sun.jndi.dns.timeout.retries", "1");

            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute attr = attrs.get("MX");

            return (attr != null && attr.size() > 0) ? MxStatus.VALID : MxStatus.INVALID;
        } catch (NamingException e) {
            return MxStatus.UNKNOWN;
        }
    }
}
