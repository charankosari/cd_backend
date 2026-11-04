# Feedback After Delivery: API Integration Guide (Android app)

The JSON examples are real responses captured from the running backend (IDs and dates will differ on your machine).
The exceptions are the `THANK_YOU` example and the order examples in #16–#18: they have the real shape, but
their values were written by hand.

> **Rule:** the app shows only what the backend sends. Whether to show a card, which screen comes after a rating,
> and which issue options to show are all decided by the backend (`show`, `nextStep`, `issueForm.options`).

---

## 1. Basics

### Base URL

| Where the backend runs | Base URL |
|---|---|
| **Deployed (Railway): use this** | **`https://cd-feedback-backend-production.up.railway.app`** |
| Local laptop, app on the Android emulator | `http://10.0.2.2:8081` |
| Local laptop, app on a phone on the same Wi-Fi | `http://<laptop-IP>:8081` (e.g. `http://192.168.1.20:8081`) |

The deployed server uses HTTPS, so it works on any phone with no extra setup. The local URLs are plain HTTP,
so allow cleartext traffic for debug builds if you use them.

The deployed server restarts with fresh test data every time a new version is deployed.

### Authentication

| Type | How |
|---|---|
| **Bearer token (JWT)** | Header `Authorization: Bearer <accessToken>` |

