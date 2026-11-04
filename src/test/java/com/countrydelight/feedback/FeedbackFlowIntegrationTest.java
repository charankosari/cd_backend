package com.countrydelight.feedback;

import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.IssueOption;
import com.countrydelight.feedback.domain.OrderItem;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.domain.Subscription;
import com.countrydelight.feedback.domain.SubscriptionStatus;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.IssueOptionRepository;
import com.countrydelight.feedback.repository.OrderRepository;
import com.countrydelight.feedback.repository.ProductRepository;
import com.countrydelight.feedback.repository.SubscriptionRepository;
import com.countrydelight.feedback.seed.DataSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * End-to-end checks of the rules through the HTTP API, covering the tricky cases from the brief:
 * subscribed product, bought twice before rating, frequency cap, skipped feedback, rating exactly 4.
 */
@SpringBootTest(properties = {"app.seed.enabled=false", "app.upload-dir=target/test-uploads"})
@AutoConfigureMockMvc
class FeedbackFlowIntegrationTest {

    static final LocalDate T = LocalDate.of(2026, 11, 10);
    static final long C = 1L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TimeProvider time;
    @Autowired DataSeeder seeder;
    @Autowired ProductRepository products;
    @Autowired IssueOptionRepository issueOptions;
    @Autowired CustomerRepository customers;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired OrderRepository orders;

    @BeforeEach
    void setUp() {
        seeder.wipe();
        time.setToday(T);
        product("PANEER", 1, "95");
        product("DAHI", 1, "45");
        product("BREAD", 2, "50");
        product("ATTA", 5, "285");
        product("GHEE", 7, "375");
        int i = 1;
        for (String code : List.of("HARD", "SMELL", "NEAR_EXPIRY", "OTHER")) {
            issueOptions.save(new IssueOption("PANEER", code, code.toLowerCase(), i++));
        }
        issueOptions.save(new IssueOption("BREAD", "STALE", "Stale", 1));
        customers.insertWithId(C, "Test", "9000000001", "Sector 45", "Gurugram", null, Instant.now());
        customers.insertWithId(2L, "Other", "9000000002", "HSR Layout", "Bengaluru", null, Instant.now());
    }

    @AfterEach
    void tearDown() {
        time.reset();
    }

    // ------------------------------------------------------------------ who we ask / when

    @Test
    @DisplayName("Subscribed product (active) is never asked, even for a one-time extra")
    void subscribedProductNotAsked() throws Exception {
        subscribe("PANEER", SubscriptionStatus.ACTIVE);
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        assertHidden(next(C), "NOTHING_TO_ASK");
    }

    @Test
    @DisplayName("Paused subscription still counts as subscribed")
    void pausedSubscriptionCountsAsSubscribed() throws Exception {
        subscribe("GHEE", SubscriptionStatus.PAUSED);
        deliver(7, PurchaseType.ONE_TIME, "GHEE");
        assertHidden(next(C), "NOTHING_TO_ASK");
    }

    @Test
    @DisplayName("Items delivered by a subscription are not asked even after the subscription is cancelled")
    void subscriptionDeliveryNotAsked() throws Exception {
        subscribe("DAHI", SubscriptionStatus.CANCELLED);
        deliver(1, PurchaseType.SUBSCRIPTION, "DAHI");
        assertHidden(next(C), "NOTHING_TO_ASK");
    }

    @Test
    @DisplayName("Not asked before X days; asked exactly on day X")
    void askedOnlyFromDayX() throws Exception {
        deliver(6, PurchaseType.ONE_TIME, "GHEE");            // X=7, due tomorrow
        assertHidden(next(C), "NOTHING_TO_ASK");
        time.setToday(T.plusDays(1));
        JsonNode r = next(C);
        assertThat(r.path("show").asBoolean()).isTrue();
        assertThat(r.path("prompt").path("productId").asText()).isEqualTo("GHEE");
    }

    @Test
    @DisplayName("Not asked once the window after day X has passed")
    void notAskedAfterWindow() throws Exception {
        deliver(5, PurchaseType.ONE_TIME, "PANEER");          // due T-4, window ended T-1
        assertHidden(next(C), "NOTHING_TO_ASK");
    }

