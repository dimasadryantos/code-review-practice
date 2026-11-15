package com.practice.transfers;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private String id;

    private String ownerId;

    private double balance;

    private String currency;

    private boolean frozen;

    public Account() {
    }

    public Account(String id, String ownerId, double balance, String currency) {
        this.id = id;
        this.ownerId = ownerId;
        this.balance = balance;
        this.currency = currency;
    }

    public String getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public String getCurrency() { return currency; }
    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
}
