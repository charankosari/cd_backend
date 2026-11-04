package com.countrydelight.feedback.web;

import com.countrydelight.feedback.domain.Product;
import com.countrydelight.feedback.service.CatalogService;
import com.countrydelight.feedback.web.dto.ProductDtos.ProductView;
import com.countrydelight.feedback.web.dto.PromptDtos.IssueOptionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Product catalog (open, no login needed). Images are served from /products/*. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ProductView> list() {
        return catalog.allProducts().stream().map(this::view).toList();
    }

    @GetMapping("/{productId}")
    public ProductView get(@PathVariable String productId) {
        return view(catalog.product(productId.toUpperCase()));
    }

    @GetMapping("/{productId}/issue-options")
    public List<IssueOptionDto> issueOptions(@PathVariable String productId) {
        catalog.product(productId);
        return catalog.issueOptions(productId);
    }

    private ProductView view(Product p) {
        return new ProductView(p.getId(), p.getName(), p.getDescription(), p.getCategory(), p.getUnit(), p.getPrice(),
                catalog.imageUrl(p), p.getFeedbackDelayDays() != null, p.getFeedbackDelayDays(),
                catalog.issueOptions(p.getId()));
    }
}
