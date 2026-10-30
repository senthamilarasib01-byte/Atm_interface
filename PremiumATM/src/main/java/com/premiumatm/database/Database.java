package com.premiumatm.database;

import com.premiumatm.util.PinHasher;
import java.io.File;
import java.sql.*;

public final class Database {
    private static final String EMBEDDED_URL = "jdbc:h2:./data/premium_atm;AUTO_SERVER=TRUE;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    private static volatile boolean useEmbedded = false;

    private Database() {}

    public static Connection getConnection() throws SQLException {
        if (!useEmbedded) {
            String url = System.getenv("DB_URL");
            String user = System.getenv("DB_USER");
            String pass = System.getenv("DB_PASSWORD");
            if (url != null && user != null && pass != null) {
                try {
                    return DriverManager.getConnection(url, user, pass);
                } catch (SQLException e) {
                    System.err.println("Warning: Could not connect to external MySQL (" + e.getMessage() + "). Falling back to local embedded database.");
                    useEmbedded = true;
                }
            } else {
                useEmbedded = true;
            }
        }
        new File("./data").mkdirs();
        return DriverManager.getConnection(EMBEDDED_URL, "sa", "");
    }

    /** Creates clearly labelled demo accounts on first run when schema exists or embedded DB is initialized. */
    public static void ensureDemoUser() {
        try (Connection c = getConnection()) {
            initSchema(c);
            try (PreparedStatement p = c.prepareStatement("SELECT user_id FROM users WHERE email=?")) {
                p.setString(1, "demo@aurel.example");
                try (ResultSet r = p.executeQuery()) { if (r.next()) return; }
            }
            PinHasher.Hash h = PinHasher.hash("1234");
            c.setAutoCommit(false);
            try {
                long uid;
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO users(full_name,email,phone,pin_hash,pin_salt) VALUES(?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, "Alex Morgan"); p.setString(2, "demo@aurel.example");
                    p.setString(3, "+91 90000 00000"); p.setString(4, h.hash()); p.setString(5, h.salt());
                    p.executeUpdate(); try (ResultSet k = p.getGeneratedKeys()) { k.next(); uid = k.getLong(1); }
                }
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO accounts(user_id,account_number,account_type,balance) VALUES(?,?,?,?)")) {
                    p.setLong(1, uid); p.setString(2, "1000000001"); p.setString(3, "SAVINGS"); p.setBigDecimal(4, new java.math.BigDecimal("25000.00")); p.executeUpdate();
                }

                // Add secondary demo account for testing transfers
                long uid2;
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO users(full_name,email,phone,pin_hash,pin_salt) VALUES(?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, "Jordan Reed"); p.setString(2, "jordan@aurel.example");
                    p.setString(3, "+91 91111 22222"); p.setString(4, h.hash()); p.setString(5, h.salt());
                    p.executeUpdate(); try (ResultSet k = p.getGeneratedKeys()) { k.next(); uid2 = k.getLong(1); }
                }
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO accounts(user_id,account_number,account_type,balance) VALUES(?,?,?,?)")) {
                    p.setLong(1, uid2); p.setString(2, "1000000002"); p.setString(3, "CURRENT"); p.setBigDecimal(4, new java.math.BigDecimal("10000.00")); p.executeUpdate();
                }

                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (Exception e) {
            System.err.println("Database/demo setup: " + e.getMessage());
        }
    }

    private static void initSchema(Connection c) {
        String usersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "user_id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "full_name VARCHAR(120) NOT NULL," +
                "email VARCHAR(190) NOT NULL UNIQUE," +
                "phone VARCHAR(30)," +
                "pin_hash VARCHAR(256) NOT NULL," +
                "pin_salt VARCHAR(128) NOT NULL," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";

        String accountsTable = "CREATE TABLE IF NOT EXISTS accounts (" +
                "account_id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "user_id BIGINT NOT NULL," +
                "account_number VARCHAR(20) NOT NULL UNIQUE," +
                "account_type VARCHAR(20) NOT NULL DEFAULT 'SAVINGS'," +
                "balance DECIMAL(15,2) NOT NULL DEFAULT 0.00," +
                "currency CHAR(3) NOT NULL DEFAULT 'INR'," +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";

        String transactionsTable = "CREATE TABLE IF NOT EXISTS transactions (" +
                "transaction_id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "reference_id VARCHAR(40) NOT NULL UNIQUE," +
                "account_id BIGINT NOT NULL," +
                "related_account_id BIGINT NULL," +
                "transaction_type VARCHAR(20) NOT NULL," +
                "amount DECIMAL(15,2) NOT NULL," +
                "status VARCHAR(20) NOT NULL," +
                "description VARCHAR(255)," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";

        try (Statement s = c.createStatement()) {
            s.execute(usersTable);
            s.execute(accountsTable);
            s.execute(transactionsTable);
        } catch (Exception ignored) {}
    }
}
