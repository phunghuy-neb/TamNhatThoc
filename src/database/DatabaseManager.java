package database;

import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import shared.User;
import shared.Match;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Quản lý kết nối và thao tác với MongoDB
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> usersCollection;
    private MongoCollection<Document> matchHistoryCollection;
    
    private static final String DB_NAME = "tam_nhat_thoc";
    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    
    private DatabaseManager() {
        try {
            mongoClient = MongoClients.create(CONNECTION_STRING);
            database = mongoClient.getDatabase(DB_NAME);
            usersCollection = database.getCollection("users");
            matchHistoryCollection = database.getCollection("match_history");
            System.out.println("✅ Kết nối MongoDB thành công!");
        } catch (Exception e) {
            System.err.println("❌ Lỗi kết nối MongoDB: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    // ==================== USER OPERATIONS ====================
    
    /**
     * Đăng ký user mới
     * @return true nếu thành công, false nếu username đã tồn tại
     */
    public boolean registerUser(String username, String hashedPassword, String email) {
        try {
            // Kiểm tra username đã tồn tại chưa
            if (usersCollection.find(Filters.eq("username", username)).first() != null) {
                return false; // Username đã tồn tại
            }
            
            Document newUser = new Document()
                    .append("username", username)
                    .append("password", hashedPassword)
                    .append("email", email)
                    .append("total_score", 0)
                    .append("total_wins", 0)
                    .append("total_losses", 0)
                    .append("total_draws", 0)
                    .append("win_rate", 0.0)
                    .append("is_admin", false)
                    .append("created_at", new Date());
            
            usersCollection.insertOne(newUser);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Đăng nhập
     * @return User object nếu thành công, null nếu thất bại
     */
    public User loginUser(String username, String hashedPassword) {
        try {
            Document userDoc = usersCollection.find(
                Filters.and(
                    Filters.eq("username", username),
                    Filters.eq("password", hashedPassword)
                )
            ).first();
            
            if (userDoc == null) {
                return null;
            }
            
            return documentToUser(userDoc);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Lấy thông tin user theo username
     */
    public User getUserByUsername(String username) {
        try {
            Document userDoc = usersCollection.find(Filters.eq("username", username)).first();
            return userDoc != null ? documentToUser(userDoc) : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**l
     * Lấy thông tin user theo ID
     */
    public User getUserById(String userId) {
        try {
            // Tìm user theo hashCode của _id
            FindIterable<Document> results = usersCollection.find();
            for (Document doc : results) {
                if (doc.getObjectId("_id").hashCode() == Integer.parseInt(userId)) {
                    return documentToUser(doc);
                }
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Cập nhật điểm sau trận đấu
     */
    public void updateUserScore(String userId, int scoreToAdd, String result) {
        try {
            // Tìm user theo hashCode của _id
            FindIterable<Document> results = usersCollection.find();
            ObjectId userObjectId = null;
            Document userDoc = null;
            
            for (Document doc : results) {
                if (doc.getObjectId("_id").hashCode() == Integer.parseInt(userId)) {
                    userObjectId = doc.getObjectId("_id");
                    userDoc = doc;
                    break;
                }
            }
            
            if (userDoc == null) return;
            
            int currentScore = userDoc.getInteger("total_score", 0);
            int wins = userDoc.getInteger("total_wins", 0);
            int losses = userDoc.getInteger("total_losses", 0);
            int draws = userDoc.getInteger("total_draws", 0);
            int newScore = currentScore;
            
            // Cập nhật kết quả và điểm
            if ("win".equals(result)) {
                wins++;
                newScore = currentScore + scoreToAdd; // Người thắng: cộng toàn bộ điểm
            } else if ("lose".equals(result)) {
                losses++;
                // Người thua: KHÔNG cộng điểm (hoặc chỉ cộng 10%)
                // newScore = currentScore + (scoreToAdd / 10);
            } else if ("draw".equals(result)) {
                draws++;
                newScore = currentScore + (scoreToAdd / 2); // Hòa: cộng 50% điểm
            }
            
            // Tính tỷ lệ thắng
            int totalMatches = wins + losses + draws;
            double winRate = totalMatches > 0 ? (wins * 100.0 / totalMatches) : 0.0;
            
            usersCollection.updateOne(
                Filters.eq("_id", userObjectId),
                Updates.combine(
                    Updates.set("total_score", newScore),
                    Updates.set("total_wins", wins),
                    Updates.set("total_losses", losses),
                    Updates.set("total_draws", draws),
                    Updates.set("win_rate", winRate)
                )
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Cập nhật thông tin cá nhân
     */
    public boolean updateUserProfile(String userId, String email) {
        try {
            // Tìm user theo hashCode của _id
            FindIterable<Document> results = usersCollection.find();
            ObjectId userObjectId = null;
            
            for (Document doc : results) {
                if (doc.getObjectId("_id").hashCode() == Integer.parseInt(userId)) {
                    userObjectId = doc.getObjectId("_id");
                    break;
                }
            }
            
            if (userObjectId == null) return false;
            
            usersCollection.updateOne(
                Filters.eq("_id", userObjectId),
                Updates.set("email", email)
            );
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Lấy bảng xếp hạng
     */
    public List<User> getLeaderboard(int limit) {
        List<User> leaderboard = new ArrayList<>();
        try {
            FindIterable<Document> results = usersCollection.find()
                    .sort(Sorts.orderBy(
                        Sorts.descending("total_score"),
                        Sorts.descending("total_wins")
                    ))
                    .limit(limit);
            
            for (Document doc : results) {
                leaderboard.add(documentToUser(doc));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return leaderboard;
    }
    
    // ==================== MATCH OPERATIONS ====================
    
    /**
     * Lưu lịch sử trận đấu
     */
    public void saveMatch(String player1Id, String player2Id, int player1Score, 
                         int player2Score, String winnerId, int duration) {
        try {
            Document match = new Document()
                    .append("player1_id", player1Id)
                    .append("player2_id", player2Id)
                    .append("player1_score", player1Score)
                    .append("player2_score", player2Score)
                    .append("winner_id", winnerId)
                    .append("match_duration", duration)
                    .append("created_at", new Date());
            
            matchHistoryCollection.insertOne(match);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Lấy lịch sử đấu của user
     */
    public List<Match> getUserMatchHistory(String userId, int limit) {
        List<Match> history = new ArrayList<>();
        try {
            FindIterable<Document> results = matchHistoryCollection.find(
                Filters.or(
                    Filters.eq("player1_id", userId),
                    Filters.eq("player2_id", userId)
                )
            ).sort(Sorts.descending("created_at")).limit(limit);
            
            for (Document doc : results) {
                Match match = new Match();
                match.setMatchId(doc.getObjectId("_id").hashCode());
                match.setPlayer1Id(doc.getString("player1_id").hashCode());
                match.setPlayer2Id(doc.getString("player2_id").hashCode());
                match.setPlayer1Score(doc.getInteger("player1_score", 0));
                match.setPlayer2Score(doc.getInteger("player2_score", 0));
                match.setMatchDuration(doc.getInteger("match_duration", 0));
                match.setCreatedAt(new java.sql.Timestamp(doc.getDate("created_at").getTime()));
                
                // Lấy tên người chơi
                User p1 = getUserById(doc.getString("player1_id"));
                User p2 = getUserById(doc.getString("player2_id"));
                if (p1 != null) match.setPlayer1Name(p1.getUsername());
                if (p2 != null) match.setPlayer2Name(p2.getUsername());
                
                history.add(match);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return history;
    }
    
    // ==================== ADMIN OPERATIONS ====================
    
    /**
     * Xóa user (chỉ admin)
     */
    public boolean deleteUser(String userId) {
        try {
            // Tìm user theo hashCode của _id
            FindIterable<Document> results = usersCollection.find();
            ObjectId userObjectId = null;
            
            for (Document doc : results) {
                if (doc.getObjectId("_id").hashCode() == Integer.parseInt(userId)) {
                    userObjectId = doc.getObjectId("_id");
                    break;
                }
            }
            
            if (userObjectId == null) return false;
            
            usersCollection.deleteOne(Filters.eq("_id", userObjectId));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Reset điểm user (chỉ admin)
     */
    public boolean resetUserScore(String userId) {
        try {
            // Tìm user theo hashCode của _id
            FindIterable<Document> results = usersCollection.find();
            ObjectId userObjectId = null;
            
            for (Document doc : results) {
                if (doc.getObjectId("_id").hashCode() == Integer.parseInt(userId)) {
                    userObjectId = doc.getObjectId("_id");
                    break;
                }
            }
            
            if (userObjectId == null) return false;
            
            usersCollection.updateOne(
                Filters.eq("_id", userObjectId),
                Updates.combine(
                    Updates.set("total_score", 0),
                    Updates.set("total_wins", 0),
                    Updates.set("total_losses", 0),
                    Updates.set("total_draws", 0),
                    Updates.set("win_rate", 0.0)
                )
            );
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private User documentToUser(Document doc) {
        User user = new User();
        user.setUserId(doc.getObjectId("_id").hashCode());
        user.setUsername(doc.getString("username"));
        user.setTotalScore(doc.getInteger("total_score", 0));
        user.setTotalWins(doc.getInteger("total_wins", 0));
        user.setTotalLosses(doc.getInteger("total_losses", 0));
        user.setTotalDraws(doc.getInteger("total_draws", 0));
        user.setWinRate(doc.getDouble("win_rate"));
        user.setStatus("offline"); // Sẽ cập nhật từ server
        return user;
    }
    
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}

