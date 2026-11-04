package com.countrydelight.feedback.service;

import com.countrydelight.feedback.common.ApiException;
import com.countrydelight.feedback.domain.IssueOption;
import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.repository.IssueOptionRepository;
import com.countrydelight.feedback.repository.ProductRepository;
import com.countrydelight.feedback.web.dto.PromptDtos.IssueOptionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogService {

    private final ProductRepository products;
    private final IssueOptionRepository issueOptions;

    public CatalogService(ProductRepository products, IssueOptionRepository issueOptions) {
        this.products = products;
        this.issueOptions = issueOptions;
    }

    @Transactional(readOnly = true)
    public Product product(String productId) {
        return products.findById(productId).orElseThrow(() -> ApiException.notFound("Product " + productId));
    }

    @Transactional(readOnly = true)
    public List<Product> allProducts() {
        return products.findAll().stream().sorted((a, b) -> a.getId().compareTo(b.getId())).toList();
    }

    public String imageUrl(Product p) {
        String path = p == null ? null : p.getImageUrl();
        if (path == null || path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (RequestContextHolder.getRequestAttributes() == null) {
            return path;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString();
    }

    @Transactional(readOnly = true)
    public List<IssueOptionDto> issueOptions(String productId) {
        return issueOptions.findByProductIdOrderBySortOrderAsc(productId).stream()
                .map(o -> new IssueOptionDto(o.getCode(), o.getLabel()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, String> issueLabelIndex() {
        Map<String, String> index = new LinkedHashMap<>();
        for (IssueOption o : issueOptions.findAllByOrderByProductIdAscSortOrderAsc()) {
            index.put(key(o.getProductId(), o.getCode()), o.getLabel());
        }
        return index;
    }

    public List<IssueOptionDto> toDtos(String productId, Collection<String> codes, Map<String, String> labelIndex) {
        List<String> order = List.copyOf(labelIndex.keySet());
        return codes.stream()
                .sorted(Comparator.comparingInt((String c) -> {
                    int i = order.indexOf(key(productId, c));
                    return i < 0 ? Integer.MAX_VALUE : i;
                }))
                .map(c -> new IssueOptionDto(c, labelIndex.getOrDefault(key(productId, c), c)))
                .toList();
    }

    public static String key(String productId, String code) {
        return productId + ":" + code;
    }
}
