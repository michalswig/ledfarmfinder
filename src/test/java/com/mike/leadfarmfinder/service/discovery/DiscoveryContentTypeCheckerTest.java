package com.mike.leadfarmfinder.service.discovery;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryContentTypeCheckerTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Nested
    @DisplayName("classifyContentType")
    class ClassifyContentTypeTests {

        @Test
        @DisplayName("application/pdf -> PDF, skip body")
        void pdf() {
            DiscoveryContentTypeResult r = DiscoveryContentTypeChecker.classifyContentType("application/pdf");
            assertThat(r.reason()).isEqualTo(DiscoveryContentTypeResult.Reason.PDF);
            assertThat(r.isHtml()).isFalse();
            assertThat(r.shouldSkipFullFetch()).isTrue();
            assertThat(r.contentType()).contains("application/pdf");
        }

        @Test
        @DisplayName("text/html with charset -> OK")
        void htmlWithCharset() {
            DiscoveryContentTypeResult r = DiscoveryContentTypeChecker.classifyContentType("text/html; charset=UTF-8");
            assertThat(r.reason()).isEqualTo(DiscoveryContentTypeResult.Reason.OK);
            assertThat(r.isHtml()).isTrue();
            assertThat(r.shouldSkipFullFetch()).isFalse();
        }

        @Test
        @DisplayName("application/xhtml+xml -> OK")
        void xhtml() {
            DiscoveryContentTypeResult r = DiscoveryContentTypeChecker.classifyContentType("application/xhtml+xml");
            assertThat(r.reason()).isEqualTo(DiscoveryContentTypeResult.Reason.OK);
            assertThat(r.isHtml()).isTrue();
        }

        @Test
        @DisplayName("application/octet-stream -> NON_HTML")
        void octetStream() {
            DiscoveryContentTypeResult r = DiscoveryContentTypeChecker.classifyContentType("application/octet-stream");
            assertThat(r.reason()).isEqualTo(DiscoveryContentTypeResult.Reason.NON_HTML);
            assertThat(r.shouldSkipFullFetch()).isTrue();
        }

        @Test
        @DisplayName("blank header -> UNKNOWN")
        void blank() {
            DiscoveryContentTypeResult r = DiscoveryContentTypeChecker.classifyContentType("   ");
            assertThat(r.reason()).isEqualTo(DiscoveryContentTypeResult.Reason.UNKNOWN);
            assertThat(r.shouldSkipFullFetch()).isFalse();
        }
    }

    @Nested
    @DisplayName("check (HTTP)")
    class CheckHttpTests {

        @Test
        @DisplayName("HEAD returns text/html -> OK without GET")
        void headReturnsHtml() throws Exception {
            startServer(exchange -> {
                if ("HEAD".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
                exchange.close();
            });

            DiscoveryContentTypeChecker checker = new DiscoveryContentTypeChecker();
            DiscoveryContentTypeResult r = checker.check(baseUrl() + "/p");

            assertThat(r.reason()).isEqualTo(DiscoveryContentTypeResult.Reason.OK);
            assertThat(r.shouldSkipFullFetch()).isFalse();
        }

        @Test
        @DisplayName("HEAD 405 then GET returns text/html -> OK")
        void head405GetHtml() throws Exception {
            startServer(exchange -> {
                if ("HEAD".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                } else {
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, 0);
                }
                exchange.close();
            });

            DiscoveryContentTypeChecker checker = new DiscoveryContentTypeChecker();
            DiscoveryContentTypeResult r = checker.check(baseUrl() + "/p");

            assertThat(r.reason()).isEqualTo(DiscoveryContentTypeResult.Reason.OK);
        }

        @Test
        @DisplayName("HEAD returns application/pdf -> skip (no GET needed)")
        void headPdf() throws Exception {
            startServer(exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                if ("HEAD".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.sendResponseHeaders(200, 0);
                }
                exchange.close();
            });

            DiscoveryContentTypeChecker checker = new DiscoveryContentTypeChecker();
            DiscoveryContentTypeResult r = checker.check(baseUrl() + "/p");

            assertThat(r.reason()).isEqualTo(DiscoveryContentTypeResult.Reason.PDF);
            assertThat(r.shouldSkipFullFetch()).isTrue();
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/p", handler);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }
}
