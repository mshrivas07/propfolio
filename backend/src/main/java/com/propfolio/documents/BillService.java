package com.propfolio.documents;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.propfolio.billing.InvoiceLineRepository;
import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.InvalidRequestException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.documents.storage.StorageService;
import com.propfolio.documents.web.BillDtos.BillFile;
import com.propfolio.documents.web.BillDtos.BillResponse;
import com.propfolio.rentals.Property;
import com.propfolio.rentals.PropertyRepository;
import com.propfolio.rentals.UtilityAccount;
import com.propfolio.rentals.UtilityAccountRepository;
import com.propfolio.security.CurrentLandlord;

/**
 * Uploading, listing, viewing and deleting bill files.
 *
 * <p>Upload order: validate → duplicate check → create the bill row (id assigned) → store the
 * file under {@code <landlordId>/<yyyy>/<MM>/<billId>.<ext>} → commit. If the transaction doesn't
 * commit (e.g. a duplicate slips past the check in a race), the stored file is deleted again.
 */
@Service
@Transactional
public class BillService {

    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_FILENAME_LENGTH = 200;
    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    private final CurrentLandlord currentLandlord;
    private final BillRepository bills;
    private final UtilityAccountRepository utilityAccounts;
    private final PropertyRepository properties;
    private final InvoiceLineRepository invoiceLines;
    private final StorageService storage;
    private final Clock clock;

    public BillService(CurrentLandlord currentLandlord, BillRepository bills, UtilityAccountRepository utilityAccounts,
            PropertyRepository properties, InvoiceLineRepository invoiceLines, StorageService storage, Clock clock) {
        this.currentLandlord = currentLandlord;
        this.bills = bills;
        this.utilityAccounts = utilityAccounts;
        this.properties = properties;
        this.invoiceLines = invoiceLines;
        this.storage = storage;
        this.clock = clock;
    }

    public BillResponse upload(MultipartFile file, UUID utilityAccountId) {
        UUID landlordId = currentLandlord.id();

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("file", "The file is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidRequestException("file", "Files can be at most 10 MB");
        }
        byte[] content = readAll(file);
        BillFileType type = BillFileType.detect(content)
                .orElseThrow(() -> new InvalidRequestException("file", "Upload a PDF, PNG or JPG file"));

        UtilityAccount account = null;
        if (utilityAccountId != null) {
            account = utilityAccounts.findByIdAndLandlordId(utilityAccountId, landlordId)
                    .orElseThrow(() -> new InvalidRequestException("utilityAccountId",
                            "Choose a utility account from your properties"));
        }

        String sha256 = sha256(content);
        bills.findFirstByLandlordIdAndFileSha256(landlordId, sha256).ifPresent(existing -> {
            throw new ConflictException("You already uploaded this file as \"%s\"."
                    .formatted(existing.getOriginalFilename()), Map.of("existingBillId", existing.getId()));
        });

        String filename = cleanFilename(file.getOriginalFilename(), type);
        Bill bill = new Bill(landlordId, filename, "pending", type.contentType(), content.length, sha256);
        bill.setUtilityAccountId(account == null ? null : account.getId());
        bills.save(bill); // assigns the id; the insert itself runs at commit

        String key = storageKey(landlordId, bill.getId(), type);
        bill.setStoragePath(key);
        storage.store(key, new ByteArrayInputStream(content));
        deleteFileUnlessCommitted(key);

        return toResponses(List.of(bill), landlordId).getFirst();
    }

    @Transactional(readOnly = true)
    public List<BillResponse> list(BillStatus status, UUID propertyId) {
        UUID landlordId = currentLandlord.id();
        List<Bill> rows;
        if (propertyId == null) {
            rows = status == null
                    ? bills.findAllByLandlordIdOrderByCreatedAtDesc(landlordId)
                    : bills.findAllByLandlordIdAndStatusOrderByCreatedAtDesc(landlordId, status);
        } else {
            List<UUID> accountIds = utilityAccounts
                    .findAllByLandlordIdAndPropertyIdOrderByUtilityTypeAscProviderNameAsc(landlordId, propertyId)
                    .stream().map(UtilityAccount::getId).toList();
            if (accountIds.isEmpty()) {
                return List.of();
            }
            rows = status == null
                    ? bills.findAllByLandlordIdAndUtilityAccountIdInOrderByCreatedAtDesc(landlordId, accountIds)
                    : bills.findAllByLandlordIdAndStatusAndUtilityAccountIdInOrderByCreatedAtDesc(landlordId,
                            status, accountIds);
        }
        return toResponses(rows, landlordId);
    }

