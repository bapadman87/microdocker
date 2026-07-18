# LLD 05 — Food Delivery App (Swiggy / Zomato)

> **Design a food delivery platform covering restaurant/menu management, cart, order lifecycle, delivery assignment, and payment.**

---

## Clarify Requirements

**Functional:**
- Browse restaurants and menus
- Add items to cart, place order
- Order lifecycle: PLACED → ACCEPTED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
- Assign delivery partner to order
- Support multiple payment methods
- Cancel order (only before PREPARING)

**Non-Functional:**
- Extensible payment methods
- Real-time order status updates (observer)
- Concurrent orders

---

## Identify Entities → Classes

```
Restaurant      → name, menu, location
MenuItem        → name, price, category, availability
Cart            → user's current items before order
Order           → placed order, lifecycle state machine
OrderItem       → menu item + quantity
DeliveryPartner → assigned to an order
PaymentService  → interface: CashPayment, CardPayment, UPIPayment
OrderObserver   → notified on status change
```

---

## Class Design

```java
// MenuItem
public class MenuItem {
    private final String id;
    private final String name;
    private final double price;
    private boolean available;

    public MenuItem(String id, String name, double price) {
        this.id = id; this.name = name; this.price = price; this.available = true;
    }
    public boolean isAvailable() { return available; }
    public double getPrice() { return price; }
    public String getId() { return id; }
    public String getName() { return name; }
}

// Restaurant
public class Restaurant {
    private final String id;
    private final String name;
    private final Map<String, MenuItem> menu = new LinkedHashMap<>();

    public Restaurant(String id, String name) { this.id = id; this.name = name; }
    public void addItem(MenuItem item) { menu.put(item.getId(), item); }
    public MenuItem getItem(String itemId) { return menu.get(itemId); }
    public String getId() { return id; }
}

// Cart
public class Cart {
    private final String userId;
    private final Map<String, Integer> items = new LinkedHashMap<>(); // itemId → qty

    public Cart(String userId) { this.userId = userId; }

    public void addItem(String itemId, int qty) {
        items.merge(itemId, qty, Integer::sum);
    }

    public void removeItem(String itemId) { items.remove(itemId); }
    public Map<String, Integer> getItems() { return items; }
    public void clear() { items.clear(); }
}

// Order status (State Machine)
public enum OrderStatus {
    PLACED, ACCEPTED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED
}

// Order
public class Order {
    private final String id;
    private final String userId;
    private final Restaurant restaurant;
    private final List<OrderItem> items;
    private OrderStatus status;
    private DeliveryPartner deliveryPartner;
    private final List<OrderObserver> observers = new ArrayList<>();
    private final double totalAmount;

    public Order(String userId, Restaurant restaurant, List<OrderItem> items) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.restaurant = restaurant;
        this.items = items;
        this.status = OrderStatus.PLACED;
        this.totalAmount = items.stream()
            .mapToDouble(i -> i.getMenuItem().getPrice() * i.getQuantity())
            .sum();
    }

    public void addObserver(OrderObserver observer) { observers.add(observer); }

    public void updateStatus(OrderStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new IllegalStateException("Invalid transition: " + status + " → " + newStatus);
        }
        this.status = newStatus;
        notifyObservers();
    }

    private boolean isValidTransition(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PLACED    -> to == OrderStatus.ACCEPTED || to == OrderStatus.CANCELLED;
            case ACCEPTED  -> to == OrderStatus.PREPARING || to == OrderStatus.CANCELLED;
            case PREPARING -> to == OrderStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> to == OrderStatus.DELIVERED;
            default -> false;
        };
    }

    private void notifyObservers() {
        observers.forEach(o -> o.onStatusChange(this));
    }

    public void assignDeliveryPartner(DeliveryPartner dp) { this.deliveryPartner = dp; }
    public OrderStatus getStatus() { return status; }
    public String getId() { return id; }
    public double getTotalAmount() { return totalAmount; }
}

// OrderItem
public record OrderItem(MenuItem menuItem, int quantity) {
    public MenuItem getMenuItem() { return menuItem; }
    public int getQuantity() { return quantity; }
}

// Observer Pattern — notify user and restaurant on status change
public interface OrderObserver {
    void onStatusChange(Order order);
}

public class UserNotificationObserver implements OrderObserver {
    @Override
    public void onStatusChange(Order order) {
        System.out.printf("📱 User notified: Order %s is now %s%n",
            order.getId(), order.getStatus());
    }
}

public class RestaurantObserver implements OrderObserver {
    @Override
    public void onStatusChange(Order order) {
        System.out.printf("🍴 Restaurant notified: Order %s → %s%n",
            order.getId(), order.getStatus());
    }
}

// DeliveryPartner
public class DeliveryPartner {
    private final String id;
    private final String name;
    private boolean available = true;

    public DeliveryPartner(String id, String name) { this.id = id; this.name = name; }
    public boolean isAvailable() { return available; }
    public void assign() { this.available = false; }
    public void release() { this.available = true; }
    public String getName() { return name; }
}

// Payment Strategy
public interface PaymentStrategy {
    boolean pay(double amount, String orderId);
}

public class UPIPayment implements PaymentStrategy {
    private final String upiId;
    public UPIPayment(String upiId) { this.upiId = upiId; }

    @Override
    public boolean pay(double amount, String orderId) {
        System.out.printf("UPI payment of ₹%.2f from %s for order %s%n", amount, upiId, orderId);
        return true;  // simulate success
    }
}

// OrderService — main orchestrator
public class OrderService {
    private final Map<String, Restaurant> restaurants = new HashMap<>();
    private final List<DeliveryPartner> deliveryPartners = new ArrayList<>();

    public void registerRestaurant(Restaurant r) { restaurants.put(r.getId(), r); }
    public void addDeliveryPartner(DeliveryPartner dp) { deliveryPartners.add(dp); }

    public Order placeOrder(String userId, Cart cart, String restaurantId,
                            PaymentStrategy payment) {
        Restaurant restaurant = restaurants.get(restaurantId);

        List<OrderItem> orderItems = cart.getItems().entrySet().stream()
            .map(e -> new OrderItem(restaurant.getItem(e.getKey()), e.getValue()))
            .collect(Collectors.toList());

        Order order = new Order(userId, restaurant, orderItems);
        order.addObserver(new UserNotificationObserver());
        order.addObserver(new RestaurantObserver());

        boolean paid = payment.pay(order.getTotalAmount(), order.getId());
        if (!paid) throw new RuntimeException("Payment failed");

        cart.clear();

        // Assign delivery partner
        deliveryPartners.stream()
            .filter(DeliveryPartner::isAvailable)
            .findFirst()
            .ifPresent(dp -> {
                dp.assign();
                order.assignDeliveryPartner(dp);
            });

        return order;
    }
}
```

