package com.propfolio.payments;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndLandlordId(UUID id, UUID landlordId);

    List<Payment> findAllByInvoiceIdOrderByPaidOnAscCreatedAtAsc(UUID invoiceId);

    List<Payment> findAllByInvoiceIdIn(List<UUID> invoiceIds);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoiceId = :invoiceId")
    BigDecimal sumByInvoiceId(@Param("invoiceId") UUID invoiceId);
}