- Get the token from **Login** or **Register**. It's valid for **7 days**.
- 🔓 **Open:** no token needed. 🔒 **Token:** the `Authorization` header is required.
- Any **401** response means the token is missing, expired or invalid: clear it and show the login screen.
- **Logout** = delete the token on the phone (there's no logout endpoint).

### Common rules

- Request bodies are JSON: send `Content-Type: application/json`. The only exception is photo upload, which is `multipart/form-data`.
- Dates are `yyyy-MM-dd` (e.g. `2026-11-05`). Timestamps are ISO-8601 UTC (e.g. `2026-11-11T05:43:36Z`).
- Prices are numbers in rupees (`375.0`).
- **`imageUrl`** is a full HTTPS URL (e.g. `https://cd-feedback-backend-production.up.railway.app/products/paneer.jpg`).
  Load it directly; no token needed. It's included in products, feedback cards, buy-again offers, cart lines and
  order items. Images are square (1000×1000) on a white background.
- Fields with no value are **left out** of the response; treat them as optional.
- New fields may be added later; ignore fields you don't recognise.

---

## 2. Quick reference

| # | Method | Endpoint | Auth | When to use |
|---|---|---|---|---|
| 1 | POST | `/api/auth/otp/request` | 🔓 | User entered a phone number (login or signup), or taps "Resend OTP" |
| 2 | POST | `/api/auth/login` | 🔓 | Existing user entered the OTP (`registered = true` from #1) |
| 3 | POST | `/api/auth/register` | 🔓 | New user filled the signup form + OTP (`registered = false` from #1) |
| 4 | GET | `/api/auth/me` | 🔒 | App start: check the saved token still works, get the profile |
| 5 | GET | `/api/feedback-prompts/next` | 🔒 | **Every time the home screen loads**: is there a feedback card? |
| 6 | POST | `/api/feedback-prompts/{promptId}/rating` | 🔒 | User tapped a star on the card |
| 7 | POST | `/api/feedback-prompts/{promptId}/skip` | 🔒 | User tapped "Skip" on the card |
| 8 | POST | `/api/feedback/{feedbackId}/photo` | 🔒 | Optional photo on the issue sheet (rating 1–3) |
| 9 | PUT | `/api/feedback/{feedbackId}/issues` | 🔒 | User submits the issue sheet (rating 1–3) |
| 10 | GET | `/api/feedback/{feedbackId}` | 🔒 | Show a submitted feedback again (optional) |
| 11 | POST | `/api/cart/items` | 🔒 | User taps "Add" on the buy-again sheet (rating 4–5) |
| 12 | GET | `/api/cart` | 🔒 | Show the cart |
| 13 | DELETE | `/api/cart/items/{cartItemId}` | 🔒 | Remove a cart line |
| 14 | GET | `/api/products` | 🔓 | Product catalog: name, price, pack size, image, description, category |
| 15 | GET | `/api/products/{productId}` | 🔓 | One product (product screen) |
| 16 | POST | `/api/orders` | 🔒 | Checkout: user places the order from the cart |
| 17 | GET | `/api/orders` | 🔒 | "My orders" screen (shows "Rate now" / "Rated 4★" per item) |
| 18 | GET | `/api/orders/{orderId}` | 🔒 | Order detail screen |
| 19 | POST | `/api/feedback-prompts/rate-now` | 🔒 | User taps **"Rate now"** on a delivered item: rate without waiting X days |
| 20 | GET | `/api/recommendations/buy-again` | 🔒 | "Order again" section (home / shop): products the user rated 4–5★ |
| 21 | PATCH | `/api/cart/items/{cartItemId}` | 🔒 | Change a cart line's quantity (+/−) or delivery date |
| 22 | DELETE | `/api/cart` | 🔒 | "Clear cart" |
| 23 | GET | `/api/cart/summary` | 🔒 | Cart badge (item count + total) on every screen |
| 24 | GET | `/api/recommendations/for-cart` | 🔒 | "Goes well with your cart" on the cart / checkout screen |
| 25 | GET | `/api/addresses` | 🔒 | Address book / address picker at checkout |
| 26 | POST | `/api/addresses` | 🔒 | "Add new address" |
| 27 | PUT | `/api/addresses/{addressId}` | 🔒 | Edit an address |
| 28 | POST | `/api/addresses/{addressId}/default` | 🔒 | "Set as default" |
| 29 | DELETE | `/api/addresses/{addressId}` | 🔒 | Delete an address |
| 30 | GET | `/api/subscriptions` | 🔒 | "My subscriptions" screen |
| 31 | POST | `/api/subscriptions` | 🔒 | "Subscribe" on a product |
| 32 | PATCH | `/api/subscriptions/{id}` | 🔒 | Change quantity / frequency / address |
| 33 | POST | `/api/subscriptions/{id}/pause` · `/resume` | 🔒 | Pause / resume deliveries |
| 34 | DELETE | `/api/subscriptions/{id}` | 🔒 | Cancel a subscription |

### Order of calls

**Login:**
1. Phone screen → **#1**.
2. If `registered = true` → OTP screen → **#2**.
3. If `registered = false` → signup screen (name, area, city, OTP) → **#3**.
4. Save `accessToken` → home screen.

**Feedback:**
1. Home screen → **#5**. If `show = false`, show nothing.
2. If `show = true`, show the card.
3. Star tapped → **#6** → look at `nextStep`:
   - `BUY_AGAIN` → buy-again sheet → "Add" → **#11** (pass `feedbackId`)
   - `ISSUE_DETAILS` → issue sheet → optional photo **#8** → Submit **#9**
   - `THANK_YOU` → show `message`, hide the card
4. Skip tapped → **#7** → hide the card.

The rating is saved as soon as the star is tapped. If the user closes the buy-again or issue sheet, nothing else needs to be called.

**Orders and Rate now:**
1. Cart screen → "Place order" → **#16** → the order is `SCHEDULED`.
2. It becomes `DELIVERED` automatically on its delivery date.
3. "My orders" → **#17**. On each delivered item, show a **"Rate now"** button when `feedback.canRateNow = true`. Show "Rated N★" when `feedback.status = RATED`.
4. "Rate now" tapped → **#19** → returns the **same card as #5** → then the same flow as above: star → **#6**, Skip → **#7**.

Without "Rate now", the home screen still asks by itself on `feedback.autoAskOn` (delivery + X days).

---

## 3. Endpoints

### #1 Request OTP
| | |
|---|---|
| **Method / URL** | `POST /api/auth/otp/request` |
| **Auth** | 🔓 Open |
| **When** | User submits a phone number, or taps "Resend OTP" (allowed after `resendAfterSeconds`) |

Request body:

| Field | Type | Required | Notes |
|---|---|---|---|
| `phone` | string | yes | Any common format: `9876543210`, `+91 98765 43210`, `098765-43210` |

```json
{ "phone": "+91 90000 00106" }
```

Response **200**:
```json
{
  "phone": "9000000106",
  "registered": true,
  "otpLength": 6,
  "expiresInSeconds": 300,
  "resendAfterSeconds": 30,
  "message": "OTP sent to ******0106"
}
```
- `registered`: `true` → go to the OTP screen (#2); `false` → go to the signup screen (#3).
- For now **the OTP is always `123456`** (no SMS provider yet).

Errors: `400 INVALID_PHONE`, `429 OTP_RESEND_TOO_SOON`, `429 OTP_LIMIT_REACHED` (max 5 per hour).

---

### #2 Login
| | |
|---|---|
| **Method / URL** | `POST /api/auth/login` |
| **Auth** | 🔓 Open |
| **When** | Existing user enters the OTP |

Request body:

| Field | Type | Required | Notes |
|---|---|---|---|
| `phone` | string | yes | Same number as in #1 |
| `otp` | string | yes | Digits only, e.g. `"123456"` |

```json
{ "phone": "9000000106", "otp": "123456" }
```

Response **200**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJjZC1mZWV...",
  "tokenType": "Bearer",
  "expiresInSeconds": 604800,
  "expiresAt": "2026-11-11T05:43:36.250246100Z",
  "newUser": false,
  "customer": {
    "customerId": 106,
    "name": "Kabir",
    "phone": "9000000106",
    "area": "Sohna Road",
    "city": "Gurugram"
  }
}
```
Save `accessToken` securely and send it on all 🔒 calls.

Errors: `400 OTP_INVALID` (message says attempts left), `400 OTP_EXPIRED`, `400 OTP_NOT_REQUESTED`,
`429 OTP_TOO_MANY_ATTEMPTS` (5 wrong tries), `404 NOT_REGISTERED` (→ signup), `400 VALIDATION_ERROR`.

---

### #3 Register (signup)
| | |
|---|---|
| **Method / URL** | `POST /api/auth/register` |
| **Auth** | 🔓 Open |
| **When** | New user (`registered = false` in #1) submits the signup form with the OTP |

Request body:

| Field | Type | Required | Notes |
|---|---|---|---|
| `phone` | string | yes | Same number as in #1 |
| `otp` | string | yes | Digits only |
| `name` | string | yes | Max 80 characters |
| `area` | string | yes | Max 80 characters, e.g. `HSR Layout` |
| `city` | string | yes | Max 60 characters |

```json
{ "phone": "9876500001", "otp": "123456", "name": "Riya", "area": "HSR Layout", "city": "Bengaluru" }
```

Response **201**: same shape as Login, with `"newUser": true`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJjZC1mZWV...",
  "tokenType": "Bearer",
  "expiresInSeconds": 604800,
  "expiresAt": "2026-11-11T05:43:52.868903600Z",
  "newUser": true,
  "customer": { "customerId": 100001, "name": "Riya", "phone": "9876500001", "area": "HSR Layout", "city": "Bengaluru" }
}
```

Errors: `409 PHONE_ALREADY_REGISTERED` (→ login), `400 VALIDATION_ERROR`, and the same OTP errors as Login.

---

### #4 Current user
| | |
|---|---|
| **Method / URL** | `GET /api/auth/me` |
| **Auth** | 🔒 Bearer token |
| **When** | App start, to check the saved token and get the profile |

Request body: none.

Response **200**:
```json
{ "customerId": 106, "name": "Kabir", "phone": "9000000106", "area": "Sohna Road", "city": "Gurugram" }
```
Errors: `401` → show login.

---

### #5 Get the feedback card
| | |
|---|---|
| **Method / URL** | `GET /api/feedback-prompts/next` |
| **Auth** | 🔒 Bearer token |
| **When** | **Every time the home screen loads.** Don't cache the card between app opens. |

Request body: none.

Response **200** with a card:
```json
{
  "show": true,
  "prompt": {
    "promptId": 357,
    "productId": "GHEE",
    "productName": "Desi Cow Ghee",
    "productUnit": "500 ml",
    "imageUrl": "https://cd-feedback-backend-production.up.railway.app/products/ghee.jpg",
    "title": "How was your Desi Cow Ghee?",
    "subtitle": "Delivered on Wed, 28 Oct",
    "orderId": 13,
    "deliveryDate": "2026-10-28",
    "expiresOn": "2026-11-07",
    "reasons": ["NEW_TO_HOUSEHOLD", "FIRST_TIME_CUSTOMER", "ONE_TIME_PURCHASE"],
    "ratingMin": 1,
    "ratingMax": 5,
    "highRatingThreshold": 4,
    "skipLabel": "Skip"
  }
}
```
- Show `title`, `subtitle`, product image, stars from `ratingMin` to `ratingMax`, and a `skipLabel` button.
- Keep `promptId`; it's needed for #6 and #7.
- `reasons` is for analytics only; don't display it.

Response **200** with no card (show nothing on screen):
```json
{ "show": false, "reasonCode": "NOTHING_TO_ASK", "message": "No delivery is due for feedback today" }
```
```json
{ "show": false, "reasonCode": "FREQUENCY_CAP", "message": "Asked recently; next feedback ask possible on 2026-11-07", "nextEligibleOn": "2026-11-07" }
```
Only check `show`. The other fields are for debugging.

---

### #6 Submit a rating
| | |
|---|---|
| **Method / URL** | `POST /api/feedback-prompts/{promptId}/rating` |
| **Auth** | 🔒 Bearer token |
| **When** | User taps a star (one tap = submit, no extra button) |

Path: `promptId` from #5.

Request body:

| Field | Type | Required | Notes |
|---|---|---|---|
| `rating` | number | yes | 1 to 5 |

```json
{ "rating": 4 }
```

The response's **`nextStep`** decides the next screen.

**A) `nextStep = "BUY_AGAIN"`** (rating 4–5, not subscribed): show the buy-again sheet.
```json
{
  "feedbackId": 355,
  "promptId": 357,
  "rating": 4,
  "nextStep": "BUY_AGAIN",
  "message": "Glad you liked it! Want it again?",
  "buyAgain": {
    "productId": "GHEE",
    "productName": "Desi Cow Ghee",
    "unit": "500 ml",
    "price": 375.0,
    "defaultQuantity": 1,
    "suggestedDate": "2026-11-05",
    "minDate": "2026-11-05",
    "maxDate": "2026-12-04",
    "quickDates": [
      { "label": "Tomorrow", "date": "2026-11-05" },
      { "label": "Fri, 6 Nov", "date": "2026-11-06" }
    ],
    "addToCartPath": "/api/cart/items"
  }
}
```
- Pre-select `suggestedDate`, show `quickDates` as quick options, and limit the date picker to `minDate`–`maxDate`.
- Keep `feedbackId` for #11.

**B) `nextStep = "ISSUE_DETAILS"`** (rating 1–3): show the issue sheet.
```json
{
  "feedbackId": 356,
  "promptId": 358,
  "rating": 2,
  "nextStep": "ISSUE_DETAILS",
  "message": "Sorry about that. Tell us what went wrong.",
  "issueForm": {
    "question": "What went wrong with your Fresh Paneer?",
    "options": [
      { "code": "HARD", "label": "Too hard / rubbery" },
      { "code": "SMELL", "label": "Bad smell" },
      { "code": "NEAR_EXPIRY", "label": "Close to expiry" },
      { "code": "SOUR", "label": "Sour taste" },
      { "code": "CRUMBLY", "label": "Crumbles / breaks" },
      { "code": "PACKAGING", "label": "Packet damaged" },
      { "code": "OTHER", "label": "Something else" }
    ],
    "multiSelect": true,
    "minSelections": 1,
    "noteAllowed": true,
    "noteMaxLength": 200,
    "photoAllowed": true,
    "submitPath": "/api/feedback/356/issues",
    "photoUploadPath": "/api/feedback/356/photo"
  }
}
```
- Show `question` and `options`: multi-select, at least `minSelections`.
- Note (max `noteMaxLength` characters) and photo are optional.
- The options are **different for each product**; always use this list.
- Keep `feedbackId` for #8 and #9.

**C) `nextStep = "THANK_YOU"`** (rating 4–5 but the user is subscribed to this product; illustrative values): show `message`.
```json
{ "feedbackId": 360, "promptId": 362, "rating": 5, "nextStep": "THANK_YOU", "message": "Thanks for rating! Glad you liked it." }
```

