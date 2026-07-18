# LLD 03 — Chess Game

> **Design a two-player Chess game with move validation, turn management, check/checkmate detection, and undo.**

---

## Clarify Requirements

**Functional:**
- 8x8 board, two players (White / Black)
- All standard pieces: King, Queen, Rook, Bishop, Knight, Pawn
- Validate legal moves per piece type
- Detect check and checkmate
- Undo last move
- Turn enforcement

**Non-Functional:**
- Extensible (add new piece types easily)
- Clean separation between board state and rules

---

## Identify Entities → Classes

```
Board          → 8x8 grid of cells
Cell           → position (row, col) + optional piece
Piece          → abstract base: King, Queen, Rook, Bishop, Knight, Pawn
Player         → White / Black
Move           → from + to + piece (for undo/history)
Game           → orchestrates turns, checks, checkmate
MoveValidator  → validates move legality per piece type
```

---

## Class Design

```java
// Color
public enum Color { WHITE, BLACK }

// Position
public record Position(int row, int col) {
    public boolean isValid() {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }
}

// Piece (abstract — polymorphism is the key)
public abstract class Piece {
    protected Color color;
    protected Position position;

    public Piece(Color color, Position position) {
        this.color = color;
        this.position = position;
    }

    // Each piece knows its own valid moves
    public abstract List<Position> validMoves(Board board);

    public Color getColor() { return color; }
    public Position getPosition() { return position; }
    public void setPosition(Position p) { this.position = p; }
}

// Rook — moves any number of squares horizontally or vertically
public class Rook extends Piece {
    public Rook(Color color, Position position) { super(color, position); }

    @Override
    public List<Position> validMoves(Board board) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{0,1},{0,-1},{1,0},{-1,0}};
        for (int[] d : directions) {
            int r = position.row() + d[0];
            int c = position.col() + d[1];
            while (new Position(r, c).isValid()) {
                Piece occupant = board.getPieceAt(new Position(r, c));
                if (occupant == null) {
                    moves.add(new Position(r, c));
                } else {
                    if (occupant.getColor() != this.color) moves.add(new Position(r, c)); // capture
                    break; // blocked
                }
                r += d[0]; c += d[1];
            }
        }
        return moves;
    }
}

// Knight — L-shaped, jumps over pieces
public class Knight extends Piece {
    public Knight(Color color, Position position) { super(color, position); }

    @Override
    public List<Position> validMoves(Board board) {
        List<Position> moves = new ArrayList<>();
        int[][] offsets = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] o : offsets) {
            Position target = new Position(position.row()+o[0], position.col()+o[1]);
            if (target.isValid()) {
                Piece occupant = board.getPieceAt(target);
                if (occupant == null || occupant.getColor() != this.color) {
                    moves.add(target);
                }
            }
        }
        return moves;
    }
}

// Board
public class Board {
    private final Piece[][] grid = new Piece[8][8];

    public void placePiece(Piece piece) {
        Position p = piece.getPosition();
        grid[p.row()][p.col()] = piece;
    }

    public Piece getPieceAt(Position p) {
        return grid[p.row()][p.col()];
    }

    public void movePiece(Position from, Position to) {
        Piece piece = grid[from.row()][from.col()];
        grid[to.row()][to.col()] = piece;
        grid[from.row()][from.col()] = null;
        piece.setPosition(to);
    }

    public void removePiece(Position p) {
        grid[p.row()][p.col()] = null;
    }

    public List<Piece> getAllPieces(Color color) {
        List<Piece> pieces = new ArrayList<>();
        for (Piece[] row : grid)
            for (Piece p : row)
                if (p != null && p.getColor() == color) pieces.add(p);
        return pieces;
    }
}

// Move (for Command pattern / undo)
public class Move {
    private final Piece piece;
    private final Position from;
    private final Position to;
    private final Piece captured;    // null if no capture

    public Move(Piece piece, Position from, Position to, Piece captured) {
        this.piece = piece;
        this.from = from;
        this.to = to;
        this.captured = captured;
    }

    public Piece getPiece() { return piece; }
    public Position getFrom() { return from; }
    public Position getTo() { return to; }
    public Piece getCaptured() { return captured; }
}

// Game — orchestrates everything
public class Game {
    private final Board board;
    private Color currentTurn = Color.WHITE;
    private final Deque<Move> moveHistory = new ArrayDeque<>();

    public Game() {
        this.board = new Board();
        setupBoard();
    }

    public boolean makeMove(Position from, Position to) {
        Piece piece = board.getPieceAt(from);
        if (piece == null || piece.getColor() != currentTurn) return false;

        List<Position> valid = piece.validMoves(board);
        if (!valid.contains(to)) return false;

        Piece captured = board.getPieceAt(to);
        board.movePiece(from, to);

        // Check if this move leaves own king in check
        if (isInCheck(currentTurn)) {
            board.movePiece(to, from);                     // rollback
            if (captured != null) board.placePiece(captured);
            return false;
        }

        moveHistory.push(new Move(piece, from, to, captured));
        currentTurn = (currentTurn == Color.WHITE) ? Color.BLACK : Color.WHITE;
        return true;
    }

    // Undo — Command Pattern
    public void undo() {
        if (moveHistory.isEmpty()) return;
        Move last = moveHistory.pop();
        board.movePiece(last.getTo(), last.getFrom());
        if (last.getCaptured() != null) board.placePiece(last.getCaptured());
        currentTurn = last.getPiece().getColor();
    }

    public boolean isInCheck(Color color) {
        Position kingPos = findKing(color);
        Color opponent = (color == Color.WHITE) ? Color.BLACK : Color.WHITE;
        return board.getAllPieces(opponent).stream()
            .anyMatch(p -> p.validMoves(board).contains(kingPos));
    }

    public boolean isCheckmate(Color color) {
        if (!isInCheck(color)) return false;
        return board.getAllPieces(color).stream()
            .allMatch(piece -> piece.validMoves(board).stream()
                .noneMatch(to -> !wouldLeaveKingInCheck(piece, to, color)));
    }

    private boolean wouldLeaveKingInCheck(Piece piece, Position to, Color color) {
        Position from = piece.getPosition();
        Piece captured = board.getPieceAt(to);
        board.movePiece(from, to);
        boolean inCheck = isInCheck(color);
        board.movePiece(to, from);
        if (captured != null) board.placePiece(captured);
        return inCheck;
    }

    private Position findKing(Color color) {
        return board.getAllPieces(color).stream()
            .filter(p -> p instanceof King)
            .map(Piece::getPosition)
            .findFirst()
            .orElseThrow();
    }

    private void setupBoard() {
        // Place all pieces at standard starting positions
        // (abbreviated for clarity)
        board.placePiece(new Rook(Color.WHITE, new Position(7, 0)));
        board.placePiece(new Knight(Color.WHITE, new Position(7, 1)));
        // ... all pieces
    }
}
```

