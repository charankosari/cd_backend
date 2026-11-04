package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.common.TimeProvider;
import com.countrydelight.feedback.config.FeedbackProperties;
import com.countrydelight.feedback.domain.Customer;
import com.countrydelight.feedback.domain.Feedback;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.repository.CartItemRepository;
import com.countrydelight.feedback.repository.CustomerRepository;
import com.countrydelight.feedback.repository.FeedbackRepository;
import com.countrydelight.feedback.web.dto.DashboardDtos.Alert;
import com.countrydelight.feedback.web.dto.DashboardDtos.AreaOption;
import com.countrydelight.feedback.web.dto.DashboardDtos.AreaStat;
import com.countrydelight.feedback.web.dto.DashboardDtos.FeedbackRow;
import com.countrydelight.feedback.web.dto.DashboardDtos.Filters;
import com.countrydelight.feedback.web.dto.DashboardDtos.IssueCount;
import com.countrydelight.feedback.web.dto.DashboardDtos.IssueStat;
import com.countrydelight.feedback.web.dto.DashboardDtos.MatrixCell;
import com.countrydelight.feedback.web.dto.DashboardDtos.Page;
import com.countrydelight.feedback.web.dto.DashboardDtos.ProductOption;
import com.countrydelight.feedback.web.dto.DashboardDtos.ProductStat;
import com.countrydelight.feedback.web.dto.DashboardDtos.Summary;
import com.countrydelight.feedback.web.dto.DashboardDtos.Totals;
import com.countrydelight.feedback.web.dto.DashboardDtos.TrendPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregations for the quality dashboard. Done in memory over the filtered rows: fine for the demo's
 * volume. In production this would be SQL GROUP BYs on a read replica or warehouse.
 */
@Service
public class DashboardService {

    private static final int DEFAULT_RANGE_DAYS = 30;
    // Hotspot needs enough data to not be noise: >= 8 ratings in the cell, >= 3 of them low, >= 35% low,
    // and at least 2x the low rate of the same product in other areas.
    private static final int HOTSPOT_MIN_SAMPLES = 8;
    private static final int HOTSPOT_MIN_LOW = 3;
    private static final double HOTSPOT_MIN_LOW_PCT = 35.0;
    private static final int SPIKE_MIN_COUNT = 4;
    private static final int PRODUCT_MIN_SAMPLES = 10;
    private static final double PRODUCT_LOW_PCT_ALERT = 30.0;

    private final FeedbackRepository feedbacks;
    private final CartItemRepository cart;
    private final CustomerRepository customers;
    private final CatalogService catalog;
    private final TimeProvider time;
    private final FeedbackProperties props;

    public DashboardService(FeedbackRepository feedbacks, CartItemRepository cart, CustomerRepository customers,
                            CatalogService catalog, TimeProvider time, FeedbackProperties props) {
        this.feedbacks = feedbacks;
        this.cart = cart;
        this.customers = customers;
        this.catalog = catalog;
        this.time = time;
        this.props = props;
    }

    private record Range(LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to, String productId, String area) {
        Range r = range(from, to);
        String pf = blankToNull(productId);
        String af = blankToNull(area);
        List<Feedback> rows = feedbacks.findForDashboard(r.from(), r.to(), pf, af);
        Map<String, Product> products = catalog.allProducts().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<String, String> labels = catalog.issueLabelIndex();

        List<Feedback> spikeRows = feedbacks.findForDashboard(r.to().minusDays(13), r.to(), pf, af);

        return new Summary(r.from(), r.to(), pf, af,
                totals(rows),
                byProduct(rows, products, labels),
                byIssue(rows, products, labels),
                byArea(rows, products, labels),
                matrix(rows),
                trend(rows, r),
                alerts(rows, spikeRows, r, products, labels));
    }

    // ---------------------------------------------------------------- aggregations

    private Totals totals(List<Feedback> rows) {
        long n = rows.size();
        long low = rows.stream().filter(this::isLow).count();
        long withDetails = rows.stream().filter(f -> !f.getIssueCodes().isEmpty()).count();
        long withPhoto = rows.stream().filter(f -> f.getPhotoUrl() != null).count();
        List<Long> ids = rows.stream().map(Feedback::getId).toList();
        long buyAgain = ids.isEmpty() ? 0 : cart.countByFeedbackIdIn(ids);
        return new Totals(n, avg(rows), low, pct(low, n), withDetails, withPhoto, buyAgain);
    }

