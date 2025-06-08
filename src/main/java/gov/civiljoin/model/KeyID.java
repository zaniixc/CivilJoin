package gov.civiljoin.model;

/**
 * KeyID model representing the 'key_ids' table in the database
 */
public class KeyID {
    private int id;
    private String keyValue;  // The 16-digit key
    private boolean isUsed;
    private Integer generatedBy;  // Foreign key to user who generated it (admin)

    // Constructors
    public KeyID() {
    }

    public KeyID(String keyValue, Integer generatedBy) {
        this.keyValue = keyValue;
        this.isUsed = false;
        this.generatedBy = generatedBy;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getKeyValue() {
        return keyValue;
    }

    public void setKeyValue(String keyValue) {
        this.keyValue = keyValue;
    }

    public boolean isUsed() {
        return isUsed;
    }

    public void setUsed(boolean used) {
        isUsed = used;
    }

    public Integer getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(Integer generatedBy) {
        this.generatedBy = generatedBy;
    }

    @Override
    public String toString() {
        return "KeyID{" +
                "id=" + id +
                ", keyValue='" + keyValue + '\'' +
                ", isUsed=" + isUsed +
                ", generatedBy=" + generatedBy +
                '}';
    }
} 