    @Transactional(readOnly = true)
    public BillResponse get(UUID billId) {
        UUID landlordId = currentLandlord.id();
        return toResponses(List.of(require(billId, landlordId)), landlordId).getFirst();
    }

    @Transactional(readOnly = true)
    public BillFile openFile(UUID billId) {
        Bill bill = require(billId, currentLandlord.id());
        return new BillFile(storage.open(bill.getStoragePath()), bill.getContentType(), bill.getFileSizeBytes(),
                bill.getOriginalFilename());
    }

    public void delete(UUID billId) {
        Bill bill = require(billId, currentLandlord.id());
        if (bill.getStatus() == BillStatus.VERIFIED) {
            throw new ConflictException("Verified bills can't be deleted.");
        }
        if (invoiceLines.existsByBillId(bill.getId())) {
            throw new ConflictException("This bill is on an invoice, so it can't be deleted.");
        }
        String key = bill.getStoragePath();
        bills.delete(bill);
        deleteFileAfterCommit(key);
    }

    private Bill require(UUID billId, UUID landlordId) {
        return bills.findByIdAndLandlordId(billId, landlordId).orElseThrow(() -> new NotFoundException("Bill"));
    }

    String storageKey(UUID landlordId, UUID billId, BillFileType type) {
        LocalDate today = LocalDate.now(clock);
        return "%s/%04d/%02d/%s.%s".formatted(landlordId, today.getYear(), today.getMonthValue(), billId,
                type.extension());
    }

    /** Keeps only the last path part, drops control characters and limits the length. */
    static String cleanFilename(String original, BillFileType type) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.length() > MAX_FILENAME_LENGTH) {
            name = name.substring(name.length() - MAX_FILENAME_LENGTH);
        }
        return name.isEmpty() ? "bill." + type.extension() : name;
    }

    private List<BillResponse> toResponses(List<Bill> rows, UUID landlordId) {
        List<UUID> accountIds = rows.stream().map(Bill::getUtilityAccountId).filter(id -> id != null).distinct()
                .toList();
        Map<UUID, UtilityAccount> accountById = accountIds.isEmpty() ? Map.of()
                : byId(utilityAccounts.findAllById(accountIds), UtilityAccount::getId);
        Map<UUID, Property> propertyById = accountById.isEmpty() ? Map.of()
                : byId(properties.findAllById(accountById.values().stream().map(UtilityAccount::getPropertyId)
                        .distinct().toList()), Property::getId);

        return rows.stream().map(bill -> {
            UtilityAccount account = bill.getUtilityAccountId() == null ? null
                    : accountById.get(bill.getUtilityAccountId());            Property property = account == null ? null : propertyById.get(account.getPropertyId());
            return new BillResponse(bill.getId(), bill.getStatus(), bill.getOriginalFilename(), bill.getContentType(),
                    bill.getFileSizeBytes(), bill.getUtilityAccountId(),
                    account == null ? null : account.getUtilityType(),
                    account != null ? account.getProviderName() : bill.getProviderName(),
                    account != null && account.getAccountNumber() != null ? account.getAccountNumber()
                            : bill.getAccountNumber(),
                    property == null ? null : property.getId(), property == null ? null : property.getName(),
                    bill.getPeriodStart(), bill.getPeriodEnd(), bill.getAmountDue(), bill.getDueDate(),
                    bill.getConfidence(), bill.getVerifiedAt(), bill.getCreatedAt());
        }).toList();
    }

    private void deleteFileUnlessCommitted(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(key);
                }
            }
        });
    }

    private void deleteFileAfterCommit(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Couldn't delete stored file {}; it can be removed by hand", key, e);
        }
    }

    private static byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("file", "The upload didn't finish. Try again.");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
