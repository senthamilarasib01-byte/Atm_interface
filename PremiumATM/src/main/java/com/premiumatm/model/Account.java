package com.premiumatm.model;
import java.math.BigDecimal;
public record Account(long accountId, long userId, String accountNumber, String accountType,
                      BigDecimal balance, String currency, String status, String fullName, String email, String phone) {}