    @Test
    @DisplayName("Bought twice before rating: one card, about the latest delivery")
    void boughtTwiceSingleCardForLatest() throws Exception {
        deliver(3, PurchaseType.ONE_TIME, "PANEER");
        OrderItem latest = deliver(1, PurchaseType.ONE_TIME, "PANEER");

        JsonNode r = next(C);
        assertThat(r.path("prompt").path("productId").asText()).isEqualTo("PANEER");
        assertThat(r.path("prompt").path("deliveryDate").asText()).isEqualTo(T.minusDays(1).toString());
        assertThat(r.path("prompt").path("orderId").asLong()).isEqualTo(latest.getOrder().getId());

        rate(C, r.path("prompt").path("promptId").asLong(), 5);
        time.setToday(T.plusDays(3));                          // past the frequency cap
        assertHidden(next(C), "NOTHING_TO_ASK");
    }

    @Test
    @DisplayName("Bought twice, second delivery becomes due later: not asked again after rating")
    void secondDeliveryDueLaterNotAskedAgain() throws Exception {
        deliver(2, PurchaseType.ONE_TIME, "BREAD");            // due today
        deliver(1, PurchaseType.ONE_TIME, "BREAD");            // due tomorrow
        JsonNode r = next(C);
        assertThat(r.path("prompt").path("deliveryDate").asText()).isEqualTo(T.minusDays(2).toString());
        rate(C, r.path("prompt").path("promptId").asLong(), 4);

        time.setToday(T.plusDays(3));
        assertHidden(next(C), "NOTHING_TO_ASK");
        JsonNode report = getJson(get("/api/admin/customers/" + C + "/eligibility"));
        assertThat(report.toString()).contains("PRODUCT_ALREADY_REVIEWED");   // one review per product
    }

    @Test
    @DisplayName("Priority: product new to the household is asked before a repeat purchase")
    void newToHouseholdFirst() throws Exception {
        deliver(30, PurchaseType.ONE_TIME, "BREAD");
        deliver(2, PurchaseType.ONE_TIME, "BREAD");
        deliver(7, PurchaseType.ONE_TIME, "GHEE");
        JsonNode r = next(C);
        assertThat(r.path("prompt").path("productId").asText()).isEqualTo("GHEE");
        assertThat(r.path("prompt").path("reasons").toString()).contains("NEW_TO_HOUSEHOLD");
    }

    // ------------------------------------------------------------------ frequency cap, skip, ignore

    @Test
    @DisplayName("Frequency cap: after answering, no new card for 3 days even if another product is due")
    void frequencyCap() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        deliver(1, PurchaseType.ONE_TIME, "DAHI");
        JsonNode first = next(C);
        rate(C, first.path("prompt").path("promptId").asLong(), 5);

        JsonNode capped = next(C);
        assertHidden(capped, "FREQUENCY_CAP");
        assertThat(capped.path("nextEligibleOn").asText()).isEqualTo(T.plusDays(3).toString());

        time.setToday(T.plusDays(2));
        assertHidden(next(C), "FREQUENCY_CAP");

