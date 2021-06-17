package unprotesting.com.github.Data.Ephemeral;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.Objects.Section;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.CSV.CSVReader;
import unprotesting.com.github.Data.Ephemeral.Data.EnchantmentData;
import unprotesting.com.github.Data.Ephemeral.Data.GDPData;
import unprotesting.com.github.Data.Ephemeral.Data.ItemData;
import unprotesting.com.github.Data.Ephemeral.Data.LoanData;
import unprotesting.com.github.Data.Ephemeral.Data.MaxBuySellData;
import unprotesting.com.github.Data.Ephemeral.Data.TransactionData;
import unprotesting.com.github.Data.Ephemeral.Data.TransactionData.TransactionPositionType;
import unprotesting.com.github.Data.Ephemeral.Other.PlayerSaleData;
import unprotesting.com.github.Data.Ephemeral.Other.Sale;
import unprotesting.com.github.Data.Ephemeral.Other.Sale.SalePositionType;
import unprotesting.com.github.Data.Persistent.TimePeriods.EnchantmentsTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.GDPTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.ItemTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.LoanTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.TransactionsTimePeriod;
import unprotesting.com.github.Logging.Logging;

//  Global functions file between ephemeral and persistent storage

public class LocalDataCache {

    //  Globally accessable caches for persistent storage

    @Getter
    private ConcurrentHashMap<String, ItemData> ITEMS;
    @Getter
    private ConcurrentHashMap<String, EnchantmentData> ENCHANTMENTS;
    @Getter
    private List<LoanData> LOANS;
    @Getter
    private List<TransactionData> TRANSACTIONS;
    @Getter
    private ConcurrentHashMap<UUID, PlayerSaleData> PLAYER_SALES;
    @Getter
    private List<Section> SECTIONS;
    @Getter
    private ConcurrentHashMap<String, MaxBuySellData> MAX_PURCHASES;
    @Getter
    private ConcurrentHashMap<String, Double> PERCENTAGE_CHANGES;
    @Getter
    private GDPData GDPDATA;


    private int size;

    public LocalDataCache(){
        this.ITEMS = new ConcurrentHashMap<String, ItemData>();
        this.ENCHANTMENTS = new ConcurrentHashMap<String, EnchantmentData>();
        this.LOANS = new ArrayList<LoanData>();
        this.TRANSACTIONS = new ArrayList<TransactionData>();
        this.PLAYER_SALES = new ConcurrentHashMap<UUID, PlayerSaleData>();
        this.SECTIONS = new ArrayList<Section>();
        this.MAX_PURCHASES = new ConcurrentHashMap<String, MaxBuySellData>();
        this.PERCENTAGE_CHANGES = new ConcurrentHashMap<String, Double>();
        this.size = Main.getDatabase().map.size();
        init();
    }

