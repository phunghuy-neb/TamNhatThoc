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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý kết nối và thao tác với MongoDB
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> usersCollection;
    private MongoCollection<Document> matchHistoryCollection;
    
    // BUG FIX #1: Cache để tránh O(n) query performance issue
    private Map<String, User> userCache;
    
    private static final String DB_NAME = "tam_nhat_thoc";
    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    
    private DatabaseManager() {
        try {
            mongoClient = MongoClients.create(CONNECTION_STRING);
            database = mongoClient.getDatabase(DB_NAME);
            usersCollection = database.getCollection("users");
            matchHistoryCollection = database.getCollection("match_history");
            
            // Initialize cache (thread-safe)
            userCache = new ConcurrentHashMap<>();
            
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
            
            // BUG FIX #1: Cache user mới ngay sau khi tạo
            // (Tối ưu cho trường hợp register → login → play game ngay)
            // Note: Không cache ngay vì chưa có ObjectId, sẽ cache khi login
            
            System.out.println("✅ User registered successfully: " + username);
            return true;
        } catch (Exception e) {
            // BUG FIX #22: Better error handling - phân biệt các loại lỗi
            String errorMsg = e.getMessage();
            
            // Check if it's a duplicate key error (race condition case)
            if (errorMsg != null && (errorMsg.contains("duplicate key") || errorMsg.contains("E11000"))) {
                System.out.println("⚠️ Duplicate username (race condition): " + username);
                return false; // Username đã tồn tại (race condition)
            }
            
            // Other database errors - log chi tiết
            System.err.println("❌ Database error during registration for user: " + username);
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Đăng nhập
     * BUG FIX #1: Cache user sau khi login thành công
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
            
            User user = documentToUser(userDoc);
            
            // BUG FIX #1: Cache user ngay sau khi login
            String userId = String.valueOf(user.getUserId());
            userCache.put(userId, user);
            System.out.println("✅ User cached on login: " + user.getUsername());
            
            return user;
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
    
    /**
     * Helper method: Tìm Document của user theo userId
     * BUG FIX #1: Centralized method để dễ optimize sau
     */
    private Document findUserDocument(String userId) throws NumberFormatException {
        int userIdHash = Integer.parseInt(userId);
        FindIterable<Document> results = usersCollection.find();
        for (Document doc : results) {
            if (doc.getObjectId("_id").hashCode() == userIdHash) {
                return doc;
            }
        }
        return null;
    }
    
    /**
     * Lấy thông tin user theo ID (with caching)
     * BUG FIX #1: Thêm cache để tránh O(n) scan toàn bộ users collection
     */
    public User getUserById(String userId) {
        try {
            // Check cache trước (O(1))
            User cachedUser = userCache.get(userId);
            if (cachedUser != null) {
                System.out.println("🎯 Cache hit for userId: " + userId);
                return cachedUser;
            }
            
            System.out.println("💾 Cache miss for userId: " + userId + ", querying database...");
            
            // Cache miss → Query database
            Document userDoc = findUserDocument(userId);
            if (userDoc != null) {
                User user = documentToUser(userDoc);
                
                // Lưu vào cache cho lần sau
                userCache.put(userId, user);
                System.out.println("✅ Cached user: " + user.getUsername());
                
                return user;
            }
            return null;
        } catch (NumberFormatException e) {
            System.out.println("❌ Invalid userId format: " + userId);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Cập nhật điểm sau trận đấu
     * BUG FIX #1: Dùng helper method để tránh duplicate O(n) scan
     */
    public void updateUserScore(String userId, int scoreToAdd, String result) {
        try {
            // Tìm user document (optimized)
            Document userDoc = findUserDocument(userId);
            if (userDoc == null) {
                System.out.println("❌ User not found for score update: " + userId);
                return;
            }
            
            ObjectId userObjectId = userDoc.getObjectId("_id");
            
            int currentScore = userDoc.getInteger("total_score", 0);
            int wins = userDoc.getInteger("total_wins", 0);
            int losses = userDoc.getInteger("total_losses", 0);
            int draws = userDoc.getInteger("total_draws", 0);
            int newScore = currentScore;
            
            // Cập nhật kết quả và điểm
            System.out.println("🔍 DEBUG DatabaseManager: User " + userId + ", result: " + result + 
                             ", scoreToAdd: " + scoreToAdd + ", currentScore: " + currentScore);
            
            if ("win".equals(result)) {
                wins++;
                newScore = currentScore + scoreToAdd; // Người thắng: cộng toàn bộ điểm
                System.out.println("✅ WIN: " + scoreToAdd + " points added, new score: " + newScore);
            } else if ("lose".equals(result)) {
                losses++;
                int halfScore = (scoreToAdd + 1) / 2; // Làm tròn lên
                newScore = currentScore + halfScore; // Người thua: cộng 50% điểm
                System.out.println("❌ LOSE: " + halfScore + " points added (50% of " + scoreToAdd + "), new score: " + newScore);
            } else if ("draw".equals(result)) {
                draws++;
                newScore = currentScore + scoreToAdd; // Hòa: cộng toàn bộ điểm
                System.out.println("🤝 DRAW: " + scoreToAdd + " points added, new score: " + newScore);
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
            
            // BUG FIX #1: Invalidate cache sau khi update
            userCache.remove(userId);
            System.out.println("🗑️ Cache invalidated for userId: " + userId);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Đổi mật khẩu (yêu cầu xác thực mật khẩu cũ)
     * BUG FIX #1: Dùng helper method để tránh duplicate O(n) scan
     * @param userId ID của user
     * @param oldHashedPassword Mật khẩu cũ đã hash (để verify)
     * @param newHashedPassword Mật khẩu mới đã hash
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean changePassword(String userId, String oldHashedPassword, String newHashedPassword) {
        try {
            // Tìm user document (optimized)
            Document userDoc = findUserDocument(userId);
            if (userDoc == null) {
                System.out.println("❌ User not found for password change");
                return false;
            }
            
            ObjectId userObjectId = userDoc.getObjectId("_id");
            
            // Verify mật khẩu cũ
            String currentPassword = userDoc.getString("password");
            if (!currentPassword.equals(oldHashedPassword)) {
                System.out.println("❌ Old password incorrect");
                return false;
            }
            
            // Cập nhật mật khẩu mới
            usersCollection.updateOne(
                Filters.eq("_id", userObjectId),
                Updates.set("password", newHashedPassword)
            );
            
            // BUG FIX #1: Invalidate cache sau khi update password
            userCache.remove(userId);
            System.out.println("🗑️ Cache invalidated for userId: " + userId);
            
            System.out.println("✅ Password changed successfully for user: " + userDoc.getString("username"));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Kiểm tra username đã tồn tại chưa
     */
    public boolean isUsernameExists(String username) {
        try {
            Document userDoc = usersCollection.find(Filters.eq("username", username)).first();
            return userDoc != null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Đổi tên đăng nhập
     */
    public boolean changeUsername(String userId, String newUsername) {
        try {
            // Tìm user document
            Document userDoc = findUserDocument(userId);
            if (userDoc == null) {
                System.out.println("❌ User not found for username change");
                return false;
            }
            
            ObjectId userObjectId = userDoc.getObjectId("_id");
            
            // Cập nhật username mới
            usersCollection.updateOne(
                Filters.eq("_id", userObjectId),
                Updates.set("username", newUsername)
            );
            
            // BUG FIX #1: Invalidate cache sau khi update username
            userCache.remove(userId);
            System.out.println("🗑️ Cache invalidated for userId: " + userId);
            
            System.out.println("✅ Username changed successfully to: " + newUsername);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Cập nhật cache user sau khi thay đổi thông tin
     */
    public void updateUserCache(User updatedUser) {
        String userId = String.valueOf(updatedUser.getUserId());
        userCache.put(userId, updatedUser);
        System.out.println("✅ User cache updated: " + updatedUser.getUsername());
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
    
    /**
     * Lấy tất cả người chơi trong database
     */
    public List<User> getAllUsers() {
        List<User> allUsers = new ArrayList<>();
        try {
            FindIterable<Document> results = usersCollection.find()
                    .sort(Sorts.orderBy(
                        Sorts.descending("total_score"),
                        Sorts.descending("total_wins")
                    ));
            
            for (Document doc : results) {
                allUsers.add(documentToUser(doc));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return allUsers;
    }
    
    // ==================== MATCH OPERATIONS ====================
    
    /**
     * Lưu lịch sử trận đấu
     * BUG FIX #3: Thêm player names để tránh N+1 query problem
     */
    public void saveMatch(String player1Id, String player2Id, int player1Score, 
                         int player2Score, String winnerId, int duration,
                         String player1Name, String player2Name) {
        try {
            Document match = new Document()
                    .append("player1_id", player1Id)
                    .append("player2_id", player2Id)
                    .append("player1_name", player1Name)      // ✅ Lưu tên luôn
                    .append("player2_name", player2Name)      // ✅ Lưu tên luôn
                    .append("player1_score", player1Score)
                    .append("player2_score", player2Score)
                    .append("winner_id", winnerId)
                    .append("match_duration", duration)
                    .append("created_at", new Date());
            
            matchHistoryCollection.insertOne(match);
            System.out.println("✅ Match saved: " + player1Name + " vs " + player2Name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Lấy lịch sử đấu của user
     * BUG FIX #3 & #8: Lấy player names từ DB thay vì query getUserById() (N+1 problem)
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
                
                // BUG FIX #3: Lấy tên từ DB thay vì query
                String player1Name = doc.getString("player1_name");
                String player2Name = doc.getString("player2_name");
                
                // Fallback cho matches cũ (không có player names trong DB)
                if (player1Name == null || player2Name == null) {
                    System.out.println("⚠️ Old match without names, querying users (one-time)...");
                    User p1 = getUserById(doc.getString("player1_id"));
                    User p2 = getUserById(doc.getString("player2_id"));
                    player1Name = p1 != null ? p1.getUsername() : "Unknown";
                    player2Name = p2 != null ? p2.getUsername() : "Unknown";
                }
                
                match.setPlayer1Name(player1Name);
                match.setPlayer2Name(player2Name);
                
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
     * BUG FIX #1: Dùng helper method để tránh duplicate O(n) scan
     */
    public boolean deleteUser(String userId) {
        try {
            // Tìm user document (optimized)
            Document userDoc = findUserDocument(userId);
            if (userDoc == null) return false;
            
            ObjectId userObjectId = userDoc.getObjectId("_id");
            usersCollection.deleteOne(Filters.eq("_id", userObjectId));
            
            // Invalidate cache
            userCache.remove(userId);
            System.out.println("🗑️ User deleted and cache cleared: " + userId);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Reset điểm user (chỉ admin)
     * BUG FIX #1: Dùng helper method để tránh duplicate O(n) scan
     */
    public boolean resetUserScore(String userId) {
        try {
            // Tìm user document (optimized)
            Document userDoc = findUserDocument(userId);
            if (userDoc == null) return false;
            
            ObjectId userObjectId = userDoc.getObjectId("_id");
            
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
            
            // BUG FIX #1: Invalidate cache sau khi reset
            userCache.remove(userId);
            System.out.println("🗑️ Cache invalidated for userId: " + userId);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Clear user cache (for maintenance or testing)
     */
    public void clearCache() {
        userCache.clear();
        System.out.println("🗑️ User cache cleared");
    }
    
    /**
     * Get cache statistics (for monitoring)
     */
    public int getCacheSize() {
        return userCache.size();
    }
    
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
            // Clear cache trước khi close
            clearCache();
            mongoClient.close();
        }
    }
}

