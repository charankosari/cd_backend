package com.countrydelight.feedback.web;

import com.countrydelight.feedback.service.CatalogService;
import com.countrydelight.feedback.web.dto.DashboardDtos.ProductOption;
import com.countrydelight.feedback.web.dto.PromptDtos.IssueOptionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ProductOption> list() {
        return catalog.allProducts().stream()
                .map(p -> new ProductOption(p.getId(), p.getName(), p.getFeedbackDelayDays(), catalog.issueOptions(p.getId())))
                .toList();
    }

    @GetMapping("/{productId}/issue-options")
    public List<IssueOptionDto> issueOptions(@PathVariable String productId) {
        catalog.product(productId);
        return catalog.issueOptions(productId);
    }
}