Errors: `409 ALREADY_RATED`, `409 PROMPT_CLOSED`, `409 ALREADY_REVIEWED` (this product was already reviewed through another
card; hide the card for all three), `404 NOT_FOUND`, `400 VALIDATION_ERROR` (rating outside 1–5).

---

### #7 Skip the card
| | |
|---|---|
| **Method / URL** | `POST /api/feedback-prompts/{promptId}/skip` |
| **Auth** | 🔒 Bearer token |
| **When** | User taps "Skip" |

Request body: none.

Response **200**:
```json
{ "promptId": 359, "status": "SKIPPED", "message": "No problem. We won't ask about Whole Wheat Bread again for a while." }
```
Hide the card. Calling it twice is safe.

Errors: `409 PROMPT_CLOSED` (already rated), `404 NOT_FOUND`.

---

### #8 Upload issue photo (optional)
| | |
|---|---|
| **Method / URL** | `POST /api/feedback/{feedbackId}/photo` |
| **Auth** | 🔒 Bearer token |
| **Content type** | `multipart/form-data` |
| **When** | User attaches a photo on the issue sheet (rating 1–3 only) |

Request body (form-data):

| Part name | Type | Required | Notes |
|---|---|---|---|
| `file` | image file | yes | JPEG, PNG, WEBP or HEIC; **max 5 MB** (compress camera photos) |

Response **200**:
```json
{ "feedbackId": 356, "photoUrl": "/uploads/feedback-356-b29a43e5-f65f-451d-8c39-ea5baf82b0dd.jpg" }
```
- The photo is attached to the feedback straight away; no need to send `photoUrl` again in #9.
- Can be called before or after #9.
- To display it: base URL + `photoUrl`.

Errors: `400 UNSUPPORTED_FILE`, `400 EMPTY_FILE`, `413 FILE_TOO_LARGE`, `400 DETAILS_NOT_NEEDED` (rating was 4–5).

---

### #9 Submit issue details
| | |
|---|---|
| **Method / URL** | `PUT /api/feedback/{feedbackId}/issues` |
| **Auth** | 🔒 Bearer token |
| **When** | User taps "Submit" on the issue sheet (rating 1–3 only) |

Request body:

| Field | Type | Required | Notes |
|---|---|---|---|
| `issueCodes` | array of strings | yes | At least 1; `code` values from `issueForm.options` |
| `note` | string | no | Max 200 characters |

```json
{ "issueCodes": ["SMELL", "NEAR_EXPIRY"], "note": "Smelled sour when opened" }
```