    //  Add a new sale to related maps depending on type, item, etc.
    public void addSale(Player player, String item, double price, int amount, SalePositionType position){
        PlayerSaleData playerSaleData = getPlayerSaleData(player);
        playerSaleData.addSale(item, amount, position);
        UUID uuid = player.getUniqueId();
        this.PLAYER_SALES.put(player.getUniqueId(), playerSaleData);
        String uuid_string = uuid.toString();
        try{
            switch(position){
                case BUY:
                    ItemData bdata = this.ITEMS.get(item);
                    bdata.increaseBuys(amount);
                    this.ITEMS.put(item, bdata);
                    this.TRANSACTIONS.add(new TransactionData(uuid_string, item, amount, price, TransactionPositionType.BI));
                    this.GDPDATA.increaseGDP((amount*price));
                    break;
                case SELL:
                    ItemData sdata = this.ITEMS.get(item);
                    sdata.increaseSells(amount);
                    this.ITEMS.put(item, sdata);
                    this.TRANSACTIONS.add(new TransactionData(uuid_string, item, amount, price, TransactionPositionType.SI));
                    this.GDPDATA.increaseGDP((amount*price));
                    this.GDPDATA.increaseLoss((amount*getItemPrice(item, false))-(amount*price));
                    break;
                case EBUY:
                    EnchantmentData ebdata = this.ENCHANTMENTS.get(item);
                    ebdata.increaseBuys(amount);
                    this.ENCHANTMENTS.put(item, ebdata);
                    this.TRANSACTIONS.add(new TransactionData(uuid_string, item, amount, price, TransactionPositionType.BE));
                    this.GDPDATA.increaseGDP((amount*price));
                    break;
                case ESELL:
                    EnchantmentData esdata = this.ENCHANTMENTS.get(item);
                    esdata.increaseSells(amount);
                    this.ENCHANTMENTS.put(item, esdata);
                    this.TRANSACTIONS.add(new TransactionData(uuid_string, item, amount, price, TransactionPositionType.SE));
                    this.GDPDATA.increaseGDP((amount*price));
                    this.GDPDATA.increaseLoss((amount*getOverallEnchantmentPrice(item,
                     getItemPrice(player.getInventory().getItemInMainHand().getType().toString(), false), false)-(amount*price)));
                    break;
                default:
                    break;
            }
        }
        catch(NullPointerException | IllegalArgumentException e){
            Logging.error(4);
            System.out.println("Cannot parse " + item + " into " + position.toString());
        }
    }

    //  Add a new loan to ephemeral storage
    public void addLoan(double value, double intrest_rate, Player player){
        this.LOANS.add(new LoanData(value, intrest_rate, player));
        Collections.sort(LOANS);
    }

    //  Get item price
    public double getItemPrice(String item, boolean sell){
        Double price;
        try{
            price = this.ITEMS.get(item).getPrice();
        }
        catch(NullPointerException e){
            try{
                price = getEnchantmentPrice(item, sell);
                return price;
            }
            catch(NullPointerException e2){
                return 0;
            }
        };
        if (!sell){
            return price;
        }
        Double spd = Config.getSellPriceDifference();
        if (Main.getDfiles().getShops().getConfigurationSection("shops").getConfigurationSection(item).contains("sell-difference")){
            spd = Main.getDfiles().getShops().getConfigurationSection("shops").getConfigurationSection(item).getDouble("sell-difference");
        }
        return (price - price*spd*0.01);
    }

    //  Get enchantment price
    public double getEnchantmentPrice(String enchantment, boolean sell){
        Double price;
        try{
            price = this.ENCHANTMENTS.get(enchantment).getPrice();
        }
        catch(NullPointerException e){
            return 0;
        };
        if (!sell){
            return price;
        }
        Double spd = Config.getSellPriceDifference();
        if (Main.getDfiles().getEnchantments().getConfigurationSection("enchantments").getConfigurationSection(enchantment).contains("sell-difference")){
            spd = Main.getDfiles().getEnchantments().getConfigurationSection("enchantments").getConfigurationSection(enchantment).getDouble("sell-difference");
        }
        return (price - price*spd*0.01);
    }

    //  Get enchantement ratio
    public double getEnchantmentRatio(String enchantment){
        Double price;
        try{
            price = this.ENCHANTMENTS.get(enchantment).getRatio();
        }
        catch(NullPointerException e){
            return 0;
        };
        return price;
    }

    //  Get ItemData object for map
    public ItemData getItemData(String item){
        return this.ITEMS.get(item);
    }

    //  Get ItemData object for map
    public EnchantmentData getEnchantmentData(String enchantment){
        return this.ENCHANTMENTS.get(enchantment);
    }

    //  Get price for adding an enchantment to an item
    public double getOverallEnchantmentPrice(String enchantment, double item_price, boolean sell){
        double price = getEnchantmentPrice(enchantment, sell);
        double ratio = getEnchantmentRatio(enchantment);
        return (price + ratio*item_price);
    }

