package com.lystok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The client against a small local HTTP server that answers from a script. */
class ClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Answer(int status, String body, Map<String, String> headers, long sleepMillis) {
    }

    record Call(String method, String path, Map<String, String> headers, JsonNode json) {
    }

    private HttpServer server;
    private ExecutorService executor;
    private final ConcurrentLinkedQueue<Answer> answers = new ConcurrentLinkedQueue<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private String url;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] raw = exchange.getRequestBody().readAllBytes();
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, v.get(0)));
            calls.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(), headers,
                    raw.length == 0 ? null : MAPPER.readTree(raw)));
            Answer answer = answers.poll();
            if (answer == null) {
                answer = new Answer(200, "{}", Map.of(), 0);
            }
            try {
                if (answer.sleepMillis() > 0) {
                    Thread.sleep(answer.sleepMillis());
                }
                answer.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
                if (answer.body() == null) {
                    exchange.sendResponseHeaders(answer.status(), -1);
                } else {
                    byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(answer.status(), body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            } catch (InterruptedException | IOException e) {
                // the client gave up
            } finally {
                exchange.close();
            }
        });
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void answer(int status, String body) {
        answers.add(new Answer(status, body, Map.of(), 0));
    }

    private void answer(int status, String body, Map<String, String> headers) {
        answers.add(new Answer(status, body, headers, 0));
    }

    private static String apiError(String code) {
        return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"x\",\"requestId\":\"req_1\"}}";
    }

    private Lystok client() {
        return Lystok.builder().apiKey("lk_live_test").baseUrl(url).build();
    }

    private static SendEmailParams email() {
        return SendEmailParams.builder().from("a@x.example").to("b@x.example").text("t").build();
    }

    @Test
    void needsAnApiKey() {
        Assumptions.assumeTrue(System.getenv("LYSTOK_API_KEY") == null, "LYSTOK_API_KEY is set");
        assertThrows(IllegalArgumentException.class, Lystok::fromEnv);
        assertThrows(IllegalArgumentException.class, () -> new Lystok(null));
    }

    @Test
    void send() {
        answer(200, "{\"id\":\"019ab3f2\"}");
        EmailCreated created = client().emails().send(SendEmailParams.builder()
                .from("Пекарня <orders@shop.example>")
                .to("olena@example.com")
                .subject("Замовлення")
                .templateId("t1")
                .data(Map.of("first_name", "Olena"))
                .tag("type", "order")
                .tracking(true, false)
                .attachment(Attachment.of("a.txt", "hi".getBytes(StandardCharsets.UTF_8)))
                .build());
        assertEquals("019ab3f2", created.id());
        Call call = calls.get(0);
        assertEquals("POST /v1/emails", call.method() + " " + call.path());
        assertEquals("Пекарня <orders@shop.example>", call.json().get("from").asText());
        assertEquals("t1", call.json().get("template_id").asText());
        assertEquals("Olena", call.json().get("data").get("first_name").asText());
        assertEquals("order", call.json().get("tags").get("type").asText());
        assertEquals(false, call.json().get("tracking").get("clicks").asBoolean());
        assertEquals("aGk=", call.json().get("attachments").get(0).get("content").asText());
        assertTrue(call.json().get("attachments").get(0).get("content_type") == null, "null left out");
        assertTrue(call.json().get("html") == null, "unset left out");
        assertEquals("Bearer lk_live_test", call.headers().get("Authorization"));
        assertEquals("application/json", call.headers().get("Content-Type"));
        assertEquals("lystok-java/" + Lystok.VERSION, call.headers().get("User-Agent"));
        assertTrue(call.headers().get("Idempotency-Key").matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), "a UUID v4 key");
    }

    @Test
    void batchWithItsOwnIdempotencyKey() {
        answer(200, "{\"data\":[{\"id\":\"a\"},{\"id\":\"b\"}]}");
        EmailBatchCreated res = client().emails().sendBatch(List.of(email(), email()), RequestOptions.withIdempotencyKey("order-1042"));
        assertEquals(List.of("a", "b"), res.data().stream().map(EmailCreated::id).toList());
        assertEquals("/v1/emails/batch", calls.get(0).path());
        assertEquals(2, calls.get(0).json().size());
        assertEquals("order-1042", calls.get(0).headers().get("Idempotency-Key"));
    }

    @Test
    void getAnEmail() {
        answer(200, "{\"id\":\"e1\",\"from\":\"a@x.example\",\"to\":\"b@x.example\",\"subject\":\"s\",\"template_id\":null,"
                + "\"state\":\"delivered\",\"error\":null,\"tags\":{\"sdk\":\"java\"},\"created_at\":\"2026-11-24T10:15:00.000Z\","
                + "\"delivered_at\":\"2026-11-24T10:15:02.120Z\",\"something_new\":1}");
        Email email = client().emails().get("e1");
        assertEquals("delivered", email.state());
        assertEquals(Map.of("sdk", "java"), email.tags());
        assertEquals("2026-11-24T10:15:02.120Z", email.deliveredAt());
        assertNull(email.templateId());
        assertEquals("GET /v1/emails/e1", calls.get(0).method() + " " + calls.get(0).path());
        assertTrue(!calls.get(0).headers().containsKey("Idempotency-Key"), "no key on reads");
    }

    @Test
    void contacts() {
        String contact = "{\"id\":\"c1\",\"email\":\"olena@example.com\",\"first_name\":\"Olena\",\"fields\":{\"city\":\"Lviv\"},\"tags\":[\"a\"]}";
        answer(201, contact);
        answer(200, contact);
        answer(200, contact);
        answer(204, null);
        Lystok lystok = client();
        Contact made = lystok.contacts().upsert(UpsertContactParams.builder("olena@example.com").firstName("Olena").consentSource("Checkout").build());
        assertEquals("Olena", made.firstName());
        assertEquals(Map.of("city", "Lviv"), made.fields());
        lystok.contacts().get("olena+shop@example.com");
        lystok.contacts().update("c1", UpdateContactParams.builder().removeTags(List.of("old")).field("city", null).unsubscribe().build());
        lystok.contacts().delete("c1");
        assertEquals(List.of(
                "POST /v1/contacts",
                "GET /v1/contacts/olena%2Bshop%40example.com",
                "PATCH /v1/contacts/c1",
                "DELETE /v1/contacts/c1"), calls.stream().map(c -> c.method() + " " + c.path()).toList());
        assertEquals("{\"email\":\"olena@example.com\",\"first_name\":\"Olena\",\"consent_source\":\"Checkout\"}", calls.get(0).json().toString());
        assertEquals("{\"fields\":{\"city\":null},\"remove_tags\":[\"old\"],\"status\":\"unsubscribed\"}", calls.get(2).json().toString());
        assertTrue(!calls.get(0).headers().containsKey("Idempotency-Key"), "no key on contacts");
    }

    @Test
    void anErrorAnswer() {
        answer(400, "{\"error\":{\"code\":\"validation\",\"message\":\"The request is not valid. See `issues` for the fields.\","
                + "\"issues\":[{\"path\":\"to\",\"message\":\"Invalid email\"}],\"params\":{\"limit\":100},\"requestId\":\"req_1\"}}");
        LystokException e = assertThrows(LystokException.class, () -> client().emails().send(email()));
        assertEquals(400, e.getStatus());
        assertEquals("validation", e.getCode());
        assertEquals("req_1", e.getRequestId());
        assertEquals(List.of(new LystokException.Issue("to", "Invalid email")), e.getIssues());
        assertEquals(100, e.getParams().get("limit"));
        assertTrue(e.getMessage().contains("not valid"), "the message");
        assertEquals(1, calls.size(), "a refused request is not tried again");
    }

    @Test
    void anAnswerThatIsNotJson() {
        answer(502, "<html>Bad gateway</html>", Map.of("X-Request-Id", "req_9"));
        LystokException e = assertThrows(LystokException.class,
                () -> Lystok.builder().apiKey("k").baseUrl(url).maxRetries(0).build().emails().get("e1"));
        assertEquals(502, e.getStatus());
        assertEquals("server_error", e.getCode());
        assertEquals("req_9", e.getRequestId());
    }

    @Test
    void retries503ThenOkWithTheSameKey() {
        answer(503, apiError("unavailable"));
        answer(200, "{\"id\":\"e1\"}");
        assertEquals("e1", client().emails().send(email()).id());
        assertEquals(2, calls.size());
        assertEquals(calls.get(0).headers().get("Idempotency-Key"), calls.get(1).headers().get("Idempotency-Key"));
    }

    @Test
    void waitsAsLongAsRetryAfterSays() {
        answer(429, apiError("rate_limited"), Map.of("Retry-After", "1"));
        answer(200, "{\"id\":\"e1\"}");
        long started = System.nanoTime();
        client().emails().get("e1");
        assertTrue(System.nanoTime() - started >= 950_000_000L, "waited a second");
    }

    @Test
    void retriesIdempotencyInProgress() {
        answer(409, apiError("idempotency_in_progress"));
        answer(200, "{\"id\":\"e1\"}");
        assertEquals("e1", client().emails().send(email()).id());
    }

    @Test
    void notAfterIdempotencyMismatch() {
        answer(409, apiError("idempotency_mismatch"));
        LystokException e = assertThrows(LystokException.class, () -> client().emails().send(email()));
        assertEquals("idempotency_mismatch", e.getCode());
        assertEquals(1, calls.size());
    }

    @Test
    void aConnectionError() {
        Lystok lystok = Lystok.builder().apiKey("k").baseUrl("http://127.0.0.1:9").maxRetries(1).build();
        LystokException e = assertThrows(LystokException.class, () -> lystok.emails().get("e1"));
        assertEquals(0, e.getStatus());
        assertEquals("connection_error", e.getCode());
    }

    @Test
    void aTimeout() {
        answers.add(new Answer(200, "{\"id\":\"e1\"}", Map.of(), 1000));
        Lystok lystok = Lystok.builder().apiKey("k").baseUrl(url).timeout(Duration.ofMillis(200)).maxRetries(0).build();
        LystokException e = assertThrows(LystokException.class, () -> lystok.emails().get("e1"));
        assertEquals(0, e.getStatus());
        assertEquals("timeout", e.getCode());
    }

    @Test
    void aTimeoutForOneCall() {
        answers.add(new Answer(200, "{\"id\":\"e1\"}", Map.of(), 1000));
        Lystok lystok = Lystok.builder().apiKey("k").baseUrl(url).maxRetries(0).build();
        LystokException e = assertThrows(LystokException.class, () -> lystok.emails().get("e1", RequestOptions.withTimeout(Duration.ofMillis(200))));
        assertEquals("timeout", e.getCode());
    }

    @Test
    void aDeleteThatWentThroughBeforeAFailure() {
        answer(503, apiError("unavailable"));
        answer(404, apiError("not_found"));
        client().contacts().delete("c1");
        assertEquals(2, calls.size());
    }
}
