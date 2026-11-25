package com.lystok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Against a real Lystok API; skipped unless LYSTOK_LIVE=1. Needs a full-access key and a sender on
 * a verified domain of that account:
 *
 * <pre>
 * LYSTOK_LIVE=1 LYSTOK_API_KEY=lk_... LYSTOK_BASE_URL=http://localhost:3200 \
 * LYSTOK_TEST_FROM=hello@your-domain LYSTOK_TEST_TO=you@your-domain mvn verify
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "LYSTOK_LIVE", matches = "1")
class LiveTest {
    private Lystok lystok;
    private String to;

    @BeforeEach
    void setUp() {
        String base = System.getenv("LYSTOK_BASE_URL");
        lystok = Lystok.builder().apiKey(System.getenv("LYSTOK_API_KEY")).baseUrl(base == null || base.isEmpty() ? null : base).build();
        to = System.getenv("LYSTOK_TEST_TO");
    }

    private SendEmailParams.Builder email(String subject) {
        return SendEmailParams.builder().from(System.getenv("LYSTOK_TEST_FROM")).to(to).subject(subject).text("lystok-java live test");
    }

    @Test
    void sendAndReadBack() {
        EmailCreated sent = lystok.emails().send(email("lystok-java live test").tag("sdk", "java").build());
        Email email = lystok.emails().get(sent.id());
        assertEquals(sent.id(), email.id());
        assertEquals(to, email.to());
        assertEquals(Map.of("sdk", "java"), email.tags());
    }

    @Test
    void oncePerIdempotencyKey() {
        RequestOptions key = RequestOptions.withIdempotencyKey("lystok-java-" + System.nanoTime());
        EmailCreated first = lystok.emails().send(email("lystok-java idempotency").build(), key);
        EmailCreated second = lystok.emails().send(email("lystok-java idempotency").build(), key);
        assertEquals(first.id(), second.id());
    }

    @Test
    void batch() {
        EmailBatchCreated res = lystok.emails().sendBatch(List.of(email("lystok-java batch 1").build(), email("lystok-java batch 2").build()));
        assertEquals(2, res.data().size());
    }

    @Test
    void aValidationError() {
        LystokException e = assertThrows(LystokException.class, () -> lystok.emails().send(email("s").to("not-an-address").build()));
        assertEquals(400, e.getStatus());
        assertEquals("validation", e.getCode());
        assertNotNull(e.getRequestId());
    }

    @Test
    void aContact() {
        String address = "lystok-java-" + System.nanoTime() + "@example.com";
        Contact made = lystok.contacts().upsert(UpsertContactParams.builder(address)
                .firstName("Olena").tags(List.of("sdk-test")).consentSource("lystok-java live test").build());
        assertEquals("subscribed", made.status());
        assertEquals(List.of("sdk-test"), made.tags());
        Contact changed = lystok.contacts().update(address, UpdateContactParams.builder()
                .lastName("Shevchenko").removeTags(List.of("sdk-test")).build());
        assertEquals("Shevchenko", changed.lastName());
        assertEquals(List.of(), changed.tags());
        assertEquals(address, lystok.contacts().get(made.id()).email());
        lystok.contacts().delete(made.id());
        LystokException e = assertThrows(LystokException.class, () -> lystok.contacts().get(address));
        assertEquals(404, e.getStatus());
    }
}
