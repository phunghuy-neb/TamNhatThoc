package client.gui;

import client.GameClient;
import org.json.JSONArray;
import org.json.JSONObject;
import shared.Protocol;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog de moi nguoi choi vao phong
 */
public class InvitePlayerDialog extends JDialog implements GameClient.MessageListener {
    private final GameClient client;
    private final String currentUserId;
    
    private JTextField searchField;
    private JTable playerTable;
    private DefaultTableModel tableModel;
    private JButton inviteButton;
    private JButton refreshButton;
    private JButton cancelButton;
    
    private final List<JSONObject> allPlayers;
    private final List<JSONObject> filteredPlayers;
    // Snapshot mapping of rows currently displayed in table → players
    private final List<JSONObject> tableRows;
    
    public InvitePlayerDialog(JFrame parent, GameClient client, String currentUserId, String roomId) {
        super(parent, "Moi Nguoi Choi", true);
        this.client = client;
        this.currentUserId = currentUserId;
        this.allPlayers = new ArrayList<>();
        this.filteredPlayers = new ArrayList<>();
        this.tableRows = new ArrayList<>();
        
        setSize(500, 400);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        client.addMessageListener(this);
        initComponents();
        
        // Yeu cau danh sach tat ca nguoi choi
        requestAllUsers();
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Top panel - Search
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Tim kiem nguoi choi"));
        
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(300, 30));
        searchField.setFont(new Font("Arial", Font.PLAIN, 14));
        searchField.addActionListener(e -> filterPlayers());
        
        refreshButton = new JButton("Lam moi");
        refreshButton.setPreferredSize(new Dimension(100, 30));
        refreshButton.setBackground(new Color(33, 150, 243));
        refreshButton.setForeground(Color.BLACK);
        refreshButton.setFocusPainted(false);
        refreshButton.setFont(new Font("Arial", Font.BOLD, 12));
        refreshButton.addActionListener(e -> requestAllUsers());
        
        JPanel searchInputPanel = new JPanel(new BorderLayout(5, 0));
        searchInputPanel.add(new JLabel("Ten nguoi choi:"), BorderLayout.WEST);
        searchInputPanel.add(searchField, BorderLayout.CENTER);
        searchInputPanel.add(refreshButton, BorderLayout.EAST);
        
        searchPanel.add(searchInputPanel, BorderLayout.CENTER);
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        
        // Center panel - Player list
        JPanel listPanel = new JPanel(new BorderLayout(5, 5));
        listPanel.setBorder(BorderFactory.createTitledBorder("Danh sach nguoi choi online"));
        