---

## Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Polymorphism** | Piece subclasses | Each piece knows its own valid moves |
| **Command** | Move + moveHistory stack | Enables undo without coupling Game to move logic |
| **Template Method** | Piece.validMoves() | Common structure, piece-specific implementation |

---

## Follow-up Depth Points

**1. How do you detect checkmate efficiently?**
> For each piece of the current player, try every valid move. If any move leaves the king NOT in check → not checkmate. Brute-force O(pieces × moves) is fine for chess (bounded).

**2. Special moves — castling, en passant, pawn promotion?**
> Each needs state:
> - Castling: track if King or Rook has moved (`hasMoved` flag)
> - En passant: track last pawn double-step in game state
> - Promotion: on pawn reaching back rank, prompt player for piece type

**3. Stalemate?**
> Player's king is NOT in check but has NO legal moves. Same logic as checkmate but without the `isInCheck` precondition.

**4. How does undo work for captures?**
> Move stores the captured piece. On undo: move piece back to `from`, place captured piece back at `to`. Works because Move is a value object with full context.

**5. Multiplayer over network?**
> Serialize `Move` as JSON. Send over WebSocket. Other client applies the move. Game state is the single source of truth on server.

---

## One-Line Interview Answer

> *"Chess LLD centers on the Piece hierarchy — each subclass implements validMoves() for its movement rules, making the design open for extension. Board is a simple 8x8 grid. Game orchestrates turns, delegates move validation to pieces, and checks if the resulting state leaves the king in check. Undo uses a Command pattern — each Move is an immutable value object pushed onto a stack, fully reversible. Special moves (castling, en passant) require additional state flags on pieces."*
