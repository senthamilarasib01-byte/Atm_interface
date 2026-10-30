# Premium ATM — JavaFX + MySQL

A premium navy-and-gold **banking simulation** built with Java 21, JavaFX, Maven, JDBC and MySQL. It does not connect to a real bank and does not dispense cash.

## Requirements
- macOS (Apple Silicon or Intel), JDK 21, Maven 3.9+, MySQL 8+
- VS Code with Extension Pack for Java (or another Java IDE)

Check tools:
```bash
java -version
mvn -version
mysql --version
```

## 1. Create the database
Log in to MySQL as an administrator and run `sql/schema.sql`. It creates the database, tables, a least-privilege app user, and a demo account. Change the sample password before using it anywhere shared.
```bash
mysql -u root -p < sql/schema.sql
```

The sample account number is `1000000001`, demo PIN is `1234`. The app stores a PBKDF2 hash, not the PIN. This demo PIN is intentionally public and must never be reused.

## 2. Configure environment variables
Set environment variables in the same terminal that launches Maven. Do not commit secrets.
```bash
export DB_URL='jdbc:mysql://localhost:3306/premium_atm?useSSL=false&serverTimezone=UTC'
export DB_USER='premium_atm_app'
export DB_PASSWORD='change_me'
```
Use the actual password you chose in `sql/schema.sql`. The app refuses to start database-backed operations when required configuration is missing.

## 3. Run
From the project directory:
```bash
mvn clean javafx:run
```
If JavaFX cannot launch on macOS, verify `java -version` and `mvn -version` both use JDK 21. In VS Code choose **File → Open Folder** and select this folder, then run the command in the integrated terminal.

## Demo flow
1. Get Started → enter account number and PIN.
2. Dashboard shows live balance and recent transactions.
3. Deposit, withdraw, transfer, history, account details and settings use the database.
4. Use the sidebar Logout action to end the session.

Transfers are only permitted between accounts in this demo database. All money operations use JDBC transactions and row locking. A database outage is shown as an error rather than silently substituting fake balances.

## Security and demo limitations
- PBKDF2-HMAC-SHA-256 PIN hashing with per-user salt.
- Prepared statements and database transactions for money operations.
- PIN input is masked by default; account numbers are masked in account details.
- This is an educational demo, not production banking software. Production needs professional security review, rate limiting/lockout, MFA, secret management, TLS certificate verification, monitoring, compliance controls and threat modelling.
- The sample schema creates one demo account. Create additional demo accounts through controlled SQL/admin tooling for transfer testing; never add plaintext PIN columns.
