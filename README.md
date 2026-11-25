# Lystok for Java and Kotlin

The official library for the [Lystok](https://lystok.com) email API: send transactional email,
keep contacts in step and check webhooks. Java 17+, works the same from Kotlin; its only
dependency is Jackson (`jackson-databind`).

Maven:

```xml
<dependency>
  <groupId>com.lystok</groupId>
  <artifactId>lystok-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
implementation("com.lystok:lystok-java:0.1.0")
```

## Send an email

Create an API key in Lystok → Settings → For developers, and verify your sender domain.

```java
import com.lystok.*;

Lystok lystok = Lystok.fromEnv(); // reads LYSTOK_API_KEY

EmailCreated email = lystok.emails().send(SendEmailParams.builder()
        .from("Shop <orders@shop.example>")
        .to("olena@example.com")
        .subject("Your order #1042")
        .html("<p>Thank you! We are packing your order.</p>")
        .build());
System.out.println(email.id());
```

Kotlin:

```kotlin
import com.lystok.*

val lystok = Lystok.fromEnv()

val email = lystok.emails().send(
    SendEmailParams.builder()
        .from("Shop <orders@shop.example>")
        .to("olena@example.com")
        .subject("Your order #1042")
        .html("<p>Thank you! We are packing your order.</p>")
        .build()
)
println(email.id())
```

`new Lystok("lk_live_…")` takes the key directly. Keys belong on your server: never put one in a
browser or a mobile app. Make one client and share it: it is safe to use from many threads.

### With a template

```java
lystok.emails().send(SendEmailParams.builder()
        .from("Shop <orders@shop.example>")
        .to("olena@example.com")
        .templateId("019ab3f2-…") // Templates → ⋯ → Copy ID
        .data(Map.of("first_name", "Olena", "order", Map.of("number", 1042)))
        .locale("uk")
        .build());
```

### Attachments, tags, tracking

```java
lystok.emails().send(SendEmailParams.builder()
        .from("Shop <orders@shop.example>")
        .to("olena@example.com")
        .subject("Invoice #1042")
        .html("<p>Your invoice is attached.</p>")
        .attachment(Attachment.of("invoice-1042.pdf", Files.readAllBytes(Path.of("invoice-1042.pdf"))))
        .tag("type", "invoice")
        .tracking(true, true)
        .build());
```

### Many at once, and what happened

```java
EmailBatchCreated res = lystok.emails().sendBatch(List.of(
        SendEmailParams.builder().from("Shop <orders@shop.example>").to("olena@example.com").templateId("…").data("first_name", "Olena").build(),
        SendEmailParams.builder().from("Shop <orders@shop.example>").to("taras@example.com").templateId("…").data("first_name", "Taras").build()
)); // up to 100; ids in the same order

Email email = lystok.emails().get(res.data().get(0).id());
email.state(); // "queued", "sent", "delivered", "bounced", "complained", "failed" or "suppressed"
```

## Sending once

Every send carries an `Idempotency-Key`, so the library's own retries never send an email twice.
To be safe across your own retries too (a crashed job, a repeated webhook), pass your own key —
the same key within 24 hours gets the first answer back:

```java
lystok.emails().send(email, RequestOptions.withIdempotencyKey("order-" + order.id()));
```

## Contacts

Needs a key with full access.

```java
Contact contact = lystok.contacts().upsert(UpsertContactParams.builder("olena@example.com")
        .firstName("Olena")
        .lists(List.of("Customers"))
        .tags(List.of("bought-online"))
        .consentSource("Newsletter checkbox at checkout") // required for a new contact
        .build());

lystok.contacts().get("olena@example.com"); // by address or by id
lystok.contacts().update("olena@example.com", UpdateContactParams.builder()
        .removeTags(List.of("bought-online"))
        .field("city", "Lviv")
        .build());
lystok.contacts().update("olena@example.com", UpdateContactParams.builder().unsubscribe().build());
lystok.contacts().delete(contact.id());
```

## Errors

Every failure is a `LystokException` (unchecked):

```java
try {
    lystok.emails().send(email);
} catch (LystokException e) {
    e.getStatus();    // 400, 401, 403, 404, 409, 429, 5xx — or 0 when no answer came
    e.getCode();      // "validation", "not_found", "plan_limit", "access_ended", "timeout", "connection_error", …
    e.getMessage();   // for people
    e.getIssues();    // [Issue(path=to, message=…)] for validation errors
    e.getRequestId(); // quote it when you write to support
}
```

In Kotlin these read as `e.status`, `e.code`, `e.issues`, `e.requestId`.

## Retries and timeouts

Network errors, timeouts, `429` and `5xx` are tried again twice, waiting a little longer each time
(or as long as `Retry-After` says). Each try may take 30 seconds.

```java
Lystok lystok = Lystok.builder()
        .apiKey(System.getenv("LYSTOK_API_KEY"))
        .maxRetries(4)
        .timeout(Duration.ofSeconds(10))
        .build();
lystok.emails().get(id, RequestOptions.withTimeout(Duration.ofSeconds(5)));
```

`Lystok.builder().httpClient(...)` takes your own `java.net.http.HttpClient`, for a proxy for example.

## Webhooks

Lystok signs every webhook ([Standard Webhooks](https://www.standardwebhooks.com)). Check the
**raw body** before parsing it; `Webhook.verify` returns the event or throws
`WebhookVerificationException`. The secret (`whsec_…`) is in the webhook's menu → Signing secret.

Spring Boot:

```java
@RestController
class LystokWebhookController {
    @Value("${lystok.webhook-secret}")
    private String secret;

    @PostMapping("/webhooks/lystok")
    ResponseEntity<Void> receive(@RequestBody String body, @RequestHeader Map<String, String> headers) {
        WebhookEvent event;
        try {
            event = Webhook.verify(body, headers, secret);
        } catch (WebhookVerificationException e) {
            return ResponseEntity.status(401).build();
        }
        if (event.type().equals("email.bounced")) {
            // answer within 10 seconds: hand slow work to a queue
        }
        return ResponseEntity.ok().build();
    }
}
```

Ktor:

```kotlin
post("/webhooks/lystok") {
    val event = try {
        Webhook.verify(call.receiveText(), call.request.headers.toMap(), System.getenv("LYSTOK_WEBHOOK_SECRET"))
    } catch (e: WebhookVerificationException) {
        return@post call.respond(HttpStatusCode.Unauthorized)
    }
    if (event.type() == "email.bounced") {
        // …
    }
    call.respond(HttpStatusCode.OK)
}
```

The same event can arrive twice: skip ones you have handled by the `webhook-id` header.

## Development

```sh
mvn verify
LYSTOK_LIVE=1 LYSTOK_API_KEY=lk_… LYSTOK_TEST_FROM=… LYSTOK_TEST_TO=… mvn verify   # also against the real API
```

`src/test/resources/webhooks.json` holds webhook signatures made by the Lystok platform itself; the
same file is checked by every Lystok library.

## License

[MIT](LICENSE)
