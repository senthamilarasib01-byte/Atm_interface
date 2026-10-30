package com.premiumatm.service;

import com.premiumatm.dao.*;
import com.premiumatm.database.Database;
import com.premiumatm.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.UUID;

public class BankingService {
    private final AccountDao accounts=new AccountDao();
    private final UserDao users=new UserDao();
    private final TransactionDao transactions=new TransactionDao();
    public Account login(String number,String pin) throws SQLException {
        if(number==null || number.trim().isEmpty()) throw new IllegalArgumentException("Enter a valid account number.");
        if(pin==null || pin.trim().isEmpty()) throw new IllegalArgumentException("PIN must contain 4–8 digits.");
        return users.authenticate(number.trim(), pin.trim());
    }
    public Account refresh(long accountId) throws SQLException {
        try(Connection c=Database.getConnection()){return accounts.findById(c,accountId,false);}
    }
    public List<TransactionRecord> history(long id) throws SQLException { return transactions.recent(id,100); }
    public void changePin(Account a,String oldPin,String newPin,String confirm) throws SQLException {
        if(newPin==null || !newPin.matches("\\d{4,8}")) throw new IllegalArgumentException("New PIN must be 4–8 digits.");
        if(!newPin.equals(confirm)) throw new IllegalArgumentException("PIN confirmation does not match.");
        if(newPin.equals(oldPin)) throw new IllegalArgumentException("Choose a different PIN.");
        users.changePin(a.userId(),oldPin,newPin);
    }
    public String deposit(Account a, BigDecimal amount, String method) throws SQLException {
        validateAmount(amount);
        return moneyOperation(a.accountId(),amount,"DEPOSIT",method);
    }
    public String withdraw(Account a, BigDecimal amount) throws SQLException {
        validateAmount(amount);
        return moneyOperation(a.accountId(),amount,"WITHDRAWAL","ATM cash withdrawal (simulation)");
    }
    private String moneyOperation(long id,BigDecimal amount,String type,String description) throws SQLException {
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try {
                Account a=accounts.findById(c,id,true);
                if(a==null || !"ACTIVE".equals(a.status())) throw new IllegalArgumentException("Account is unavailable.");
                if("WITHDRAWAL".equals(type) && a.balance().compareTo(amount)<0) throw new IllegalArgumentException("Insufficient available balance.");
                String op="DEPOSIT".equals(type)?"+":"-";
                try(PreparedStatement p=c.prepareStatement("UPDATE accounts SET balance=balance "+op+" ? WHERE account_id=?")){
                    p.setBigDecimal(1,amount);p.setLong(2,id);p.executeUpdate();
                }
                String ref=reference();
                insertTransaction(c,ref,id,null,type,amount,description);
                c.commit(); return ref;
            } catch(Exception e){c.rollback();if(e instanceof SQLException s)throw s;if(e instanceof RuntimeException r)throw r;throw new SQLException(e);}
        }
    }
    public String transfer(Account from,String recipientNumber,BigDecimal amount) throws SQLException {
        validateAmount(amount);
        if(recipientNumber==null || !recipientNumber.matches("\\d{8,20}")) throw new IllegalArgumentException("Enter a valid recipient account number.");
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                Account recipient;
                try(PreparedStatement p=c.prepareStatement("SELECT account_id FROM accounts WHERE account_number=? AND status='ACTIVE'")){
                    p.setString(1,recipientNumber);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Recipient account not found or inactive.");recipient=accounts.findById(c,r.getLong(1),false);}
                }
                if(recipient.accountId()==from.accountId())throw new IllegalArgumentException("You cannot transfer to the same account.");
                // Lock in ascending ID order to reduce deadlock risk.
                long first=Math.min(from.accountId(),recipient.accountId()), second=Math.max(from.accountId(),recipient.accountId());
                Account one=accounts.findById(c,first,true), two=accounts.findById(c,second,true);
                Account sender=from.accountId()==first?one:two;
                Account receiver=recipient.accountId()==first?one:two;
                if(sender.balance().compareTo(amount)<0)throw new IllegalArgumentException("Insufficient available balance.");
                try(PreparedStatement p=c.prepareStatement("UPDATE accounts SET balance=balance-? WHERE account_id=?")){p.setBigDecimal(1,amount);p.setLong(2,sender.accountId());p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("UPDATE accounts SET balance=balance+? WHERE account_id=?")){p.setBigDecimal(1,amount);p.setLong(2,receiver.accountId());p.executeUpdate();}
                String ref=reference();
                insertTransaction(c,ref,sender.accountId(),receiver.accountId(),"TRANSFER_OUT",amount,"Transfer to ••••"+recipientNumber.substring(Math.max(0,recipientNumber.length()-4)));
                insertTransaction(c,ref+"-IN",receiver.accountId(),sender.accountId(),"TRANSFER_IN",amount,"Transfer received");
                c.commit();return ref;
            }catch(Exception e){c.rollback();if(e instanceof SQLException s)throw s;if(e instanceof RuntimeException r)throw r;throw new SQLException(e);}
        }
    }
    private void insertTransaction(Connection c,String ref,long id,Long related,String type,BigDecimal amount,String desc)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO transactions(reference_id,account_id,related_account_id,transaction_type,amount,status,description) VALUES(?,?,?,?,?,'SUCCESS',?)")){
            p.setString(1,ref);p.setLong(2,id);if(related==null)p.setNull(3,Types.BIGINT);else p.setLong(3,related);
            p.setString(4,type);p.setBigDecimal(5,amount);p.setString(6,desc);p.executeUpdate();
        }
    }
    private void validateAmount(BigDecimal a){
        if(a==null || a.scale()>2 || a.compareTo(new BigDecimal("0.01"))<0 || a.compareTo(new BigDecimal("1000000.00"))>0)
            throw new IllegalArgumentException("Enter an amount between ₹0.01 and ₹10,00,000.00 with at most 2 decimal places.");
    }
    private String reference(){return "AUR-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);}
}