Response **200**:
```json
{
  "feedbackId": 356,
  "promptId": 358,
  "customerId": 101,
  "productId": "PANEER",
  "productName": "Fresh Paneer",
  "rating": 2,
  "issues": [
    { "code": "SMELL", "label": "Bad smell" },
    { "code": "NEAR_EXPIRY", "label": "Close to expiry" }
  ],
  "note": "Smelled sour when opened",
  "photoUrl": "/uploads/feedback-356-b29a43e5-f65f-451d-8c39-ea5baf82b0dd.jpg",
  "orderId": 1,
  "orderItemId": 1,
  "deliveryDate": "2026-11-03",
  "area": "Sector 45",
  "city": "Gurugram",
  "createdAt": "2026-11-04T05:43:51.174356Z",
  "message": "Thanks for telling us. Our quality team will look into it."
}
```
Show `message` as the thank-you text. Submitting again replaces the earlier answer.

Errors: `400 ISSUE_REQUIRED` (no option picked), `400 INVALID_ISSUE_CODE`, `400 NOTE_TOO_LONG`, `400 DETAILS_NOT_NEEDED`.

---

### #10 Get one feedback (optional)
| | |
|---|---|
| **Method / URL** | `GET /api/feedback/{feedbackId}` |
| **Auth** | 🔒 Bearer token |
| **When** | Show a submitted feedback again |

Request body: none. Response **200**: same shape as #9 (without `message`). Errors: `404 NOT_FOUND`.

---

### #11 Add to cart (buy again)
| | |
|---|---|
| **Method / URL** | `POST /api/cart/items` |
| **Auth** | 🔒 Bearer token |
| **When** | User taps "Add" on the buy-again sheet |

Request body:

| Field | Type | Required | Notes |
|---|---|---|---|
| `productId` | string | yes | From `buyAgain.productId` |
| `quantity` | number | no | 1–20, default 1 (use `buyAgain.defaultQuantity`) |
| `deliveryDate` | string `yyyy-MM-dd` | no | Default **tomorrow**; allowed `buyAgain.minDate`–`buyAgain.maxDate` |
| `feedbackId` | number | **yes from buy-again** | From #6; used to count buy-again conversions |

```json
{ "productId": "GHEE", "quantity": 1, "deliveryDate": "2026-11-06", "feedbackId": 355 }
```

Response **201**:
```json
{
  "item": {
    "cartItemId": 114,
    "productId": "GHEE",
    "productName": "Desi Cow Ghee",
    "unit": "500 ml",
    "unitPrice": 375.0,
    "quantity": 1,
    "lineTotal": 375.0,
    "deliveryDate": "2026-11-05",
    "source": "FEEDBACK_BUY_AGAIN",
    "feedbackId": 355
  },
  "message": "Desi Cow Ghee added for tomorrow",
  "cart": {
    "customerId": 106,
    "items": [
      {
        "cartItemId": 114,
        "productId": "GHEE",
        "productName": "Desi Cow Ghee",
        "unit": "500 ml",
        "unitPrice": 375.0,
        "quantity": 1,
        "lineTotal": 375.0,
        "deliveryDate": "2026-11-05",
        "source": "FEEDBACK_BUY_AGAIN",
        "feedbackId": 355
      }
    ],
    "totalQuantity": 1,
    "totalAmount": 375.0
  }
}
```
Show `message` as a confirmation. Adding the same product for the same date increases that line's quantity instead of adding a new line.

Errors: `400 INVALID_DELIVERY_DATE` (today or earlier, or more than 30 days ahead), `400 QUANTITY_LIMIT`, `400 PRODUCT_MISMATCH`, `404 NOT_FOUND` (unknown product).

---

### #12 View cart
| | |
|---|---|
| **Method / URL** | `GET /api/cart` |
| **Auth** | 🔒 Bearer token |
| **When** | Cart screen |

Request body: none. Response **200**: the `cart` object from #11. Items are **most recently added first**.

---

### #13 Remove cart item
| | |
|---|---|
| **Method / URL** | `DELETE /api/cart/items/{cartItemId}` |
| **Auth** | 🔒 Bearer token |
| **When** | User removes a line from the cart |

Request body: none. Response **200**: the updated `cart` object. Errors: `404 NOT_FOUND`.

---

### #21 Update cart line
| | |
|---|---|
| **Method / URL** | `PATCH /api/cart/items/{cartItemId}` |
| **Auth** | 🔒 Bearer token |
| **When** | User taps +/− on a cart line, or changes its delivery date |

Request body (send one or both):

