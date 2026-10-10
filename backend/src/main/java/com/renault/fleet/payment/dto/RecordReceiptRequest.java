package com.renault.fleet.payment.dto;

import com.renault.fleet.payment.entity.PaymentMethod;
import com.renault.fleet.payment.entity.ReceiptType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code transactionReference} (UTR, cheque number, ...) must be unique, ignoring case and surrounding spaces. */
public record RecordReceiptRequest(
        @NotNull ReceiptType receiptType,
        @NotNull PaymentMethod paymentMethod,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^\s*[A-Za-z0-9][A-Za-z0-9 /_.-]*\s*$",
                message = "may contain letters, digits, spaces and / _ . -") String transactionReference,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull LocalDate receivedDate) {
}