        // Table model
        String[] columnNames = {"Ten nguoi choi", "Diem so", "Trang thai", "Hanh dong"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // Chi cot "Hanh dong" co the edit
            }
        };
        
        playerTable = new JTable(tableModel);
        playerTable.setRowHeight(35);
        playerTable.setFont(new Font("Arial", Font.PLAIN, 12));
        playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Custom renderer cho cot hanh dong
        playerTable.getColumnModel().getColumn(3).setCellRenderer(new ButtonRenderer());
        playerTable.getColumnModel().getColumn(3).setCellEditor(new ButtonEditor(new JCheckBox()));
        
        JScrollPane scrollPane = new JScrollPane(playerTable);
        scrollPane.setPreferredSize(new Dimension(450, 200));
        
        listPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(listPanel, BorderLayout.CENTER);
        
        // Bottom panel - Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        inviteButton = new JButton("Moi nguoi duoc chon");
        inviteButton.setPreferredSize(new Dimension(150, 35));
        inviteButton.setBackground(new Color(76, 175, 80));
        inviteButton.setForeground(Color.BLACK);
        inviteButton.setFocusPainted(false);
        inviteButton.setFont(new Font("Arial", Font.BOLD, 12));
        inviteButton.setEnabled(false);
        inviteButton.addActionListener(e -> inviteSelectedPlayer());
        
        cancelButton = new JButton("Dong");
        cancelButton.setPreferredSize(new Dimension(100, 35));
        cancelButton.setBackground(new Color(158, 158, 158));
        cancelButton.setForeground(Color.BLACK);
        cancelButton.setFocusPainted(false);
        cancelButton.setFont(new Font("Arial", Font.BOLD, 12));
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(inviteButton);
        buttonPanel.add(cancelButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Selection listener
        playerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                inviteButton.setEnabled(playerTable.getSelectedRow() != -1);
            }
        });
    }
    
    private void requestAllUsers() {
        // Gui yeu cau lay danh sach nguoi choi online
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.GET_ALL_USERS);
        client.sendMessage(packet.toString());
        
        searchField.setText("");
        tableModel.setRowCount(0);
        allPlayers.clear();
        filteredPlayers.clear();
    }
    
    private void filterPlayers() {
        String searchText = searchField.getText().toLowerCase().trim();
        filteredPlayers.clear();
        
        for (JSONObject player : allPlayers) {
            String username = player.getString("username").toLowerCase();
            if (searchText.isEmpty() || username.contains(searchText)) {
                filteredPlayers.add(player);
            }
        }
        
        updateTable();
    }
    
    private void updateTable() {
        tableModel.setRowCount(0);
        tableRows.clear();
        
        for (JSONObject player : filteredPlayers) {
            String username = player.getString("username");
            int totalScore = player.getInt("total_score");
            String status = player.getString("status");
            
            // Chỉ hiển thị những người có thể mời được
            boolean canInvite = false;
            String statusText = "";
            
            if ("online".equals(status)) {
                canInvite = true;
                statusText = "Sẵn sàng";
            } else if ("waiting".equals(status) && !player.has("room_info")) {
                // Đang tìm trận (không trong phòng)
                canInvite = true;
                statusText = "Đang tìm trận";
            } else if ("waiting".equals(status) && player.has("room_info")) {
                // Đang trong phòng khác
                canInvite = false;
                statusText = "Đang trong phòng";
            } else if ("playing".equals(status)) {
                // Đang chơi game
                canInvite = false;
                statusText = "Đang chơi";
            } else {
                // Offline
                canInvite = false;
                statusText = "Offline";
            }
            
            if (canInvite) {
                Object[] row = {username, totalScore, statusText, "Mời"};
                tableModel.addRow(row);
                // Keep row-to-player mapping in sync with the table snapshot
                tableRows.add(player);
            }
        }
    }
    
    private void inviteSelectedPlayer() {
        int selectedRow = playerTable.getSelectedRow();
        if (selectedRow == -1) return;
        
        // Resolve player strictly from current table snapshot
        if (selectedRow >= tableRows.size()) return;
        JSONObject selectedPlayer = tableRows.get(selectedRow);
        String targetUsername = selectedPlayer.getString("username");
        
        // DEBUG: In ra để kiểm tra
        System.out.println("DEBUG: Selected row: " + selectedRow);
        System.out.println("DEBUG: Target username from table: " + targetUsername);
        System.out.println("DEBUG: Current user ID: " + currentUserId);
        
        if (selectedPlayer == null) {
            JOptionPane.showMessageDialog(this,
                "Không tìm thấy người chơi!",
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int targetUserId = selectedPlayer.getInt("user_id");
        String foundUsername = selectedPlayer.getString("username");
        
        System.out.println("DEBUG: Final target - user_id: " + targetUserId + ", username: " + foundUsername);
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Ban co chac muon moi " + foundUsername + " vao phong?",
            "Xac nhan moi",
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            // Gui loi moi
            JSONObject packet = new JSONObject();
            packet.put("type", Protocol.INVITE);
            packet.put("to_user_id", targetUserId);
            client.sendMessage(packet.toString());
            
            JOptionPane.showMessageDialog(this,
                "Da gui loi moi den " + foundUsername + "!",
                "Thanh cong",
                JOptionPane.INFORMATION_MESSAGE);
            
            dispose();
        }
    }
    
    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                JSONObject response = new JSONObject(message);
                String type = response.getString("type");
                
                if (Protocol.ONLINE_USERS_UPDATE.equals(type)) {
                    handleOnlineUsersUpdate(response);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void handleOnlineUsersUpdate(JSONObject response) {
        allPlayers.clear();
        JSONArray usersArray = response.getJSONArray("users");
        
        for (int i = 0; i < usersArray.length(); i++) {
            JSONObject player = usersArray.getJSONObject(i);
            String playerUserId = String.valueOf(player.getInt("user_id"));
            
            // KHÔNG thêm bản thân vào allPlayers
            if (!playerUserId.equals(currentUserId)) {
                allPlayers.add(player);
            }
        }
        
        filterPlayers();
    }
    
    @Override
    public void dispose() {
        client.removeMessageListener(this);
        super.dispose();
    }
    
    // Custom button renderer and editor
    class ButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setText((value == null) ? "" : value.toString());
            return this;
        }
    }
    
    class ButtonEditor extends DefaultCellEditor {
        protected JButton button;
        private String label;
        private boolean isPushed;
        private int row;
        
        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> fireEditingStopped());
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            label = (value == null) ? "" : value.toString();
            button.setText(label);
            isPushed = true;
            this.row = row;
            return button;
        }
        
        @Override
        public Object getCellEditorValue() {
            if (isPushed) {
                // Mời người chơi tại hàng hiện tại dựa trên snapshot tableRows
                if (row < tableRows.size()) {
                    JSONObject player = tableRows.get(row);
                    String targetUserId = String.valueOf(player.getInt("user_id"));
                    String targetUsername = player.getString("username");
                    
                    int confirm = JOptionPane.showConfirmDialog(InvitePlayerDialog.this,
                        "Ban co chac muon moi " + targetUsername + " vao phong?",
                        "Xac nhan moi",
                        JOptionPane.YES_NO_OPTION);
                    
                    if (confirm == JOptionPane.YES_OPTION) {
                        JSONObject packet = new JSONObject();
                        packet.put("type", Protocol.INVITE);
                        packet.put("to_user_id", Integer.parseInt(targetUserId));
                        client.sendMessage(packet.toString());
                        
                        JOptionPane.showMessageDialog(InvitePlayerDialog.this,
                            "Da gui loi moi den " + targetUsername + "!",
                            "Thanh cong",
                            JOptionPane.INFORMATION_MESSAGE);
                        
                        dispose();
                    }
                }
            }
            isPushed = false;
            return label;
        }
        
        @Override
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }
        
        @Override
        protected void fireEditingStopped() {
            super.fireEditingStopped();
        }
    }
}
