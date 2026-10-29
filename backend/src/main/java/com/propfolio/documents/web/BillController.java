package com.propfolio.documents.web;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.propfolio.documents.BillService;
import com.propfolio.documents.BillStatus;
import com.propfolio.documents.web.BillDtos.BillFile;
import com.propfolio.documents.web.BillDtos.BillResponse;

/**
 * Bill files: upload one per request (multipart field "file"), list, view, download, delete.
 */
@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse upload(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID utilityAccountId) {
        return billService.upload(file, utilityAccountId);
    }

    /** Newest first. Both filters are optional. */
    @GetMapping
    public List<BillResponse> list(@RequestParam(required = false) BillStatus status,
            @RequestParam(required = false) UUID propertyId) {
        return billService.list(status, propertyId);
    }

    @GetMapping("/{billId}")
    public BillResponse get(@PathVariable UUID billId) {
        return billService.get(billId);
    }

    /** The original file, shown inline in the browser. Never cached. */
    @GetMapping("/{billId}/file")
    public ResponseEntity<Resource> file(@PathVariable UUID billId) {
        BillFile file = billService.openFile(billId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.filename(), StandardCharsets.UTF_8).build()
                                .toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new InputStreamResource(file.content()));
    }

    @DeleteMapping("/{billId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID billId) {
        billService.delete(billId);
    }
}
