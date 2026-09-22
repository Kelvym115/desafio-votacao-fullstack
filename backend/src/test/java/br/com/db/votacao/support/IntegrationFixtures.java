package br.com.db.votacao.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** HTTP real e tempo controlado: nenhum teste precisa aguardar a duração de uma sessão. */
public final class IntegrationFixtures {
    public static final Instant INICIO = Instant.parse("2026-09-22T12:00:00Z");

    private IntegrationFixtures() {
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class ClockConfiguration {
        @Bean
        @Primary
        public MutableClock testClock() {
            return new MutableClock(INICIO, ZoneOffset.UTC);
        }
    }

    public static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        public MutableClock(Instant initial, ZoneId zone) {
            this(new AtomicReference<>(initial), zone);
        }

        private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        public void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(instant, requestedZone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    public record Response(int status, JsonNode body) {
    }

    public static final class Api {
        private final String baseUrl;
        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
        private final ObjectMapper json = new ObjectMapper();

        public Api(int port) {
            baseUrl = "http://127.0.0.1:" + port + "/api/v1";
        }

        public Response get(String path) throws IOException, InterruptedException {
            return send("GET", path, null);
        }

        public Response post(String path, String body) throws IOException, InterruptedException {
            return send("POST", path, body);
        }

        private Response send(String method, String path, String body)
                throws IOException, InterruptedException {
            var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body);
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method(method, publisher).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), json.readTree(response.body()));
        }
    }
}
