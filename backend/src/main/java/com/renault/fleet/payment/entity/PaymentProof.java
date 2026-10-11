package com.renault.fleet.payment.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import com.renault.fleet.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** The file a dealer uploaded with a receipt (PDF, PNG or JPEG, at most 5 MB). Immutable once stored. */
@Entity
@Table(name = "payment_proofs")
public class PaymentProof extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_receipt_id", nullable = false, unique = true)
    private PaymentReceipt receipt;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private int sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    protected PaymentProof() {
    }

    public PaymentProof(PaymentReceipt receipt, String fileName, String contentType, byte[] content, String sha256,
            User uploadedBy) {
        this.receipt = receipt;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.sizeBytes = content.length;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
    }

    public PaymentReceipt getReceipt() { return receipt; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public byte[] getContent() { return content; }
    public User getUploadedBy() { return uploadedBy; }
}
