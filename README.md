# Product Feedback After Delivery: Backend

Spring Boot 3 (Java 21) service that decides **when and whom to ask** for product feedback, records the
one-tap rating, routes to **buy again** (rating 4–5) or **issue capture** (rating 1–3), and serves
**dashboard data** for the quality team.

The app shows only what this backend sends. The backend returns **at most one card**, and every response
includes `nextStep` so the app knows which screen comes next.

---

## Run it

```bash
cd backend
mvn spring-boot:run              # http://localhost:8081   (PORT=xxxx to change)
mvn test                         # 25 integration tests covering the tricky cases
```

- In-memory H2 database, seeded on every start **relative to today**, so the demo always lines up.
- Quality dashboard page: **http://localhost:8081/dashboard.html**
- DB console: http://localhost:8081/h2-console (JDBC URL `jdbc:h2:mem:feedback`, user `sa`)
- Ready-made calls: [`requests.http`](requests.http) (VS Code REST Client / IntelliJ HTTP client)

The logged-in household is passed as the header **`X-Customer-Id`** (stand-in for the real auth token).

---

## Demo households (seeded)

| ID | Name | Scenario | `GET /next` today |
|----|------|----------|-------------------|
| 101 | Asha | First-ever order, Paneer delivered yesterday | **Paneer** card (`NEW_TO_HOUSEHOLD`, `FIRST_TIME_CUSTOMER`) |
| 102 | Rohan | Subscribed to Dahi + Milk, plus a one-time extra Dahi | nothing (**subscribed product**) |
| 103 | Meera | Paneer bought 3 days ago and again yesterday, never rated | **one** Paneer card, about **yesterday's** delivery |
| 104 | Vikram | Rated Dahi yesterday; Ghee due today | nothing (**FREQUENCY_CAP**); Ghee shows at today+2 |
| 105 | Neha | Skipped Atta 4 days ago; new Atta due; Bread due | **Bread** card (Atta suppressed after **skip**) |
| 106 | Kabir | First-time Ghee delivered 7 days ago | **Ghee** card. Rate **exactly 4** → BUY_AGAIN |
| 107 | Ananya | Atta delivered 2 days ago (X = 5) | nothing; move clock +3 → Atta card |
| 108 | Priya | Repeat Bread + first-time Ghee both due | **Ghee** first (new to household beats repeat) |
| 109 | Arjun | Paneer card seen on 3 days, never tapped | Paneer → **IGNORED**; cap until today+2, then Dahi |
| 110 | Sara | Ghee subscription **paused**, bought a Ghee jar | nothing (paused = still subscribed) |

Plus 60 households in 6 areas (Gurugram: Sector 45, DLF Phase 3, Sohna Road; Bengaluru: Indiranagar,
Koramangala, HSR Layout) with ~350 past ratings. Three quality problems are planted for the dashboard to find:

- Paneer smell in **Sector 45**
- Stale bread spike in **Indiranagar** last week
- Sour dahi in **Koramangala**

**Testing helpers** (no auth, demo only):

```
POST   /api/admin/clock                 {"plusDays": 2}  or  {"date": "2026-11-10"}
DELETE /api/admin/clock                 back to the real date
POST   /api/admin/reset-demo-data       wipe + reseed relative to the current (simulated) date
GET    /api/admin/customers             demo households and their scenario
GET    /api/admin/customers/{id}/eligibility   why this household does / doesn't get a card (read-only)
PUT    /api/admin/products/{id}/feedback-delay  {"days": 3}   change X
```

---

## The rules

### X days per product

| Product | X | Why |
|---------|---|-----|
| Paneer | 1 | Eaten within a day or two; shelf life is short |
| Dahi | 1 | Same, and sourness complaints need a fast signal |
| Bread | 2 | A loaf lasts 2–3 days; staleness shows by day 2 |
| Atta | 5 | Needs a few rounds of rotis before people can judge it |
| Ghee | 7 | Used a spoon at a time; aroma and texture are judged over a week |
| Milk | none | Almost always on subscription, so we never ask |

X is stored per product (`products.feedback_delay_days`) and can be changed at runtime.

### Who we ask (checked per delivered item, in this order)

1. Items **delivered by a subscription**: never asked.
2. Products with **no X**: never asked.
3. Household **subscribed** to the product today (ACTIVE *or* PAUSED): not asked. This also covers one-time extras of a subscribed product.
4. Each delivered item is asked about **at most once**.
5. Same product **rated** in the last **30 days**, or **skipped / ignored** in the last **60 days**: not asked.
6. Ask from **delivery + X** until **delivery + X + 3 days** (the window). Older deliveries are no longer fresh in the user's mind.

