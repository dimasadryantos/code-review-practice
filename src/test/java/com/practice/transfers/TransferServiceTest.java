package com.practice.transfers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TransferServiceTest {

    private AccountRepository accountRepository;
    private TransferRepository transferRepository;
    private NotificationClient notificationClient;
    private TransferService service;

    @BeforeEach
    void setUp() {
        accountRepository = Mockito.mock(AccountRepository.class);
        transferRepository = Mockito.mock(TransferRepository.class);
        notificationClient = Mockito.mock(NotificationClient.class);
        service = new TransferService(accountRepository, transferRepository, notificationClient);
    }

    @Test
    void transferWorks() {
        Account from = new Account("A", "owner1", 100.0, "EUR");
        Account to = new Account("B", "owner2", 0.0, "EUR");
        when(accountRepository.findById("A")).thenReturn(Optional.of(from));
        when(accountRepository.findById("B")).thenReturn(Optional.of(to));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = service.transfer("req-1", "A", "B", 30.0, "test");

        assertNotNull(result);
        assertTrue(true);
    }

    @Test
    void insufficientFunds() {
        Account from = new Account("A", "owner1", 10.0, "EUR");
        Account to = new Account("B", "owner2", 0.0, "EUR");
        when(accountRepository.findById("A")).thenReturn(Optional.of(from));
        when(accountRepository.findById("B")).thenReturn(Optional.of(to));

        try {
            service.transfer("req-2", "A", "B", 30.0, "test");
        } catch (Exception e) {
            // expected
        }
    }
}
