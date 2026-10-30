package com.premiumatm.web;

import com.premiumatm.database.Database;
import com.premiumatm.model.Account;
import com.premiumatm.model.TransactionRecord;
import com.premiumatm.service.BankingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebServer {
    private static final int PORT = 8080;
    private static final BankingService service = new BankingService();
    private static final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public static void main(String[] args) throws Exception {
        Database.ensureDemoUser();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/overview", new OverviewHandler());
        server.createContext("/api/deposit", new DepositHandler());
        server.createContext("/api/withdraw", new WithdrawHandler());
        server.createContext("/api/transfer", new TransferHandler());
        server.createContext("/api/change-pin", new ChangePinHandler());
        server.setExecutor(null);

        server.start();
        System.out.println("==================================================");
        System.out.println("  AUREL — Premium ATM Web Server is Running!      ");
        System.out.println("  Open your browser at: http://localhost:" + PORT + "  ");
        System.out.println("==================================================");

        // Auto-open browser on macOS if running interactively
        try {
            Runtime.getRuntime().exec(new String[]{"open", "http://localhost:" + PORT});
        } catch (Exception ignored) {}
    }

    private static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, "text/plain", "Method Not Allowed");
                return;
            }
            byte[] html = getHtmlContent().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, html.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html);
            }
        }
    }

    private static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, "application/json", "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            String body = readBody(ex);
            String number = getJsonField(body, "accountNumber");
            String pin = getJsonField(body, "pin");
            if (number != null) number = number.trim();
            if (pin != null) pin = pin.trim();

            try {
                Account account = service.login(number, pin);
                if (account == null) {
                    // Fallback to primary demo account if user typed demo keywords
                    if (number != null && (number.isEmpty() || number.equalsIgnoreCase("demo") || number.equals("1"))) {
                        account = service.login("1000000001", "1234");
                    }
                }
                if (account == null) {
                    sendJson(ex, 401, "{\"success\":false,\"error\":\"Account number or PIN is incorrect.\"}");
                    return;
                }
                String resp = String.format(Locale.ROOT,
                        "{\"success\":true,\"account\":{\"accountId\":%d,\"accountNumber\":\"%s\",\"accountType\":\"%s\",\"balance\":%.2f,\"formattedBalance\":\"%s\",\"currency\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"status\":\"%s\"}}",
                        account.accountId(), account.accountNumber(), account.accountType(),
                        account.balance().doubleValue(), money.format(account.balance()),
                        account.currency(), account.fullName(), account.email(), account.phone(), account.status());
                sendJson(ex, 200, resp);
            } catch (Exception e) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private static class OverviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String query = ex.getRequestURI().getQuery();
            String idStr = getQueryParam(query, "accountId");
            if (idStr == null) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"Missing accountId parameter\"}");
                return;
            }
            try {
                long accountId = Long.parseLong(idStr);
                Account account = service.refresh(accountId);
                List<TransactionRecord> history = service.history(accountId);

                StringBuilder txJson = new StringBuilder("[");
                for (int i = 0; i < history.size(); i++) {
                    TransactionRecord t = history.get(i);
                    if (i > 0) txJson.append(",");
                    txJson.append(String.format(Locale.ROOT,
                            "{\"reference\":\"%s\",\"type\":\"%s\",\"amount\":%.2f,\"formattedAmount\":\"%s\",\"status\":\"%s\",\"description\":\"%s\",\"date\":\"%s\"}",
                            t.referenceId(), t.type(), t.amount().doubleValue(),
                            money.format(t.amount()), t.status(), escape(t.description()), t.createdAt().toString()));
                }
                txJson.append("]");

                String resp = String.format(Locale.ROOT,
                        "{\"success\":true,\"account\":{\"accountId\":%d,\"accountNumber\":\"%s\",\"accountType\":\"%s\",\"balance\":%.2f,\"formattedBalance\":\"%s\",\"currency\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"status\":\"%s\"},\"transactions\":%s}",
                        account.accountId(), account.accountNumber(), account.accountType(),
                        account.balance().doubleValue(), money.format(account.balance()),
                        account.currency(), account.fullName(), account.email(), account.phone(), account.status(),
                        txJson.toString());
                sendJson(ex, 200, resp);
            } catch (Exception e) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private static class DepositHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String body = readBody(ex);
            try {
                long accountId = Long.parseLong(getJsonField(body, "accountId"));
                BigDecimal amount = new BigDecimal(getJsonField(body, "amount"));
                String method = getJsonField(body, "method");
                if (method == null) method = "Cash deposit (simulation)";

                Account account = service.refresh(accountId);
                String ref = service.deposit(account, amount, method);
                sendJson(ex, 200, "{\"success\":true,\"reference\":\"" + ref + "\"}");
            } catch (Exception e) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private static class WithdrawHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String body = readBody(ex);
            try {
                long accountId = Long.parseLong(getJsonField(body, "accountId"));
                BigDecimal amount = new BigDecimal(getJsonField(body, "amount"));

                Account account = service.refresh(accountId);
                String ref = service.withdraw(account, amount);
                sendJson(ex, 200, "{\"success\":true,\"reference\":\"" + ref + "\"}");
            } catch (Exception e) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private static class TransferHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String body = readBody(ex);
            try {
                long accountId = Long.parseLong(getJsonField(body, "accountId"));
                String recipientNumber = getJsonField(body, "recipientNumber");
                BigDecimal amount = new BigDecimal(getJsonField(body, "amount"));

                Account account = service.refresh(accountId);
                String ref = service.transfer(account, recipientNumber, amount);
                sendJson(ex, 200, "{\"success\":true,\"reference\":\"" + ref + "\"}");
            } catch (Exception e) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private static class ChangePinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String body = readBody(ex);
            try {
                long accountId = Long.parseLong(getJsonField(body, "accountId"));
                String oldPin = getJsonField(body, "oldPin");
                String newPin = getJsonField(body, "newPin");
                String confirm = getJsonField(body, "confirm");

                Account account = service.refresh(accountId);
                service.changePin(account, oldPin, newPin, confirm);
                sendJson(ex, 200, "{\"success\":true,\"message\":\"PIN updated successfully.\"}");
            } catch (Exception e) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        sendResponse(ex, code, "application/json; charset=UTF-8", json);
    }

    private static void sendResponse(HttpExchange ex, int code, String contentType, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getJsonField(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        Pattern numP = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.]+)");
        Matcher numM = numP.matcher(json);
        if (numM.find()) return numM.group(1);
        return null;
    }

    private static String getQueryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length == 2 && parts[0].equals(key)) return parts[1];
        }
        return null;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static String getHtmlContent() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AUREL — Private Banking</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
  :root {
    --bg-dark: #0B1535;
    --sidebar-bg: #0A122B;
    --card-bg: linear-gradient(135deg, #19294F, #121E3E);
    --gold: #D4AF37;
    --gold-light: #E5C75D;
    --gold-hover: #F0D775;
    --border: #2B3B60;
    --text-white: #F5F5F5;
    --text-muted: #AAB6D0;
    --text-dim: #7181A3;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Plus Jakarta Sans', -apple-system, sans-serif; }
  body { background-color: var(--bg-dark); color: var(--text-white); min-height: 100vh; display: flex; flex-direction: column; }
  
  /* Welcome Screen */
  .view-container { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 24px; text-align: center; }
  .brand-title { font-size: 32px; font-weight: 800; color: var(--gold); letter-spacing: 8px; margin-bottom: 8px; }
  .eyebrow { color: #B7C1D8; font-size: 11px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 24px; }
  .hero-headline { font-size: 48px; font-weight: 700; line-height: 1.2; margin-bottom: 16px; }
  .hero-sub { color: var(--text-muted); font-size: 16px; max-width: 480px; margin-bottom: 32px; }
  
  /* Buttons */
  .btn-gold { 
    background: linear-gradient(90deg, var(--gold-light), var(--gold)); 
    color: #111A31; 
    border: none; 
    font-size: 14px; 
    font-weight: 800; 
    padding: 14px 28px; 
    border-radius: 12px; 
    cursor: pointer; 
    transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1); 
    box-shadow: 0 4px 15px rgba(212,175,55,0.2); 
    position: relative;
  }
  .btn-gold:hover { 
    background: linear-gradient(90deg, #F5DC7E, #E5C75D); 
    transform: translateY(-3px) scale(1.02); 
    box-shadow: 0 10px 25px rgba(212,175,55,0.4), 0 0 18px rgba(229,199,93,0.25); 
  }
  .btn-gold:active { 
    transform: translateY(-1px) scale(0.99); 
    box-shadow: 0 4px 12px rgba(212,175,55,0.3); 
  }
  .btn-quiet { 
    background: transparent; 
    color: var(--text-muted); 
    border: none; 
    font-size: 13px; 
    font-weight: 600; 
    padding: 10px 16px; 
    border-radius: 8px; 
    cursor: pointer; 
    transition: all 0.25s ease; 
  }
  .btn-quiet:hover { 
    background: rgba(212,175,55,0.12); 
    color: var(--gold-light); 
    transform: translateY(-1px); 
  }
  
  /* Card Panels */
  .panel-card { 
    background: var(--card-bg); 
    border: 1px solid var(--border); 
    border-radius: 24px; 
    padding: 40px; 
    width: 100%; 
    max-width: 460px; 
    box-shadow: 0 12px 35px rgba(0,0,0,0.3); 
    transition: all 0.35s ease; 
  }
  .panel-card:hover { 
    border-color: #3C4E79; 
    box-shadow: 0 16px 40px rgba(0,0,0,0.42); 
  }
  .input-group { margin-bottom: 20px; text-align: left; }
  .input-label { display: block; font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 8px; }
  .input-field { 
    width: 100%; 
    background: #0E1936; 
    border: 1px solid #344466; 
    border-radius: 12px; 
    color: #fff; 
    padding: 14px 16px; 
    font-size: 15px; 
    outline: none; 
    transition: all 0.25s ease; 
  }
  .input-field:hover { 
    border-color: #4A5F8E; 
    background: #111D3E; 
  }
  .input-field:focus { 
    border-color: var(--gold); 
    background: #0E1936; 
    box-shadow: 0 0 0 3px rgba(212,175,55,0.2); 
  }
  .demo-hint { font-size: 12px; color: var(--text-dim); margin-top: 16px; }
  .error-text { color: #FF7B7B; font-size: 13px; margin-top: 12px; min-height: 20px; }
  
  /* App Shell & Dashboard */
  .app-shell { display: none; min-height: 100vh; flex-direction: row; }
  .sidebar { width: 260px; background: var(--sidebar-bg); border-right: 1px solid #1E2B4B; padding: 28px 18px; display: flex; flex-direction: column; }
  .sidebar-nav { margin-top: 28px; flex: 1; }
  .nav-item { 
    width: 100%; 
    text-align: left; 
    background: none; 
    border: none; 
    color: var(--text-muted); 
    padding: 12px 16px; 
    font-size: 13px; 
    font-weight: 600; 
    border-radius: 10px; 
    cursor: pointer; 
    display: flex; 
    align-items: center; 
    gap: 10px; 
    margin-bottom: 4px; 
    transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1); 
  }
  .nav-item:hover, .nav-item.active { 
    background: #162447; 
    color: var(--gold-light); 
    transform: translateX(6px); 
    box-shadow: inset 3px 0 0 var(--gold); 
  }
  .main-content { flex: 1; display: flex; flex-direction: column; }
  .topbar { padding: 20px 40px; border-bottom: 1px solid #1E2B4B; display: flex; justify-content: space-between; align-items: center; }
  .secure-pill { 
    font-size: 11px; 
    font-weight: 700; 
    color: var(--gold); 
    border: 1px solid #6E5B23; 
    border-radius: 20px; 
    padding: 6px 14px; 
    background: rgba(212,175,55,0.05); 
    transition: all 0.3s ease; 
  }
  .secure-pill:hover { 
    background: rgba(212,175,55,0.15); 
    border-color: var(--gold); 
    box-shadow: 0 0 12px rgba(212,175,55,0.25); 
  }
  .dashboard-body { padding: 40px; overflow-y: auto; flex: 1; max-width: 1080px; width: 100%; }
  
  /* Balance Card */
  .balance-card { 
    background: linear-gradient(135deg, #1C2D54, #152342); 
    border: 1px solid #695B2B; 
    border-radius: 22px; 
    padding: 32px; 
    display: flex; 
    justify-content: space-between; 
    align-items: center; 
    margin-bottom: 32px; 
    transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1); 
    cursor: default; 
  }
  .balance-card:hover { 
    transform: translateY(-4px); 
    border-color: var(--gold); 
    box-shadow: 0 16px 36px rgba(0, 0, 0, 0.45), 0 0 25px rgba(212, 175, 55, 0.2); 
  }
  .balance-amount { font-size: 42px; font-weight: 800; color: #fff; margin: 8px 0; }
  .gold-badge { 
    font-size: 11px; 
    font-weight: 800; 
    color: var(--gold); 
    border: 1px solid #8B742C; 
    border-radius: 12px; 
    padding: 4px 12px; 
    text-transform: uppercase; 
    transition: all 0.25s ease; 
  }
  .gold-badge:hover { 
    background: rgba(212,175,55,0.15); 
    border-color: var(--gold); 
  }
  
  /* Action Cards */
  .quick-actions { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 36px; }
  .action-btn { 
    background: #162447; 
    border: 1px solid #2A3B60; 
    border-radius: 16px; 
    padding: 22px; 
    color: #E9EDF7; 
    font-size: 14px; 
    font-weight: 700; 
    cursor: pointer; 
    transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1); 
    text-align: left; 
    position: relative; 
    overflow: hidden; 
  }
  .action-btn:hover { 
    border-color: var(--gold); 
    background: linear-gradient(135deg, #1D2E55, #162447); 
    transform: translateY(-4px) scale(1.03); 
    box-shadow: 0 12px 28px rgba(0,0,0,0.4), 0 0 20px rgba(212, 175, 55, 0.25); 
    color: #FFF; 
  }
  .action-btn:active { 
    transform: translateY(-1px) scale(0.99); 
  }
  
  /* Transactions Table */
  .tx-table { width: 100%; border-collapse: collapse; margin-top: 16px; }
  .tx-row { 
    background: #131F3D; 
    border-bottom: 8px solid var(--bg-dark); 
    border-radius: 12px; 
    transition: all 0.25s ease; 
    cursor: pointer; 
  }
  .tx-row:hover { 
    background: #19284F; 
    transform: translateX(6px); 
    box-shadow: 0 6px 18px rgba(0,0,0,0.35), inset 4px 0 0 var(--gold); 
  }
  .tx-row td { padding: 18px 20px; font-size: 14px; }
  .tx-row td:first-child { border-top-left-radius: 12px; border-bottom-left-radius: 12px; }
  .tx-row td:last-child { border-top-right-radius: 12px; border-bottom-right-radius: 12px; text-align: right; }
  .tx-amount { font-weight: 700; color: #E8D27B; transition: all 0.2s ease; }
  .tx-row:hover .tx-amount { color: #FFF; text-shadow: 0 0 8px rgba(212,175,55,0.45); }
  
  /* Form containers */
  .form-panel { 
    background: #142040; 
    border: 1px solid #2B3B60; 
    border-radius: 20px; 
    padding: 32px; 
    max-width: 640px; 
    transition: all 0.35s ease; 
  }
  .form-panel:hover { 
    border-color: #3C4F7B; 
    box-shadow: 0 12px 30px rgba(0,0,0,0.35); 
  }
  .form-panel h3 { font-size: 20px; margin-bottom: 8px; }
  .form-panel p { color: var(--text-muted); font-size: 14px; margin-bottom: 24px; }
  .quick-amounts { display: flex; gap: 8px; margin-bottom: 16px; }
  .chip-btn { 
    background: #0E1936; 
    border: 1px solid #344466; 
    color: var(--text-muted); 
    border-radius: 8px; 
    padding: 8px 16px; 
    font-size: 13px; 
    font-weight: 600; 
    cursor: pointer; 
    transition: all 0.25s ease; 
  }
  .chip-btn:hover { 
    border-color: var(--gold); 
    color: #fff; 
    background: #1B294A; 
    transform: translateY(-2px); 
    box-shadow: 0 4px 12px rgba(212,175,55,0.22); 
  }
</style>
</head>
<body>

<!-- WELCOME SCREEN -->
<div id="welcomeView" class="view-container">
  <div class="brand-title">AUREL</div>
  <div class="eyebrow">Private Banking · Reimagined</div>
  <h1 class="hero-headline">Your money.<br>Your momentum.</h1>
  <p class="hero-sub">A refined, secure space to manage everyday banking on localhost.</p>
  <button class="btn-gold" onclick="showLogin()">Get Started &nbsp;→</button>
  <div class="demo-hint" style="margin-top: 32px;">DEMO ENVIRONMENT • Runs locally with zero external configuration</div>
</div>

<!-- LOGIN SCREEN -->
<div id="loginView" class="view-container" style="display: none;">
  <div class="panel-card">
    <div class="brand-title" style="font-size: 24px;">AUREL</div>
    <div class="eyebrow" style="margin-bottom: 18px;">Sign in to your account</div>
    
    <div class="input-group">
      <label class="input-label">Account Number</label>
      <input id="loginNumber" class="input-field" type="text" value="1000000001">
    </div>
    <div class="input-group">
      <label class="input-label">Secure PIN</label>
      <input id="loginPin" class="input-field" type="password" value="1234">
      <div style="margin-top: 8px; text-align: left;">
        <label style="font-size: 12px; color: var(--text-muted); cursor: pointer;">
          <input type="checkbox" onchange="togglePin(this)"> Show PIN
        </label>
      </div>
    </div>
    
    <button class="btn-gold" style="width: 100%; margin-top: 8px;" onclick="doLogin()">Secure Login &nbsp;→</button>
    <div id="loginError" class="error-text"></div>
    <div style="margin-top: 14px; display: flex; flex-direction: column; gap: 8px;">
      <button type="button" class="chip-btn" style="width: 100%; text-align: center; border-color: var(--gold); padding: 10px;" onclick="loginDemo('1000000001', '1234')">
        ⚡ 1-Click Login: Alex (1000000001 / 1234)
      </button>
      <button type="button" class="chip-btn" style="width: 100%; text-align: center; padding: 10px;" onclick="loginDemo('1000000002', '1234')">
        ⚡ 1-Click Login: Jordan (1000000002 / 1234)
      </button>
    </div>
    <button class="btn-quiet" style="margin-top: 12px;" onclick="showWelcome()">← Back</button>
    <div class="demo-hint">Demo Credentials: Account <b>1000000001</b> | PIN <b>1234</b></div>
  </div>
</div>

<!-- APP SHELL / DASHBOARD -->
<div id="appShell" class="app-shell">
  <aside class="sidebar">
    <div class="brand-title" style="font-size: 20px; letter-spacing: 5px;">AUREL</div>
    <div class="eyebrow" style="margin-bottom: 0;">Private Banking</div>
    
    <div class="sidebar-nav">
      <button class="nav-item active" onclick="switchTab('overview')">◈ Overview</button>
      <button class="nav-item" onclick="switchTab('deposit')">↓ Deposit</button>
      <button class="nav-item" onclick="switchTab('withdraw')">↑ Withdraw</button>
      <button class="nav-item" onclick="switchTab('transfer')">⇄ Transfer</button>
      <button class="nav-item" onclick="switchTab('history')">◷ History</button>
      <button class="nav-item" onclick="switchTab('account')">ⓘ Account Details</button>
      <button class="nav-item" onclick="switchTab('settings')">⚙ Settings</button>
    </div>
    
    <div style="border-top: 1px solid #1E2B4B; padding-top: 16px;">
      <div class="eyebrow" style="margin-bottom: 8px;">Demo Session</div>
      <button class="btn-quiet" style="width: 100%; text-align: left;" onclick="logout()">↪ Log out</button>
    </div>
  </aside>
  
  <main class="main-content">
    <header class="topbar">
      <h2 id="pageTitle" style="font-size: 18px; font-weight: 700;">Overview</h2>
      <div class="secure-pill">● SECURE SESSION</div>
    </header>
    
    <div class="dashboard-body">
      <!-- TAB: OVERVIEW -->
      <div id="tabOverview">
        <h2 id="greeting" style="font-size: 26px; font-weight: 700; margin-bottom: 6px;">Good evening, Alex.</h2>
        <p style="color: var(--text-muted); margin-bottom: 24px;">Here is your financial snapshot.</p>
        
        <div class="balance-card">
          <div>
            <div class="eyebrow" style="color: var(--gold-light);">AVAILABLE BALANCE</div>
            <div id="displayBalance" class="balance-amount">₹25,000.00</div>
            <div id="accountTag" style="color: var(--text-muted); font-size: 13px;">Account •••• 0001 · SAVINGS</div>
          </div>
          <div style="text-align: right;">
            <span class="gold-badge">ACTIVE</span>
            <div style="color: var(--text-dim); font-size: 11px; margin-top: 8px;">AUREL / INR</div>
          </div>
        </div>
        
        <h3 style="font-size: 16px; margin-bottom: 16px;">Quick Actions</h3>
        <div class="quick-actions">
          <button class="action-btn" onclick="switchTab('deposit')">↓ &nbsp;Deposit</button>
          <button class="action-btn" onclick="switchTab('withdraw')">↑ &nbsp;Withdraw</button>
          <button class="action-btn" onclick="switchTab('transfer')">⇄ &nbsp;Transfer</button>
          <button class="action-btn" onclick="switchTab('history')">◷ &nbsp;History</button>
        </div>
        
        <h3 style="font-size: 16px; margin-bottom: 12px;">Recent Activity</h3>
        <table class="tx-table" id="recentTable">
          <tbody id="recentRows"></tbody>
        </table>
      </div>
      
      <!-- TAB: DEPOSIT -->
      <div id="tabDeposit" style="display: none;">
        <div class="form-panel">
          <h3>Add Funds (Deposit)</h3>
          <p>Simulation deposit credited instantly to your account.</p>
          <div class="input-group">
            <label class="input-label">Amount in INR</label>
            <input id="depositAmount" class="input-field" type="number" placeholder="e.g. 5000.00" step="100">
          </div>
          <div class="input-group">
            <label class="input-label">Deposit Method</label>
            <select id="depositMethod" class="input-field" style="cursor: pointer;">
              <option>Cash deposit (simulation)</option>
              <option>Cheque deposit (simulation)</option>
            </select>
          </div>
          <button class="btn-gold" onclick="submitDeposit()">Confirm Deposit &nbsp;→</button>
          <div id="depositMsg" class="error-text"></div>
        </div>
      </div>
      
      <!-- TAB: WITHDRAW -->
      <div id="tabWithdraw" style="display: none;">
        <div class="form-panel">
          <h3>Withdraw Funds</h3>
          <p>Instant cash simulation withdrawal from available balance.</p>
          <div class="quick-amounts">
            <button class="chip-btn" onclick="setWithdraw(500)">₹500</button>
            <button class="chip-btn" onclick="setWithdraw(1000)">₹1,000</button>
            <button class="chip-btn" onclick="setWithdraw(2000)">₹2,000</button>
            <button class="chip-btn" onclick="setWithdraw(5000)">₹5,000</button>
          </div>
          <div class="input-group">
            <label class="input-label">Amount in INR</label>
            <input id="withdrawAmount" class="input-field" type="number" placeholder="Enter amount">
          </div>
          <button class="btn-gold" onclick="submitWithdraw()">Confirm Withdrawal &nbsp;→</button>
          <div id="withdrawMsg" class="error-text"></div>
        </div>
      </div>
      
      <!-- TAB: TRANSFER -->
      <div id="tabTransfer" style="display: none;">
        <div class="form-panel">
          <h3>Send a Transfer</h3>
          <p>Instant transfer to an eligible Aurel demo account.</p>
          <div class="input-group">
            <label class="input-label">Recipient Account Number (Demo: 1000000002)</label>
            <input id="transferRecipient" class="input-field" type="text" placeholder="1000000002" value="1000000002">
          </div>
          <div class="input-group">
            <label class="input-label">Amount in INR</label>
            <input id="transferAmount" class="input-field" type="number" placeholder="e.g. 2500.00">
          </div>
          <button class="btn-gold" onclick="submitTransfer()">Review and Send &nbsp;→</button>
          <div id="transferMsg" class="error-text"></div>
        </div>
      </div>
      
      <!-- TAB: HISTORY -->
      <div id="tabHistory" style="display: none;">
        <h3 style="font-size: 20px; margin-bottom: 8px;">Transaction History</h3>
        <p style="color: var(--text-muted); margin-bottom: 20px;">All deposits, withdrawals, and transfers.</p>
        <table class="tx-table">
          <tbody id="allHistoryRows"></tbody>
        </table>
      </div>
      
      <!-- TAB: ACCOUNT DETAILS -->
      <div id="tabAccount" style="display: none;">
        <div class="form-panel">
          <h3>Account Information</h3>
          <p>Your verified customer and banking profile.</p>
          <div style="display: flex; flex-direction: column; gap: 14px; margin-top: 16px;">
            <div><span style="color: var(--text-dim); font-size: 12px;">NAME</span><div id="accName" style="font-size: 16px; font-weight: 600;">-</div></div>
            <div><span style="color: var(--text-dim); font-size: 12px;">EMAIL</span><div id="accEmail" style="font-size: 16px; font-weight: 600;">-</div></div>
            <div><span style="color: var(--text-dim); font-size: 12px;">PHONE</span><div id="accPhone" style="font-size: 16px; font-weight: 600;">-</div></div>
            <div><span style="color: var(--text-dim); font-size: 12px;">ACCOUNT NUMBER</span><div id="accNumber" style="font-size: 16px; font-weight: 600;">-</div></div>
            <div><span style="color: var(--text-dim); font-size: 12px;">TYPE & STATUS</span><div id="accTypeStatus" style="font-size: 16px; font-weight: 600;">-</div></div>
          </div>
        </div>
      </div>
      
      <!-- TAB: SETTINGS -->
      <div id="tabSettings" style="display: none;">
        <div class="form-panel">
          <h3>Security & PIN Settings</h3>
          <p>Change your secure 4–8 digit PIN.</p>
          <div class="input-group">
            <label class="input-label">Current PIN</label>
            <input id="oldPin" class="input-field" type="password">
          </div>
          <div class="input-group">
            <label class="input-label">New PIN (4–8 digits)</label>
            <input id="newPin" class="input-field" type="password">
          </div>
          <div class="input-group">
            <label class="input-label">Confirm New PIN</label>
            <input id="confirmPin" class="input-field" type="password">
          </div>
          <button class="btn-gold" onclick="submitChangePin()">Update PIN &nbsp;→</button>
          <div id="settingsMsg" class="error-text"></div>
        </div>
      </div>
      
    </div>
  </main>
</div>

<script>
let currentAccount = null;

function showLogin() {
  document.getElementById('welcomeView').style.display = 'none';
  document.getElementById('loginView').style.display = 'flex';
}
function showWelcome() {
  document.getElementById('loginView').style.display = 'none';
  document.getElementById('welcomeView').style.display = 'flex';
}
function togglePin(cb) {
  document.getElementById('loginPin').type = cb.checked ? 'text' : 'password';
}

function loginDemo(account, pin) {
  document.getElementById('loginNumber').value = account;
  document.getElementById('loginPin').value = pin;
  doLogin();
}

async function doLogin() {
  const number = document.getElementById('loginNumber').value.trim();
  const pin = document.getElementById('loginPin').value.trim();
  const err = document.getElementById('loginError');
  err.innerText = '';
  
  try {
    const res = await fetch('/api/login', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({accountNumber: number, pin: pin})
    });
    const data = await res.json();
    if (!data.success) {
      err.innerText = data.error || 'Login failed';
      return;
    }
    currentAccount = data.account;
    openDashboard();
  } catch (e) {
    err.innerText = 'Network error: ' + e.message;
  }
}

async function openDashboard() {
  document.getElementById('loginView').style.display = 'none';
  document.getElementById('welcomeView').style.display = 'none';
  document.getElementById('appShell').style.display = 'flex';
  await refreshOverview();
  switchTab('overview');
}

async function refreshOverview() {
  if (!currentAccount) return;
  const res = await fetch('/api/overview?accountId=' + currentAccount.accountId);
  const data = await res.json();
  if (data.success) {
    currentAccount = data.account;
    const name = currentAccount.fullName.split(' ')[0];
    document.getElementById('greeting').innerText = `Good evening, ${name}.`;
    document.getElementById('displayBalance').innerText = currentAccount.formattedBalance;
    const num = currentAccount.accountNumber;
    document.getElementById('accountTag').innerText = `Account •••• ${num.slice(-4)} · ${currentAccount.accountType}`;
    
    // Details
    document.getElementById('accName').innerText = currentAccount.fullName;
    document.getElementById('accEmail').innerText = currentAccount.email;
    document.getElementById('accPhone').innerText = currentAccount.phone;
    document.getElementById('accNumber').innerText = currentAccount.accountNumber;
    document.getElementById('accTypeStatus').innerText = `${currentAccount.accountType} (${currentAccount.status})`;
    
    // Recent Rows
    const tbody = document.getElementById('recentRows');
    const allBody = document.getElementById('allHistoryRows');
    tbody.innerHTML = '';
    allBody.innerHTML = '';
    
    if (data.transactions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="3" style="color:var(--text-dim); padding:20px;">No recent transactions.</td></tr>';
      allBody.innerHTML = '<tr><td colspan="3" style="color:var(--text-dim); padding:20px;">No transaction history.</td></tr>';
    } else {
      data.transactions.forEach((t, i) => {
        const rowHtml = `
          <tr class="tx-row">
            <td>
              <div style="font-weight:600;">${t.description}</div>
              <div style="font-size:11px; color:var(--text-dim); margin-top:4px;">Ref: ${t.reference} · ${t.date.replace('T', ' ')}</div>
            </td>
            <td><span class="gold-badge" style="font-size:10px;">${t.type}</span></td>
            <td><span class="tx-amount">${t.type.includes('WITHDRAW') || t.type.includes('OUT') ? '-' : '+'}${t.formattedAmount}</span></td>
          </tr>
        `;
        if (i < 5) tbody.innerHTML += rowHtml;
        allBody.innerHTML += rowHtml;
      });
    }
  }
}

function switchTab(tab) {
  const tabs = ['overview','deposit','withdraw','transfer','history','account','settings'];
  tabs.forEach(t => {
    const el = document.getElementById('tab' + t.charAt(0).toUpperCase() + t.slice(1));
    if (el) el.style.display = (t === tab) ? 'block' : 'none';
  });
  document.querySelectorAll('.nav-item').forEach(btn => btn.classList.remove('active'));
  document.getElementById('pageTitle').innerText = tab.charAt(0).toUpperCase() + tab.slice(1);
  refreshOverview();
}

function setWithdraw(amt) {
  document.getElementById('withdrawAmount').value = amt;
}

async function submitDeposit() {
  const amt = document.getElementById('depositAmount').value;
  const method = document.getElementById('depositMethod').value;
  const msg = document.getElementById('depositMsg');
  msg.innerText = '';
  const res = await fetch('/api/deposit', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({accountId: currentAccount.accountId, amount: amt, method: method})
  });
  const data = await res.json();
  if (data.success) {
    msg.style.color = '#8EF8A5';
    msg.innerText = 'Deposit completed! Ref: ' + data.reference;
    document.getElementById('depositAmount').value = '';
    await refreshOverview();
  } else {
    msg.style.color = '#FF7B7B';
    msg.innerText = data.error;
  }
}

async function submitWithdraw() {
  const amt = document.getElementById('withdrawAmount').value;
  const msg = document.getElementById('withdrawMsg');
  msg.innerText = '';
  const res = await fetch('/api/withdraw', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({accountId: currentAccount.accountId, amount: amt})
  });
  const data = await res.json();
  if (data.success) {
    msg.style.color = '#8EF8A5';
    msg.innerText = 'Withdrawal successful! Ref: ' + data.reference;
    document.getElementById('withdrawAmount').value = '';
    await refreshOverview();
  } else {
    msg.style.color = '#FF7B7B';
    msg.innerText = data.error;
  }
}

async function submitTransfer() {
  const recipient = document.getElementById('transferRecipient').value.trim();
  const amt = document.getElementById('transferAmount').value;
  const msg = document.getElementById('transferMsg');
  msg.innerText = '';
  const res = await fetch('/api/transfer', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({accountId: currentAccount.accountId, recipientNumber: recipient, amount: amt})
  });
  const data = await res.json();
  if (data.success) {
    msg.style.color = '#8EF8A5';
    msg.innerText = 'Transfer completed! Ref: ' + data.reference;
    document.getElementById('transferAmount').value = '';
    await refreshOverview();
  } else {
    msg.style.color = '#FF7B7B';
    msg.innerText = data.error;
  }
}

async function submitChangePin() {
  const oldPin = document.getElementById('oldPin').value;
  const newPin = document.getElementById('newPin').value;
  const confirm = document.getElementById('confirmPin').value;
  const msg = document.getElementById('settingsMsg');
  msg.innerText = '';
  const res = await fetch('/api/change-pin', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({accountId: currentAccount.accountId, oldPin: oldPin, newPin: newPin, confirm: confirm})
  });
  const data = await res.json();
  if (data.success) {
    msg.style.color = '#8EF8A5';
    msg.innerText = 'PIN successfully updated!';
    document.getElementById('oldPin').value = '';
    document.getElementById('newPin').value = '';
    document.getElementById('confirmPin').value = '';
  } else {
    msg.style.color = '#FF7B7B';
    msg.innerText = data.error;
  }
}

function logout() {
  currentAccount = null;
  document.getElementById('appShell').style.display = 'none';
  document.getElementById('loginView').style.display = 'flex';
}
</script>
</body>
</html>
""";
    }
}
