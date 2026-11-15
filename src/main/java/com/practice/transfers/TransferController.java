package com.practice.transfers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<Object> transfer(@RequestBody Map<String, Object> body) {
        try {
            String requestId = (String) body.get("requestId");
            String from = (String) body.get("from");
            String to = (String) body.get("to");
            double amount = Double.parseDouble(body.get("amount").toString());
            String note = (String) body.get("note");

            Transfer t = transferService.transfer(requestId, from, to, amount, note);
            return ResponseEntity.ok(t);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage() + "\n" + e.getStackTrace()[0]);
        }
    }

    @GetMapping("/transfer/history")
    public List<Transfer> history(@RequestParam String accountId) {
        return transferService.history(accountId);
    }

    @GetMapping("/accounts/{id}/totalSent")
    public double totalSent(@PathVariable String id) {
        return transferService.totalSent(id);
    }
}
