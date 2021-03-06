package com.turqmelon.MelonEco.data;

import com.turqmelon.MelonEco.MelonEco;
import com.turqmelon.MelonEco.utils.Account;
import com.turqmelon.MelonEco.utils.AccountManager;
import com.turqmelon.MelonEco.utils.CachedTopList;
import com.turqmelon.MelonEco.utils.Currency;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.sql.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creator: Devon
 * Project: MelonEco
 */
public class MySQLStorage extends DataStore {

    private Connection connection;
    private String host;
    private int port;
    private String username;
    private String password;
    private String database;
    private String tablePrefix;
    private Map<UUID, CachedTopList> cachedTopList = new HashMap<>();

    public MySQLStorage(String name, boolean topSupported, String host, int port, String username, String password, String database, String tablePrefix) {
        super(name, topSupported);
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.database = database;
        this.tablePrefix = tablePrefix;
    }

    private void reviveConnection() {
        try {
            if (getConnection().isClosed() || !getConnection().isValid(3)) {
                initalize();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initalize() {
        try {
            this.connection = DriverManager.getConnection("jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabase(), getUsername(), getPassword());
        } catch (SQLException e) {
            e.printStackTrace();
            this.connection = null;
        }

        // setup necessary tables
        if (getConnection() != null) {
            try {
                try (PreparedStatement currencyTableStmt = getConnection().prepareStatement("CREATE TABLE IF NOT EXISTS " + getTablePrefix() + "_currencies" +
                        "(" +
                        "    id INT PRIMARY KEY AUTO_INCREMENT," +
                        "    uuid VARCHAR(255)," +
                        "    name_singular VARCHAR(255)," +
                        "    name_plural VARCHAR(255)," +
                        "    default_balance DECIMAL," +
                        "    symbol VARCHAR(10)," +
                        "    decimals_supported INT," +
                        "    is_default INT," +
                        "    payable INT," +
                        "    color VARCHAR(255)" +
                        ");")) {
                    currencyTableStmt.execute();
                }
                try (PreparedStatement accountTableStmt = getConnection().prepareStatement("CREATE TABLE IF NOT EXISTS " + getTablePrefix() + "_accounts" +
                        "(" +
                        "    id INT PRIMARY KEY AUTO_INCREMENT," +
                        "    nickname VARCHAR(255)," +
                        "    uuid VARCHAR(255)," +
                        "    payable INT" +
                        ");")) {
                    accountTableStmt.execute();
                }
                try (PreparedStatement balancesTableStmt = getConnection().prepareStatement("CREATE TABLE IF NOT EXISTS " + getTablePrefix() + "_balances" +
                        "(" +
                        "    account_id VARCHAR(255)," +
                        "    currency_id VARCHAR(255)," +
                        "    balance DECIMAL" +
                        ");")) {
                    balancesTableStmt.execute();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() {
        if (getConnection() != null) {
            try {
                getConnection().close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void loadCurrencies() {
        if (getConnection() == null)
            return;
        reviveConnection();

        try (PreparedStatement stmt = getConnection().prepareStatement("SELECT * FROM " + getTablePrefix() + "_currencies")) {
            try (ResultSet set = stmt.executeQuery()) {
                while (set.next()) {
                    UUID uuid = UUID.fromString(set.getString("uuid"));
                    String singular = set.getString("name_singular");
                    String plural = set.getString("name_plural");
                    double defaultBalance = set.getDouble("default_balance");
                    String symbol = set.getString("symbol");
                    boolean decimals = set.getInt("decimals_supported") == 1;
                    boolean isDefault = set.getInt("is_default") == 1;
                    boolean payable = set.getInt("payable") == 1;
                    ChatColor color = ChatColor.valueOf(set.getString("color"));
                    Currency currency = new Currency(uuid, singular, plural);
                    currency.setDefaultBalance(defaultBalance);
                    currency.setSymbol(symbol);
                    currency.setDecimalSupported(decimals);
                    currency.setDefaultCurrency(isDefault);
                    currency.setPayable(payable);
                    currency.setColor(color);
                    AccountManager.getCurrencies().add(currency);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveCurrency(Currency currency) {
        if (getConnection() == null)
            return;
        reviveConnection();


        try (PreparedStatement stmt = getConnection().prepareStatement("SELECT * FROM " + getTablePrefix() + "_currencies WHERE uuid = ? LIMIT 1;")) {
            stmt.setString(1, currency.getUuid().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                int resultCount = rs.last() ? rs.getRow() : 0;

                if (resultCount == 0) {
                    try (PreparedStatement insertStmt = getConnection().prepareStatement("INSERT INTO " + getTablePrefix() + "_currencies (uuid, name_singular, name_plural, default_balance, symbol, decimals_supported, is_default, payable, color) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        insertStmt.setString(1, currency.getUuid().toString());
                        insertStmt.setString(2, currency.getSingular());
                        insertStmt.setString(3, currency.getPlural());
                        insertStmt.setDouble(4, currency.getDefaultBalance());
                        insertStmt.setString(5, currency.getSymbol());
                        insertStmt.setInt(6, currency.isDecimalSupported() ? 1 : 0);
                        insertStmt.setInt(7, currency.isDefaultCurrency() ? 1 : 0);
                        insertStmt.setInt(8, currency.isPayable() ? 1 : 0);
                        insertStmt.setString(9, currency.getColor().name());
                        insertStmt.execute();
                    }
                } else {
                    try (PreparedStatement updateStmt = getConnection().prepareStatement("UPDATE " + getTablePrefix() + "_currencies SET default_balance = ?, symbol = ?, decimals_supported = ?, is_default = ?, payable = ?, color = ? WHERE uuid = ?")) {
                        updateStmt.setDouble(1, currency.getDefaultBalance());
                        updateStmt.setString(2, currency.getSymbol());
                        updateStmt.setInt(3, currency.isDecimalSupported() ? 1 : 0);
                        updateStmt.setInt(4, currency.isDefaultCurrency() ? 1 : 0);
                        updateStmt.setInt(5, currency.isPayable() ? 1 : 0);
                        updateStmt.setString(6, currency.getColor().name());
                        updateStmt.setString(7, currency.getUuid().toString());
                        updateStmt.execute();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void deleteCurrency(Currency currency) {
        if (getConnection() == null)
            return;
        reviveConnection();

        try {
            try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM " + getTablePrefix() + "_currencies WHERE uuid = ?")) {
                stmt.setString(1, currency.getUuid().toString());
                stmt.execute();
            }
            try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM " + getTablePrefix() + "_balances WHERE currency_id = ?")) {
                stmt.setString(1, currency.getUuid().toString());
                stmt.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public LinkedHashMap<String, Double> getTopList(Currency currency, int offset, int amount) {

        if (cachedTopList.containsKey(currency.getUuid())) {
            CachedTopList ctl = cachedTopList.get(currency.getUuid());
            if (ctl.matches(currency, offset, amount) && !ctl.isExpired()) {
                return ctl.getResults();
            }
        }

        if (getConnection() == null)
            return null;
        reviveConnection();

        LinkedHashMap<String, Double> resultPair = new LinkedHashMap<>();

        try {

            LinkedHashMap<String, Double> idBalancePair = new LinkedHashMap<>();

            try (PreparedStatement stmt = getConnection().prepareStatement("SELECT * FROM " + getTablePrefix() + "_balances WHERE currency_id = ? ORDER BY balance DESC LIMIT " + offset + ", " + amount)) {
                stmt.setString(1, currency.getUuid().toString());
                try (ResultSet set = stmt.executeQuery()) {
                    while (set.next()) {
                        idBalancePair.put(set.getString("account_id"), set.getDouble("balance"));
                    }
                }

                if (idBalancePair.size() > 0) {
                    for (String id : idBalancePair.keySet()) {
                        try (PreparedStatement selecAccountStmt = getConnection().prepareStatement("SELECT * FROM " + getTablePrefix() + "_accounts WHERE uuid = ? LIMIT 1")) {
                            selecAccountStmt.setString(1, id);
                            try (ResultSet set = selecAccountStmt.executeQuery()) {
                                if (set.next()) {
                                    resultPair.put(set.getString("nickname"), idBalancePair.get(id));
                                }
                            }
                        }
                    }
                }

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        CachedTopList ctl = new CachedTopList(currency, amount, offset, System.currentTimeMillis());
        ctl.setResults(resultPair);
        cachedTopList.put(currency.getUuid(), ctl);

        return resultPair;
    }

    private Account returnAccountWithBalances(Account account) {
        if (account == null)
            return null;


        try (PreparedStatement stmt = getConnection().prepareStatement("SELECT * FROM " + getTablePrefix() + "_balances WHERE account_id = ?")) {
            stmt.setString(1, account.getUuid().toString());
            try (ResultSet set = stmt.executeQuery()) {
                while (set.next()) {
                    Currency currency = AccountManager.getCurrency(UUID.fromString(set.getString("currency_id")));
                    if (currency == null) continue;
                    account.setBalance(currency, set.getDouble("balance"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return account;
    }

    @Override
    public Account loadAccount(String name) {
        Account account = null;

        if (getConnection() != null) {
            reviveConnection();

            try (PreparedStatement stmt = getConnection().prepareStatement("SELECT * FROM " + getTablePrefix() + "_accounts WHERE nickname = ? LIMIT 1")) {
                stmt.setString(1, name);
                try (ResultSet set = stmt.executeQuery()) {
                    if (set.next()) {
                        account = new Account(UUID.fromString(set.getString("uuid")), set.getString("nickname"));
                        account.setCanReceiveCurrency(set.getInt("payable") == 1);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }


        return returnAccountWithBalances(account);
    }

    @Override
    public Account loadAccount(UUID uuid) {

        Account account = null;

        if (getConnection() != null) {
            reviveConnection();

            try (PreparedStatement stmt = getConnection().prepareStatement("SELECT * FROM " + getTablePrefix() + "_accounts WHERE uuid = ? LIMIT 1")) {
                stmt.setString(1, uuid.toString());
                try (ResultSet set = stmt.executeQuery()) {
                    if (set.next()) {
                        account = new Account(uuid, set.getString("nickname"));
                        account.setCanReceiveCurrency(set.getInt("payable") == 1);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return returnAccountWithBalances(account);
    }

    @Override
    public void saveAccount(Account account) {
        if (Bukkit.getServer().isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(MelonEco.getInstance(), () -> saveAccount(account));
            return;
        }
        if (getConnection() == null)
            return;
        reviveConnection();


        try (PreparedStatement stmt = getConnection().prepareStatement("SELECT * FROM " + getTablePrefix() + "_accounts WHERE uuid = ? LIMIT 1")) {
            stmt.setString(1, account.getUuid().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                int resultCount = rs.last() ? rs.getRow() : 0;


                if (resultCount == 0) {
                    try (PreparedStatement insertStmt = getConnection().prepareStatement("INSERT INTO " + getTablePrefix() + "_accounts (nickname, uuid, payable) VALUES (?, ?, ?)")) {
                        insertStmt.setString(1, account.getDisplayName());
                        insertStmt.setString(2, account.getUuid().toString());
                        insertStmt.setInt(3, account.isCanReceiveCurrency() ? 1 : 0);
                        insertStmt.execute();
                    }
                } else {
                    try (PreparedStatement updateStmt = getConnection().prepareStatement("UPDATE " + getTablePrefix() + "_accounts SET nickname = ?, payable = ? WHERE uuid = ?")) {
                        updateStmt.setString(1, account.getDisplayName());
                        updateStmt.setInt(2, account.isCanReceiveCurrency() ? 1 : 0);
                        updateStmt.setString(3, account.getUuid().toString());
                        updateStmt.execute();
                    }
                }

                for (Currency currency : AccountManager.getCurrencies()) {
                    double balance = account.getBalance(currency);
                    if (balance != currency.getDefaultBalance()) {
                        try (PreparedStatement balSelectStmt = getConnection().prepareStatement("SELECT * FROM " + getTablePrefix() + "_balances WHERE account_id = ? AND currency_id = ? LIMIT 1")) {
                            balSelectStmt.setString(1, account.getUuid().toString());
                            balSelectStmt.setString(2, currency.getUuid().toString());
                            try (ResultSet balRs = balSelectStmt.executeQuery()) {
                                resultCount = balRs.last() ? balRs.getRow() : 0;
                            }
                            if (resultCount == 0) {
                                try (PreparedStatement insertStmt = getConnection().prepareStatement("INSERT INTO " + getTablePrefix() + "_balances (account_id, currency_id, balance) VALUES (?, ?, ?)")) {
                                    insertStmt.setString(1, account.getUuid().toString());
                                    insertStmt.setString(2, currency.getUuid().toString());
                                    insertStmt.setDouble(3, balance);
                                    insertStmt.execute();
                                }
                            } else {
                                try (PreparedStatement updateStmt = getConnection().prepareStatement("UPDATE " + getTablePrefix() + "_balances SET balance = ? WHERE account_id = ? AND currency_id = ?")) {
                                    updateStmt.setDouble(1, balance);
                                    updateStmt.setString(2, account.getUuid().toString());
                                    updateStmt.setString(3, currency.getUuid().toString());
                                    updateStmt.execute();
                                }
                            }
                        }
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void deleteAccount(Account account) {
        if (getConnection() == null)
            return;
        reviveConnection();

        try {
            try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM " + getTablePrefix() + "_accounts WHERE uuid = ? LIMIT 1")) {
                stmt.setString(1, account.getUuid().toString());
                stmt.execute();
            }
            try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM " + getTablePrefix() + "_balances WHERE account_id = ?")) {
                stmt.setString(1, account.getUuid().toString());
                stmt.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDatabase() {
        return database;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }
}
