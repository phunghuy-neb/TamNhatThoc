package client.gui;

import client.GameClient;
import org.json.JSONArray;
import org.json.JSONObject;
import shared.Grain;
import shared.Protocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình gameplay - kéo thả hạt gạo/thóc
 */
public class GameplayFrame extends JFrame implements GameClient.MessageListener {
    private static GameplayFrame currentInstance = null; // Track current instance
    
    private GameClient client;
    private JSONObject currentUser;
    private boolean isHost;
    private String opponentUsername;
    
    private List<Grain> grains;
    private Grain draggingGrain;
    private int offsetX, offsetY;
    
    private volatile int myScore; // volatile để tránh race condition
    private int opponentScore;
    private int timeLeft; // giây
    private int totalGrains; // Tổng số hạt trong trận đấu này
    
    private JLabel timerLabel;
    private JLabel myScoreLabel;
    private JLabel opponentScoreLabel;
    private JLabel playerNameLabel;
    private JLabel opponentNameLabel;
    private GamePanel gamePanel;
    
    private Timer gameTimer;
    private Rectangle riceBasket;
    private Rectangle paddyBasket;
    
    private boolean gameEnded;
    
    public GameplayFrame(GameClient client, JSONObject user, JSONObject gameStartData, boolean isHost) {
        // Đóng instance cũ nếu có và remove listener
        if (currentInstance != null && currentInstance != this) {
            currentInstance.client.removeMessageListener(currentInstance);
            currentInstance.dispose();
        }
        currentInstance = this;
        
        this.client = client;
        this.currentUser = user;
        this.isHost = isHost;
        this.myScore = 0;
        System.out.println("🔍 Constructor - myScore initialized to: " + myScore + ", instance: " + System.identityHashCode(this));
        this.opponentScore = 0;
        this.timeLeft = 300; // 5 phút = 300 giây
        this.gameEnded = false;
        this.totalGrains = gameStartData.optInt("total_grains", 100); // Lấy tổng số hạt từ server
        
        // Lấy tên đối thủ từ gameStartData
        this.opponentUsername = gameStartData.optString("opponent_username", "Đối thủ");
        
        setTitle("Tấm Nhặt Thóc - Đang chơi");
        // Tối ưu kích thước để vừa khít màn hình
        setSize(1200, 800);
        setLocationRelativeTo(null); // Căn giữa màn hình
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        
        client.addMessageListener(this);
        
        // Parse grains data
        parseGrainsData(gameStartData);
        
        initComponents();
        startGameTimer();
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleQuit();
            }
        });
    }
    
    private void parseGrainsData(JSONObject data) {
        grains = new ArrayList<>();
        JSONArray grainsArray = data.getJSONArray("grains");
        
        for (int i = 0; i < grainsArray.length(); i++) {
            JSONObject grainObj = grainsArray.getJSONObject(i);
            Grain grain = new Grain(
                grainObj.getInt("id"),
                grainObj.getString("type"),
                grainObj.getInt("x"),
                grainObj.getInt("y")
            );
            grains.add(grain);
        }
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Top panel - Info
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBackground(new Color(76, 175, 80));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        
        timerLabel = new JLabel("⏱️ Thời gian: 02:00");
        timerLabel.setFont(new Font("Arial", Font.BOLD, 18));
        timerLabel.setForeground(Color.WHITE);
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JPanel scoresPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        scoresPanel.setOpaque(false);
        
        myScoreLabel = new JLabel("📊 Bạn: 0");
        myScoreLabel.setFont(new Font("Arial", Font.BOLD, 16));
        myScoreLabel.setForeground(Color.WHITE);
        
        opponentScoreLabel = new JLabel("👤 Đối thủ: 0");
        opponentScoreLabel.setFont(new Font("Arial", Font.BOLD, 16));
        opponentScoreLabel.setForeground(Color.WHITE);
        opponentScoreLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        
        // Tên người chơi ở góc
        playerNameLabel = new JLabel(currentUser.getString("username"));
        playerNameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        playerNameLabel.setForeground(new Color(255, 255, 0)); // Màu vàng
        playerNameLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        opponentNameLabel = new JLabel(opponentUsername);
        opponentNameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        opponentNameLabel.setForeground(new Color(255, 255, 0)); // Màu vàng
        opponentNameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        opponentNameLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        scoresPanel.add(myScoreLabel);
        scoresPanel.add(opponentScoreLabel);
        
        // Panel tên người chơi
        JPanel namesPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        namesPanel.setOpaque(false);
        namesPanel.add(playerNameLabel);
        namesPanel.add(opponentNameLabel);
        
        infoPanel.add(timerLabel, BorderLayout.CENTER);
        infoPanel.add(namesPanel, BorderLayout.NORTH);
        infoPanel.add(scoresPanel, BorderLayout.SOUTH);
        
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        
        // Game panel
        gamePanel = new GamePanel();
        mainPanel.add(gamePanel, BorderLayout.CENTER);
        
        // Bottom panel - Info only (no finish button)
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        
        JLabel infoLabel = new JLabel("Kéo thả hạt gạo vào giỏ gạo, hạt thóc vào giỏ thóc");
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        infoLabel.setForeground(new Color(100, 100, 100));
        bottomPanel.add(infoLabel);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    private void startGameTimer() {
        gameTimer = new Timer(1000, e -> {
            if (gameEnded) {
                gameTimer.stop();
                return;
            }
            
            timeLeft--;
            
            int minutes = timeLeft / 60;
            int seconds = timeLeft % 60;
            timerLabel.setText(String.format("⏱️ Thời gian: %02d:%02d", minutes, seconds));
            
            if (timeLeft <= 0) {
                gameTimer.stop();
                handleTimeout();
            } else if (timeLeft <= 10) {
                timerLabel.setForeground(Color.RED);
            }
        });
        gameTimer.start();
    }
    
    
    private void handleMaxScore() {
        if (gameEnded) return;
        
        System.out.println("🎯 handleMaxScore called - myScore: " + myScore);
        gameEnded = true;
        gameTimer.stop();
        gamePanel.setEnabled(false);
        
        // Gửi thông báo đạt điểm tối đa
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.MAX_SCORE);
        packet.put("final_score", myScore);
        System.out.println("📤 Sending MAX_SCORE packet: " + packet.toString());
        client.sendMessage(packet.toString());
        
        JOptionPane.showMessageDialog(this, 
            "🎉 Bạn đã hoàn thành tất cả hạt!\nChờ kết quả...", 
            "Hoàn thành!", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private synchronized void handleTimeout() {
        if (gameEnded) return;
        
        gameEnded = true;
        gameTimer.stop();
        gamePanel.setEnabled(false);
        
        System.out.println("🔍 handleTimeout DEBUG - myScore: " + myScore + ", instance: " + System.identityHashCode(this));
        
        // Double-check: nếu myScore = 0 nhưng có grains đã được thu thập, có thể bị reset
        if (myScore == 0 && !grains.isEmpty()) {
            System.out.println("⚠️ WARNING: myScore is 0 but grains still exist! This might be a bug.");
        }
        
        // Gửi packet với điểm hiện tại
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.TIMEOUT);
        packet.put("final_score", myScore);
        System.out.println("📤 Sending TIMEOUT packet: " + packet.toString());
        client.sendMessage(packet.toString());
        
        // Hiển thị thông báo hết thời gian (không chờ kết quả)
        JOptionPane.showMessageDialog(this, 
            "⏰ Thời gian đã hết!", 
            "Hết thời gian", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void handleQuit() {
        if (gameEnded) return;
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Nếu thoát bạn sẽ tự động thua!\nBạn có chắc muốn thoát?", 
            "Cảnh báo", 
            JOptionPane.YES_NO_OPTION, 
            JOptionPane.WARNING_MESSAGE);
        
        if (confirm == JOptionPane.YES_OPTION) {
            gameEnded = true;
            gameTimer.stop();
            gamePanel.setEnabled(false);
            
            // Gửi timeout với điểm hiện tại và flag thoát
            JSONObject packet = new JSONObject();
            packet.put("type", Protocol.TIMEOUT);
            packet.put("final_score", myScore);
            packet.put("is_quit", true); // Đánh dấu là thoát
            client.sendMessage(packet.toString());
            
            // Remove listener SAU KHI gửi packet
            client.removeMessageListener(this);
            
            dispose();
            MainMenuFrame mainMenu = new MainMenuFrame(client, currentUser);
            mainMenu.setVisible(true);
            
            // Yêu cầu cập nhật danh sách online (sau khi tạo MainMenuFrame)
            SwingUtilities.invokeLater(() -> {
                JSONObject request = new JSONObject();
                request.put("type", Protocol.GET_ALL_USERS);
                client.sendMessage(request.toString());
            });
        }
    }
    
    @Override
    public void onMessageReceived(String message) {
        // Chỉ xử lý message nếu đây là instance hiện tại
        if (currentInstance != this) {
            System.out.println("⚠️ Ignoring message for old instance: " + System.identityHashCode(this));
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                JSONObject response = new JSONObject(message);
                String type = response.getString("type");
                
                System.out.println("🎮 GameplayFrame received: " + type);
                
                switch (type) {
                    case Protocol.OPPONENT_SCORE:
                        handleOpponentScore(response);
                        break;
                    case Protocol.OPPONENT_FINISHED:
                        handleOpponentFinished(response);
                        break;
                    case Protocol.OPPONENT_LEFT:
                        handleOpponentLeft(response);
                        break;
                    case Protocol.GAME_END:
                        handleGameEnd(response);
                        break;
                    case Protocol.ERROR:
                        handleError(response);
                        break;
                }
            } catch (Exception e) {
                System.out.println("❌ GameplayFrame parse error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    private void handleOpponentScore(JSONObject response) {
        opponentScore = response.getInt("opponent_score");
        opponentScoreLabel.setText("👤 Đối thủ: " + opponentScore);
    }
    
    private void handleOpponentFinished(JSONObject response) {
        System.out.println("🎮 Received OPPONENT_FINISHED: " + response.toString());
        
        // Không làm gì cả, chỉ log để debug
        // Server sẽ gửi GAME_END ngay lập tức
    }
    
    private void handleOpponentLeft(JSONObject response) {
        if (gameEnded) {
            System.out.println("⚠️ Game already ended, ignoring OPPONENT_LEFT");
            return;
        }
        
        gameEnded = true;
        gameTimer.stop();
        
        // Disable game panel
        gamePanel.setEnabled(false);
        
        // Remove listener TRƯỚC KHI hiển thị dialog để tránh nhận duplicate packets
        client.removeMessageListener(this);
        
        // Hiển thị thông báo chi tiết như handleGameEnd
        String title = "🏆 CHIẾN THẮNG!";
        String message = String.format(
            "Chúc mừng bạn đã thắng!\n\n" +
            "Điểm của bạn: %d\n" +
            "Đối thủ đã thoát khỏi trận đấu.\n\n" +
            "Bạn đã được tích lũy điểm từ trận đấu này!",
            myScore
        );
        
        JOptionPane.showMessageDialog(this, message, title, 
            JOptionPane.INFORMATION_MESSAGE);
        
        dispose();
        MainMenuFrame mainMenu = new MainMenuFrame(client, currentUser);
        mainMenu.setVisible(true);
        
        // Yêu cầu cập nhật danh sách online (sau khi tạo MainMenuFrame)
        SwingUtilities.invokeLater(() -> {
            JSONObject request = new JSONObject();
            request.put("type", Protocol.GET_ALL_USERS);
            client.sendMessage(request.toString());
        });
    }
    
    private void handleGameEnd(JSONObject response) {
        System.out.println("🎮 Received GAME_END: " + response.toString());
        
        // Không check gameEnded ở đây vì có thể đã được set khi hết thời gian
        if (!gameEnded) {
            gameEnded = true;
            gameTimer.stop();
        }
        
        // Disable game panel
        gamePanel.setEnabled(false);
        
        String result = response.getString("result");
        int myFinalScore = response.getInt("my_score");
        int oppFinalScore = response.getInt("opponent_score");
        int newTotalScore = response.getInt("new_total_score");
        
        String title, message;
        int messageType;
        
        if ("win".equals(result)) {
            title = "🏆 CHIẾN THẮNG!";
            message = String.format(
                "Chúc mừng bạn đã thắng!\n\n" +
                "Điểm của bạn: %d\n" +
                "Điểm đối thủ: %d\n\n" +
                "Tổng điểm tích lũy: %d (+%d)",
                myFinalScore, oppFinalScore, newTotalScore, myFinalScore
            );
            messageType = JOptionPane.INFORMATION_MESSAGE;
        } else if ("lose".equals(result)) {
            title = "❌ THUA";
            message = String.format(
                "Rất tiếc! Bạn đã thua.\n\n" +
                "Điểm của bạn: %d\n" +
                "Điểm đối thủ: %d\n\n" +
                "Tổng điểm tích lũy: %d (+%d)",
                myFinalScore, oppFinalScore, newTotalScore, myFinalScore
            );
            messageType = JOptionPane.WARNING_MESSAGE;
        } else {
            title = "🤝 HÒA";
            message = String.format(
                "Trận đấu hòa!\n\n" +
                "Điểm của bạn: %d\n" +
                "Điểm đối thủ: %d\n\n" +
                "Tổng điểm tích lũy: %d (+%d)",
                myFinalScore, oppFinalScore, newTotalScore, myFinalScore
            );
            messageType = JOptionPane.INFORMATION_MESSAGE;
        }
        
        JOptionPane.showMessageDialog(this, message, title, messageType);
        
        // Cập nhật thông tin user với điểm mới
        currentUser.put("total_score", newTotalScore);
        
        // Remove listener để tránh leak
        client.removeMessageListener(this);
        
        dispose();
        MainMenuFrame mainMenu = new MainMenuFrame(client, currentUser);
        mainMenu.setVisible(true);
        
        // Yêu cầu cập nhật danh sách online (sau khi tạo MainMenuFrame)
        SwingUtilities.invokeLater(() -> {
            JSONObject request = new JSONObject();
            request.put("type", Protocol.GET_ALL_USERS);
            client.sendMessage(request.toString());
        });
    }
    
    private void handleError(JSONObject response) {
        if (gameEnded) {
            System.out.println("⚠️ Game already ended, ignoring error: " + response.optString("message", "Unknown error"));
            return;
        }
        
        String message = response.getString("message");
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
    
    // ==================== GAME PANEL ====================
    
    class GamePanel extends JPanel {
        private List<PlusOneEffect> plusOneEffects;
        
        public GamePanel() {
            plusOneEffects = new ArrayList<>();
            setBackground(new Color(255, 248, 220));
            
            // Định nghĩa vị trí 2 giỏ
            riceBasket = new Rectangle(200, 450, 120, 100);  // Nâng cao lên 70px
            paddyBasket = new Rectangle(680, 450, 120, 100); // Nâng cao lên 70px
            
            // Mouse listeners cho drag & drop
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    
                    // Tìm hạt tại vị trí click
                    for (int i = grains.size() - 1; i >= 0; i--) {
                        Grain grain = grains.get(i);
                        Rectangle grainBounds = new Rectangle(grain.getX() - 15, grain.getY() - 15, 30, 30);
                        
                        if (grainBounds.contains(e.getPoint())) {
                            draggingGrain = grain;
                            offsetX = e.getX() - grain.getX();
                            offsetY = e.getY() - grain.getY();
                            break;
                        }
                    }
                }
                
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!isEnabled() || draggingGrain == null) return;
                    
                    // Kiểm tra thả vào giỏ nào
                    Point dropPoint = e.getPoint();
                    
                    if (riceBasket.contains(dropPoint)) {
                        checkDrop(draggingGrain, "rice");
                    } else if (paddyBasket.contains(dropPoint)) {
                        checkDrop(draggingGrain, "paddy");
                    } else {
                        // Thả ngoài giỏ - hạt quay về vị trí cũ
                        repaint();
                    }
                    
                    draggingGrain = null;
                }
            });
            
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!isEnabled() || draggingGrain == null) return;
                    
                    draggingGrain.setX(e.getX() - offsetX);
                    draggingGrain.setY(e.getY() - offsetY);
                    repaint();
                }
            });
            
            // Timer cho hiệu ứng +1
            Timer effectTimer = new Timer(50, e -> {
                plusOneEffects.removeIf(effect -> {
                    effect.step++;
                    return effect.step >= 20;
                });
                repaint();
            });
            effectTimer.start();
        }
        
        private synchronized void checkDrop(Grain grain, String basketType) {
            if (grain.getType().equals(basketType)) {
                // ĐÚNG!
                myScore++;
                System.out.println("🎯 Score increased to: " + myScore + ", instance: " + System.identityHashCode(this));
                myScoreLabel.setText("📊 Bạn: " + myScore);
                
                // Hiệu ứng +1
                Rectangle basket = basketType.equals("rice") ? riceBasket : paddyBasket;
                plusOneEffects.add(new PlusOneEffect(basket.x + basket.width / 2, basket.y));
                
                // Xóa hạt
                grains.remove(grain);
                
                // Gửi điểm lên server
                JSONObject packet = new JSONObject();
                packet.put("type", Protocol.SCORE_UPDATE);
                packet.put("new_score", myScore);
                System.out.println("📤 Sending SCORE_UPDATE: " + myScore);
                client.sendMessage(packet.toString());
                
                // Kiểm tra nếu đạt điểm tối đa (tất cả hạt)
                if (myScore >= totalGrains) {
                    handleMaxScore();
                }
                
                repaint();
            } else {
                // SAI! Hạt không làm gì cả, vẫn ở vị trí hiện tại
                repaint();
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Vẽ 2 giỏ
            // Giỏ gạo
            g2d.setColor(new Color(245, 222, 179));
            g2d.fill(riceBasket);
            g2d.setColor(new Color(139, 69, 19));
            g2d.draw(riceBasket);
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            g2d.drawString("🧺 Giỏ Gạo", riceBasket.x + 20, riceBasket.y + riceBasket.height / 2);
            
            // Giỏ thóc
            g2d.setColor(new Color(222, 184, 135));
            g2d.fill(paddyBasket);
            g2d.setColor(new Color(139, 69, 19));
            g2d.draw(paddyBasket);
            g2d.drawString("🌾 Giỏ Thóc", paddyBasket.x + 15, paddyBasket.y + paddyBasket.height / 2);
            
            // Vẽ các hạt
            for (Grain grain : grains) {
                if (grain == draggingGrain) continue; // Vẽ hạt đang kéo sau cùng
                
                drawGrain(g2d, grain);
            }
            
            // Vẽ hạt đang kéo
            if (draggingGrain != null) {
                drawGrain(g2d, draggingGrain);
            }
            
            // Vẽ hiệu ứng +1
            for (PlusOneEffect effect : plusOneEffects) {
                float alpha = 1.0f - (effect.step / 20.0f);
                g2d.setColor(new Color(0, 255, 0, (int) (alpha * 255)));
                g2d.setFont(new Font("Arial", Font.BOLD, 24));
                g2d.drawString("+1", effect.x - 10, effect.y - effect.step * 3);
            }
        }
        
        private void drawGrain(Graphics2D g2d, Grain grain) {
            int x = grain.getX();
            int y = grain.getY();
            
            if ("rice".equals(grain.getType())) {
                // Vẽ hạt gạo (màu trắng, hình oval)
                g2d.setColor(Color.WHITE);
                g2d.fillOval(x - 15, y - 15, 30, 30);
                g2d.setColor(Color.GRAY);
                g2d.drawOval(x - 15, y - 15, 30, 30);
                
                // Ký tự
                g2d.setFont(new Font("Arial", Font.BOLD, 12));
                g2d.setColor(Color.BLACK);
                g2d.drawString("🍚", x - 8, y + 5);
            } else {
                // Vẽ hạt thóc (màu vàng nâu, hình oval dài hơn)
                g2d.setColor(new Color(218, 165, 32));
                g2d.fillOval(x - 12, y - 18, 24, 36);
                g2d.setColor(new Color(139, 90, 0));
                g2d.drawOval(x - 12, y - 18, 24, 36);
                
                // Ký tự
                g2d.setFont(new Font("Arial", Font.BOLD, 12));
                g2d.setColor(Color.BLACK);
                g2d.drawString("🌾", x - 8, y + 5);
            }
        }
    }
    
    static class PlusOneEffect {
        int x, y;
        int step;
        
        PlusOneEffect(int x, int y) {
            this.x = x;
            this.y = y;
            this.step = 0;
        }
    }
    
    @Override
    public void dispose() {
        System.out.println("🗑️ Disposing GameplayFrame instance: " + System.identityHashCode(this));
        
        // Remove listener trước khi dispose
        if (client != null) {
            client.removeMessageListener(this);
        }
        
        // Clear static reference khi dispose
        if (currentInstance == this) {
            currentInstance = null;
        }
        super.dispose();
    }
}

