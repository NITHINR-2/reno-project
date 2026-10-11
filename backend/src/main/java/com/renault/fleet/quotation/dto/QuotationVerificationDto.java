package com.renault.fleet.quotation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public verification result. Deliberately minimal: no dealer, contact or deposit details. */
public record QuotationVerificationDto(String result, String quotationNumber, LocalDate issueDate,
        BigDecimal totalAmount, String currency, String status) {
}
