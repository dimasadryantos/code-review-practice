package com.practice.transfers;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final NotificationClient notificationClient;

    private final Map<String, Long> processedRequests = new HashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private static final double MAX_TRANSFER = 100000;

    public TransferService(AccountRepository accountRepository,
                           TransferRepository transferRepository,
                           NotificationClient notificationClient) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.notificationClient = notificationClient;
    }

    public Transfer transfer(String requestId, String fromAccountId, String toAccountId, double amount, String note) {
        if (processedRequests.containsKey(requestId)) {
            return transferRepository.findById(processedRequests.get(requestId)).get();
        }

        Account from = accountRepository.findById(fromAccountId).get();
        Account to = accountRepository.findById(toAccountId).get();

        if (amount > MAX_TRANSFER) {
            throw new RuntimeException("Amount too large");
        }

        if (from.getBalance() < amount) {
            throw new RuntimeException("Insufficient funds");
        }

        from.setBalance(from.getBalance() - amount);
        accountRepository.save(from);

        to.setBalance(to.getBalance() + amount);
        accountRepository.save(to);

        Transfer transfer = new Transfer(fromAccountId, toAccountId, amount, note);
        transfer.setStatus("COMPLETED");
        transfer = transferRepository.save(transfer);

        processedRequests.put(requestId, transfer.getId());

        final Transfer saved = transfer;
        executor.submit(() -> {
            try {
                notificationClient.notifyTransfer(from.getOwnerId(), saved);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return transfer;
    }

    public List<Transfer> history(String accountId) {
        List<Transfer> all = transferRepository.findAll();
        List<Transfer> result = new ArrayList<>();
        for (Transfer t : all) {
            if (t.getFromAccountId().equals(accountId) || t.getToAccountId().equals(accountId)) {
                result.add(t);
            }
        }
        return result;
    }

    public double totalSent(String accountId) {
        double total = 0;
        for (Transfer t : transferRepository.findByFromAccountId(accountId)) {
            if (t.getStatus() == "COMPLETED") {
                total += t.getAmount();
            }
        }
        return total;
    }
}