        time.setToday(T.plusDays(3));
        JsonNode second = next(C);
        assertThat(second.path("show").asBoolean()).isTrue();
        assertThat(second.path("prompt").path("productId").asText())
                .isNotEqualTo(first.path("prompt").path("productId").asText());
    }

    @Test
    @DisplayName("Same card is returned on repeat loads the same day (no new prompt, no extra impression)")
    void sameCardSameDay() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        long a = next(C).path("prompt").path("promptId").asLong();
        long b = next(C).path("prompt").path("promptId").asLong();
        assertThat(a).isEqualTo(b);
        JsonNode report = getJson(get("/api/admin/customers/" + C + "/eligibility"));
        assertThat(report.path("prompts").get(0).path("shownDays").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Skip: product is not asked again, other products still are after the cap")
    void skipSuppressesProduct() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");           // new to household -> asked first
        deliver(30, PurchaseType.ONE_TIME, "BREAD");
        deliver(2, PurchaseType.ONE_TIME, "BREAD");            // repeat -> lower priority, due today, window to T+3
        JsonNode r = next(C);
        assertThat(r.path("prompt").path("productId").asText()).isEqualTo("PANEER");
        long promptId = r.path("prompt").path("promptId").asLong();

        JsonNode skip = getJson(post("/api/feedback-prompts/" + promptId + "/skip").with(asCustomer(C)));
        assertThat(skip.path("status").asText()).isEqualTo("SKIPPED");
        assertHidden(next(C), "FREQUENCY_CAP");

        // A fresh paneer delivery becomes due on T+3 too; paneer must stay suppressed, bread is asked.
        deliver(-2, PurchaseType.ONE_TIME, "PANEER");
        time.setToday(T.plusDays(3));
        JsonNode after = next(C);
        assertThat(after.path("prompt").path("productId").asText()).isEqualTo("BREAD");
    }

    @Test
    @DisplayName("Ignored: shown on 3 days without action -> IGNORED, cap applies, product not asked again")
    void ignoredAfterThreeDays() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        long id = next(C).path("prompt").path("promptId").asLong();
        time.setToday(T.plusDays(1));
        assertThat(next(C).path("prompt").path("promptId").asLong()).isEqualTo(id);
        time.setToday(T.plusDays(2));
        assertThat(next(C).path("prompt").path("promptId").asLong()).isEqualTo(id);

        time.setToday(T.plusDays(3));
        JsonNode r = next(C);
        assertHidden(r, "FREQUENCY_CAP");                       // cap counted from last impression (T+2)
        assertThat(r.path("nextEligibleOn").asText()).isEqualTo(T.plusDays(5).toString());

        deliver(-4, PurchaseType.ONE_TIME, "PANEER");           // delivered T+4, due T+5
        time.setToday(T.plusDays(5));
        assertHidden(next(C), "NOTHING_TO_ASK");
    }

    @Test
    @DisplayName("Rating twice on the same card is rejected; skip after rating is rejected")
    void ratingTwiceRejected() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        long id = next(C).path("prompt").path("promptId").asLong();
        rate(C, id, 5);
        MvcResult again = mvc.perform(ratingRequest(C, id, 3)).andReturn();
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        MvcResult skip = mvc.perform(post("/api/feedback-prompts/" + id + "/skip").with(asCustomer(C))).andReturn();
        assertThat(skip.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Another household cannot rate my card")
    void otherCustomerCannotRate() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        long id = next(C).path("prompt").path("promptId").asLong();
        assertThat(mvc.perform(ratingRequest(2L, id, 5)).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ rating branches

    @Test
    @DisplayName("Rating exactly 4 -> BUY_AGAIN with tomorrow as default date")
    void ratingExactlyFourIsBuyAgain() throws Exception {
        deliver(7, PurchaseType.ONE_TIME, "GHEE");
        JsonNode r = rate(C, next(C).path("prompt").path("promptId").asLong(), 4);
        assertThat(r.path("nextStep").asText()).isEqualTo("BUY_AGAIN");
        assertThat(r.path("buyAgain").path("suggestedDate").asText()).isEqualTo(T.plusDays(1).toString());
        assertThat(r.path("buyAgain").path("maxDate").asText()).isEqualTo(T.plusDays(30).toString());
        assertThat(r.has("issueForm")).isFalse();
    }

    @Test
    @DisplayName("Rating 3 -> ISSUE_DETAILS with product-specific chips")
    void ratingThreeIsIssueDetails() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        JsonNode r = rate(C, next(C).path("prompt").path("promptId").asLong(), 3);
        assertThat(r.path("nextStep").asText()).isEqualTo("ISSUE_DETAILS");
        List<String> codes = new ArrayList<>();
        r.path("issueForm").path("options").forEach(o -> codes.add(o.path("code").asText()));
        assertThat(codes).contains("HARD", "SMELL", "NEAR_EXPIRY");
        assertThat(r.has("buyAgain")).isFalse();
    }

    @Test
    @DisplayName("Rating out of range (0, 6) is rejected")
    void ratingRangeValidated() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        long id = next(C).path("prompt").path("promptId").asLong();
        assertThat(mvc.perform(ratingRequest(C, id, 0)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(ratingRequest(C, id, 6)).andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Subscribed between seeing the card and rating 5 -> THANK_YOU, no buy-again")
    void noBuyAgainForSubscribed() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        long id = next(C).path("prompt").path("promptId").asLong();
        subscribe("PANEER", SubscriptionStatus.ACTIVE);
        JsonNode r = rate(C, id, 5);
        assertThat(r.path("nextStep").asText()).isEqualTo("THANK_YOU");
        assertThat(r.has("buyAgain")).isFalse();
    }

    @Test
    @DisplayName("Pending card is withdrawn if the user subscribes before answering")
    void pendingCancelledOnSubscribe() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        next(C);
        subscribe("PANEER", SubscriptionStatus.ACTIVE);
        assertHidden(next(C), "NOTHING_TO_ASK");
    }

    // ------------------------------------------------------------------ issue details

    @Test
    @DisplayName("Issue details: saved with order trace (product, delivery date, area); validation enforced")
    void issueDetails() throws Exception {
        OrderItem item = deliver(1, PurchaseType.ONE_TIME, "PANEER");
        long feedbackId = rate(C, next(C).path("prompt").path("promptId").asLong(), 2).path("feedbackId").asLong();

        assertThat(putIssues(feedbackId, "{\"issueCodes\":[\"STALE\"]}").getResponse().getStatus()).isEqualTo(400);
        assertThat(putIssues(feedbackId, "{\"issueCodes\":[]}").getResponse().getStatus()).isEqualTo(400);
        assertThat(putIssues(feedbackId, "{\"issueCodes\":[\"HARD\"],\"note\":\"" + "x".repeat(201) + "\"}")
                .getResponse().getStatus()).isEqualTo(400);

        MvcResult ok = putIssues(feedbackId, "{\"issueCodes\":[\"smell\",\"HARD\"],\"note\":\"  smelled off \"}");
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(ok.getResponse().getContentAsString());
        assertThat(body.path("issues").get(0).path("code").asText()).isEqualTo("HARD");  // chip order
        assertThat(body.path("issues").get(1).path("code").asText()).isEqualTo("SMELL");
        assertThat(body.path("note").asText()).isEqualTo("smelled off");
        assertThat(body.path("deliveryDate").asText()).isEqualTo(T.minusDays(1).toString());
        assertThat(body.path("area").asText()).isEqualTo("Sector 45");
        assertThat(body.path("orderId").asLong()).isEqualTo(item.getOrder().getId());

        MockMultipartFile photo = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MvcResult up = mvc.perform(multipart("/api/feedback/" + feedbackId + "/photo").file(photo).with(asCustomer(C))).andReturn();
        assertThat(up.getResponse().getStatus()).isEqualTo(200);
        MockMultipartFile pdf = new MockMultipartFile("file", "p.pdf", "application/pdf", new byte[]{1});
        assertThat(mvc.perform(multipart("/api/feedback/" + feedbackId + "/photo").file(pdf).with(asCustomer(C)))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Issue details are refused for ratings of 4 or 5")
    void noIssueDetailsForHighRating() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        long feedbackId = rate(C, next(C).path("prompt").path("promptId").asLong(), 4).path("feedbackId").asLong();
        assertThat(putIssues(feedbackId, "{\"issueCodes\":[\"HARD\"]}").getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ cart

    @Test
    @DisplayName("Cart: tomorrow by default, any future date allowed, today / >30 days rejected, same day merges")
    void cartDates() throws Exception {
        JsonNode a = addToCart("{\"productId\":\"PANEER\"}", 201);
        assertThat(a.path("item").path("deliveryDate").asText()).isEqualTo(T.plusDays(1).toString());
        assertThat(a.path("message").asText()).contains("tomorrow");

        JsonNode b = addToCart("{\"productId\":\"PANEER\",\"quantity\":2,\"deliveryDate\":\"" + T.plusDays(1) + "\"}", 201);
        assertThat(b.path("item").path("quantity").asInt()).isEqualTo(3);

        addToCart("{\"productId\":\"PANEER\",\"deliveryDate\":\"" + T.plusDays(10) + "\"}", 201);
        addToCart("{\"productId\":\"PANEER\",\"deliveryDate\":\"" + T + "\"}", 400);
        addToCart("{\"productId\":\"PANEER\",\"deliveryDate\":\"" + T.plusDays(31) + "\"}", 400);
        addToCart("{\"productId\":\"NOPE\"}", 404);

        JsonNode cart = getJson(get("/api/cart").with(asCustomer(C)));
        assertThat(cart.path("items").size()).isEqualTo(2);
        assertThat(cart.path("totalQuantity").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("Buy-again add is linked to the feedback and counted on the dashboard")
    void buyAgainLinkedToFeedback() throws Exception {
        deliver(7, PurchaseType.ONE_TIME, "GHEE");
        long feedbackId = rate(C, next(C).path("prompt").path("promptId").asLong(), 5).path("feedbackId").asLong();
        JsonNode add = addToCart("{\"productId\":\"GHEE\",\"feedbackId\":" + feedbackId + "}", 201);
        assertThat(add.path("item").path("source").asText()).isEqualTo("FEEDBACK_BUY_AGAIN");
        addToCart("{\"productId\":\"PANEER\",\"feedbackId\":" + feedbackId + "}", 400);   // wrong product

        JsonNode summary = getJson(get("/api/dashboard/summary"));
        assertThat(summary.path("totals").path("buyAgainAddsToCart").asLong()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ dashboard

    @Test
    @DisplayName("Dashboard groups by product, issue and area")
    void dashboardAggregates() throws Exception {
        deliver(1, PurchaseType.ONE_TIME, "PANEER");
        long fid = rate(C, next(C).path("prompt").path("promptId").asLong(), 1).path("feedbackId").asLong();
        putIssues(fid, "{\"issueCodes\":[\"SMELL\"]}");

        JsonNode s = getJson(get("/api/dashboard/summary"));
        assertThat(s.path("totals").path("feedbackCount").asLong()).isEqualTo(1);
        assertThat(s.path("totals").path("lowRatingCount").asLong()).isEqualTo(1);
        assertThat(s.path("byProduct").get(0).path("productId").asText()).isEqualTo("PANEER");
        assertThat(s.path("byIssue").get(0).path("issueCode").asText()).isEqualTo("SMELL");
        assertThat(s.path("byArea").get(0).path("area").asText()).isEqualTo("Sector 45");

        JsonNode rows = getJson(get("/api/dashboard/feedback").param("issueCode", "SMELL"));
        assertThat(rows.path("totalElements").asLong()).isEqualTo(1);
        JsonNode none = getJson(get("/api/dashboard/feedback").param("productId", "GHEE"));
        assertThat(none.path("totalElements").asLong()).isZero();

        // Row carries pack size and starts as NEW; the quality team's follow-up is saved server-side.
        JsonNode row = rows.path("content").get(0);
        assertThat(row.path("productUnit").asText()).isEqualTo("1 unit");
        assertThat(row.path("followUpStatus").asText()).isEqualTo("NEW");
        MvcResult upd = mvc.perform(put("/api/dashboard/feedback/" + fid + "/follow-up").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\",\"internalNote\":\"  Check Sector 45 batch \"}")).andReturn();
        assertThat(upd.getResponse().getStatus()).isEqualTo(200);
        JsonNode after = getJson(get("/api/dashboard/feedback"));
        assertThat(after.path("content").get(0).path("followUpStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(after.path("content").get(0).path("internalNote").asText()).isEqualTo("Check Sector 45 batch");
        assertThat(after.path("content").get(0).path("followUpUpdatedAt").asText()).isNotEmpty();
        assertThat(mvc.perform(put("/api/dashboard/feedback/" + fid + "/follow-up").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}")).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(put("/api/dashboard/feedback/999999/follow-up").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("No token -> 401; token for a customer that doesn't exist -> 404; dashboard is open")
    void tokenRequired() throws Exception {
        assertThat(mvc.perform(get("/api/feedback-prompts/next")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/cart")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/feedback-prompts/next").with(asCustomer(999)))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/api/dashboard/summary")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ helpers

    /** Pretends the request carries a valid login token for this customer (spring-security-test). */
    static RequestPostProcessor asCustomer(long customerId) {
        return jwt().jwt(j -> j.subject(String.valueOf(customerId)));
    }

    private void product(String id, int delay, String price) {
        products.save(new Product(id, id.charAt(0) + id.substring(1).toLowerCase(), "1 unit", new BigDecimal(price), delay, null));
    }

    private void subscribe(String productId, SubscriptionStatus status) {
        subscriptions.save(new Subscription(C, productId, status, T.minusDays(60), null));
    }

    /** Delivers one item {@code daysAgo} days before T (negative = in the future relative to T). */
    private OrderItem deliver(int daysAgo, PurchaseType type, String productId) {
        CustomerOrder o = new CustomerOrder(C, T.minusDays(daysAgo), OrderStatus.DELIVERED, "Sector 45", "Gurugram");
        OrderItem item = o.addItem(productId, 1, BigDecimal.TEN, type);
        orders.save(o);
        return item;
    }

    private JsonNode next(long customerId) throws Exception {
        return getJson(get("/api/feedback-prompts/next").with(asCustomer(customerId)));
    }

    private MockHttpServletRequestBuilder ratingRequest(long customerId, long promptId, int rating) {
        return post("/api/feedback-prompts/" + promptId + "/rating")
                .with(asCustomer(customerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":" + rating + "}");
    }

    private JsonNode rate(long customerId, long promptId, int rating) throws Exception {
        return getJson(ratingRequest(customerId, promptId, rating));
    }

    private MvcResult putIssues(long feedbackId, String body) throws Exception {
        return mvc.perform(put("/api/feedback/" + feedbackId + "/issues")
                .with(asCustomer(C)).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private JsonNode addToCart(String body, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(post("/api/cart/items").with(asCustomer(C))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(expectedStatus);
        return json.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode getJson(MockHttpServletRequestBuilder req) throws Exception {
        MvcResult r = mvc.perform(req).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString());
    }

    private void assertHidden(JsonNode r, String reason) {
        assertThat(r.path("show").asBoolean()).as(r.toString()).isFalse();
        assertThat(r.path("reasonCode").asText()).isEqualTo(reason);
    }
}
