package com.practice.transfers;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Date;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fromAccountId;
    private String toAccountId;
    private double amount;
    private String status;
    private Date createdAt;
    private String note;

    public Transfer() {
    }

    public Transfer(String fromAccountId, String toAccountId, double amount, String note) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.note = note;
        this.status = "PENDING";
        this.createdAt = new Date();
    }

    public Long getId() { return id; }
    public String getFromAccountId() { return fromAccountId; }
    public String getToAccountId() { return toAccountId; }
    public double getAmount() { return amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public String getNote() { return note; }
}
