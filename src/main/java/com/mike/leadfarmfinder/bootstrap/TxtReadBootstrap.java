package com.mike.leadfarmfinder.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;

@Component
@Slf4j
public class TxtReadBootstrap implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        var resource = new ClassPathResource("MY_DB_EMAILS.txt");
        if (!resource.exists()) {
            log.error("No farms available");
            return;
        }
        var path = resource.getFile().toPath();
        long count = Files.lines(path).count();
        log.info("{} lines found", count);

    }
}
