package net.corda.samples.example.contracts

import net.corda.core.identity.CordaX500Name
import net.corda.samples.example.states.IOUState
import net.corda.samples.example.states.TicTacToeGame
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class IOUContractTests {
    private val ledgerServices = MockServices()
    private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val iouValue = 1

    @Test
    fun `transaction must include Create command`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                fails()
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `transaction must have no inputs`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("No inputs should be consumed when issuing an IOU.")
            }
        }
    }

    @Test
    fun `transaction must have one output`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("Only one output state should be created.")
            }
        }
    }

    @Test
    fun `lender must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(miniCorp.publicKey, IOUContract.Commands.Create())
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `borrower must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party))
                command(megaCorp.publicKey, IOUContract.Commands.Create())
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `lender is not borrower`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, megaCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("The lender and the borrower cannot be the same entity.")
            }
        }
    }

    @Test
    fun `cannot create negative-value IOUs`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(-1, miniCorp.party, megaCorp.party))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`("The IOU's value must be non-negative.")
            }
        }
    }
}

class TicTacToeContractTests {
    private val ledgerServices = MockServices()
    private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))

    @Test
    fun `transaction must include Create or Play command`() {
        ledgerServices.ledger {
            transaction {
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,,|,,", ""))
                fails()
            }
        }
    }

    @Test
    fun `transaction may include Create command`() {
        ledgerServices.ledger {
            transaction {
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,,|,,", ""))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `transaction must include Create command only with empty output board`() {
        ledgerServices.ledger {
            transaction {
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,X,|,,", "1,1,X"))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Create())
                `fails with`("Output state must be a pristine TicTacToe board.")
            }
        }
    }

    @Test
    fun `transaction may include Play command`() {
        ledgerServices.ledger {
            transaction {
                input(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,,|,,", ""))
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,X,|,,", ""))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Play(1, 1, "X"))
                verifies()
            }
        }
    }

    @Test
    fun `transaction must include Play command only with output with non-empty moves`() {
        ledgerServices.ledger {
            transaction {
                input(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,,|,,", ""))
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,X,|,,", ""))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Play(0, 1, "X"))
                `fails with`("Output state must have at least one move.")
            }
        }
    }

    @Test
    fun `transaction must include Play command only with output with command matching last move`() {
        ledgerServices.ledger {
            transaction {
                input(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,,|,,", ""))
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,X,|,,", "0,0,X"))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Play(0, 1, "X"))
                `fails with`("Command must match last move.")
            }
        }
    }

    @Test
    fun `transaction must include Play command only with output having one additional move compared to input`() {
        ledgerServices.ledger {
            transaction {
                input(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,,|,,", ""))
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|O,X,|,,", "1,1,X|1,0,O"))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Play(1, 0, "O"))
                `fails with`("Output state must have exactly one more more than the input.")
            }
        }
    }

    @Test
    fun `transaction must include Play command only with command that matches the last output move`() {
        ledgerServices.ledger {
            transaction {
                input(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,,|,,", ""))
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,X,|,,", "1,1,X"))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Play(0, 1, "X"))
                `fails with`("Command must match last move.")
            }
        }
    }

    @Test
    fun `transaction must include Play command only with consistent input state`() {
        ledgerServices.ledger {
            transaction {
                input(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,,|,,", "1,1,X"))
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|0,X,|,,", "1,1,X|1,0,O"))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Play(1, 0, "O"))
                `fails with`("Input state has invalid moves.")
            }
        }
    }

    @Test
    fun `transaction must include Play command only with consistent output state`() {
        ledgerServices.ledger {
            transaction {
                input(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,,|,,", ""))
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|0,X,|,,", "1,1,X"))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Play(1, 1, "X"))
                `fails with`("Output state has invalid moves.")
            }
        }
    }

    @Test
    fun `transaction must include Play command only with output state with alternating moves`() {
        ledgerServices.ledger {
            transaction {
                input(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|,X,|,,", "1,1,X"))
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|X,X,|,,", "1,1,X|1,0,X"))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Play(1, 0, "X"))
                `fails with`("Output state has invalid moves.")
            }
        }
    }

    @Test
    fun `transaction must include Play command only with output state deriving correctly from the input state`() {
        ledgerServices.ledger {
            transaction {
                input(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",X,|,,|,,", "0,1,X"))
                output(TicTacToeContract.ID, TicTacToeGame(miniCorp.party, megaCorp.party, ",,|O,X,|,,", "1,1,X|1,0,O"))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), TicTacToeContract.Commands.Play(1, 0, "O"))
                `fails with`("The output state does not derive correctly from the input state by applying the last move.")
            }
        }
    }
}
