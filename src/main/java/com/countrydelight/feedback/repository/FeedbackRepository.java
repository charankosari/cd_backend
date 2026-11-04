package com.countrydelight.feedback.repository;

import com.countrydelight.feedback.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByPromptId(Long promptId);

    List<Feedback> findByCustomerId(Long customerId);

    @Query("select distinct f from Feedback f left join fetch f.issueCodes "
            + "where f.deliveryDate between :from and :to "
            + "and (:productId is null or f.productId = :productId) "
            + "and (:area is null or f.area = :area)")
    List<Feedback> findForDashboard(@Param("from") LocalDate from,
                                    @Param("to") LocalDate to,
                                    @Param("productId") String productId,
                                    @Param("area") String area);
}
