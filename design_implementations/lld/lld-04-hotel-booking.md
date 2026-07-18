# LLD 04 — Hotel Booking System

> **Design a hotel booking system that supports room search by availability, reservations, cancellations, and concurrent booking conflict prevention.**

---

## Clarify Requirements

**Functional:**
- Multiple hotels, each with multiple room types (Standard, Deluxe, Suite)
- Search available rooms by hotel, date range, room type
- Book a room — reserve for check-in to check-out dates
- Cancel a reservation
- Cancellation policy — free before N days, penalty after

**Non-Functional:**
- Prevent double booking of same room (concurrent requests)
- Extensible pricing (seasonal rates, discounts)

---

## Identify Entities → Classes

```
Hotel           → collection of rooms
Room            → type, price, list of reservations
RoomType        → STANDARD, DELUXE, SUITE
Reservation     → room, guest, check-in, check-out, status
Guest           → id, name, contact
BookingService  → search + book + cancel orchestration
CancellationPolicy → interface for fee calculation
```

---

## Class Design

```java
public enum RoomType { STANDARD, DELUXE, SUITE }
public enum ReservationStatus { CONFIRMED, CANCELLED }

// Guest
public class Guest {
    private final String id;
    private final String name;
    public Guest(String id, String name) { this.id = id; this.name = name; }
    public String getId() { return id; }
}

// Reservation
public class Reservation {
    private final String id;
    private final Room room;
    private final Guest guest;
    private final LocalDate checkIn;
    private final LocalDate checkOut;
    private ReservationStatus status;

    public Reservation(Room room, Guest guest, LocalDate checkIn, LocalDate checkOut) {
        this.id = UUID.randomUUID().toString();
        this.room = room;
        this.guest = guest;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = ReservationStatus.CONFIRMED;
    }

    public boolean overlaps(LocalDate in, LocalDate out) {
        return checkIn.isBefore(out) && checkOut.isAfter(in);
    }

    public void cancel() { this.status = ReservationStatus.CANCELLED; }
    public ReservationStatus getStatus() { return status; }
    public String getId() { return id; }
    public LocalDate getCheckIn() { return checkIn; }
    public Room getRoom() { return room; }
}

// Room — thread safety is the critical part
public class Room {
    private final String roomNumber;
    private final RoomType type;
    private final double pricePerNight;
    private final List<Reservation> reservations = new ArrayList<>();

    public Room(String roomNumber, RoomType type, double pricePerNight) {
        this.roomNumber = roomNumber;
        this.type = type;
        this.pricePerNight = pricePerNight;
    }

    // Synchronized — prevent concurrent double booking
    public synchronized boolean isAvailable(LocalDate checkIn, LocalDate checkOut) {
        return reservations.stream()
            .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
            .noneMatch(r -> r.overlaps(checkIn, checkOut));
    }

    public synchronized Reservation book(Guest guest, LocalDate checkIn, LocalDate checkOut) {
        if (!isAvailable(checkIn, checkOut)) {
            throw new IllegalStateException("Room " + roomNumber + " not available");
        }
        Reservation r = new Reservation(this, guest, checkIn, checkOut);
        reservations.add(r);
        return r;
    }

    public RoomType getType() { return type; }
    public double getPricePerNight() { return pricePerNight; }
    public String getRoomNumber() { return roomNumber; }
}

// Cancellation Policy (Strategy Pattern)
public interface CancellationPolicy {
    double calculatePenalty(Reservation reservation, LocalDate cancelDate);
}

public class FreeCancellation implements CancellationPolicy {
    private final int freeDaysBefore;
    private final double penaltyPercentage;

    public FreeCancellation(int freeDaysBefore, double penaltyPercentage) {
        this.freeDaysBefore = freeDaysBefore;
        this.penaltyPercentage = penaltyPercentage;
    }

    @Override
    public double calculatePenalty(Reservation r, LocalDate cancelDate) {
        long daysUntilCheckIn = ChronoUnit.DAYS.between(cancelDate, r.getCheckIn());
        if (daysUntilCheckIn >= freeDaysBefore) return 0.0;
        long nights = ChronoUnit.DAYS.between(r.getCheckIn(), r.getRoom().getPricePerNight() > 0
            ? r.getCheckIn().plusDays(1) : r.getCheckIn());
        return r.getRoom().getPricePerNight() * penaltyPercentage / 100.0;
    }
}

// Hotel
public class Hotel {
    private final String id;
    private final String name;
    private final List<Room> rooms = new ArrayList<>();

    public Hotel(String id, String name) { this.id = id; this.name = name; }

    public void addRoom(Room room) { rooms.add(room); }

    public List<Room> searchAvailable(RoomType type, LocalDate checkIn, LocalDate checkOut) {
        return rooms.stream()
            .filter(r -> r.getType() == type && r.isAvailable(checkIn, checkOut))
            .collect(Collectors.toList());
    }
}

// BookingService — orchestrator
public class BookingService {
    private final Map<String, Hotel> hotels = new HashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final CancellationPolicy cancellationPolicy;

    public BookingService(CancellationPolicy policy) {
        this.cancellationPolicy = policy;
    }

    public void registerHotel(Hotel hotel) { hotels.put(hotel.getId(), hotel); }

    public Reservation book(String hotelId, RoomType type,
                            Guest guest, LocalDate checkIn, LocalDate checkOut) {
        Hotel hotel = hotels.get(hotelId);
        List<Room> available = hotel.searchAvailable(type, checkIn, checkOut);
        if (available.isEmpty()) throw new RuntimeException("No rooms available");

        Room room = available.get(0);   // pick first available
        Reservation reservation = room.book(guest, checkIn, checkOut);
        reservations.put(reservation.getId(), reservation);
        return reservation;
    }

    public double cancel(String reservationId) {
        Reservation r = reservations.get(reservationId);
        double penalty = cancellationPolicy.calculatePenalty(r, LocalDate.now());
        r.cancel();
        return penalty;
    }
}
```

