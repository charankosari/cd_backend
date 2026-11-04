# Frequency Cap

## Rules

| Rule | Value |
|---|---|
| Max cards on screen | **1** at a time |
| Gap between asks (any product) | **3 days** after the user answers, skips or ignores |
| Card counts as "ignored" | Seen on **3** different days with no tap |
| Same product after a rating | **Never asked again**: one review per product, even on later orders |
| Same product after skip / ignore | Not asked again for **60 days** |
| Subscribed products | **Never** asked |

All values live in `application.yml` (`feedback:`) and can be changed without code changes.

## Why

- **3 days:** users open the app almost daily, so asking daily would be noise. The shortest wait is 1 day
  (paneer, dahi), and a 3-day gap still catches those while they're fresh. That's at most about 2 asks a week.
- **Ignored after 3 days:** one unanswered view may just mean "busy"; three means "not interested".
- **60 days after skip / ignore:** respects the "no" without blocking the product forever, since people rebuy.
- **One review per product:** once a household has rated paneer, a later paneer order gets no card and no "Rate now"; we
  already know their opinion. (Setting `one-review-per-product`; switch it off to ask again after 30 days.)

## How the engine applies it (`GET /api/feedback-prompts/next`)

1. **A card is already showing?** Show the same one again, unless it has turned into "ignored".
2. **Asked in the last 3 days?** Show nothing (`FREQUENCY_CAP`).
3. **Otherwise** pick one eligible delivery and show its card. A delivery is skipped if it was:
   - from a subscription, or the household is subscribed to that product
   - already asked about
   - already reviewed (ever), or skipped / ignored in the last 60 days
   - not yet X days after delivery, or more than 3 days past that

   If several deliveries are eligible, a product new to the household goes first.

"Rate now" (the user chose to rate from My Orders) doesn't count toward the 3-day gap.

## Example

```
Mon  paneer rated            → no new card until Thu
Thu  dahi shown … Sat (3rd day, no tap)
Sun  dahi = ignored          → no card until Tue, no dahi for 60 days
```
