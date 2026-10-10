package com.renault.fleet.quotation.dto;

import jakarta.validation.constraints.Size;

/** Optional body for calculate: custom terms text (otherwise the configured default is used). */
public record CalculateQuotationRequest(@Size(max = 4000) String termsText) {
}
