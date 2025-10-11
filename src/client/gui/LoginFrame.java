package client.gui;

import client.GameClient;
import org.json.JSONObject;
import shared.Protocol;

import javax.swing.*;
import java.awt.*;
import java.security.MessageDigest;

/**
 * Màn hình đăng nhập/đăng ký
 */
public class LoginFrame extends JFrame implements GameClient.MessageListener {
    private GameClient client;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    
    public LoginFrame() {
        setTitle("Tấm Nhặt Thóc - Đăng Nhập");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        
        initComponents();
        
        // Kết nối server
        client = new GameClient();
        client.addMessageListener(this);
        
        if (!client.connect()) {
            JOptionPane.showMessageDialog(this, 
                "Không thể kết nối đến server!\nVui lòng kiểm tra server đã chạy chưa.",
                "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        
        // Title
        JLabel titleLabel = new JLabel("TẤM NHẶT THÓC", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(76, 175, 80));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // Username
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        formPanel.add(userLabel, gbc);
        
        gbc.gridx = 1;
        usernameField = new JTextField(20);
        usernameField.setMinimumSize(new Dimension(250, 35));
        usernameField.setPreferredSize(new Dimension(250, 35));
        usernameField.setMaximumSize(new Dimension(250, 35));
        usernameField.setFont(new Font("Arial", Font.PLAIN, 14));
        formPanel.add(usernameField, gbc);
        
        // Password
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        formPanel.add(passLabel, gbc);
        
        gbc.gridx = 1;
        passwordField = new JPasswordField(20);
        passwordField.setMinimumSize(new Dimension(250, 35));
        passwordField.setPreferredSize(new Dimension(250, 35));
        passwordField.setMaximumSize(new Dimension(250, 35));
        passwordField.setFont(new Font("Arial", Font.PLAIN, 14));
        formPanel.add(passwordField, gbc);
        
        mainPanel.add(formPanel, BorderLayout.CENTER);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        loginButton = new JButton("Đăng Nhập");
        loginButton.setPreferredSize(new Dimension(120, 35));
        loginButton.setBackground(new Color(33, 150, 243));
        loginButton.setForeground(Color.BLACK);
        loginButton.setFocusPainted(false);
        loginButton.setFont(new Font("Arial", Font.BOLD, 14));
        loginButton.addActionListener(e -> handleLogin());
        
        registerButton = new JButton("Đăng Ký");
        registerButton.setPreferredSize(new Dimension(120, 35));
        registerButton.setBackground(new Color(76, 175, 80));
        registerButton.setForeground(Color.BLACK);
        registerButton.setFocusPainted(false);
        registerButton.setFont(new Font("Arial", Font.BOLD, 14));
        registerButton.addActionListener(e -> handleRegister());
        
        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Enter key
        passwordField.addActionListener(e -> handleLogin());
    }
    
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Vui lòng nhập đầy đủ thông tin!", 
                "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Hash password
        String hashedPassword = hashPassword(password);
        
        // BUG FIX #19: Check null - nếu hash fail thì không gửi
        if (hashedPassword == null) {
            JOptionPane.showMessageDialog(this, 
                "Lỗi hệ thống: Không thể mã hóa mật khẩu!\nVui lòng thử lại sau.", 
                "Lỗi nghiêm trọng", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.LOGIN);
        packet.put("username", username);
        packet.put("password", hashedPassword);
        
        client.sendMessage(packet.toString());
        
        loginButton.setEnabled(false);
        registerButton.setEnabled(false);
    }
    
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Vui lòng nhập đầy đủ thông tin!", 
                "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (username.length() < 3) {
            JOptionPane.showMessageDialog(this, 
                "Username phải có ít nhất 3 ký tự!", 
                "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (password.length() < 6) {
            JOptionPane.showMessageDialog(this, 
                "Password phải có ít nhất 6 ký tự!", 
                "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String hashedPassword = hashPassword(password);
        
        // BUG FIX #19: Check null - nếu hash fail thì không gửi
        if (hashedPassword == null) {
            JOptionPane.showMessageDialog(this, 
                "Lỗi hệ thống: Không thể mã hóa mật khẩu!\nVui lòng thử lại sau.", 
                "Lỗi nghiêm trọng", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.REGISTER);
        packet.put("username", username);
        packet.put("password", hashedPassword);
        packet.put("email", "");
        
        client.sendMessage(packet.toString());
        
        loginButton.setEnabled(false);
        registerButton.setEnabled(false);
    }
    
    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                JSONObject response = new JSONObject(message);
                String type = response.getString("type");
                
                if (Protocol.LOGIN_RESPONSE.equals(type)) {
                    handleLoginResponse(response);
                } else if (Protocol.REGISTER_RESPONSE.equals(type)) {
                    handleRegisterResponse(response);
                } else if (Protocol.ERROR.equals(type)) {
                    handleError(response);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void handleLoginResponse(JSONObject response) {
        String status = response.getString("status");
        
        if ("success".equals(status)) {
            JSONObject user = response.getJSONObject("user");
            
            // Chuyển sang màn hình chính
            dispose();
            MainMenuFrame mainMenu = new MainMenuFrame(client, user);
            mainMenu.setVisible(true);
        } else {
            String errorMsg = response.getString("message");
            JOptionPane.showMessageDialog(this, errorMsg, "Đăng nhập thất bại", 
                JOptionPane.ERROR_MESSAGE);
            
            loginButton.setEnabled(true);
            registerButton.setEnabled(true);
        }
    }
    
    private void handleRegisterResponse(JSONObject response) {
        String status = response.getString("status");
        String msg = response.getString("message");
        
        if ("success".equals(status)) {
            JOptionPane.showMessageDialog(this, msg, "Thành công", 
                JOptionPane.INFORMATION_MESSAGE);
            passwordField.setText("");
        } else {
            JOptionPane.showMessageDialog(this, msg, "Đăng ký thất bại", 
                JOptionPane.ERROR_MESSAGE);
        }
        
        loginButton.setEnabled(true);
        registerButton.setEnabled(true);
    }
    
    private void handleError(JSONObject response) {
        String message = response.getString("message");
        JOptionPane.showMessageDialog(this, message, "Lỗi", 
            JOptionPane.ERROR_MESSAGE);
    }
    
    /**
     * Hash password using SHA-256
     * BUG FIX #19: Return null on error thay vì plain password
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            System.err.println("❌ CRITICAL: Cannot hash password! SHA-256 not available!");
            e.printStackTrace();
            // BUG FIX #19: KHÔNG return plain password! Return null để caller handle
            return null;
        }
    }
    
    @Override
    public void dispose() {
        // BUG FIX #27: Remove listener để tránh memory leak
        client.removeMessageListener(this);
        super.dispose();
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            LoginFrame frame = new LoginFrame();
            frame.setVisible(true);
        });
    }
}

