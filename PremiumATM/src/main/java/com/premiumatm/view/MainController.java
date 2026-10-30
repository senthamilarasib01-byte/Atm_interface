package com.premiumatm.view;

import com.premiumatm.model.*;
import com.premiumatm.service.BankingService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class MainController {
    @FXML private BorderPane root;
    private final BankingService service=new BankingService();
    private Account account;
    private final NumberFormat money=NumberFormat.getCurrencyInstance(new Locale("en","IN"));
    private VBox sidebar;
    private BorderPane shell;
    private Label pageTitle;
    private VBox content;
    private Label status;
    private String lastReceipt="";

    @FXML public void initialize() {}
    public void showWelcome(){root.setCenter(centerWelcome());}
    private VBox centerWelcome(){
        VBox box=new VBox(22);box.setAlignment(javafx.geometry.Pos.CENTER);box.setPadding(new Insets(50));
        Label logo=new Label("AUREL");logo.getStyleClass().add("brand-large");
        Label eyebrow=new Label("PRIVATE BANKING · REIMAGINED");eyebrow.getStyleClass().add("eyebrow");
        Label headline=new Label("Your money.\\nYour momentum.");headline.getStyleClass().add("hero");
        Label copy=new Label("A refined, secure space to manage everyday banking.");copy.getStyleClass().add("muted");
        Button start=goldButton("Get Started  →");start.setOnAction(e->showLogin());
        Label demo=new Label("DEMO ENVIRONMENT  •  No real bank or cash network connected");demo.getStyleClass().add("small-muted");
        box.getChildren().addAll(logo,eyebrow,headline,copy,start,demo);return box;
    }
    private void showLogin(){
        VBox box=panel("Welcome back","Sign in to your Aurel account");
        TextField number=field("Account number");number.setText("1000000001");
        PasswordField pin=new PasswordField();pin.setPromptText("Secure PIN");pin.setText("1234");
        CheckBox show=new CheckBox("Show PIN");show.getStyleClass().add("check");
        TextField visiblePin=field("Secure PIN");visiblePin.setText("1234");visiblePin.setManaged(false);visiblePin.setVisible(false);
        show.selectedProperty().addListener((o,old,v)->{if(v){visiblePin.setText(pin.getText());pin.setManaged(false);pin.setVisible(false);visiblePin.setManaged(true);visiblePin.setVisible(true);}else{pin.setText(visiblePin.getText());visiblePin.setManaged(false);visiblePin.setVisible(false);pin.setManaged(true);pin.setVisible(true);}});
        Label msg=message();Button login=goldButton("Secure Login  →");login.setOnAction(e->{
            try{account=service.login(number.getText().trim(),show.isSelected()?visiblePin.getText().trim():pin.getText().trim());if(account==null){msg.setText("Account number or PIN is incorrect.");return;}showDashboard();}
            catch(Exception ex){msg.setText(safe(ex));}
        });
        Button fill1=quietButton("⚡ Auto-fill Demo 1 (Alex Morgan · 1000000001 / 1234)");
        fill1.setOnAction(e->{number.setText("1000000001");pin.setText("1234");visiblePin.setText("1234");});
        Button fill2=quietButton("⚡ Auto-fill Demo 2 (Jordan Reed · 1000000002 / 1234)");
        fill2.setOnAction(e->{number.setText("1000000002");pin.setText("1234");visiblePin.setText("1234");});
        Button back=quietButton("← Back");back.setOnAction(e->showWelcome());
        box.getChildren().addAll(number,pin,visiblePin,show,login,fill1,fill2,msg,back);root.setCenter(box);
    }
    private void showDashboard(){buildShell("Overview");showOverview();}
    private void buildShell(String title){
        shell=new BorderPane();shell.getStyleClass().add("app-shell");
        sidebar=new VBox(8);sidebar.getStyleClass().add("sidebar");sidebar.setPadding(new Insets(28,18,24,18));
        Label brand=new Label("AUREL");brand.getStyleClass().add("brand");Label caption=new Label("PRIVATE BANKING");caption.getStyleClass().add("sidebar-caption");
        sidebar.getChildren().addAll(brand,caption,new Separator());
        nav("Overview",()->{buildShell("Overview");showOverview();});nav("Deposit",()->showMoney("Deposit"));nav("Withdraw",()->showMoney("Withdraw"));nav("Transfer",()->showMoney("Transfer"));nav("Transaction history",()->showHistory());nav("Account details",()->showAccount());nav("Settings",()->showSettings());
        Region spacer=new Region();VBox.setVgrow(spacer,Priority.ALWAYS);sidebar.getChildren().add(spacer);
        Label demo=new Label("DEMO ACCOUNT");demo.getStyleClass().add("sidebar-caption");
        Button logout=quietButton("↪  Log out");logout.setMaxWidth(Double.MAX_VALUE);logout.setOnAction(e->{account=null;showLogin();});
        sidebar.getChildren().addAll(demo,logout);
        VBox top=new VBox();top.getStyleClass().add("topbar");top.setPadding(new Insets(18,30,18,30));
        HBox bar=new HBox();bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        pageTitle=new Label(title);pageTitle.getStyleClass().add("page-title");Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);
        Label secure=new Label("●  SECURE SESSION");secure.getStyleClass().add("secure-pill");
        bar.getChildren().addAll(pageTitle,gap,secure);top.getChildren().add(bar);
        content=new VBox(20);content.setPadding(new Insets(30));ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);scroll.getStyleClass().add("content-scroll");
        shell.setLeft(sidebar);shell.setTop(top);shell.setCenter(scroll);root.setCenter(shell);
    }
    private void nav(String name,Runnable action){Button b=quietButton("   "+name);b.setMaxWidth(Double.MAX_VALUE);b.setAlignment(javafx.geometry.Pos.CENTER_LEFT);b.getStyleClass().add("nav-button");b.setOnAction(e->action.run());sidebar.getChildren().add(b);}
    private void showOverview(){
        try{account=service.refresh(account.accountId());}catch(Exception e){showError(e);return;}
        content.getChildren().clear();
        Label hello=new Label("Good evening, "+account.fullName().split(" ")[0]+".");hello.getStyleClass().add("hero-small");
        Label sub=new Label("Here is your financial snapshot.");sub.getStyleClass().add("muted");
        HBox balance=new HBox();balance.getStyleClass().add("balance-card");balance.setPadding(new Insets(28));balance.setSpacing(20);
        VBox left=new VBox(12);Label bcap=new Label("AVAILABLE BALANCE");bcap.getStyleClass().add("eyebrow");Label amount=new Label(money.format(account.balance()));amount.getStyleClass().add("balance-amount");
        Label masked=new Label("Account  •••• "+last4(account.accountNumber())+"   ·   "+account.accountType());masked.getStyleClass().add("muted");
        left.getChildren().addAll(bcap,amount,masked);Region g=new Region();HBox.setHgrow(g,Priority.ALWAYS);
        VBox right=new VBox(8);right.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);Label tag=new Label("ACTIVE");tag.getStyleClass().add("gold-tag");Label type=new Label("AUREL / INR");type.getStyleClass().add("small-muted");right.getChildren().addAll(tag,type);balance.getChildren().addAll(left,g,right);
        Label quick=new Label("Quick actions");quick.getStyleClass().add("section-title");HBox actions=new HBox(12);
        for(String s:new String[]{"Deposit","Withdraw","Transfer","History"}){Button b=actionCard(s);b.setOnAction(e->{switch(s){case "History"->showHistory();default->showMoney(s);}});actions.getChildren().add(b);}
        Label recent=new Label("Recent activity");recent.getStyleClass().add("section-title");
        VBox rows=new VBox(8);
        try{for(TransactionRecord t:service.history(account.accountId()).stream().limit(5).toList())rows.getChildren().add(transactionRow(t));}
        catch(Exception e){rows.getChildren().add(new Label("Could not load transactions: "+safe(e)));}
        if(rows.getChildren().isEmpty())rows.getChildren().add(empty("No transactions yet. Your activity will appear here."));
        content.getChildren().addAll(hello,sub,balance,quick,actions,recent,rows);
    }
    private void showMoney(String mode){
        pageTitle.setText(mode);content.getChildren().clear();
        VBox form=card();form.setMaxWidth(720);
        Label title=new Label(mode.equals("Withdraw")?"Withdraw funds":mode.equals("Deposit")?"Add funds":"Send a transfer");title.getStyleClass().add("section-title");
        Label desc=new Label(mode.equals("Transfer")?"Transfer to an eligible Aurel demo account.":"Review the amount before confirming.");desc.getStyleClass().add("muted");
        TextField amount=field("Amount in INR (e.g. 1500.00)");TextField recipient=field("Recipient account number");
        ComboBox<String> method=new ComboBox<>();method.getItems().addAll("Cash deposit (simulation)","Cheque deposit (simulation)");method.setValue("Cash deposit (simulation)");method.setMaxWidth(Double.MAX_VALUE);
        if(mode.equals("Transfer"))form.getChildren().add(recipient);
        if(mode.equals("Deposit"))form.getChildren().add(method);
        if(mode.equals("Withdraw")){HBox quick=new HBox(8);for(String v:new String[]{"₹500","₹1,000","₹2,000","₹5,000"}){Button q=quietButton(v);q.setOnAction(e->amount.setText(v.replace("₹","").replace(",","")));quick.getChildren().add(q);}form.getChildren().add(quick);}
        Label msg=message();Button review=goldButton("Review and confirm  →");
        review.setOnAction(e->{
            try{
                BigDecimal value=new BigDecimal(amount.getText().trim());
                Alert confirm=new Alert(Alert.AlertType.CONFIRMATION);confirm.setTitle("Confirm transaction");confirm.setHeaderText(mode+" · "+money.format(value));
                confirm.setContentText(mode.equals("Transfer")?"Recipient account: "+recipient.getText().trim():"This is a simulated banking transaction. Continue?");
                if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
                if(mode.equals("Deposit"))lastReceipt=service.deposit(account,value,method.getValue());
                else if(mode.equals("Withdraw"))lastReceipt=service.withdraw(account,value);
                else lastReceipt=service.transfer(account,recipient.getText().trim(),value);
                account=service.refresh(account.accountId());showResult(true,mode+" completed", "Reference: "+lastReceipt+"\\nAmount: "+money.format(value));
            }catch(Exception ex){msg.setText(safe(ex));}
        });
        Button cancel=quietButton("Cancel");cancel.setOnAction(e->showOverview());
        form.getChildren().addAll(title,desc,amount,review,msg,cancel);content.getChildren().addAll(form);
    }
    private void showHistory(){
        pageTitle.setText("Transaction history");content.getChildren().clear();
        Label heading=new Label("Your activity");heading.getStyleClass().add("hero-small");
        TextField search=field("Search by type, reference or description");
        VBox rows=new VBox(8);content.getChildren().addAll(heading,search,rows);
        Runnable refresh=()->{rows.getChildren().clear();try{
            var list=service.history(account.accountId());String q=search.getText().toLowerCase(Locale.ROOT);
            list.stream().filter(t->(t.type()+" "+t.referenceId()+" "+t.description()).toLowerCase(Locale.ROOT).contains(q))
                .forEach(t->rows.getChildren().add(transactionRow(t)));
            if(rows.getChildren().isEmpty())rows.getChildren().add(empty("No matching transactions."));
        }catch(Exception e){rows.getChildren().add(empty(safe(e)));}};
        search.textProperty().addListener((o,a,b)->refresh.run());refresh.run();
    }
    private void showAccount(){
        pageTitle.setText("Account details");content.getChildren().clear();VBox c=card();
        c.getChildren().addAll(section("ACCOUNT HOLDER",account.fullName()),section("ACCOUNT NUMBER","•••• •••• "+last4(account.accountNumber())),section("ACCOUNT TYPE",account.accountType()),section("EMAIL",account.email()),section("PHONE",account.phone()==null?"Not provided":account.phone()),section("STATUS",account.status()),section("CURRENCY",account.currency()));
        content.getChildren().add(c);
    }
    private void showSettings(){
        pageTitle.setText("Settings");content.getChildren().clear();VBox c=card();Label h=new Label("Security settings");h.getStyleClass().add("section-title");
        PasswordField old= new PasswordField();old.setPromptText("Current PIN");PasswordField next=new PasswordField();next.setPromptText("New PIN");PasswordField confirm=new PasswordField();confirm.setPromptText("Confirm new PIN");Label msg=message();
        Button save=goldButton("Update PIN");save.setOnAction(e->{try{service.changePin(account,old.getText(),next.getText(),confirm.getText());msg.setText("PIN updated successfully.");old.clear();next.clear();confirm.clear();}catch(Exception ex){msg.setText(safe(ex));}});
        CheckBox compact=new CheckBox("Use compact dashboard spacing (session only)");compact.getStyleClass().add("check");compact.setOnAction(e->content.setSpacing(compact.isSelected()?12:20));
        c.getChildren().addAll(h,new Label("Your PIN is stored as a salted PBKDF2 hash."),old,next,confirm,save,msg,new Separator(),compact);
        content.getChildren().add(c);
    }
    private void showResult(boolean ok,String title,String details){
        content.getChildren().clear();VBox c=card();Label icon=new Label(ok?"✓":"!");icon.getStyleClass().add("result-icon");
        Label h=new Label(title);h.getStyleClass().add("hero-small");Label d=new Label(details);d.getStyleClass().add("muted");d.setWrapText(true);
        Button home=goldButton("Return to dashboard");home.setOnAction(e->{buildShell("Overview");showOverview();});
        Button history=quietButton("View transaction history");history.setOnAction(e->showHistory());c.getChildren().addAll(icon,h,d,home,history);content.getChildren().add(c);
    }
    private void showError(Exception e){showResult(false,"Something went wrong",safe(e));}
    private VBox panel(String title,String subtitle){VBox b=card();b.setMaxWidth(480);b.setAlignment(javafx.geometry.Pos.CENTER_LEFT);b.setPadding(new Insets(38));Label logo=new Label("AUREL");logo.getStyleClass().add("brand");Label h=new Label(title);h.getStyleClass().add("hero-small");Label sub=new Label(subtitle);sub.getStyleClass().add("muted");b.getChildren().addAll(logo,h,sub);return b;}
    private VBox card(){VBox b=new VBox(16);b.getStyleClass().add("panel");b.setPadding(new Insets(26));return b;}
    private VBox section(String name,String value){VBox b=new VBox(5);Label n=new Label(name);n.getStyleClass().add("eyebrow");Label v=new Label(value==null?"—":value);v.getStyleClass().add("detail-value");b.getChildren().addAll(n,v,new Separator());return b;}
    private HBox transactionRow(TransactionRecord t){HBox r=new HBox(14);r.getStyleClass().add("transaction-row");r.setPadding(new Insets(14));r.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox left=new VBox(5);Label name=new Label(t.description()==null?t.type():t.description());name.getStyleClass().add("transaction-title");Label meta=new Label(t.type()+"  ·  "+t.createdAt().toString().replace('T',' '));meta.getStyleClass().add("small-muted");left.getChildren().addAll(name,meta);Region g=new Region();HBox.setHgrow(g,Priority.ALWAYS);
        VBox right=new VBox(4);right.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);Label amt=new Label(("DEPOSIT".equals(t.type())||"TRANSFER_IN".equals(t.type())?"+":"−")+money.format(t.amount()));amt.getStyleClass().add("transaction-amount");Label ref=new Label(t.referenceId());ref.getStyleClass().add("small-muted");right.getChildren().addAll(amt,ref);r.getChildren().addAll(left,g,right);return r;}
    private Button actionCard(String text){Button b=new Button("✦\\n"+text);b.getStyleClass().add("action-card");b.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(b,Priority.ALWAYS);return b;}
    private TextField field(String prompt){TextField f=new TextField();f.setPromptText(prompt);f.getStyleClass().add("input");return f;}
    private Button goldButton(String text){Button b=new Button(text);b.getStyleClass().add("gold-button");return b;}
    private Button quietButton(String text){Button b=new Button(text);b.getStyleClass().add("quiet-button");return b;}
    private Label message(){Label l=new Label();l.getStyleClass().add("error-message");l.setWrapText(true);return l;}
    private Label empty(String s){Label l=new Label(s);l.getStyleClass().add("muted");return l;}
    private String sectionText(String a,String b){return a+": "+b;}
    private String safe(Exception e){String m=e.getMessage();return m==null?"Unexpected error.":m;}
    private String last4(String s){return s.substring(Math.max(0,s.length()-4));}
}
