package net.corda.samples.example.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
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
            verifyPlay(tx)
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
        if (tx.outputStates.size != 1 || tx.inputStates.isNotEmpty()) throw IllegalArgumentException("Zero Input and One Output Expected")
        if (tx.getOutput(0) !is TicTacToeGame) throw IllegalArgumentException("Output of type TicTacToeState expected")
    }

    @Throws(IllegalArgumentException::class)
    private fun verifyPlay(tx: LedgerTransaction) {
        if (tx.references.size != 0) {
            throw IllegalArgumentException("No reference expected")
        }
        if (tx.outputStates.size != 1 || tx.inputStates.size != 1) throw IllegalArgumentException("One Input and One Output Expected")
    }

    interface Commands : CommandData {
        class Create : Commands
        class Play(val row: Int, val col: Int, val symbol: String) : Commands
    }

}