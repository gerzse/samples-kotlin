package net.corda.samples.example.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.example.helpers.BoardAndMoves
import net.corda.samples.example.states.TicTacToeGame

class TicTacToeContract : Contract {

    companion object {
        @JvmStatic
        val ID = "net.corda.samples.example.contracts.TicTacToeContract"
    }

    override fun verify(tx: LedgerTransaction) {
        if (tx.commands.size != 1) throw IllegalArgumentException("One command Expected")
        val command = tx.getCommand<CommandData>(0).value
        if (command is Commands.Create) {
            verifyCreate(tx)
        } else if (command is Commands.Play) {
            verifyPlay(command, tx)
        }
        requireThat {
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<TicTacToeGame>().single()
            "The players cannot be the same entity." using (out.playerX != out.playerO)
            "The board should have 3 rows." using (out.board.split("|").size == 3)
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun verifyCreate(tx: LedgerTransaction) {
        if (tx.outputStates.size != 1 || tx.inputStates.isNotEmpty()) {
            throw IllegalArgumentException("Zero Input and One Output Expected")
        }

        val output = tx.getOutput(0)
        if (output !is TicTacToeGame) {
            throw IllegalArgumentException("Output of type TicTacToeGame expected")
        }

        val boardAndMoves = BoardAndMoves(output.board, output.moves)
        if (!boardAndMoves.isPristine()) {
            throw IllegalArgumentException("Output state must be a pristine TicTacToe board.")
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun verifyPlay(playCommand: Commands.Play, tx: LedgerTransaction) {
        if (tx.references.isNotEmpty()) {
            throw IllegalArgumentException("No reference expected")
        }

        if (tx.outputStates.size != 1 || tx.inputStates.size != 1) {
            throw IllegalArgumentException("One Input and One Output Expected")
        }

        val input = tx.getInput(0)
        if (input !is TicTacToeGame) {
            throw IllegalArgumentException("Input of type TicTacToeGame expected")
        }

        val output = tx.getOutput(0)
        if (output !is TicTacToeGame) {
            throw IllegalArgumentException("Output of type TicTacToeGame expected")
        }

        val inputBoardAndMoves = BoardAndMoves(input.board, input.moves)
        val outputBoardAndMoves = BoardAndMoves(output.board, output.moves)
        if (inputBoardAndMoves.moves.size + 1 != outputBoardAndMoves.moves.size) {
            throw IllegalArgumentException("Output state must have exactly one more more than the input.")
        }

        if (!inputBoardAndMoves.areMovesValid()) {
            throw IllegalArgumentException("Input state has invalid moves.")
        }

        if (!outputBoardAndMoves.areMovesValid()) {
            throw IllegalArgumentException("Output state has invalid moves.")
        }

        val lastMove = outputBoardAndMoves.moves[outputBoardAndMoves.moves.size - 1]
        if (lastMove.row != playCommand.row || lastMove.col != playCommand.col || lastMove.symbol != playCommand.symbol) {
            throw IllegalArgumentException("Command must match last move.")
        }

        inputBoardAndMoves.play(lastMove.row, lastMove.col, lastMove.symbol)
        if (inputBoardAndMoves.serializeBoard() != outputBoardAndMoves.serializeBoard() ||
            inputBoardAndMoves.serializeMoves() != outputBoardAndMoves.serializeMoves()
        ) {
            throw IllegalArgumentException("The output state does not derive correctly from the input state by applying the last move.")
        }
    }

    interface Commands : CommandData {
        class Create : Commands
        class Play(val row: Int, val col: Int, val symbol: String) : Commands
    }

}