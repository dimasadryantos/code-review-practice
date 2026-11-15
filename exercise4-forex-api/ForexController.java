package com.practice.forex;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PR #512 — "Forex module: quotes and conversions for multi-currency accounts"
 *
 * Review the API design and the implementation. ~10 minutes.
 * Then: how would YOU design these endpoints? (This is the question a candidate reported.)
 */
@RestController
@RequestMapping("/forex")
public class ForexController {

    private final ForexService forexService;

    public ForexController(ForexService forexService) {
        this.forexService = forexService;
    }

    // Get the current rate, e.g. /forex/getRate?from=EUR&to=USD
    @GetMapping("/getRate")
    public double getRate(@RequestParam String from, @RequestParam String to) {
        return forexService.currentRate(from, to);
    }

    // Historic rates: /forex/rates/EUR/USD/2026-01-01/2026-02-01
    @GetMapping("/rates/{from}/{to}/{start}/{end}")
    public List<Map<String, Object>> history(@PathVariable String from, @PathVariable String to,
                                             @PathVariable String start, @PathVariable String end) {
        return forexService.history(from, to, start, end);
    }

    // Convert money between a customer's sub-accounts. Called by the app on button press.
    @GetMapping("/convert")
    public ResponseEntity<String> convert(@RequestParam String accountId,
                                          @RequestParam String from,
                                          @RequestParam String to,
                                          @RequestParam double amount) {
        try {
            forexService.convert(accountId, from, to, amount);
            return ResponseEntity.ok("converted");
        } catch (Exception e) {
            return ResponseEntity.ok("error: " + e.getMessage());
        }
    }

    // Create or update a rate alert. If id is given we update, otherwise create.
    @PostMapping("/alert")
    public ResponseEntity<Object> alert(@RequestParam(required = false) Long id,
                                        @RequestBody Map<String, Object> body) {
        if (id != null) {
            forexService.updateAlert(id, body);
            return ResponseEntity.ok(body);
        }
        Long newId = forexService.createAlert(body);
        return ResponseEntity.ok(Map.of("id", newId));
    }

    // Delete alert
    @GetMapping("/alert/delete")
    public String deleteAlert(@RequestParam Long id) {
        forexService.deleteAlert(id);
        return "OK";
    }

    // Admin: list all conversions of all customers
    @GetMapping("/conversions")
    public List<Map<String, Object>> allConversions() {
        return forexService.allConversions();
    }
}
