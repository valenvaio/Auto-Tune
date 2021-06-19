package unprotesting.com.github.data.ephemeral.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.economy.EconomyFunctions;

public class GDPData {

    @Getter
    private double GDP,
                   balance,
                   debt, 
                   loss;
    @Getter
    private int playerCount;

    public GDPData(double GDP, double balance, double loss, double debt, int playerCount){
        this.GDP = GDP;
        this.loss = loss;
        this.balance = balance;
        this.playerCount = playerCount;
        this.debt = debt;
    }

    public void increaseGDP(double d){
        this.GDP += d;
    }

    public void increaseLoss(double d){
        this.loss += d;
    }

    public void updateBalance(){
        double server_balance = 0;
        int server_player_count = 0;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()){
            if (player == null){
                continue;
            }
            try{
                double bal = EconomyFunctions.getEconomy().getBalance(player);
                server_balance += bal;
            }
            catch(RuntimeException e){}
            server_player_count++;
        }
        this.balance = server_balance;
        this.playerCount = server_player_count;
    }

    public void updateDebt(){
        double server_debt = 0;
        for (LoanData data : Main.getCache().getLOANS()){
            server_debt += data.getValue();
        }
        this.debt = server_debt;
    }
    
}