| Field | Type | Required | Notes |
|---|---|---|---|
| `quantity` | number | no | New quantity, 1–20 (to remove a line use #13) |
| `deliveryDate` | string `yyyy-MM-dd` | no | tomorrow … today + 30 days |

```json
{ "quantity": 2 }
```
Response **200**: same shape as #11, with `message` like `"Whole Wheat Bread: 2 for tomorrow"`.
If the new date already has the same product, the two lines are **merged** (quantities added).

Errors: `400 INVALID_DELIVERY_DATE`, `400 QUANTITY_LIMIT`, `400 NOTHING_TO_UPDATE`, `404 NOT_FOUND`.

### #22 Clear cart
| | |
|---|---|
| **Method / URL** | `DELETE /api/cart` |
| **Auth** | 🔒 Bearer token |

Request body: none. Response **200**: the empty cart (`items: []`).

### #23 Cart summary (badge)
| | |
|---|---|
| **Method / URL** | `GET /api/cart/summary` |
| **Auth** | 🔒 Bearer token |
| **When** | Cart icon badge; refresh after any cart change |

Response **200**:
```json
{ "lineCount": 2, "totalQuantity": 3, "totalAmount": 385.00, "nextDeliveryDate": "2026-11-05" }
```

### #14 Products
| | |
|---|---|
| **Method / URL** | `GET /api/products` |
| **Auth** | 🔓 Open |
| **When** | Product list / shop screen |

Request body: none.

Response **200**: all 10 products, each in the same shape as #15, sorted by `productId`. Short version:

| productId | name | unit | price | category | feedbackDelayDays (X) | feedbackEnabled |
|---|---|---|---|---|---|---|
| `ATTA` | Chakki Atta | 5 kg | 285.0 | Staples | 5 | true |
| `BREAD` | Whole Wheat Bread | 400 g | 50.0 | Bakery | 2 | true |
| `BUTTER` | White Butter | 100 g | 60.0 | Dairy | 3 | true |
| `COCONUT_WATER` | Tender Coconut Water | 200 ml | 45.0 | Beverages | 1 | true |
| `DAHI` | Fresh Dahi | 400 g | 45.0 | Dairy | 1 | true |
| `EGGS` | Farm Eggs | 6 pcs | 72.0 | Eggs | 2 | true |
| `GHEE` | Desi Cow Ghee | 500 ml | 375.0 | Dairy | 7 | true |
| `HONEY` | Raw Honey | 250 g | 199.0 | Staples | 7 | true |
| `MILK` | Cow Milk | 500 ml | 34.0 | Dairy | — | **false** |
| `PANEER` | Fresh Paneer | 200 g | 95.0 | Dairy | 1 | true |

### #15 One product
| | |
|---|---|
| **Method / URL** | `GET /api/products/{productId}` (any case: `paneer` or `PANEER`) |
| **Auth** | 🔓 Open |
| **When** | Product detail screen |

Request body: none.

Response **200**:
```json
{
  "productId": "PANEER",
  "name": "Fresh Paneer",
  "description": "Soft malai paneer made fresh from cow milk. Best used within 2 days.",
  "category": "Dairy",
  "unit": "200 g",
  "price": 95.0,
  "imageUrl": "https://cd-feedback-backend-production.up.railway.app/products/paneer.jpg",
  "feedbackEnabled": true,
  "feedbackDelayDays": 1,
  "issueOptions": [
    { "code": "HARD", "label": "Too hard / rubbery" },
    { "code": "SMELL", "label": "Bad smell" },
    { "code": "NEAR_EXPIRY", "label": "Close to expiry" },
    { "code": "SOUR", "label": "Sour taste" },
    { "code": "CRUMBLY", "label": "Crumbles / breaks" },
    { "code": "PACKAGING", "label": "Packet damaged" },
    { "code": "OTHER", "label": "Something else" }
  ]
}
```
- `feedbackEnabled: false` (milk) means we never ask for feedback on it, and "Rate now" isn't offered.
- `feedbackDelayDays` is X: how many days after delivery the home card asks.
- Just the issue options: `GET /api/products/{productId}/issue-options` returns the `issueOptions` list on its own.

Errors: `404 NOT_FOUND` (unknown product).

---

### #16 Place order (checkout)
| | |
|---|---|
| **Method / URL** | `POST /api/orders` |
| **Auth** | 🔒 Bearer token |
| **When** | User taps "Place order" on the cart screen |

Request body: **optional**. With no body, every cart line is ordered, to the **default address**.

| Field | Type | Required | Notes |
|---|---|---|---|
| `deliveryDate` | string `yyyy-MM-dd` | no | Order only the cart lines for this date |
| `addressId` | number | no | Deliver to this saved address (#25); default address if omitted |

```json
{ "deliveryDate": "2026-11-05", "addressId": 12 }
```
Each order in the response (and in #17/#18) also has `addressId` and `deliveryAddress` (full address text).

Response **201**: one order per delivery date; the ordered lines are removed from the cart:
```json
{
  "orders": [
    {
      "orderId": 412,
      "deliveryDate": "2026-11-05",
      "status": "SCHEDULED",
      "area": "Sohna Road",
      "city": "Gurugram",
      "totalQuantity": 2,
      "totalAmount": 190.0,
      "items": [
        {
          "orderItemId": 873,
          "productId": "PANEER",
          "productName": "Fresh Paneer",
          "unit": "200 g",
          "quantity": 2,
          "unitPrice": 95.0,
          "lineTotal": 190.0,
          "purchaseType": "ONE_TIME",
          "feedback": { "status": "NOT_DELIVERED", "canRateNow": false, "autoAskOn": "2026-11-06" }
        }
      ]
    }
  ],
  "message": "Order placed for Thu, 5 Nov"
}
```
Errors: `400 CART_EMPTY`, `400 CART_DATE_PASSED` (a cart line's date is today or earlier: ask the user to pick a new date with #11).

---

### #17 My orders
| | |
|---|---|
| **Method / URL** | `GET /api/orders` |
| **Auth** | 🔒 Bearer token |
| **When** | "My orders" screen |

Request body: none.

Response **200**: a list of orders (same shape as in #16), **newest first** (by `placedAt`, the time the order was placed):
```json
[
  {
    "orderId": 412,
    "deliveryDate": "2026-11-05",
    "placedAt": "2026-11-04T09:12:31Z",
    "status": "DELIVERED",
    "area": "Sohna Road",
    "city": "Gurugram",
    "totalQuantity": 2,
    "totalAmount": 190.0,
    "items": [
      {
        "orderItemId": 873,
        "productId": "PANEER",
        "productName": "Fresh Paneer",
        "unit": "200 g",
        "quantity": 2,
        "unitPrice": 95.0,
        "lineTotal": 190.0,
        "purchaseType": "ONE_TIME",
        "feedback": { "status": "NOT_RATED", "canRateNow": true, "autoAskOn": "2026-11-06" }
      }
    ]
  },
  {
    "orderId": 13,
    "deliveryDate": "2026-10-28",
    "status": "DELIVERED",
    "...": "...",
    "items": [
      {
        "orderItemId": 13,
        "productId": "GHEE",
        "...": "...",
        "feedback": { "status": "RATED", "canRateNow": false, "autoAskOn": "2026-11-04", "rating": 4, "feedbackId": 355 }
      }
    ]
  }
]
```

`placedAt` is when the order was placed (UTC, ISO-8601). Daily subscription deliveries are created the evening before, so they sit at the top on their delivery day.

`status` (order): `SCHEDULED` (not delivered yet) → `DELIVERED` (on its delivery date). Also `CANCELLED`.

`feedback` (per item):

| Field | Meaning |
|---|---|
| `status` | `NOT_DELIVERED` · `NOT_RATED` · `RATED` · `ALREADY_REVIEWED` (reordered a product they already reviewed: no review option; `rating` is their earlier one) · `NOT_AVAILABLE` (product without feedback, e.g. milk) |
| `canRateNow` | `true` → show the **"Rate now"** button (calls #19) |
| `autoAskOn` | Date the home-screen card would ask by itself (delivery + X). Absent for subscription items / products without feedback |
| `rating`, `feedbackId` | Only when `RATED` |

---

### #18 One order
| | |
|---|---|
| **Method / URL** | `GET /api/orders/{orderId}` |
| **Auth** | 🔒 Bearer token |
| **When** | Order detail screen |

Request body: none. Response **200**: one order (same shape as an item of #17). Errors: `404 NOT_FOUND` (not this user's order).

---

### #19 Rate now (skip the X-day wait)
| | |
|---|---|
| **Method / URL** | `POST /api/feedback-prompts/rate-now` |
| **Auth** | 🔒 Bearer token |
| **When** | User taps "Rate now" on a delivered item (only shown when `feedback.canRateNow = true` in #17/#18) |

Normally we ask on the home screen X days after delivery (paneer 1 day, ghee 7 days…). "Rate now" lets the user
give feedback **immediately** after delivery, without waiting.

Request body:

| Field | Type | Required | Notes |
|---|---|---|---|
| `orderItemId` | number | yes | From `items[].orderItemId` in #17 / #18 |

```json
{ "orderItemId": 13 }
```

Response **200**: **exactly the same shape as #5**, so reuse the same card UI:
```json
{
  "show": true,
  "prompt": {
    "promptId": 357,
    "productId": "GHEE",
    "productName": "Desi Cow Ghee",
    "productUnit": "500 ml",
    "imageUrl": "https://cd-feedback-backend-production.up.railway.app/products/ghee.jpg",
    "title": "How was your Desi Cow Ghee?",
    "subtitle": "Delivered on Wed, 28 Oct",
    "orderId": 13,
    "deliveryDate": "2026-10-28",
    "expiresOn": "2026-11-07",
    "reasons": ["USER_REQUESTED"],
    "ratingMin": 1,
    "ratingMax": 5,
    "highRatingThreshold": 4,
    "skipLabel": "Skip"
  }
}
```
Then: star → **#6** (same `nextStep` flow: buy-again / issue sheet / thank-you), Skip → **#7**.

- Calling it again for the same item returns the same card.
- An item that was skipped earlier can still be rated with "Rate now".
- Ratings given this way don't block the home-screen card (they don't count toward the "ask at most once every 3 days" limit).

Errors: `409 NOT_DELIVERED` (order not delivered yet), `409 ALREADY_RATED`, `409 ALREADY_REVIEWED` (this product was
already reviewed on an earlier order: one review per product), `400 FEEDBACK_NOT_AVAILABLE` (e.g. milk), `404 NOT_FOUND`
(not this user's item).

---

### #20 Buy-again recommendations
| | |
|---|---|
| **Method / URL** | `GET /api/recommendations/buy-again` |
| **Auth** | 🔒 Bearer token |
| **When** | "Order again" section on the home or shop screen |

Shows products **this user rated 4–5★**, so they can reorder in one tap. Products are **left out** when:
- the user's **latest** rating of it is **1–3★** (`RATED_LOW`)
- the user is **subscribed** to it, active or paused (`SUBSCRIBED`)
- it's **already in the cart** (`IN_CART`)

Only ratings from the last 90 days count.

Request body: none.

Response **200** (account `9000000203`, Karan):
```json
{
  "items": [
    {
      "productId": "EGGS",
      "productName": "Farm Eggs",
      "unit": "6 pcs",
      "price": 72.0,
      "imageUrl": "https://cd-feedback-backend-production.up.railway.app/products/eggs.png",
      "rating": 5,
      "ratedOn": "2026-10-31",
      "feedbackId": 360,
      "reason": "You rated it 5★",
      "suggestedDate": "2026-11-05",
      "addToCartPath": "/api/cart/items"
    }
  ],
  "excluded": [
    { "productId": "ATTA", "productName": "Chakki Atta", "rating": 3, "reason": "RATED_LOW" },
    { "productId": "BREAD", "productName": "Whole Wheat Bread", "rating": 2, "reason": "RATED_LOW" }
  ]
}
```
- Show `items` as cards with image, name, unit, price, `reason` and an **"Order again"** button.
- The button calls **#11** with `{ "productId", "feedbackId", "deliveryDate": suggestedDate }`.
- Empty `items` → hide the section.
- `excluded` is only for testing and explaining the rules; **don't show it** in the app.

### #24 Goes well with your cart
| | |
|---|---|
| **Method / URL** | `GET /api/recommendations/for-cart` |
| **Auth** | 🔒 Bearer token |
| **When** | Cart / checkout screen: "Goes well with your cart" row |

Suggests up to 4 products that go with what's in the cart (e.g. Bread → Butter, Eggs, Honey; Atta → Ghee, Dahi,
Paneer). Products the user **rated 4–5★ come first**. Left out, with the same rules as #20:
- already in the cart (`IN_CART`)
- the user's latest rating was 1–3★ (`RATED_LOW`)
- subscribed (`SUBSCRIBED`)

Request body: none. Response **200** (cart: Bread ×2 + Atta, for account `9000000202`):
```json
{
  "items": [
    {
      "productId": "GHEE",
      "productName": "Desi Cow Ghee",
      "unit": "500 ml",
      "price": 375.0,
      "imageUrl": "https://cd-feedback-backend-production.up.railway.app/products/ghee.jpg",
      "pairsWith": ["ATTA"],
      "yourRating": 5,
      "reason": "Goes well with Chakki Atta · you rated it 5★",
      "suggestedDate": "2026-11-05",
      "addToCartPath": "/api/cart/items"
    },
    {
      "productId": "BUTTER",
      "productName": "White Butter",
      "unit": "100 g",
      "price": 60.0,
      "imageUrl": "https://cd-feedback-backend-production.up.railway.app/products/butter.png",
      "pairsWith": ["BREAD"],
      "reason": "Goes well with Whole Wheat Bread",
      "suggestedDate": "2026-11-05",
      "addToCartPath": "/api/cart/items"
    }
  ],
  "excluded": []
}
```
- "Add" calls **#11** with `{ "productId", "deliveryDate": suggestedDate }`, so it arrives with the cart item it goes with.
- Empty cart → `items: []` (hide the row).
- `excluded` is only for testing; **don't show it**.

### #25 My addresses
| | |
|---|---|
| **Method / URL** | `GET /api/addresses` |
| **Auth** | 🔒 Bearer token |
| **When** | Address book screen; address picker at checkout |

Response **200** (account `9000000106`, first 2 of 5 shown): the default address is listed first, then the rest **newest first**. Every demo account has 4–5 addresses.
```json
{
  "addresses": [
    {
      "addressId": 23,
      "label": "Home",
      "line1": "Flat 423, Tower C, Silver Oak Enclave",
      "line2": "Badshahpur Road",
      "landmark": "Vatika Chowk",
      "area": "Sohna Road",
      "city": "Gurugram",
      "pincode": "122018",
      "isDefault": true,
      "fullAddress": "Flat 423, Tower C, Silver Oak Enclave, Badshahpur Road, near Vatika Chowk, Sohna Road, Gurugram 122018"
    },
    {
      "addressId": 24,
      "label": "Work",
      "line1": "Floor 10, Cyber City Building 10",
      "line2": "U Block, Nathupur Road",
      "landmark": "Cyber Hub Gate 2",
      "area": "DLF Phase 3",
      "city": "Gurugram",
      "pincode": "122010",
      "isDefault": false,
      "fullAddress": "Floor 10, Cyber City Building 10, U Block, Nathupur Road, near Cyber Hub Gate 2, DLF Phase 3, Gurugram 122010"
    }
  ],
  "defaultAddressId": 23
}
```

### #26 Add address
| | |
|---|---|
| **Method / URL** | `POST /api/addresses` |
| **Auth** | 🔒 Bearer token |

Request body:

| Field | Type | Required | Notes |
|---|---|---|---|
| `label` | string | yes | "Home", "Work"… (max 30) |
| `line1` | string | yes | Flat / house no. and building (max 120) |
| `line2` | string | no | Street / road (max 120) |
| `landmark` | string | no | max 80 |
| `area` | string | yes | e.g. "Sector 45" (max 80) |
| `city` | string | yes | max 60 |
| `pincode` | string | yes | 6 digits |
| `makeDefault` | boolean | no | `true` → this becomes the default |

```json
{ "label": "Work", "line1": "Floor 4, Tower A, Tech Park", "line2": "Golf Course Road", "landmark": "Sector 54 Metro",
  "area": "Golf Course Road", "city": "Gurugram", "pincode": "122002", "makeDefault": false }
```
Response **201**: the saved address (same shape as one item of #25). The first address is always the default.
Errors: `400 VALIDATION_ERROR` (missing field / bad pincode), `400 ADDRESS_LIMIT` (max 10).

### #27 Edit address
`PUT /api/addresses/{addressId}` · 🔒 · same body as #26 (all fields again). Response **200**: the updated address.
Past orders keep the address text they were delivered to. Errors: `404 NOT_FOUND`.

### #28 Set default address
`POST /api/addresses/{addressId}/default` · 🔒 · no body. Response **200**: the full list (#25) with the new default first.

### #29 Delete address
`DELETE /api/addresses/{addressId}` · 🔒 · no body. Response **200**: the full list (#25).
Deleting the default makes the oldest remaining address the default.
Errors: `409 LAST_ADDRESS` (at least one address must remain), `404 NOT_FOUND`.

New accounts (#3) start with one default **Home** address built from the signup area and city. Ask the user to complete
it (#27) before their first order.

### #30 My subscriptions
| | |
|---|---|
| **Method / URL** | `GET /api/subscriptions` |
| **Auth** | 🔒 Bearer token |

Response **200** (account `9000000201`, Ravi: 1 of his 3 plans shown). Plans are **newest first**:
```json
{
  "subscriptions": [
    {
      "subscriptionId": 4,
      "productId": "MILK",
      "productName": "Cow Milk",
      "unit": "500 ml",
      "price": 34.0,
      "imageUrl": "https://cd-feedback-backend-production.up.railway.app/products/milk.png",
      "quantity": 2,
      "frequency": "DAILY",
      "status": "ACTIVE",
      "startDate": "2026-10-05",
      "nextDeliveryDate": "2026-11-05",
      "addressId": 46,
      "deliveryAddress": "Flat 338, Tower B, Lake View Towers, Huda Market Road, near Sector 45 Community Centre, Sector 45, Gurugram 122003",
      "estimatedMonthlyCost": 2040.0
    }
  ],
  "activeCount": 3,
  "estimatedMonthlyTotal": 4815.0
}
```
Active plans first, then paused, then cancelled. `nextDeliveryDate` is absent when paused or cancelled.

### #31 Subscribe
| | |
|---|---|
| **Method / URL** | `POST /api/subscriptions` |
| **Auth** | 🔒 Bearer token |
| **When** | "Subscribe" button on a product (only when the product's `subscribable` is `true`, see #15) |

Request body:

| Field | Type | Required | Notes |
|---|---|---|---|
| `productId` | string | yes | Must be subscribable (Atta, Honey are not) |
| `quantity` | number | no | 1–10, default 1 |
| `frequency` | string | no | `DAILY` (default) or `ALTERNATE_DAYS` |
| `startDate` | string `yyyy-MM-dd` | no | Default tomorrow; tomorrow … today + 30 |
| `addressId` | number | no | Default address if omitted |

```json
{ "productId": "MILK", "quantity": 2, "frequency": "DAILY" }
```
Response **201**: the subscription (shape of one item in #30) plus `message` like `"Cow Milk subscription starts Thu, 5 Nov"`.

**Deliveries:** on every delivery day a morning order is created automatically (purchase type `SUBSCRIPTION`).
It shows in **My orders** (#17) and the dashboard. Subscribed products are **never asked for feedback** and never
appear in buy-again / cart suggestions.

Errors: `409 ALREADY_SUBSCRIBED` (one live plan per product), `400 NOT_SUBSCRIBABLE`, `400 INVALID_START_DATE`,
`400 BAD_REQUEST` (unknown frequency).

### #32 Change subscription
`PATCH /api/subscriptions/{id}` · 🔒 · body: any of `quantity`, `frequency`, `addressId`, e.g. `{ "quantity": 3 }`.
Response **200**: the updated subscription. Applies from the **next** delivery.
Errors: `400 NOTHING_TO_UPDATE`, `409 SUBSCRIPTION_CANCELLED`, `404 NOT_FOUND`.

### #33 Pause / resume
`POST /api/subscriptions/{id}/pause` and `POST /api/subscriptions/{id}/resume` · 🔒 · no body. Response **200**: the subscription.
While paused there are no deliveries; resume continues from tomorrow (paused days aren't delivered later).
A **paused** plan still counts as subscribed: no feedback asks for that product.

### #34 Cancel subscription
`DELETE /api/subscriptions/{id}` · 🔒 · no body. Response **200**: the subscription with `status: "CANCELLED"` and `endDate`.
It stays in the list; the product can be subscribed again with #31.

---

## 4. Errors

Every error has this shape:
```json
{
  "timestamp": "2026-11-04T05:43:36.143271500Z",
  "status": 400,
  "code": "OTP_INVALID",
  "message": "Wrong OTP. 4 attempt(s) left",
  "path": "/api/auth/login"
}
```
Decide what to do from **`code`**. `message` is readable text and fine to show on the login screens.

| HTTP | `code` | Meaning | App should |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | No token sent | Show login |
| 401 | `INVALID_TOKEN` | Token expired / invalid | Delete token, show login |
| 400 | `INVALID_PHONE` | Not a valid 10-digit Indian mobile | Show error on the phone field |
| 429 | `OTP_RESEND_TOO_SOON` | Resend within 30 s | Keep the resend timer running |
| 429 | `OTP_LIMIT_REACHED` | More than 5 OTPs in an hour | Show message, block resend |
| 400 | `OTP_INVALID` | Wrong OTP | Show message (includes attempts left) |
| 400 | `OTP_EXPIRED` | OTP older than 5 min | Offer "Resend OTP" |
| 400 | `OTP_NOT_REQUESTED` | No active OTP | Offer "Resend OTP" |
| 429 | `OTP_TOO_MANY_ATTEMPTS` | 5 wrong tries | Offer "Resend OTP" |
| 404 | `NOT_REGISTERED` | Login with an unknown number | Go to signup |
| 409 | `PHONE_ALREADY_REGISTERED` | Signup with an existing number | Go to login |
| 409 | `ALREADY_RATED` / `PROMPT_CLOSED` | Card already handled | Hide the card |
| 409 | `ALREADY_REVIEWED` | Product already reviewed on an earlier order (one review per product) | Hide the card / "Rate now"; show the message |
| 404 | `NOT_FOUND` | Not found / not this user's | Hide or refresh |
| 400 | `ISSUE_REQUIRED` / `INVALID_ISSUE_CODE` / `NOTE_TOO_LONG` | Issue sheet input invalid | Show message |
| 400 | `DETAILS_NOT_NEEDED` | Issue details sent for a 4–5 rating | App flow bug |
| 400 / 413 | `UNSUPPORTED_FILE` / `EMPTY_FILE` / `FILE_TOO_LARGE` | Photo problem | Ask for another / smaller photo |
| 400 | `INVALID_DELIVERY_DATE` / `QUANTITY_LIMIT` / `PRODUCT_MISMATCH` | Cart input invalid | Show message |
| 400 | `CART_EMPTY` | Place order with an empty cart | Disable "Place order" when the cart is empty |
| 400 | `CART_DATE_PASSED` | A cart line's date is no longer in the future | Ask the user to pick a new date (#21) |
| 400 | `NOTHING_TO_UPDATE` | #21 sent without quantity or deliveryDate | Request bug |
| 409 | `LAST_ADDRESS` | Deleting the only address | Show message; hide delete on the last address |
| 400 | `ADDRESS_LIMIT` | More than 10 addresses | Show message |
| 409 | `ALREADY_SUBSCRIBED` | Product already has a live subscription | Open that subscription instead |
| 400 | `NOT_SUBSCRIBABLE` | Product can't be subscribed (Atta, Honey) | Hide "Subscribe" when `subscribable = false` |
| 400 | `INVALID_START_DATE` | Start date not tomorrow … +30 days | Show message |
| 409 | `SUBSCRIPTION_CANCELLED` | Changing a cancelled plan | Offer "Subscribe again" |
| 409 | `NOT_DELIVERED` | "Rate now" before delivery | Only show "Rate now" when `canRateNow = true` |
| 400 | `FEEDBACK_NOT_AVAILABLE` | "Rate now" on a product without feedback | Only show "Rate now" when `canRateNow = true` |
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` | Missing field or broken JSON | Request bug |

---

## 5. Test accounts

Log in with phone **`9000000` + ID** and OTP **`123456`**. Test data resets when the backend restarts.

**Start with these four.** Each shows one main rule from the brief:

| Phone | Name | `#5` feedback card | `#20` buy-again | What it shows |
|---|---|---|---|---|
| `9000000201` | Ravi | `show: false` | none (Paneer → `SUBSCRIBED`) | **Subscriber:** never asked for feedback on subscribed products, and they're never pushed as "order again" |
| `9000000202` | Anjali | Dahi card | **Ghee (5★), Paneer (4★)** | **Happy customer:** products rated 4–5★ are recommended. Rate the Dahi card 4–5 → buy-again sheet |
| `9000000204` | Pooja | **Paneer** card | none yet | **Subscribed + ordered:** subscribed to Dahi; also ordered Paneer. Asked about Paneer, **never** Dahi, even though she also got a one-time Dahi |
| `9000000203` | Karan | Coconut water card | **Eggs (5★)** only; Bread 2★ and Atta 3★ → `RATED_LOW` | **Mixed ratings:** low-rated products are not recommended. Rate coconut water 1–3 → issue sheet |

All demo accounts:

| Phone | Name | `#5` returns | Use it to test |
|---|---|---|---|
| `9000000101` | Asha | Paneer card | Low rating → issue sheet |
| `9000000102` | Rohan | `show: false` | Subscribed product: no card |
| `9000000103` | Meera | One Paneer card | Bought twice: still one card |
| `9000000104` | Vikram | `show: false` | Frequency cap |
| `9000000105` | Neha | Bread card | Skip |
| `9000000106` | Kabir | Ghee card | Rate **4** → buy-again → add to cart |
| `9000000107` | Ananya | `show: false` | Product not due yet |
| `9000000108` | Priya | Ghee card | Priority between two products |
| `9000000109` | Arjun | `show: false` | Ignored card |
| `9000000110` | Sara | `show: false` | Paused subscription |
| `9000000201` | Ravi | `show: false` | Subscriber (see above) |
| `9000000202` | Anjali | Dahi card | Happy customer: buy-again recommendations |
| `9000000203` | Karan | Coconut water card | Mixed ratings: low-rated products not recommended |
| `9000000204` | Pooja | Paneer card | Subscribed product (Dahi) never asked; ordered product (Paneer) asked |
| any new number | — | `show: false` | Signup (`registered: false` in #1) |

After a rating, that account won't get another card for 3 days. To test again, ask the backend developer to
reset the data, or use another account.

---

## 6. UX rules (from the brief)

- No blocking pop-ups: the card sits inline on the home screen and is easy to skip.
- Rating is one tap: tapping a star calls #6 immediately.
- Issue details only when `nextStep = ISSUE_DETAILS`; buy-again only when `nextStep = BUY_AGAIN`.
- One card at a time, and nothing at all when `show = false`.
