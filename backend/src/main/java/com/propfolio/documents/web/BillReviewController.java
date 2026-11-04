package com.propfolio.documents.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.propfolio.documents.BillReviewService;
import com.propfolio.documents.web.BillDtos.BillFieldsRequest;
import com.propfolio.documents.web.BillDtos.BillResponse;

/**
 * Reviewing a bill after it was read: edit fields, verify, un-verify, read again.
 * Every endpoint returns the bill with its updated review.
 */
@RestController
@RequestMapping("/api/bills/{billId}")
public class BillReviewController {

    private final BillReviewService reviewService;

    public BillReviewController(BillReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PutMapping("/fields")
    public BillResponse updateFields(@PathVariable UUID billId, @Valid @RequestBody BillFieldsRequest request) {
        return reviewService.updateFields(billId, request);
    }

    /** Starts a new reading in the background; poll GET /api/bills/{id} for the result. */
    @PostMapping("/extract")
    public BillResponse extract(@PathVariable UUID billId) {
        return reviewService.requestExtraction(billId);
    }

    @PostMapping("/verify")
    public BillResponse verify(@PathVariable UUID billId) {
        return reviewService.verify(billId);
    }

    @PostMapping("/unverify")
    public BillResponse unverify(@PathVariable UUID billId) {
        return reviewService.unverify(billId);
    }
}