    public int getBuysLeft(String item, Player player){
        PlayerSaleData pdata = PLAYER_SALES.get(player.getUniqueId());
        Integer max;
        try{
            max = MAX_PURCHASES.get(item).getBuys();
        }
        catch(NullPointerException e){
            return 9999;
        }
        try{
            pdata.getBuys().isEmpty();
        }
        catch(NullPointerException e){
            return max;
        }
        int amount = 0;
        for (Sale sale : pdata.getBuys()){
            if (sale.getItem().equals(item)){
                amount += sale.getAmount();
            }
        }
        return max-amount;
    }

    public int getSellsLeft(String item, Player player){
        PlayerSaleData pdata = PLAYER_SALES.get(player.getUniqueId());
        Integer max;
        try{
            max = MAX_PURCHASES.get(item).getSells();
        }
        catch(NullPointerException e){
            return 9999;
        }
        try{
            pdata.getSells().isEmpty();
        }
        catch(NullPointerException e){
            return max;
        }
        int amount = 0;
        for (Sale sale : pdata.getSells()){
            if (sale.getItem().equals(item)){
                amount += sale.getAmount();
            }
        }
        return max-amount;
    }

    public String getPChangeString(String item){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        Double change = this.PERCENTAGE_CHANGES.get(item);
        if (change == null){
            return (ChatColor.GRAY + "%" + 0.0);
        }
        if (change < 0){
            return (ChatColor.RED + "%" + df.format(change));
        }
        if (change > 0){
            return (ChatColor.GREEN + "%" + df.format(change));
        }
        else{
            return (ChatColor.GRAY + "%" + 0.0);
        }
    }

    public void updatePrices(ConcurrentHashMap<String, ItemData> data){
        this.ITEMS = data;
    }

    public void updateEnchantments(ConcurrentHashMap<String, EnchantmentData> data){
        this.ENCHANTMENTS = data;
    }

    public void updatePercentageChanges(){
        double a = Config.getTimePeriod()/1440;
        double b = 1/a;
        int tpInDay = (int) Math.floor(b);
        int i = 0;
        for (String item : Main.getDatabase().map.get(size-1).getItp().getItems()){
            Double price;
            try{
                price = Main.getDatabase().map.get(0).getItp().getPrices()[i];
                if (size-1 > tpInDay){
                    price = Main.getDatabase().map.get(size-tpInDay).getItp().getPrices()[i]; 
                }
            }
            catch(NullPointerException e){
                price = Main.getCache().getItemPrice(item, false);
            }
            double pChange = (Main.getCache().getItemPrice(item, false)-price)/price*100;
            this.PERCENTAGE_CHANGES.put(item, pChange);
            i++;
        }
    }

    //  Initialize cache from configurations and relavent files
    private void init(){
        loadShopDataFromFile();
        loadShopDataFromData();
        loadEnchantmentDataFromFile();
        loadEnchantmentDataFromData();
        loadLoanDataFromData();
        loadTransactionDataFromData();
        loadSectionDataFromFile();
        loadGDPDataFromData();
    }

    //  Get current cache for a players PlayerData object
    private PlayerSaleData getPlayerSaleData(Player player){
        PlayerSaleData playerSaleData = new PlayerSaleData();
        if (this.PLAYER_SALES.contains(player)){
            playerSaleData = this.PLAYER_SALES.get(player.getUniqueId());
        }
        return playerSaleData;
    }