**Priority** when several products are eligible. Only the top one is shown:

| Reason | Weight |
|--------|--------|
| `NEW_TO_HOUSEHOLD`: first time this household got this product | 50 |
| `FIRST_TIME_CUSTOMER`: the household's first order with us | 30 |
| `ONE_TIME_PURCHASE`: every eligible item | 10 |

Ties go to the card closest to expiring, so it isn't lost.

**Bought twice before rating:** all eligible deliveries of that product become **one** card about the
**most recent** delivery. After it's answered, the 30-day product cooldown stops the other deliveries from being asked about.

### Frequency cap (and why)

- **At most one card at a time.** The same card keeps showing (inline, skippable) until it's answered, skipped or ignored.
- **3 days between asks** across all products, counted from the last answer, skip or ignore.

Why 3 days: Country Delight users open the app almost every day to manage next-morning deliveries. A daily ask would
become noise, and people would learn to scroll past it. The shortest X is 1 day, so a 3-day gap still catches
paneer and dahi while they're fresh. That is at most about 2 asks a week, and only for households that actually buy new or
one-time items. All the limits are config values (`application.yml → feedback.*`), so they can be tuned with data.

**Ignored:** a card shown on **3 different days** with no tap, or still unanswered when its window ends, becomes
`IGNORED`. It counts toward the cap from the last day it was seen, and that product isn't asked about again for 60 days.
Several app loads on the same day count as **one** impression.

**Skipped:** the user tapped Skip. That product isn't asked about again for 60 days, and the 3-day cap starts.

### Rating

- One tap, 1–5. The rating is **saved immediately**, before any details, so it isn't lost if the user leaves.
- **≥ 4 (4 counts as good)** → `BUY_AGAIN`, unless the household is subscribed to that product, then `THANK_YOU`.
  The offer has tomorrow as the default, quick dates, and a min/max for the date picker (tomorrow … +30 days).
- **< 4** → `ISSUE_DETAILS` with **product-specific chips**. Pick at least one; note (≤ 200 chars) and photo are optional.
- Every feedback row **copies** product, order id, order item id, delivery date, area and city, so quality can trace it.

---

## API (app)

All app calls need the header `X-Customer-Id: <id>`.

### `GET /api/feedback-prompts/next`
Call on home-screen load.
```json
{ "show": true,
  "prompt": { "promptId": 350, "productId": "PANEER", "productName": "Fresh Paneer", "productUnit": "200 g",
    "imageUrl": "...", "title": "How was your Fresh Paneer?", "subtitle": "Delivered on Mon, 2 Nov",
    "orderId": 1, "deliveryDate": "2026-11-02", "expiresOn": "2026-11-06",
    "reasons": ["NEW_TO_HOUSEHOLD","FIRST_TIME_CUSTOMER","ONE_TIME_PURCHASE"],
    "ratingMin": 1, "ratingMax": 5, "highRatingThreshold": 4, "skipLabel": "Skip" } }
```
When nothing should be shown: `{"show": false, "reasonCode": "FREQUENCY_CAP" | "NOTHING_TO_ASK", "nextEligibleOn": "..."}`
(`reasonCode` is only for debugging; the app renders nothing).

### `POST /api/feedback-prompts/{promptId}/rating`  `{"rating": 4}`
```json
{ "feedbackId": 348, "promptId": 353, "rating": 4, "nextStep": "BUY_AGAIN",
  "message": "Glad you liked it! Want it again?",
  "buyAgain": { "productId": "GHEE", "productName": "Desi Cow Ghee", "unit": "500 ml", "price": 375.0,
    "defaultQuantity": 1, "suggestedDate": "2026-11-04", "minDate": "2026-11-04", "maxDate": "2026-12-03",
    "quickDates": [{"label": "Tomorrow", "date": "2026-11-04"}, {"label": "Thu, 5 Nov", "date": "2026-11-05"}],
    "addToCartPath": "/api/cart/items" } }
```
For a rating below 4, `nextStep` is `ISSUE_DETAILS` and the response includes
`issueForm: { question, options: [{code, label}], multiSelect, minSelections, noteMaxLength, photoAllowed, submitPath, photoUploadPath }`.

Errors: `409 ALREADY_RATED` / `PROMPT_CLOSED`, `404` (not your prompt), `400` (rating not 1–5).

### `POST /api/feedback-prompts/{promptId}/skip`
`{"promptId": 352, "status": "SKIPPED", "message": "No problem. We won't ask about ... again for a while."}`. Calling it again is safe.

