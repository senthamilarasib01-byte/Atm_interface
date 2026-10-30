package com.premiumatm.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record TransactionRecord(String referenceId, String type, BigDecimal amount, String status,
                                String description, LocalDateTime createdAt) {}