    private void loadShopDataFromFile(){
        ConfigurationSection config = Main.getDfiles().getShops().getConfigurationSection("shops");
        Set<String> set = config.getKeys(false);
        ConcurrentHashMap<String, Double> map = new ConcurrentHashMap<String, Double>();
        try {
            if (Config.isReadFromCSV()){
                map = CSVReader.readData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (String key : set){
            ConfigurationSection section = config.getConfigurationSection(key);
            ItemData data = new ItemData(section.getDouble("price"));
            if (Config.isReadFromCSV()){
                data = new ItemData(map.get(key));
            }
            MaxBuySellData mbsdata = new MaxBuySellData(section.getInt("max-buy"), section.getInt("max-sell"));
            this.MAX_PURCHASES.put(key, mbsdata);
            this.ITEMS.put(key, data);
        }
    }

    private void loadShopDataFromData(){
        if (size < 1){
            for (String str : this.ITEMS.keySet()){
                this.PERCENTAGE_CHANGES.put(str, 0.0);
            }
            return;
        }
        updatePercentageChanges();
        int i = 0;
        ItemTimePeriod ITP = Main.getDatabase().map.get(size-1).getItp();
        for (String item : Main.getDatabase().map.get(size-1).getItp().getItems()){
            ItemData data = new ItemData(ITP.getPrices()[i]);
            this.ITEMS.put(item, data);
            i++;
        }
    }

    private void loadEnchantmentDataFromFile(){
        ConfigurationSection config = Main.getDfiles().getEnchantments().getConfigurationSection("enchantments");
        Set<String> set = config.getKeys(false);
        for (String key : set){
            ConfigurationSection sec = config.getConfigurationSection(key);
            EnchantmentData data = new EnchantmentData(sec.getDouble("price"), sec.getDouble("ratio"));
            this.ENCHANTMENTS.put(key, data);
        }
    }

    private void loadEnchantmentDataFromData(){
        if (size < 1){
            return;
        }
        EnchantmentsTimePeriod ETP = Main.getDatabase().map.get(size-1).getEtp();
        int i = 0;
        for (String item : ETP.getItems()){
            EnchantmentData data = new EnchantmentData(ETP.getPrices()[i], ETP.getRatios()[i]);
            this.ENCHANTMENTS.put(item, data);
            i++;
        }
    }

    private void loadLoanDataFromData(){
        this.LOANS.clear();
        for (Integer pos : Main.getDatabase().map.keySet()){
            LoanTimePeriod LTP = Main.getDatabase().map.get(pos).getLtp();
            for (int i = 0; i < LTP.getValues().length; i++){
                UUID uuid = UUID.fromString(LTP.getPlayers()[i]);
                Player player = Bukkit.getPlayer(uuid);
                LoanData data = new LoanData(LTP.getValues()[i], LTP.getIntrest_rates()[i], player, LTP.getTime()[i]);
                this.LOANS.add(data);
            }
        }
        Collections.sort(this.LOANS);
    }

    private void loadTransactionDataFromData(){
        if (size < 1){
            return;
        }
        this.TRANSACTIONS.clear();
        for (Integer pos : Main.getDatabase().map.keySet()){
            TransactionsTimePeriod TTP = Main.getDatabase().map.get(pos).getTtp();
            for (int i = 0; i < TTP.getPrices().length; i++){
                UUID uuid = UUID.fromString(TTP.getPlayers()[i]);
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                TransactionPositionType position = TransactionPositionType.valueOf(TTP.getPositions()[i]);
                TransactionData data = new TransactionData(player.getUniqueId().toString(), TTP.getItems()[i], TTP.getAmounts()[0], TTP.getPrices()[i], position, TTP.getTime()[i]);
                this.TRANSACTIONS.add(data);
            }
        }
        Collections.sort(this.TRANSACTIONS);
    }

    private void loadSectionDataFromFile(){
        ConfigurationSection csection = Main.getDfiles().getShops().getConfigurationSection("sections");
        for (String section : csection.getKeys(false)){
            ConfigurationSection icsection = csection.getConfigurationSection(section);
            SECTIONS.add(new Section(section, icsection.getString("block"), icsection.getBoolean("back-menu-button-enabled"),
             icsection.getInt("position"), icsection.getString("background")));
        }
        csection = Main.getDfiles().getEnchantments().getConfigurationSection("config");
        SECTIONS.add(new Section("Enchantments", csection.getString("block"), csection.getBoolean("back-menu-button-enabled"),
             csection.getInt("position"), csection.getString("background")));
    }

    private void loadGDPDataFromData(){
        if (size < 1){
            this.GDPDATA = new GDPData(0.0, 0.0);
            return;
        }
        GDPTimePeriod GTP = Main.getDatabase().map.get(size-1).getGtp();
        this.GDPDATA = new GDPData(GTP.getGDP(), GTP.getLoss());
    }

}
