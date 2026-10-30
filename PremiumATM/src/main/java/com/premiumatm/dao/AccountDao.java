package com.premiumatm.dao;

import com.premiumatm.database.Database;
import com.premiumatm.model.Account;
import java.sql.*;

public class AccountDao {
    public Account findByNumber(String number) throws SQLException {
        if (number == null || number.trim().isEmpty()) return null;
        String val = number.trim();
        if (val.equals("1")) val = "1000000001";
        else if (val.equals("2")) val = "1000000002";

        String sql = "SELECT a.account_id,a.user_id,a.account_number,a.account_type,a.balance,a.currency,a.status,u.full_name,u.email,u.phone " +
                "FROM accounts a JOIN users u ON u.user_id=a.user_id " +
                "WHERE a.account_number=? OR LOWER(u.email)=LOWER(?) OR LOWER(u.full_name) LIKE LOWER(?)";
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1, val);
            p.setString(2, val);
            p.setString(3, "%" + val + "%");
            try (ResultSet r=p.executeQuery()) { return r.next()?map(r):null; }
        }
    }
    public Account findById(Connection c, long id, boolean lock) throws SQLException {
        String sql = "SELECT a.account_id,a.user_id,a.account_number,a.account_type,a.balance,a.currency,a.status,u.full_name,u.email,u.phone " +
                "FROM accounts a JOIN users u ON u.user_id=a.user_id WHERE a.account_id=?" + (lock?" FOR UPDATE":"");
        try (PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1,id); try(ResultSet r=p.executeQuery()){return r.next()?map(r):null;}
        }
    }
    private Account map(ResultSet r) throws SQLException {
        return new Account(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getBigDecimal(5),
                r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10));
    }
}