    private List<ProductStat> byProduct(List<Feedback> rows, Map<String, Product> products, Map<String, String> labels) {
        Map<String, List<Feedback>> grouped = rows.stream()
                .collect(Collectors.groupingBy(Feedback::getProductId, TreeMap::new, Collectors.toList()));
        List<ProductStat> out = new ArrayList<>();
        grouped.forEach((pid, list) -> {
            long low = list.stream().filter(this::isLow).count();
            Map<Integer, Long> dist = new LinkedHashMap<>();
            for (int s = 1; s <= 5; s++) {
                final int star = s;
                dist.put(s, list.stream().filter(f -> f.getRating() == star).count());
            }
            List<IssueCount> top = issueCounts(list, labels).stream().limit(3).toList();
            out.add(new ProductStat(pid, name(products, pid), list.size(), avg(list), low, pct(low, list.size()), dist, top));
        });
        out.sort(Comparator.comparingDouble(ProductStat::lowPct).reversed());
        return out;
    }

    private List<IssueStat> byIssue(List<Feedback> rows, Map<String, Product> products, Map<String, String> labels) {
        Map<String, Long> lowPerProduct = rows.stream().filter(this::isLow)
                .collect(Collectors.groupingBy(Feedback::getProductId, Collectors.counting()));
        Map<String, Long> counts = new TreeMap<>();
        for (Feedback f : rows) {
            for (String code : f.getIssueCodes()) {
                counts.merge(CatalogService.key(f.getProductId(), code), 1L, Long::sum);
            }
        }
        List<IssueStat> out = new ArrayList<>();
        counts.forEach((key, c) -> {
            String[] parts = key.split(":", 2);
            out.add(new IssueStat(parts[0], name(products, parts[0]), parts[1], labels.getOrDefault(key, parts[1]),
                    c, pct(c, lowPerProduct.getOrDefault(parts[0], 0L))));
        });
        out.sort(Comparator.comparingLong(IssueStat::count).reversed());
        return out;
    }

    private List<AreaStat> byArea(List<Feedback> rows, Map<String, Product> products, Map<String, String> labels) {
        Map<String, List<Feedback>> grouped = rows.stream()
                .collect(Collectors.groupingBy(Feedback::getArea, TreeMap::new, Collectors.toList()));
        List<AreaStat> out = new ArrayList<>();
        grouped.forEach((area, list) -> {
            long low = list.stream().filter(this::isLow).count();
            String topIssue = topProductIssue(list, products, labels);
            out.add(new AreaStat(area, list.get(0).getCity(), list.size(), avg(list), low, pct(low, list.size()), topIssue));
        });
        out.sort(Comparator.comparingDouble(AreaStat::lowPct).reversed());
        return out;
    }

    private List<MatrixCell> matrix(List<Feedback> rows) {
        Map<String, Map<String, List<Feedback>>> grouped = rows.stream().collect(Collectors.groupingBy(
                Feedback::getProductId, TreeMap::new,
                Collectors.groupingBy(Feedback::getArea, TreeMap::new, Collectors.toList())));
        List<MatrixCell> out = new ArrayList<>();
        grouped.forEach((pid, byArea) -> byArea.forEach((area, list) -> {
            long low = list.stream().filter(this::isLow).count();
            out.add(new MatrixCell(pid, area, list.size(), low, pct(low, list.size())));
        }));
        return out;
    }

