package database;

import com.mongodb.client.*;
import org.bson.Document;
import com.mongodb.client.model.IndexOptions;

import java.util.Date;

/**
 * Setup MongoDB tự động - chạy 1 lần để khởi tạo database
 * Chạy trong NetBeans: Shift+F6 trên file này
 */
public class MongoDBSetup {
    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DB_NAME = "tam_nhat_thoc";
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  MONGODB SETUP - TẤM NHẶT THÓC");
        System.out.println("========================================\n");
        
        MongoClient mongoClient = null;
        
        try {
            // Kết nối MongoDB
            System.out.println("🔌 Đang kết nối MongoDB...");
            mongoClient = MongoClients.create(CONNECTION_STRING);
            MongoDatabase database = mongoClient.getDatabase(DB_NAME);
            System.out.println("✅ Kết nối thành công!\n");
            
            // ==================== SETUP COLLECTIONS ====================
            
            // Xóa collections cũ nếu có (để reset)
            System.out.println("🗑️  Xóa dữ liệu cũ (nếu có)...");
            try {
                database.getCollection("users").drop();
                database.getCollection("match_history").drop();
                System.out.println("✅ Đã xóa collections cũ\n");
            } catch (Exception e) {
                System.out.println("ℹ️  Không có dữ liệu cũ\n");
            }
            
            // Tạo collection users
            System.out.println("📦 Tạo collection 'users'...");
            database.createCollection("users");
            MongoCollection<Document> usersCollection = database.getCollection("users");
            
            // Tạo collection match_history
            System.out.println("📦 Tạo collection 'match_history'...");
            database.createCollection("match_history");
            MongoCollection<Document> matchHistoryCollection = database.getCollection("match_history");
            System.out.println("✅ Collections đã tạo!\n");
            
            // ==================== TẠO INDEXES ====================
            
            System.out.println("🔑 Tạo indexes...");
            
            // Index unique cho username
            usersCollection.createIndex(
                new Document("username", 1),
                new IndexOptions().unique(true)
            );
            
            // Index cho total_score (để sắp xếp bảng xếp hạng)
            usersCollection.createIndex(new Document("total_score", -1));
            
            // Indexes cho match_history
            matchHistoryCollection.createIndex(new Document("player1_id", 1));
            matchHistoryCollection.createIndex(new Document("player2_id", 1));
            matchHistoryCollection.createIndex(new Document("created_at", -1));
            
            System.out.println("✅ Indexes đã tạo!\n");
            
            // ==================== THÊM TÀI KHOẢN TEST ====================
            
            System.out.println("👤 Tạo tài khoản test...\n");
            
            // Password hash SHA-256:
            // "admin123" -> 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
            // "123456" -> 8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918
            
            // Tài khoản Admin
            Document admin = new Document()
                .append("username", "admin")
                .append("password", "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9")
                .append("email", "admin@tamnhatthoc.com")
                .append("total_score", 0)
                .append("total_wins", 0)
                .append("total_losses", 0)
                .append("total_draws", 0)
                .append("win_rate", 0.0)
                .append("is_admin", true)
                .append("created_at", new Date());
            usersCollection.insertOne(admin);
            System.out.println("✅ Tạo tài khoản: admin / admin123 (Admin)");
            
            // Tài khoản Player 1
            Document player1 = new Document()
                .append("username", "player1")
                .append("password", "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918")
                .append("email", "player1@test.com")
                .append("total_score", 1500)
                .append("total_wins", 45)
                .append("total_losses", 30)
                .append("total_draws", 5)
                .append("win_rate", 56.25)
                .append("is_admin", false)
                .append("created_at", new Date());
            usersCollection.insertOne(player1);
            System.out.println("✅ Tạo tài khoản: player1 / 123456");
            
            // Tài khoản Player 2
            Document player2 = new Document()
                .append("username", "player2")
                .append("password", "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918")
                .append("email", "player2@test.com")
                .append("total_score", 2000)
                .append("total_wins", 60)
                .append("total_losses", 20)
                .append("total_draws", 0)
                .append("win_rate", 75.0)
                .append("is_admin", false)
                .append("created_at", new Date());
            usersCollection.insertOne(player2);
            System.out.println("✅ Tạo tài khoản: player2 / 123456\n");
            
            // ==================== KIỂM TRA ====================
            
            System.out.println("🔍 Kiểm tra kết quả...");
            long userCount = usersCollection.countDocuments();
            System.out.println("   - Users: " + userCount + " tài khoản");
            System.out.println("   - Database: " + DB_NAME);
            System.out.println("   - Collections: users, match_history\n");
            
            // ==================== HOÀN TẤT ====================
            
            System.out.println("========================================");
            System.out.println("  ✅ SETUP HOÀN TẤT!");
            System.out.println("========================================\n");
            
            System.out.println("📋 Tài khoản test đã tạo:");
            System.out.println("   1. admin / admin123 (Admin)");
            System.out.println("   2. player1 / 123456");
            System.out.println("   3. player2 / 123456\n");
            
            System.out.println("🎮 Bây giờ bạn có thể chạy game:");
            System.out.println("   1. Server: Mở GameServer.java -> Shift+F6");
            System.out.println("   2. Client: Mở LoginFrame.java -> Shift+F6\n");
            
            System.out.println("💡 QUAN TRỌNG: Chạy Server trước, sau đó mới chạy Client!\n");
            
        } catch (Exception e) {
            System.err.println("\n❌ LỖI: " + e.getMessage());
            System.err.println("\n⚠️  Vui lòng kiểm tra:");
            System.err.println("   1. MongoDB đã chạy chưa?");
            System.err.println("      Windows: net start MongoDB");
            System.err.println("   2. MongoDB đang lắng nghe ở localhost:27017?");
            System.err.println("   3. Có thể kết nối MongoDB qua Compass không?\n");
            e.printStackTrace();
        } finally {
            if (mongoClient != null) {
                mongoClient.close();
            }
        }
    }
}

