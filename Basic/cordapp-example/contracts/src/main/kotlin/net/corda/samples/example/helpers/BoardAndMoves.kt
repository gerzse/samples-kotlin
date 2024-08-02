package net.corda.samples.example.helpers

/**
 * Tic-Tac-Toe board with a list of moves that lead to it.
 */
class BoardAndMoves(serializedBoard: String, serializedMoves: String) {

    class Move {
        val row: Int
        val col: Int
        val symbol: String

        constructor (serializedMove: String) {
            val parts = serializedMove.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid serialized move: $serializedMove")
            }
            this.row = parts[0].toInt()
            this.col = parts[1].toInt()
            this.symbol = parts[2]
        }

        constructor(row: Int, col: Int, symbol: String) {
            this.row = row
            this.col = col
            this.symbol = symbol
        }

        fun serialize(): String {
            return "$row,$col,$symbol"
        }
    }

    companion object {
        fun empty(): BoardAndMoves {
            return BoardAndMoves(",,|,,|,,", "")
        }
    }

    private val board: MutableList<MutableList<String>> =
        serializedBoard.split("|").map { rowIt -> rowIt.trim().split(",").map { it.trim() }.toMutableList() }
            .toMutableList()

    var moves: MutableList<Move> =
        serializedMoves.split("|").filter { it.isNotEmpty() }.map { Move(it) }.toMutableList()

    init {
        if (board.size != 3) {
            throw IllegalStateException("Board must have 3 rows.")
        }
        board.forEach {
            if (it.size != 3) {
                throw IllegalStateException("Board must have 3 columns.")
            }
        }
    }

    fun play(row: Int, col: Int, symbol: String): Unit {
        if (row < 0 || row >= board.size) {
            throw IllegalArgumentException("Row falls outside the board.")
        }
        val boardRow = board[row]
        if (col < 0 || col >= boardRow.size) {
            throw IllegalArgumentException("Col falls outside the board.")
        }
        boardRow[col] = symbol

        moves.add(Move(row, col, symbol))
    }

    fun isPristine(): Boolean {
        if (moves.isNotEmpty()) {
            return false
        }
        board.forEach { rowIt ->
            rowIt.forEach {
                if (it.isNotEmpty()) {
                    return false
                }
            }
        }
        return true
    }

    fun areMovesValid(): Boolean {
        val replayBoard = empty()
        moves.forEach {
            replayBoard.play(it.row, it.col, it.symbol)
        }
        if (replayBoard.serializeBoard() != this.serializeBoard() ||
            replayBoard.serializeMoves() != this.serializeMoves()
        ) {
            return false
        }
        if (moves.size >= 2) {
            val firstSymbol = moves[0].symbol
            val secondSymbol = moves[1].symbol
            if (firstSymbol == secondSymbol) {
                return false
            }
            moves.forEachIndexed { index, move ->
                val expectedSymbol = if (index % 2 == 0) firstSymbol else secondSymbol
                if (move.symbol != expectedSymbol) {
                    return false
                }
            }
        }
        return true
    }

    fun serializeBoard(): String {
        return board.joinToString("|") { it.joinToString(",") }
    }

    fun serializeMoves(): String {
        return moves.joinToString("|") { it.serialize() }
    }

}