package shared;

import java.io.Serializable;
import java.sql.Timestamp;

public class Match implements Serializable {
    private int matchId;
    private int player1Id;
    private int player2Id;
    private String player1Name;
    private String player2Name;
    private int player1Score;
    private int player2Score;
    private int winnerId;
    private int matchDuration;
    private Timestamp createdAt;
    
    public Match() {}
    
    // Getters and Setters
    public int getMatchId() { return matchId; }
    public void setMatchId(int matchId) { this.matchId = matchId; }
    
    public int getPlayer1Id() { return player1Id; }
    public void setPlayer1Id(int player1Id) { this.player1Id = player1Id; }
    
    public int getPlayer2Id() { return player2Id; }
    public void setPlayer2Id(int player2Id) { this.player2Id = player2Id; }
    
    public String getPlayer1Name() { return player1Name; }
    public void setPlayer1Name(String player1Name) { this.player1Name = player1Name; }
    
    public String getPlayer2Name() { return player2Name; }
    public void setPlayer2Name(String player2Name) { this.player2Name = player2Name; }
    
    public int getPlayer1Score() { return player1Score; }
    public void setPlayer1Score(int player1Score) { this.player1Score = player1Score; }
    
    public int getPlayer2Score() { return player2Score; }
    public void setPlayer2Score(int player2Score) { this.player2Score = player2Score; }
    
    public int getWinnerId() { return winnerId; }
    public void setWinnerId(int winnerId) { this.winnerId = winnerId; }
    
    public int getMatchDuration() { return matchDuration; }
    public void setMatchDuration(int matchDuration) { this.matchDuration = matchDuration; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

