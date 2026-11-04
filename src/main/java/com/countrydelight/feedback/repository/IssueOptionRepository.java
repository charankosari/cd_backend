package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.IssueOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueOptionRepository extends JpaRepository<IssueOption, Long> {

    List<IssueOption> findByProductIdOrderBySortOrderAsc(String productId);

    List<IssueOption> findAllByOrderByProductIdAscSortOrderAsc();
}
