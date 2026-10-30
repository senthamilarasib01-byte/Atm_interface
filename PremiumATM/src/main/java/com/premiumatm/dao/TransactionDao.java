package com.premiumatm.dao;
import com.premiumatm.database.Database;
import com.premiumatm.model.TransactionRecord;
import java.sql.*;
import java.util.*;

public class TransactionDao {
    public List<TransactionRecord> recent(long accountId, int limit) throws SQLException {
        List<TransactionRecord> out=new ArrayList<>();
        try(Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement(
                "SELECT reference_id,transaction_type,amount,status,description,created_at FROM transactions WHERE account_id=? ORDER BY created_at DESC LIMIT ?")) {
            p.setLong(1,accountId);p.setInt(2,Math.max(1,Math.min(limit,100)));
            try(ResultSet r=p.executeQuery()){while(r.next())out.add(new TransactionRecord(r.getString(1),r.getString(2),r.getBigDecimal(3),r.getString(4),r.getString(5),r.getTimestamp(6).toLocalDateTime()));}
        } return out;
    }
}
