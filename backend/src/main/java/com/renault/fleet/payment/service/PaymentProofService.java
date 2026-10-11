package com.renault.fleet.payment.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.payment.entity.PaymentProof;
import com.renault.fleet.payment.repository.PaymentProofRepository;
import com.renault.fleet.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HQ access to a dealer's uploaded payment proof, so Finance can check it before approving the receipt. */
@Service
public class PaymentProofService {

    private final PaymentProofRepository proofs;
    private final AuditService audit;

    public PaymentProofService(PaymentProofRepository proofs, AuditService audit) {
        this.proofs = proofs;
        this.audit = audit;
    }

    @Transactional
    public PaymentProof forReceipt(Long receiptId, AuthenticatedUser actor) {
        PaymentProof proof = proofs.findByReceiptId(receiptId)
                .orElseThrow(() -> new NotFoundException("Receipt " + receiptId + " has no payment proof."));
        audit.record(AuditEvent.of("PAYMENT_PROOF_DOWNLOADED", "PAYMENT_PROOF", String.valueOf(proof.getId()))
                .withActor(actor).withReason("receipt " + receiptId + ", sha256 " + proof.getSha256()));
        return proof;
    }
}
