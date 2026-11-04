package com.countrydelight.feedback.seed;

import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.domain.Address;
import com.countrydelight.feedback.domain.CartItem;
import com.countrydelight.feedback.domain.CartItemSource;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.domain.CustomerOrder;
import com.countrydelight.feedback.domain.Feedback;
import com.countrydelight.feedback.domain.FeedbackPrompt;
import com.countrydelight.feedback.domain.IssueOption;
import com.countrydelight.feedback.domain.OrderItem;
import com.countrydelight.feedback.domain.OrderStatus;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.domain.PromptReason;
import com.countrydelight.feedback.domain.PromptStatus;
import com.countrydelight.feedback.domain.PurchaseType;
import com.countrydelight.feedback.domain.Subscription;
import com.countrydelight.feedback.domain.SubscriptionFrequency;
import com.countrydelight.feedback.domain.SubscriptionStatus;
import com.countrydelight.feedback.repository.CartItemRepository;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.FeedbackPromptRepository;
import com.countrydelight.feedback.repository.FeedbackRepository;
import com.countrydelight.feedback.repository.IssueOptionRepository;
import com.countrydelight.feedback.repository.OrderRepository;
import com.countrydelight.feedback.repository.ProductRepository;
import com.countrydelight.feedback.repository.SubscriptionRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Test data, generated relative to "today" so the demo always works:
 * <ul>
 *   <li>Products with X days and product-specific issue chips.</li>
 *   <li>Demo households 101-110, one per tricky scenario (see demoScenario text / README).</li>
 *   <li>~360 historical ratings across 6 areas with 3 planted quality patterns for the dashboard.</li>
 * </ul>
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProductRepository products;
    private final IssueOptionRepository issueOptions;
    private final CustomerRepository customers;
    private final SubscriptionRepository subscriptions;
    private final OrderRepository orders;
    private final FeedbackPromptRepository prompts;
    private final FeedbackRepository feedbacks;
    private final CartItemRepository cart;
    private final EntityManager em;
    private final TimeProvider time;
    private final boolean enabled;

    private final Map<String, Product> productMap = new LinkedHashMap<>();
    private LocalDate today;
    /** Each customer's default (Home) address, used for their seeded orders. */
    private final Map<Long, Address> homeAddress = new java.util.HashMap<>();

    public DataSeeder(ProductRepository products, IssueOptionRepository issueOptions, CustomerRepository customers,
                      SubscriptionRepository subscriptions, OrderRepository orders, FeedbackPromptRepository prompts,
                      FeedbackRepository feedbacks, CartItemRepository cart, EntityManager em, TimeProvider time,
                      @Value("${app.seed.enabled:true}") boolean enabled) {
        this.products = products;
        this.issueOptions = issueOptions;
        this.customers = customers;
        this.subscriptions = subscriptions;
        this.orders = orders;
        this.prompts = prompts;
        this.feedbacks = feedbacks;
        this.cart = cart;
        this.em = em;
        this.time = time;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (enabled) {
            seed();
        }
    }

    /** Wipes everything and seeds again relative to the current (possibly overridden) date. */
    @Transactional
    public void reseed() {
        wipe();
        seed();
    }

    @Transactional
    public void wipe() {
        cart.deleteAllInBatch();
        em.createQuery("delete from Address").executeUpdate();
        em.createQuery("delete from OtpChallenge").executeUpdate();
        feedbacks.deleteAll();
        prompts.deleteAllInBatch();
        orders.deleteAll();
        subscriptions.deleteAllInBatch();
        issueOptions.deleteAllInBatch();
        customers.deleteAllInBatch();
        products.deleteAllInBatch();
        em.flush();
        em.clear();
        productMap.clear();
        homeAddress.clear();
    }

    private void seed() {
        today = time.today();
        seedCatalog();
        seedDemoHouseholds();
        int n = seedHistory();
        log.info("Seeded demo data relative to {}: 10 demo households, {} historical ratings", today, n);
    }

    // ------------------------------------------------------------------ catalog

    private void seedCatalog() {
        // Images: resources/static/products/* (served at /products/*). Prices and texts are illustrative demo data.
        product("PANEER", "Fresh Paneer", "200 g", "95", 1, "paneer.jpg", "Dairy",
                "Soft malai paneer made fresh from cow milk. Best used within 2 days.");
        product("DAHI", "Fresh Dahi", "400 g", "45", 1, "dahi.jpg", "Dairy",
                "Thick, set curd with a mild taste. Keep refrigerated.");
        product("BREAD", "Whole Wheat Bread", "400 g", "50", 2, "bread.jpg", "Bakery",
                "Soft whole wheat loaf, baked daily.");
        product("ATTA", "Chakki Atta", "5 kg", "285", 5, "atta.png", "Staples",
                "Stone-ground whole wheat flour for soft rotis.");
        product("GHEE", "Desi Cow Ghee", "500 ml", "375", 7, "ghee.jpg", "Dairy",
                "Slow-cooked from cow milk butter, with a rich aroma.");
        // Mostly bought on subscription; no X configured, so we never ask (shows FEEDBACK_NOT_CONFIGURED).
        product("MILK", "Cow Milk", "500 ml", "34", null, "milk.png", "Dairy",
                "Fresh cow milk, delivered every morning.");
        // Atta and honey are bought once in a while, not on a daily plan.
        productMap.get("ATTA").setSubscribable(false);
        // Extra catalog products (each with its own X and issue chips).
        product("BUTTER", "White Butter", "100 g", "60", 3, "butter.png", "Dairy",
                "Unsalted white butter churned from cow milk cream.");
        product("EGGS", "Farm Eggs", "6 pcs", "72", 2, "eggs.png", "Eggs",
                "Brown farm eggs, packed in a protective tray.");
        product("HONEY", "Raw Honey", "250 g", "199", 7, "honey.png", "Staples",
                "Unprocessed raw honey. May crystallise naturally in cold weather.");
        product("COCONUT_WATER", "Tender Coconut Water", "200 ml", "45", 1, "coconut.png", "Beverages",
                "Fresh tender coconut water. Best chilled, drink within a day.");
        productMap.get("HONEY").setSubscribable(false);

        issues("PANEER", "HARD:Too hard / rubbery", "SMELL:Bad smell", "NEAR_EXPIRY:Close to expiry",
                "SOUR:Sour taste", "CRUMBLY:Crumbles / breaks", "PACKAGING:Packet damaged", "OTHER:Something else");
        issues("DAHI", "TOO_SOUR:Too sour", "WATERY:Watery / not set", "SMELL:Bad smell",
                "NEAR_EXPIRY:Close to expiry", "LEAKING:Cup leaking", "OTHER:Something else");
        issues("BREAD", "STALE:Stale / dry", "HARD:Too hard", "MOULD:Mould / fungus",
                "NEAR_EXPIRY:Close to expiry", "SQUASHED:Squashed packet", "OTHER:Something else");
        issues("ATTA", "BUGS:Insects / bugs", "LUMPY:Lumpy / damp", "SMELL:Musty smell",
                "ROTI_HARD:Rotis turn hard", "BAG_TORN:Bag torn", "OTHER:Something else");
        issues("GHEE", "WEAK_AROMA:Weak aroma", "RANCID:Off / rancid taste", "TEXTURE:Grainy / odd texture",
                "LEAKING:Jar leaking", "NEAR_EXPIRY:Close to expiry", "OTHER:Something else");
        issues("BUTTER", "RANCID:Off / rancid taste", "SMELL:Bad smell", "MELTED:Melted on arrival",
                "NEAR_EXPIRY:Close to expiry", "PACKAGING:Wrapper torn", "OTHER:Something else");
        issues("EGGS", "CRACKED:Cracked / broken", "NOT_FRESH:Not fresh", "SMELL:Bad smell",
                "SMALL:Too small", "DIRTY:Dirty shells", "OTHER:Something else");
        issues("HONEY", "TASTE:Off taste", "TOO_THIN:Too runny / watery", "CRYSTALLISED:Crystallised",
                "LEAKING:Jar leaking", "OTHER:Something else");
        issues("COCONUT_WATER", "SOUR:Sour / fermented", "NOT_FRESH:Not fresh", "TASTE:Bland / off taste",
                "LEAKING:Bottle leaking", "NEAR_EXPIRY:Close to expiry", "OTHER:Something else");
    }

    private void product(String id, String name, String unit, String price, Integer delay, String image,
                         String category, String description) {
        Product p = products.save(new Product(id, name, unit, new BigDecimal(price), delay,
                "/products/" + image, description, category));
        productMap.put(id, p);
    }

    private void issues(String productId, String... codeLabels) {
        int order = 1;
        for (String cl : codeLabels) {
            String[] parts = cl.split(":", 2);
            issueOptions.save(new IssueOption(productId, parts[0], parts[1], order++));
        }
    }

    // ------------------------------------------------------------------ demo households (one per scenario)

    private void seedDemoHouseholds() {
        // 101: first-ever order, paneer yesterday -> paneer card with NEW_TO_HOUSEHOLD + FIRST_TIME_CUSTOMER.
        Customer asha = customer(101L, "Asha", "Sector 45", "Gurugram",
                "First-time customer. Paneer delivered yesterday (X=1) -> Paneer card shows today.");
        oneTime(asha, 1, "PANEER", 1);

        // 102: subscribed to Dahi, plus a one-time extra Dahi -> never asked.
        Customer rohan = customer(102L, "Rohan", "HSR Layout", "Bengaluru",
                "Subscribed to Dahi + Milk. Also bought one extra Dahi yesterday -> no card (subscribed product).");
        subscribe(rohan, "DAHI", SubscriptionStatus.ACTIVE, 60, 1, SubscriptionFrequency.DAILY);
        subscribe(rohan, "MILK", SubscriptionStatus.ACTIVE, 60, 2, SubscriptionFrequency.DAILY);
        for (int d = 3; d >= 1; d--) {
            CustomerOrder o = order(rohan, d);
            item(o, "DAHI", 1, PurchaseType.SUBSCRIPTION);
            item(o, "MILK", 2, PurchaseType.SUBSCRIPTION);
            if (d == 1) {
                item(o, "DAHI", 1, PurchaseType.ONE_TIME);
            }
            orders.save(o);
        }

        // 103: bought paneer twice before rating -> ONE card, about the latest delivery.
        Customer meera = customer(103L, "Meera", "Indiranagar", "Bengaluru",
                "Bought Paneer 3 days ago and again yesterday, never rated -> one card about yesterday's Paneer.");
        oneTime(meera, 20, "MILK", 1);
        oneTime(meera, 3, "PANEER", 1);
        oneTime(meera, 1, "PANEER", 2);

        // 104: rated Dahi yesterday -> frequency cap blocks the Ghee that is due today.
        Customer vikram = customer(104L, "Vikram", "DLF Phase 3", "Gurugram",
                "Rated Dahi yesterday. Ghee is due today but frequency cap (3 days) holds it -> no card until today+2.");
        OrderItem vikramDahi = oneTime(vikram, 3, "DAHI", 1);
        answeredHistory(vikram, vikramDahi, 2, 1, 5, Set.of(), null);
        oneTime(vikram, 7, "GHEE", 1);

        // 105: skipped Atta before -> new Atta not asked; Bread asked instead.
        Customer neha = customer(105L, "Neha", "Koramangala", "Bengaluru",
                "Skipped the Atta card 4 days ago. New Atta is due today but suppressed -> Bread card shows instead.");
        OrderItem nehaAtta = oneTime(neha, 10, "ATTA", 1);
        FeedbackPrompt skipped = prompt(neha, nehaAtta, 5);
        skipped.resolve(PromptStatus.SKIPPED, today.minusDays(4), time.startOf(today.minusDays(4)));
        prompts.save(skipped);
        oneTime(neha, 5, "ATTA", 1);
        oneTime(neha, 2, "BREAD", 1);

        // 106: first-time Ghee due today -> use it to test "rating exactly 4" -> buy again.
        Customer kabir = customer(106L, "Kabir", "Sohna Road", "Gurugram",
                "Ghee delivered 7 days ago (X=7) -> Ghee card today. Rate it exactly 4 to see the buy-again flow.");
        oneTime(kabir, 7, "GHEE", 1);

        // 107: Atta not due yet.
        Customer ananya = customer(107L, "Ananya", "HSR Layout", "Bengaluru",
                "Atta delivered 2 days ago (X=5) -> nothing yet. Move the clock +3 days to see the card.");
        oneTime(ananya, 2, "ATTA", 1);

        // 108: two products due: repeat Bread vs brand-new Ghee -> Ghee wins (new to household).
        Customer priya = customer(108L, "Priya", "Indiranagar", "Bengaluru",
                "Bread (bought before) and Ghee (first time) both due today -> Ghee is asked first.");
        oneTime(priya, 30, "BREAD", 1);
        oneTime(priya, 2, "BREAD", 1);
        oneTime(priya, 7, "GHEE", 1);

        // 109: ignored a Paneer card for 3 days -> marked IGNORED today, cap kicks in.
        Customer arjun = customer(109L, "Arjun", "Sector 45", "Gurugram",
                "Saw a Paneer card on 3 days and never tapped -> becomes IGNORED today; Dahi waits for the cap.");
        OrderItem arjunPaneer = oneTime(arjun, 4, "PANEER", 1);
        FeedbackPrompt ignoredSoon = prompt(arjun, arjunPaneer, 3);
        ignoredSoon.recordImpression(today.minusDays(2));
        ignoredSoon.recordImpression(today.minusDays(1));
        prompts.save(ignoredSoon);
        oneTime(arjun, 1, "DAHI", 1);

        // 110: paused Ghee subscription counts as subscribed.
        Customer sara = customer(110L, "Sara", "DLF Phase 3", "Gurugram",
                "Ghee subscription is PAUSED and she bought one Ghee jar 7 days ago -> no card (still a subscriber).");
        subscribe(sara, "GHEE", SubscriptionStatus.PAUSED, 90, 1, SubscriptionFrequency.ALTERNATE_DAYS);
        oneTime(sara, 7, "GHEE", 1);

        seedPresentationAccounts();
    }

    /**
     * Three easy-to-explain test accounts for the demo (phone 9000000 + id, OTP 123456).
     * 201 = subscriber (never asked), 202 = happy customer (gets buy-again), 203 = unhappy customer (low-rated
     * products are not recommended).
     */
    private void seedPresentationAccounts() {
        // 201 Ravi: subscriber. Never asked for feedback on subscribed products, and they're never recommended.
        Customer ravi = customer(201L, "Ravi", "Sector 45", "Gurugram",
                "SUBSCRIBER. Subscribed to Milk, Dahi and Paneer. Got a one-time Paneer extra yesterday -> no feedback "
                        + "card. He rated Paneer 5★ before subscribing, but buy-again leaves it out (SUBSCRIBED).");
        OrderItem raviOldPaneer = oneTime(ravi, 40, "PANEER", 1);
        answeredHistory(ravi, raviOldPaneer, 39, 39, 5, Set.of(), null);
        subscribe(ravi, "MILK", SubscriptionStatus.ACTIVE, 30, 2, SubscriptionFrequency.DAILY);
        subscribe(ravi, "DAHI", SubscriptionStatus.ACTIVE, 30, 1, SubscriptionFrequency.DAILY);
        subscribe(ravi, "PANEER", SubscriptionStatus.ACTIVE, 30, 1, SubscriptionFrequency.ALTERNATE_DAYS);
        for (int d = 3; d >= 1; d--) {
            CustomerOrder o = order(ravi, d);
            item(o, "MILK", 2, PurchaseType.SUBSCRIPTION);
            item(o, "DAHI", 1, PurchaseType.SUBSCRIPTION);
            item(o, "PANEER", 1, PurchaseType.SUBSCRIPTION);
            if (d == 1) {
                item(o, "PANEER", 1, PurchaseType.ONE_TIME);     // extra one-time paneer: still not asked
            }
            orders.save(o);
        }

        // 202 Anjali: happy customer. Rated Ghee 5★ and Paneer 4★ -> both recommended to buy again.
        Customer anjali = customer(202L, "Anjali", "Indiranagar", "Bengaluru",
                "HAPPY CUSTOMER. Rated Ghee 5★ and Paneer 4★ -> buy-again recommends both. Dahi delivered yesterday "
                        + "-> Dahi feedback card shows today.");
        OrderItem anjaliGhee = oneTime(anjali, 15, "GHEE", 1);
        answeredHistory(anjali, anjaliGhee, 8, 8, 5, Set.of(), null);
        OrderItem anjaliPaneer = oneTime(anjali, 7, "PANEER", 1);
        answeredHistory(anjali, anjaliPaneer, 6, 6, 4, Set.of(), null);
        oneTime(anjali, 1, "DAHI", 1);

        // 203 Karan: unhappy with some products. Bread 2★, Atta 3★ -> NOT recommended. Eggs 5★ -> recommended.
        Customer karan = customer(203L, "Karan", "HSR Layout", "Bengaluru",
                "MIXED RATINGS. Bread 2★ (stale) and Atta 3★ (lumpy) are NOT recommended; Eggs 5★ is. Coconut water "
                        + "delivered yesterday -> its feedback card shows today.");
        OrderItem karanBread = oneTime(karan, 10, "BREAD", 1);
        answeredHistory(karan, karanBread, 8, 8, 2, Set.of("STALE"), "Bread was dry on the first day");
        OrderItem karanAtta = oneTime(karan, 14, "ATTA", 1);
        answeredHistory(karan, karanAtta, 9, 9, 3, Set.of("LUMPY"), null);
        OrderItem karanEggs = oneTime(karan, 6, "EGGS", 1);
        answeredHistory(karan, karanEggs, 4, 4, 5, Set.of(), null);
        oneTime(karan, 1, "COCONUT_WATER", 1);

        // 204 Pooja: subscribed to Dahi (and Milk) but ordered Paneer once -> asked about Paneer only, never Dahi.
        Customer pooja = customer(204L, "Pooja", "Koramangala", "Bengaluru",
                "SUBSCRIBED + ORDERED. Subscribed to Dahi and Milk. Yesterday she also got Paneer (one-time, not "
                        + "subscribed) and an extra Dahi (one-time) -> card asks about Paneer only; Dahi is never asked.");
        subscribe(pooja, "DAHI", SubscriptionStatus.ACTIVE, 45, 1, SubscriptionFrequency.DAILY);
        subscribe(pooja, "MILK", SubscriptionStatus.ACTIVE, 45, 1, SubscriptionFrequency.DAILY);
        for (int d = 2; d >= 1; d--) {
            CustomerOrder o = order(pooja, d);
            item(o, "MILK", 1, PurchaseType.SUBSCRIPTION);
            item(o, "DAHI", 1, PurchaseType.SUBSCRIPTION);
            if (d == 1) {
                item(o, "PANEER", 1, PurchaseType.ONE_TIME);   // not subscribed -> asked
                item(o, "DAHI", 1, PurchaseType.ONE_TIME);     // subscribed product, even bought once -> not asked
            }
            orders.save(o);
        }
    }

    // ------------------------------------------------------------------ history for the dashboard

    private static final String[][] AREAS = {
            {"Sector 45", "Gurugram"}, {"DLF Phase 3", "Gurugram"}, {"Sohna Road", "Gurugram"},
            {"Indiranagar", "Bengaluru"}, {"Koramangala", "Bengaluru"}, {"HSR Layout", "Bengaluru"}};

    private static final String[] NAMES = {"Aarav", "Diya", "Ishaan", "Kavya", "Reyansh", "Saanvi", "Vihaan",
            "Anika", "Advik", "Myra", "Krish", "Aadhya", "Shaurya", "Navya", "Atharv", "Pari", "Dhruv", "Riya",
            "Arnav", "Tara"};

    private static final Map<String, String[]> NOTES = Map.of(
            "PANEER", new String[]{"Smelled sour when I opened it", "Very rubbery after cooking",
                    "Expiry was the next day", "Packet was puffed up"},
            "DAHI", new String[]{"Way too sour for the kids", "Did not set properly", "Cup lid was open"},
            "BREAD", new String[]{"Bread was dry on day one", "Spots of mould on two slices", "Squashed in the bag"},
            "ATTA", new String[]{"Found small insects", "Lumps in the atta", "Rotis go hard quickly"},
            "GHEE", new String[]{"Hardly any aroma", "Tastes a bit off", "Jar was sticky outside"});

    private int seedHistory() {
        Random rnd = new Random(42);
        List<Customer> households = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String[] a = AREAS[i % AREAS.length];
            households.add(insertCustomer(1001L + i, NAMES[i % NAMES.length] + " " + (char) ('A' + i / NAMES.length),
                    "98" + String.format("%08d", 10000000 + i * 7919), a[0], a[1], null));
        }
        String[] productPick = {"PANEER", "PANEER", "PANEER", "DAHI", "DAHI", "DAHI", "BREAD", "BREAD",
                "ATTA", "ATTA", "GHEE", "GHEE"};

        int created = 0;
        for (int n = 0; n < 360; n++) {
            Customer c = households.get(rnd.nextInt(households.size()));
            String pid = productPick[rnd.nextInt(productPick.length)];
            int delay = productMap.get(pid).getFeedbackDelayDays();
            int daysAgo = 2 + rnd.nextInt(32);
            LocalDate delivery = today.minusDays(daysAgo);
            LocalDate answered = delivery.plusDays(delay + rnd.nextInt(2));
            if (answered.isAfter(today)) {
                continue;
            }

            // Planted patterns the dashboard should surface:
            double lowProb = 0.12;
            Map<String, Integer> issueWeights = defaultIssueWeights(pid);
            if (pid.equals("PANEER") && c.getArea().equals("Sector 45")) {
                lowProb = 0.6;          // HOTSPOT: paneer smell / near-expiry in Sector 45
                issueWeights = Map.of("SMELL", 6, "NEAR_EXPIRY", 3, "SOUR", 1);
            } else if (pid.equals("BREAD") && c.getArea().equals("Indiranagar") && daysAgo <= 9) {
                lowProb = 0.75;         // ISSUE_SPIKE: stale bread in Indiranagar, last week only
                issueWeights = Map.of("STALE", 8, "HARD", 2);
            } else if (pid.equals("DAHI") && c.getArea().equals("Koramangala")) {
                lowProb = 0.45;         // HOTSPOT: sour dahi in Koramangala
                issueWeights = Map.of("TOO_SOUR", 7, "WATERY", 2, "OTHER", 1);
            }

            boolean low = rnd.nextDouble() < lowProb;
            int rating = low ? 1 + rnd.nextInt(3) : (rnd.nextDouble() < 0.6 ? 5 : 4);
            Set<String> issues = new LinkedHashSet<>();
            String note = null;
            if (low && rnd.nextDouble() < 0.9) {    // ~10% of low raters abandon the details step
                issues.add(weightedPick(issueWeights, rnd));
                if (rnd.nextDouble() < 0.25) {
                    issues.add(weightedPick(defaultIssueWeights(pid), rnd));
                }
                if (rnd.nextDouble() < 0.4) {
                    String[] notes = NOTES.get(pid);
                    note = notes[rnd.nextInt(notes.length)];
                }
            }

            CustomerOrder o = order(c, daysAgo);
            OrderItem item = item(o, pid, 1, PurchaseType.ONE_TIME);
            orders.save(o);
            Feedback f = answeredHistory(c, item, (int) (today.toEpochDay() - delivery.plusDays(delay).toEpochDay()),
                    (int) (today.toEpochDay() - answered.toEpochDay()), rating, issues, note);
            if (!low && rnd.nextDouble() < 0.3) {
                LocalDate addedOn = answered;
                cart.save(new CartItem(c.getId(), pid, 1, addedOn.plusDays(1), CartItemSource.FEEDBACK_BUY_AGAIN,
                        f.getId(), time.startOf(addedOn)));
            }
            created++;
        }

        // ISSUE_SPIKE, made explicit: a bad bread batch in Indiranagar during the last week.
        List<Customer> indiranagar = households.stream().filter(c -> c.getArea().equals("Indiranagar")).toList();
        for (int k = 0; k < 7; k++) {
            Customer c = indiranagar.get(k % indiranagar.size());
            int daysAgo = 3 + (k % 5);   // delivered 3..7 days ago, so X=2 has passed
            CustomerOrder o = order(c, daysAgo);
            OrderItem item = item(o, "BREAD", 1, PurchaseType.ONE_TIME);
            orders.save(o);
            answeredHistory(c, item, daysAgo - 2, daysAgo - 2, 1 + (k % 2), Set.of("STALE"),
                    k % 3 == 0 ? "Bread was dry on day one" : null);
            created++;
        }
        return created;
    }

    private Map<String, Integer> defaultIssueWeights(String pid) {
        return switch (pid) {
            case "PANEER" -> Map.of("HARD", 3, "SMELL", 1, "NEAR_EXPIRY", 2, "CRUMBLY", 2, "PACKAGING", 1, "OTHER", 1);
            case "DAHI" -> Map.of("TOO_SOUR", 2, "WATERY", 3, "LEAKING", 2, "NEAR_EXPIRY", 1, "OTHER", 1);
            case "BREAD" -> Map.of("STALE", 2, "HARD", 2, "SQUASHED", 3, "MOULD", 1, "OTHER", 1);
            case "ATTA" -> Map.of("LUMPY", 3, "ROTI_HARD", 2, "BAG_TORN", 2, "BUGS", 1, "OTHER", 1);
            case "GHEE" -> Map.of("WEAK_AROMA", 3, "TEXTURE", 2, "LEAKING", 2, "RANCID", 1, "OTHER", 1);
            default -> Map.of("OTHER", 1);
        };
    }

    private static String weightedPick(Map<String, Integer> weights, Random rnd) {
        List<String> keys = new ArrayList<>(weights.keySet());
        keys.sort(String::compareTo);      // Map.of iteration order is unspecified; sort for repeatable seeds
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int r = rnd.nextInt(total);
        for (String k : keys) {
            r -= weights.get(k);
            if (r < 0) {
                return k;
            }
        }
        return keys.get(keys.size() - 1);
    }

    // ------------------------------------------------------------------ small builders

    /** Demo households log in with phone 9000000 + id, e.g. 9000000106, and OTP 123456. */
    private Customer customer(Long id, String name, String area, String city, String scenario) {
        Customer c = insertCustomer(id, name, "9000000" + id, area, city, scenario);
        addAddressBook(c, id % 2 == 0 ? 5 : 4);      // demo accounts: 4-5 addresses
        return c;
    }

    /** Fixed ids keep the demo scenarios stable (106 is always Kabir). Signups use the sequence instead. */
    private Customer insertCustomer(Long id, String name, String phone, String area, String city, String scenario) {
        customers.insertWithId(id, name, phone, area, city, scenario, time.startOf(today.minusDays(60)));
        Customer c = customers.findById(id).orElseThrow();
        addHome(c);                                    // everyone has a default Home address
        return c;
    }

    // ------------------------------------------------------------------ addresses

    private record Place(String area, String pincode, String street, String landmark) {
    }

    /** Real area names and pincodes; streets and landmarks are illustrative. */
    private static final Map<String, List<Place>> PLACES = Map.of(
            "Gurugram", List.of(
                    new Place("Sector 45", "122003", "Huda Market Road", "Sector 45 Community Centre"),
                    new Place("DLF Phase 3", "122010", "U Block, Nathupur Road", "Cyber Hub Gate 2"),
                    new Place("Sohna Road", "122018", "Badshahpur Road", "Vatika Chowk"),
                    new Place("Sector 56", "122011", "Golf Course Extension Road", "Sector 56 HUDA Market"),
                    new Place("Golf Course Road", "122002", "DLF Phase 5", "Sector 54 Chowk Metro"),
                    new Place("Sector 14", "122001", "Old Delhi Road", "Sector 14 Market")),
            "Bengaluru", List.of(
                    new Place("Indiranagar", "560038", "100 Feet Road, HAL 2nd Stage", "Indiranagar Metro"),
                    new Place("Koramangala", "560034", "80 Feet Road, 4th Block", "Forum Mall"),
                    new Place("HSR Layout", "560102", "27th Main, Sector 2", "BDA Complex"),
                    new Place("Whitefield", "560066", "ITPL Main Road", "Phoenix Marketcity"),
                    new Place("Jayanagar", "560041", "11th Main, 4th Block", "Jayanagar Shopping Complex"),
                    new Place("Bellandur", "560103", "Outer Ring Road", "Ecospace")));

    private static final String[] BUILDINGS = {"Sunshine Residency", "Green Meadows", "Palm Grove Apartments",
            "Lake View Towers", "Silver Oak Enclave", "Royal Gardens"};
    private static final Map<String, String> OFFICES = Map.of(
            "Gurugram", "Cyber City Building 10", "Bengaluru", "Embassy Tech Village Block C");
    private static final String[] EXTRA_LABELS = {"Work", "Parents' home", "Weekend home", "Friend's place"};

    private Place place(String city, String area) {
        return PLACES.getOrDefault(city, List.of()).stream().filter(p -> p.area().equals(area)).findFirst()
                .orElse(new Place(area, null, null, null));
    }

    private void addHome(Customer c) {
        Place p = place(c.getCity(), c.getArea());
        long id = c.getId();
        Address home = new Address(id, "Home",
                "Flat " + (101 + (id * 37) % 900) + ", Tower " + (char) ('A' + id % 4) + ", " + BUILDINGS[(int) (id % BUILDINGS.length)],
                p.street(), p.landmark(), c.getArea(), c.getCity(), p.pincode(), true, time.startOf(today.minusDays(60)));
        em.persist(home);
        homeAddress.put(id, home);
    }

    /** Home + (total - 1) more addresses in other areas of the same city. */
    private void addAddressBook(Customer c, int total) {
        List<Place> others = PLACES.getOrDefault(c.getCity(), List.of()).stream()
                .filter(p -> !p.area().equals(c.getArea())).toList();
        long id = c.getId();
        for (int k = 0; k < total - 1 && k < others.size(); k++) {
            Place p = others.get((int) ((id + 2L * k) % others.size()));
            String line1 = k == 0
                    ? "Floor " + (3 + id % 9) + ", " + OFFICES.getOrDefault(c.getCity(), "Business Park")
                    : "House " + (10 + (id * 7 + k * 13) % 90) + ", " + ordinal(1 + (int) ((id + k) % 9)) + " Cross";
            em.persist(new Address(id, EXTRA_LABELS[k], line1, p.street(), p.landmark(), p.area(), c.getCity(),
                    p.pincode(), false, time.startOf(today.minusDays(50 - k))));
        }
    }

    private static String ordinal(int n) {
        return n + (n == 1 ? "st" : n == 2 ? "nd" : n == 3 ? "rd" : "th");
    }

    /**
     * A plan that has been running for a while. Its past deliveries are seeded as orders up to yesterday;
     * today's delivery onwards is created by SubscriptionDeliveryService (lastGeneratedOn = yesterday).
     */
    private void subscribe(Customer c, String productId, SubscriptionStatus status, int startedDaysAgo, int quantity,
                           SubscriptionFrequency frequency) {
        Subscription s = new Subscription(c.getId(), productId, status, today.minusDays(startedDaysAgo), null);
        s.setPlan(quantity, frequency);
        s.deliverTo(homeAddress.get(c.getId()));
        s.markGeneratedUpTo(today.minusDays(1));
        subscriptions.save(s);
    }

    private CustomerOrder order(Customer c, int daysAgo) {
        CustomerOrder o = new CustomerOrder(c.getId(), today.minusDays(daysAgo), OrderStatus.DELIVERED, c.getArea(), c.getCity());
        Address home = homeAddress.get(c.getId());
        if (home != null) {
            o.deliverTo(home);                         // seeded orders go to the Home address
        }
        return o;
    }

    private OrderItem item(CustomerOrder o, String productId, int qty, PurchaseType type) {
        return o.addItem(productId, qty, productMap.get(productId).getPrice(), type);
    }

    private OrderItem oneTime(Customer c, int daysAgo, String productId, int qty) {
        CustomerOrder o = order(c, daysAgo);
        OrderItem item = item(o, productId, qty, PurchaseType.ONE_TIME);
        orders.save(o);
        return item;
    }

    /** A prompt first shown {@code shownDaysAgo} days ago, still PENDING. */
    private FeedbackPrompt prompt(Customer c, OrderItem item, int shownDaysAgo) {
        Product p = productMap.get(item.getProductId());
        LocalDate due = item.deliveryDate().plusDays(p.getFeedbackDelayDays());
        LocalDate shown = today.minusDays(shownDaysAgo);
        FeedbackPrompt prompt = new FeedbackPrompt(c.getId(), p.getId(), item.getId(), item.getOrder().getId(),
                item.deliveryDate(), due, due.plusDays(3), c.getArea(), c.getCity(),
                List.of(PromptReason.ONE_TIME_PURCHASE), PromptReason.ONE_TIME_PURCHASE.weight(), time.startOf(shown));
        prompt.recordImpression(shown);
        return prompt;
    }

    private Feedback answeredHistory(Customer c, OrderItem item, int shownDaysAgo, int answeredDaysAgo, int rating,
                                     Set<String> issues, String note) {
        FeedbackPrompt prompt = prompt(c, item, shownDaysAgo);
        LocalDate answered = today.minusDays(answeredDaysAgo);
        Instant at = time.startOf(answered);
        prompt.resolve(PromptStatus.ANSWERED, answered, at);
        prompts.save(prompt);
        Feedback f = new Feedback(prompt, productMap.get(item.getProductId()).getName(), item.getPurchaseType(),
                rating, at, answered);
        if (!issues.isEmpty()) {
            f.setIssueDetails(issues, note, null, at);
        }
        return feedbacks.save(f);
    }
}
