package com.lystok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Webhook cases signed by the Lystok platform itself, so passing them means accepting exactly what it sends. */
class WebhookTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode CASES = load();
    private static final JsonNode VALID = find("valid");

    private static JsonNode load() {
        try (InputStream in = WebhookTest.class.getResourceAsStream("/webhooks.json")) {
            return MAPPER.readTree(in).get("cases");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode find(String name) {
        for (JsonNode c : CASES) {
            if (c.get("name").asText().equals(name)) {
                return c;
            }
        }
        throw new IllegalStateException(name);
    }

    private static Map<String, Object> headers(JsonNode c) {
        Map<String, Object> headers = new LinkedHashMap<>();
        c.get("headers").fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
        return headers;
    }

    private static Instant now(JsonNode c) {
        return Instant.ofEpochSecond(c.get("now").asLong());
    }

    private static WebhookEvent verify(JsonNode c, Map<String, ?> headers) {
        return Webhook.verify(c.get("payload").asText(), headers, c.get("secret").asText(), Webhook.DEFAULT_TOLERANCE, now(c));
    }

    @TestFactory
    Stream<DynamicTest> platformCases() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : CASES) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> {
                if (c.get("valid").asBoolean()) {
                    WebhookEvent event = verify(c, headers(c));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> expected = MAPPER.readValue(c.get("payload").asText(), Map.class);
                    assertEquals(expected.get("type"), event.type());
                    assertEquals(expected.get("created_at"), event.createdAt());
                    assertEquals(expected.get("data"), event.data());
                } else {
                    assertThrows(WebhookVerificationException.class, () -> verify(c, headers(c)));
                }
            }));
        }
        return tests.stream();
    }

    @Test
    void headerNamesInAnyCaseAndListValues() {
        Map<String, Object> upper = new LinkedHashMap<>();
        Map<String, Object> lists = new LinkedHashMap<>();
        headers(VALID).forEach((k, v) -> {
            upper.put(k.toUpperCase(), v);
            lists.put(k, List.of(v));
        });
        assertEquals("email.delivered", verify(VALID, upper).type());
        assertEquals("email.delivered", verify(VALID, lists).type());
    }

    @Test
    void bytesBody() {
        byte[] body = VALID.get("payload").asText().getBytes(StandardCharsets.UTF_8);
        WebhookEvent event = Webhook.verify(body, headers(VALID), VALID.get("secret").asText(), Webhook.DEFAULT_TOLERANCE, now(VALID));
        assertEquals("email.delivered", event.type());
    }

    @Test
    void secretThatIsNotBase64() {
        assertThrows(WebhookVerificationException.class, () -> Webhook.verify(
                VALID.get("payload").asText(), headers(VALID), "whsec_not base64!", Webhook.DEFAULT_TOLERANCE, now(VALID)));
    }

    @Test
    void onTheClient() {
        WebhookEvent event = new Lystok("lk_test_x").webhooks().verify(
                VALID.get("payload").asText(), headers(VALID), VALID.get("secret").asText(), Webhook.DEFAULT_TOLERANCE, now(VALID));
        assertEquals("email.delivered", event.type());
    }
}