### `PUT /api/feedback/{feedbackId}/issues`
`{"issueCodes": ["SMELL", "NEAR_EXPIRY"], "note": "Smelled sour", "photoUrl": null}`. The response echoes the saved
row with labelled issues and order trace. `400` for an unknown chip, no chips, a note over 200 chars, or a rating ≥ 4.

### `POST /api/feedback/{feedbackId}/photo`
Multipart, field `file`. JPEG/PNG/WEBP/HEIC, max 5 MB → `{"feedbackId": 349, "photoUrl": "/uploads/..."}`.

### `POST /api/cart/items`
`{"productId": "GHEE", "quantity": 1, "deliveryDate": "2026-11-05", "feedbackId": 348}`
- `deliveryDate` defaults to tomorrow and must be between tomorrow and today+30.
- `feedbackId` marks the add as `FEEDBACK_BUY_AGAIN`, so conversion can be measured.
- Adding the same product for the same date bumps the quantity.
- Returns `201` with `{item, message: "Desi Cow Ghee added for tomorrow", cart}`.

`GET /api/cart`, `DELETE /api/cart/items/{id}`, `GET /api/products`, `GET /api/products/{id}/issue-options`.

## API (dashboard)

Filters are optional on every endpoint: `from`, `to` (delivery dates, default last 30 days), `productId`, `area`.

- `GET /api/dashboard/summary` returns:
  - `totals`: count, average, low count and %, with details, with photo, buy-again adds
  - `byProduct`: with rating distribution and top issues
  - `byIssue`, `byArea`
  - `productAreaMatrix`: heatmap data
  - `trend`: daily
  - `alerts`: the patterns below
- `GET /api/dashboard/feedback?issueCode=&maxRating=&page=&size=`: individual rows for tracing (order id, delivery date, area, note, photo).
- `GET /api/dashboard/filters`: products with their chips, and areas.

**Pattern alerts:**

| Alert | Fires when | Severity |
|-------|------------|----------|
| `HOTSPOT` | A product × area cell has ≥ 8 ratings, ≥ 3 below 4, ≥ 35% below 4, and at least 2× the same product's rate in other areas | `HIGH` at ≥ 60% |
| `ISSUE_SPIKE` | An issue has ≥ 4 reports in the last 7 days and at least 2× the previous 7 days; the message names the top area | |
| `PRODUCT_QUALITY` | A product is ≥ 30% below 4 over ≥ 10 ratings | |

---

## Assumptions

1. **One app account = one household.** "New to household" means no earlier delivery of that product on that account.
2. **"First-time purchase"** means the household's first order with Country Delight. **"Products new to the household"** means first time buying *that* product. Both raise priority. Repeat one-time purchases are still asked, at lower priority.
3. A **paused** subscription counts as subscribed. A **cancelled** one doesn't, but items it delivered are still never asked about.
4. If the user subscribes while a card is pending, the card is withdrawn (`CANCELLED`). This doesn't use up the cap.
5. A rating is accepted on a card that was just marked `IGNORED`, since the user may still have it on screen. A late answer is still useful.
6. Ratings are 1–5 stars, and **4 is a good rating** (buy-again path).
7. The cart here is a simple stand-in for the real cart/order service. Its validation (tomorrow … +30 days) mirrors next-day delivery.
8. Photos are stored on local disk. In production this would be a pre-signed object-storage upload with the same API shape.
9. Dashboard date filters use the **delivery date**, because quality traces batches by when they went out.
10. Dashboard aggregation is done in memory over the filtered rows, which is fine for demo volumes. In production it would be SQL `GROUP BY` on a replica or warehouse.
11. Auth is a header stub (`X-Customer-Id`). Admin endpoints are open, for demo and testing only.

## Project layout

```
src/main/java/com/countrydelight/feedback
  domain/       entities: Product, IssueOption, Customer, Subscription, CustomerOrder, OrderItem,
                FeedbackPrompt (the "ask" + impressions), Feedback, CartItem
  service/      EligibilityEngine  (who / what / when; pure, read-only)
                PromptService      (card lifecycle, frequency cap, rating → next step, skip)
                FeedbackService    (issue details, photo)   CartService   DashboardService
  web/          controllers + dto/
  seed/         DataSeeder (demo households + dashboard history)
src/main/resources/static/dashboard.html   quality-team dashboard page (plain HTML/JS)
src/test/...  FeedbackFlowIntegrationTest  (25 end-to-end cases)
```
#   c d _ b a c k e n d  
 