---

## Usage

```java
Restaurant restaurant = new Restaurant("r1", "Murugan Idli Shop");
restaurant.addItem(new MenuItem("m1", "Masala Dosa", 80));
restaurant.addItem(new MenuItem("m2", "Filter Coffee", 30));

OrderService service = new OrderService();
service.registerRestaurant(restaurant);
service.addDeliveryPartner(new DeliveryPartner("dp1", "Ravi"));

Cart cart = new Cart("user1");
cart.addItem("m1", 2);
cart.addItem("m2", 1);

Order order = service.placeOrder("user1", cart, "r1", new UPIPayment("bala@upi"));

order.updateStatus(OrderStatus.ACCEPTED);
order.updateStatus(OrderStatus.PREPARING);
order.updateStatus(OrderStatus.OUT_FOR_DELIVERY);
order.updateStatus(OrderStatus.DELIVERED);
```

---

## Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **State Machine** | Order status transitions | Enforce valid lifecycle, prevent invalid jumps |
| **Observer** | OrderObserver | Notify user/restaurant without coupling Order to notification logic |
| **Strategy** | PaymentStrategy | Plug in UPI, card, cash without changing order logic |
| **Decorator** (optional) | MenuItem with add-ons | Wrap item with extra cheese, sauce etc. |

---

## Follow-up Depth Points

**1. Delivery partner assignment strategy?**
> Simple: first available partner. Real: find nearest partner using Geohashing (same as Uber). Factor in: current location, rating, delivery load.

**2. Order cancellation after PREPARING?**
> Block it — `isValidTransition` returns false. Or allow with penalty (same idea as hotel cancellation policy — Strategy pattern).

**3. Real-time tracking?**
> DeliveryPartner app sends location updates every 5 seconds via WebSocket. Server stores current location in Redis. Customer app polls or subscribes to location stream.

**4. Restaurant surge?**
> Rate limit incoming orders per restaurant. Restaurant sets `maxConcurrentOrders`. OrderService checks before accepting. Excess orders go to a wait queue.

**5. Scaling the system?**
> Each restaurant becomes its own partition key in Kafka. Orders for restaurant R1 always go to the same partition → ordered processing, no contention across restaurants.

---

## One-Line Interview Answer

> *"Food delivery LLD centers on the Order state machine — PLACED → ACCEPTED → PREPARING → OUT_FOR_DELIVERY → DELIVERED — enforcing valid transitions prevents illegal state jumps. Payment is a Strategy pattern, Observer decouples status change from notification logic. The interesting engineering problems are delivery partner assignment (geohash for proximity), real-time tracking (WebSocket + Redis), and concurrent order management per restaurant (rate limiting + queue)."*