    private List<TrendPoint> trend(List<Feedback> rows, Range r) {
        Map<LocalDate, List<Feedback>> byDay = rows.stream().collect(Collectors.groupingBy(Feedback::getDeliveryDate));
        List<TrendPoint> out = new ArrayList<>();
        for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) {
            List<Feedback> list = byDay.getOrDefault(d, List.of());
            out.add(new TrendPoint(d, list.size(), list.stream().filter(this::isLow).count(), avg(list)));
        }
        return out;
    }

    // ---------------------------------------------------------------- pattern detection

    private List<Alert> alerts(List<Feedback> rows, List<Feedback> spikeRows, Range r,
                               Map<String, Product> products, Map<String, String> labels) {
        List<Alert> alerts = new ArrayList<>();

        // 1. HOTSPOT: a product does much worse in one area than everywhere else.
        Map<String, List<Feedback>> byProduct = rows.stream().collect(Collectors.groupingBy(Feedback::getProductId));
        byProduct.forEach((pid, list) -> {
            Map<String, List<Feedback>> byArea = list.stream().collect(Collectors.groupingBy(Feedback::getArea));
            byArea.forEach((area, cell) -> {
                if (cell.size() < HOTSPOT_MIN_SAMPLES) {
                    return;
                }
                List<Feedback> others = list.stream().filter(f -> !Objects.equals(f.getArea(), area)).toList();
                long lowN = cell.stream().filter(this::isLow).count();
                double cellLow = pct(lowN, cell.size());
                double otherLow = pct(others.stream().filter(this::isLow).count(), others.size());
                boolean worseThanOthers = others.size() < HOTSPOT_MIN_SAMPLES || cellLow >= 2 * otherLow;
                if (lowN >= HOTSPOT_MIN_LOW && cellLow >= HOTSPOT_MIN_LOW_PCT && worseThanOthers) {
                    List<IssueCount> issues = issueCounts(cell, labels);
                    IssueCount top = issues.isEmpty() ? null : issues.get(0);
                    String msg = String.format("%d of %d ratings were below %d (%.0f%%) vs %.0f%% in other areas.%s",
                            lowN, cell.size(), props.highRatingThreshold(), cellLow, otherLow,
                            top == null ? "" : " Top issue: " + top.label() + " (" + top.count() + ").");
                    alerts.add(new Alert("HOTSPOT", cellLow >= 60 ? "HIGH" : "MEDIUM", pid, area,
                            top == null ? null : top.issueCode(),
                            name(products, pid) + " in " + area, msg));
                }
            });
        });

        // 2. ISSUE_SPIKE: an issue reported much more in the last 7 days than the 7 days before.
        LocalDate recentFrom = r.to().minusDays(6);
        Map<String, long[]> spike = new TreeMap<>();
        Map<String, Map<String, Long>> spikeAreas = new LinkedHashMap<>();
        for (Feedback f : spikeRows) {
            boolean recent = !f.getDeliveryDate().isBefore(recentFrom);
            for (String code : f.getIssueCodes()) {
                String key = CatalogService.key(f.getProductId(), code);
                long[] c = spike.computeIfAbsent(key, k -> new long[2]);
                if (recent) {
                    c[0]++;
                    spikeAreas.computeIfAbsent(key, k -> new TreeMap<>()).merge(f.getArea(), 1L, Long::sum);
                } else {
                    c[1]++;
                }
            }
        }
        spike.forEach((key, c) -> {
            long recent = c[0];
            long before = c[1];
            if (recent >= SPIKE_MIN_COUNT && recent >= 2 * Math.max(before, 1)) {
                String[] parts = key.split(":", 2);
                Map.Entry<String, Long> topArea = spikeAreas.getOrDefault(key, Map.of()).entrySet().stream()
                        .max(Map.Entry.comparingByValue()).orElse(null);
                String msg = String.format("'%s' reported %d times in the last 7 days vs %d in the 7 days before.%s",
                        labels.getOrDefault(key, parts[1]), recent, before,
                        topArea == null ? "" : " Most reports from " + topArea.getKey() + " (" + topArea.getValue() + ").");
                alerts.add(new Alert("ISSUE_SPIKE", recent >= 3 * Math.max(before, 1) ? "HIGH" : "MEDIUM",
                        parts[0], topArea == null ? null : topArea.getKey(), parts[1],
                        name(products, parts[0]) + ": " + labels.getOrDefault(key, parts[1]) + " rising", msg));
            }
        });

        // 3. PRODUCT_QUALITY: a product is doing badly overall.
        byProduct.forEach((pid, list) -> {
            long low = list.stream().filter(this::isLow).count();
            double lowPct = pct(low, list.size());
            if (list.size() >= PRODUCT_MIN_SAMPLES && lowPct >= PRODUCT_LOW_PCT_ALERT) {
                alerts.add(new Alert("PRODUCT_QUALITY", "MEDIUM", pid, null, null,
                        name(products, pid) + " low ratings at " + Math.round(lowPct) + "%",
                        String.format("%d of %d ratings were below %d across all areas.", low, list.size(),
                                props.highRatingThreshold())));
            }
        });

        alerts.sort(Comparator.comparing((Alert a) -> "HIGH".equals(a.severity()) ? 0 : 1)
                .thenComparing(Alert::type)
                .thenComparing(Alert::title));
        return alerts;
    }

    // ---------------------------------------------------------------- list + filters

    @Transactional(readOnly = true)
    public Page<FeedbackRow> list(LocalDate from, LocalDate to, String productId, String area, String issueCode,
                                  Integer maxRating, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw ApiException.badRequest("BAD_PAGE", "page >= 0 and 1 <= size <= 200");
        }
        Range r = range(from, to);
        String issue = blankToNull(issueCode);
        Map<String, String> labels = catalog.issueLabelIndex();
        List<Feedback> rows = feedbacks.findForDashboard(r.from(), r.to(), blankToNull(productId), blankToNull(area))
                .stream()
                .filter(f -> maxRating == null || f.getRating() <= maxRating)
                .filter(f -> issue == null || f.getIssueCodes().contains(issue.toUpperCase()))
                .sorted(Comparator.comparing(Feedback::getCreatedAt).reversed().thenComparing(Feedback::getId, Comparator.reverseOrder()))
                .toList();
        int fromIdx = Math.min(page * size, rows.size());
        int toIdx = Math.min(fromIdx + size, rows.size());
        List<FeedbackRow> content = rows.subList(fromIdx, toIdx).stream()
                .map(f -> new FeedbackRow(f.getId(), f.getCreatedOn(), f.getRating(), f.getProductId(), f.getProductName(),
                        catalog.toDtos(f.getProductId(), f.getIssueCodes(), labels), f.getNote(), f.getPhotoUrl(),
                        f.getOrderId(), f.getOrderItemId(), f.getDeliveryDate(), f.getArea(), f.getCity(), f.getCustomerId()))
                .toList();
        int totalPages = (int) Math.ceil(rows.size() / (double) size);
        return new Page<>(content, page, size, rows.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public Filters filters() {
        List<ProductOption> products = catalog.allProducts().stream()
                .filter(p -> p.getFeedbackDelayDays() != null)
                .map(p -> new ProductOption(p.getId(), p.getName(), p.getFeedbackDelayDays(), catalog.issueOptions(p.getId())))
                .toList();
        TreeSet<String> seen = new TreeSet<>();
        List<AreaOption> areas = new ArrayList<>();
        customers.findAll().stream()
                .sorted(Comparator.comparing(Customer::getCity).thenComparing(Customer::getArea))
                .forEach(c -> {
                    if (seen.add(c.getArea())) {
                        areas.add(new AreaOption(c.getArea(), c.getCity()));
                    }
                });
        return new Filters(products, areas);
    }

    // ---------------------------------------------------------------- helpers

    private Range range(LocalDate from, LocalDate to) {
        LocalDate t = to != null ? to : time.today();
        LocalDate f = from != null ? from : t.minusDays(DEFAULT_RANGE_DAYS - 1);
        if (f.isAfter(t)) {
            throw ApiException.badRequest("BAD_RANGE", "'from' must be on or before 'to'");
        }
        if (f.plusDays(366).isBefore(t)) {
            throw ApiException.badRequest("BAD_RANGE", "Range can be at most one year");
        }
        return new Range(f, t);
    }

    private boolean isLow(Feedback f) {
        return f.getRating() < props.highRatingThreshold();
    }

    private List<IssueCount> issueCounts(List<Feedback> list, Map<String, String> labels) {
        Map<String, Long> counts = new TreeMap<>();
        for (Feedback f : list) {
            for (String code : f.getIssueCodes()) {
                counts.merge(CatalogService.key(f.getProductId(), code), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new IssueCount(e.getKey().split(":", 2)[1], labels.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                .toList();
    }

    private String topProductIssue(List<Feedback> list, Map<String, Product> products, Map<String, String> labels) {
        Map<String, Long> counts = new TreeMap<>();
        for (Feedback f : list) {
            for (String code : f.getIssueCodes()) {
                counts.merge(CatalogService.key(f.getProductId(), code), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> name(products, e.getKey().split(":", 2)[0]) + ": " + labels.getOrDefault(e.getKey(), e.getKey())
                        + " (" + e.getValue() + ")")
                .orElse(null);
    }

    private static String name(Map<String, Product> products, String pid) {
        Product p = products.get(pid);
        return p == null ? pid : p.getName();
    }

    private static double avg(List<Feedback> list) {
        return round1(list.stream().mapToInt(Feedback::getRating).average().orElse(0));
    }

    private static double pct(long part, long whole) {
        return whole == 0 ? 0 : round1(100.0 * part / whole);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
