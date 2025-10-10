package shared;

import java.io.Serializable;

public class Grain implements Serializable {
    private int id;
    private String type; // "rice" hoặc "paddy"
    private int x;
    private int y;
    
    public Grain() {}
    
    public Grain(int id, String type, int x, int y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
    }
    
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
}

