package com.premiumatm.dao;
import com.premiumatm.database.Database;
import com.premiumatm.model.Account;
import com.premiumatm.util.PinHasher;
import java.sql.*;

public class UserDao {
    public Account authenticate(String accountNumber, String pin) throws SQLException {
        String sql="SELECT a.account_id FROM accounts a JOIN users u ON u.user_id=a.user_id WHERE a.account_number=? AND a.status='ACTIVE' AND u.pin_hash=? AND u.pin_salt=?";
        AccountDao dao=new AccountDao();
        Account account=dao.findByNumber(accountNumber);
        if(account==null || !"ACTIVE".equals(account.status())) return null;
        try(Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement(
                "SELECT pin_hash,pin_salt FROM users WHERE user_id=?")) {
            p.setLong(1,account.userId());
            try(ResultSet r=p.executeQuery()) {
                if(!r.next() || !PinHasher.verify(pin,r.getString(1),r.getString(2))) return null;
            }
        }
        return account;
    }
    public void changePin(long userId, String oldPin, String newPin) throws SQLException {
        try(Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("SELECT pin_hash,pin_salt FROM users WHERE user_id=?")) {
            p.setLong(1,userId);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next() || !PinHasher.verify(oldPin,r.getString(1),r.getString(2))) throw new IllegalArgumentException("Current PIN is incorrect.");
            }
        }
        PinHasher.Hash h=PinHasher.hash(newPin);
        try(Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("UPDATE users SET pin_hash=?,pin_salt=? WHERE user_id=?")) {
            p.setString(1,h.hash());p.setString(2,h.salt());p.setLong(3,userId);p.executeUpdate();
        }
    }
}
