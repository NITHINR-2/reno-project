package com.renault.fleet.quotation.dto;

/**
 * A rendered quotation PDF. {@code sha256} is of these bytes; {@code issuedSha256} is the hash frozen on issue (null
 * for drafts).
 */
public record QuotationPdf(String fileName, byte[] content, String sha256, String issuedSha256) {
}
