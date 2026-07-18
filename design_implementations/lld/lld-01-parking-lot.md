# LLD 01 — Parking Lot System

> **Design a parking lot that supports multiple levels, multiple vehicle types, entry/exit gates, and dynamic pricing.**

---

## Clarify Requirements First

**Functional:**
- Multiple levels, each with multiple slots
- Vehicle types: Motorcycle, Car, Truck (different slot sizes)
- Issue ticket on entry, calculate fee on exit
- Find nearest available slot for a vehicle type
- Support multiple entry/exit gates

**Non-Functional:**
- Concurrent entries/exits (thread safety on slot assignment)
- Extensible pricing (hourly, flat, weekend rates)

---

## Identify Entities → Classes

```
ParkingLot       → singleton, owns levels
Level            → owns slots
ParkingSlot      → knows its type and occupancy
Vehicle          → base class: Motorcycle, Car, Truck
Ticket           → issued on entry, stores slot + time
Gate             → Entry gate issues tickets, Exit gate calculates fee
PricingStrategy  → interface for fee calculation
```

---

## Class Design

```java
// Vehicle hierarchy
public enum VehicleType { MOTORCYCLE, CAR, TRUCK }

public abstract class Vehicle {
    protected String licensePlate;
    protected VehicleType type;
    public Vehicle(String plate, VehicleType type) {
        this.licensePlate = plate;
        this.type = type;
    }
    public VehicleType getType() { return type; }
}

public class Car extends Vehicle {
    public Car(String plate) { super(plate, VehicleType.CAR); }
}

public class Motorcycle extends Vehicle {
    public Motorcycle(String plate) { super(plate, VehicleType.MOTORCYCLE); }
}

// Slot
public class ParkingSlot {
    private final int slotId;
    private final VehicleType type;
    private volatile boolean occupied = false;
    private Vehicle currentVehicle;

    public ParkingSlot(int slotId, VehicleType type) {
        this.slotId = slotId;
        this.type = type;
    }

    public synchronized boolean assign(Vehicle v) {
        if (occupied || v.getType() != this.type) return false;
        this.currentVehicle = v;
        this.occupied = true;
        return true;
    }

    public synchronized void release() {
        this.currentVehicle = null;
        this.occupied = false;
    }

    public boolean isAvailable() { return !occupied; }
    public VehicleType getType() { return type; }
    public int getSlotId() { return slotId; }
}

// Ticket
public class Ticket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSlot slot;
    private final int level;
    private final long entryTime;

    public Ticket(Vehicle v, ParkingSlot slot, int level) {
        this.ticketId = UUID.randomUUID().toString();
        this.vehicle = v;
        this.slot = slot;
        this.level = level;
        this.entryTime = System.currentTimeMillis();
    }

    public long getEntryTime() { return entryTime; }
    public ParkingSlot getSlot() { return slot; }
    public Vehicle getVehicle() { return vehicle; }
}

// Pricing Strategy (Strategy Pattern)
public interface PricingStrategy {
    double calculate(long entryTimeMs, long exitTimeMs, VehicleType type);
}

public class HourlyPricing implements PricingStrategy {
    private final Map<VehicleType, Double> rates = Map.of(
        VehicleType.MOTORCYCLE, 20.0,
        VehicleType.CAR,        40.0,
        VehicleType.TRUCK,      80.0
    );

    @Override
    public double calculate(long entryMs, long exitMs, VehicleType type) {
        long hours = (long) Math.ceil((exitMs - entryMs) / 3_600_000.0);
        return hours * rates.get(type);
    }
}

// Level
public class Level {
    private final int levelNumber;
    private final List<ParkingSlot> slots;

    public Level(int levelNumber, int motorcycleSlots, int carSlots, int truckSlots) {
        this.levelNumber = levelNumber;
        this.slots = new ArrayList<>();
        for (int i = 0; i < motorcycleSlots; i++) slots.add(new ParkingSlot(i, VehicleType.MOTORCYCLE));
        for (int i = 0; i < carSlots; i++)        slots.add(new ParkingSlot(i, VehicleType.CAR));
        for (int i = 0; i < truckSlots; i++)       slots.add(new ParkingSlot(i, VehicleType.TRUCK));
    }

    public Optional<ParkingSlot> findAvailable(VehicleType type) {
        return slots.stream()
            .filter(s -> s.getType() == type && s.isAvailable())
            .findFirst();
    }

    public int getLevelNumber() { return levelNumber; }
}

// ParkingLot (Singleton)
public class ParkingLot {
    private static ParkingLot instance;
    private final List<Level> levels;
    private final PricingStrategy pricing;

    private ParkingLot(List<Level> levels, PricingStrategy pricing) {
        this.levels = levels;
        this.pricing = pricing;
    }

    public static synchronized ParkingLot getInstance(List<Level> levels, PricingStrategy pricing) {
        if (instance == null) instance = new ParkingLot(levels, pricing);
        return instance;
    }

    // Entry gate
    public Ticket park(Vehicle vehicle) {
        for (Level level : levels) {
            Optional<ParkingSlot> slot = level.findAvailable(vehicle.getType());
            if (slot.isPresent() && slot.get().assign(vehicle)) {
                return new Ticket(vehicle, slot.get(), level.getLevelNumber());
            }
        }
        throw new RuntimeException("Parking lot full for vehicle type: " + vehicle.getType());
    }

    // Exit gate
    public double exit(Ticket ticket) {
        ticket.getSlot().release();
        return pricing.calculate(ticket.getEntryTime(), System.currentTimeMillis(),
                                 ticket.getVehicle().getType());
    }
}
```

---

## Usage

```java
PricingStrategy pricing = new HourlyPricing();
Level l1 = new Level(1, 10, 20, 5);
Level l2 = new Level(2, 10, 20, 5);

ParkingLot lot = ParkingLot.getInstance(List.of(l1, l2), pricing);

Vehicle car = new Car("TN-01-AB-1234");
Ticket t = lot.park(car);

// ... after some time ...
double fee = lot.exit(t);
System.out.println("Fee: ₹" + fee);
```

---

## Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Singleton** | ParkingLot | One lot instance globally |
| **Strategy** | PricingStrategy | Swap pricing without changing lot logic |
| **Template Method** | Vehicle base class | Common structure, subclass fills type |
| **Factory** (optional) | VehicleFactory | Create vehicle by type string |

---

## Follow-up Depth Points

**1. Concurrent entry race condition?**
> Two threads find the same slot available and both try to assign. Fixed with `synchronized` on `ParkingSlot.assign()` — only one succeeds, other retries.

**2. Nearest slot vs first available?**
> Add a slot number within level. Sort by slot number. Or use a PriorityQueue<ParkingSlot> ordered by proximity to entry gate.

**3. Reservation / pre-booking?**
> Add `RESERVED` state to slot. Slot holds a `reservationExpiry` timestamp. Background thread releases expired reservations.

**4. Multiple entry gates concurrency?**
> Multiple threads calling `park()` simultaneously. `findAvailable()` + `assign()` must be atomic per slot — `synchronized` on assign handles this without locking the whole lot.

**5. Analytics — how many cars parked per day?**
> Store tickets in DB. Query by `entryTime`. Or push to Kafka on entry/exit for real-time dashboards.

---

## One-Line Interview Answer

> *"Parking Lot is a classic OOP problem — ParkingLot owns Levels, Levels own Slots typed by vehicle size, Vehicle is a hierarchy, Ticket captures entry context. PricingStrategy is a Strategy pattern — swap hourly/flat/weekend without touching lot logic. Key engineering concern is concurrent slot assignment — synchronized on assign() at the slot level avoids locking the whole lot."*