---

## Usage

```java
Hotel hotel = new Hotel("h1", "Grand Chennai");
hotel.addRoom(new Room("101", RoomType.STANDARD, 2500));
hotel.addRoom(new Room("201", RoomType.DELUXE, 4500));
hotel.addRoom(new Room("301", RoomType.SUITE, 9000));

BookingService service = new BookingService(
    new FreeCancellation(3, 50)   // free if cancelled 3+ days before, else 50% penalty
);
service.registerHotel(hotel);

Guest guest = new Guest("g1", "Bala");
Reservation r = service.book("h1", RoomType.DELUXE, guest,
    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 13));

double penalty = service.cancel(r.getId());
System.out.println("Cancellation penalty: ₹" + penalty);
```

---

## Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | CancellationPolicy | Swap free/strict/non-refundable without changing booking logic |
| **Builder** | Reservation builder (optional) | Many optional fields — avoid telescoping constructors |
| **Repository** | BookingService as repository | Centralizes access to hotels and reservations |

---

## Follow-up Depth Points

**1. Concurrent booking — two users book same room simultaneously?**
> `synchronized` on `Room.book()` ensures only one succeeds. The second gets "Room not available" exception. In distributed systems, use optimistic locking in DB (`version` column) or `SELECT FOR UPDATE`.

**2. Search performance at scale?**
> Scanning all rooms for availability is O(rooms × reservations). At scale:
> - Pre-compute availability calendar per room in Redis (`SET room:101:2026-08-10 OCCUPIED`)
> - Update on book/cancel
> - Search = Redis key check → O(1) per room

**3. Database schema?**
```
hotels(id, name, address)
rooms(id, hotel_id, room_number, type, price_per_night)
guests(id, name, email, phone)
reservations(id, room_id, guest_id, check_in, check_out, status, created_at)
```

**4. Overbooking (airlines do it, hotels sometimes too)?**
> Maintain an `overbook_factor` (e.g., 1.1 = allow 10% more bookings than rooms). Track confirmed vs available. Handle walkins with upgrade/compensation logic.

**5. Payment integration?**
> Reservation goes to `PENDING_PAYMENT` state first. Payment service confirms → state moves to `CONFIRMED`. If payment times out → auto-cancel. This is a saga pattern.

---

## One-Line Interview Answer

> *"Hotel booking has Hotel → Room → Reservation as the core hierarchy. The critical engineering challenge is concurrent double-booking — synchronized on Room.book() with an availability check inside the same lock prevents race conditions. CancellationPolicy is a Strategy pattern — swap free/penalty/non-refundable rules without changing BookingService. At scale, pre-compute room availability in Redis per date rather than scanning reservation history."*
