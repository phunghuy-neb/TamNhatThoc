package shared;

import java.io.Serializable;

public class User implements Serializable {
    private int userId;
    private String username;
    private int totalScore;
    private int totalWins;
    private int totalLosses;
    private int totalDraws;
    private double winRate;
    private String status; // "online", "playing", "offline"
    
    public User() {}
    
    public User(int userId, String username, int totalScore, int totalWins, 
                int totalLosses, int totalDraws, double winRate, String status) {
        this.userId = userId;
        this.username = username;
        this.totalScore = totalScore;
        this.totalWins = totalWins;
        this.totalLosses = totalLosses;
        this.totalDraws = totalDraws;
        this.winRate = winRate;
        this.status = status;
    }
    
    // Getters and Setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    
    public int getTotalWins() { return totalWins; }
    public void setTotalWins(int totalWins) { this.totalWins = totalWins; }
    
    public int getTotalLosses() { return totalLosses; }
    public void setTotalLosses(int totalLosses) { this.totalLosses = totalLosses; }
    
    public int getTotalDraws() { return totalDraws; }
    public void setTotalDraws(int totalDraws) { this.totalDraws = totalDraws; }
    
    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public int getTotalMatches() {
        return totalWins + totalLosses + totalDraws;
    }
}

