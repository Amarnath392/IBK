package apidemo.stategies;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a trade order that can be either single or combo
 */
public class TradeOrder {
    
    public enum OrderStatus {
        READY, MONITORING, ALERTED, PLACED, ERROR, INACTIVE
    }
    
    public static class OrderLeg {
        public final String symbol;
        public final String expiry;
        public final String action;
        public final String optionType;
        public final String role;
        public final double strike;
        public final int quantity;
        public final String account;
        public final int excelRow;
        
        public OrderLeg(String symbol, String expiry, String action, String optionType, String role, 
                       double strike, int quantity, String account, int excelRow) {
            this.symbol = symbol;
            this.expiry = expiry;
            this.action = action;
            this.optionType = optionType;
            this.role = role;
            this.strike = strike;
            this.quantity = quantity;
            this.account = account;
            this.excelRow = excelRow;
        }
        
        @Override
        public String toString() {
            return String.format("%s %d %s %s %.2f (%s)", 
                action, quantity, symbol, expiry, strike, role);
        }
    }
    
    private final String tradeId;
    private final List<OrderLeg> legs;
    private final String account;
    private double targetPrice;
    private double alertThreshold;
    private boolean isActive;
    private OrderStatus status;
    private String monitoringId;
    private double currentPrice;
    private String errorMessage;
    
    public TradeOrder(String tradeId, String account) {
        this.tradeId = tradeId;
        this.account = account;
        this.legs = new ArrayList<>();
        this.isActive = true;
        this.status = OrderStatus.READY;
        this.currentPrice = 0.0;
    }
    
    public void addLeg(OrderLeg leg) {
        legs.add(leg);
        // If this leg has role "Main", use its target/alert values
        if ("MAIN".equalsIgnoreCase(leg.role)) {
            // Target and Alert will be set from the main leg in the importer
        }
    }
    
    public boolean isComboOrder() {
        return legs.size() > 1;
    }
    
    public OrderLeg getMainLeg() {
        return legs.stream()
                .filter(leg -> "MAIN".equalsIgnoreCase(leg.role))
                .findFirst()
                .orElse(legs.isEmpty() ? null : legs.get(0));
    }
    
    public String getDisplaySymbols() {
        if (legs.isEmpty()) return "";
        
        if (isComboOrder()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < legs.size(); i++) {
                if (i > 0) sb.append(" + ");
                sb.append(legs.get(i).symbol);
            }
            return sb.toString();
        } else {
            return legs.get(0).symbol;
        }
    }
    
    public String getDisplayAction() {
        if (legs.isEmpty()) return "";
        
        if (isComboOrder()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < legs.size(); i++) {
                if (i > 0) sb.append("/");
                sb.append(legs.get(i).action);
            }
            return sb.toString();
        } else {
            return legs.get(0).action;
        }
    }
    
    public int getTotalQuantity() {
        return legs.stream().mapToInt(leg -> leg.quantity).sum();
    }
    
    public String getDisplayExpiry() {
        if (legs.isEmpty()) return "";
        // Use the main leg's expiry or first leg's expiry
        OrderLeg mainLeg = getMainLeg();
        return mainLeg != null ? mainLeg.expiry : legs.get(0).expiry;
    }
    
    // Getters and setters
    public String getTradeId() { return tradeId; }
    public String getAccount() { return account; }
    public List<OrderLeg> getLegs() { return new ArrayList<>(legs); }
    public double getTargetPrice() { return targetPrice; }
    public double getAlertThreshold() { return alertThreshold; }
    public boolean isActive() { return isActive; }
    public OrderStatus getStatus() { return status; }
    public String getMonitoringId() { return monitoringId; }
    public double getCurrentPrice() { return currentPrice; }
    public String getErrorMessage() { return errorMessage; }
    
    public void setTargetPrice(double targetPrice) { this.targetPrice = targetPrice; }
    public void setAlertThreshold(double alertThreshold) { this.alertThreshold = alertThreshold; }
    public void setActive(boolean active) { this.isActive = active; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setMonitoringId(String monitoringId) { this.monitoringId = monitoringId; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    @Override
    public String toString() {
        return String.format("TradeID: %s, %s (%s) - Target: %.2f, Alert: %.2f, Status: %s", 
            tradeId, getDisplaySymbols(), getDisplayAction(), targetPrice, alertThreshold, status);
    }
}
