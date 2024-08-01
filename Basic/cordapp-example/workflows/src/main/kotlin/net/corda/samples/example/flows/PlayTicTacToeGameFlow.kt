package net.corda.samples.example.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.Vault.StateStatus
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.loggerFor
import net.corda.samples.example.contracts.TicTacToeContract
import net.corda.samples.example.states.TicTacToeGame
import java.util.*

object PlayTicTacToeGameFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        val linearId: UUID, val symbol: String, val row: Int, val col: Int
    ) : FlowLogic<SignedTransaction>() {

        private val log = loggerFor<Initiator>()

        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on existing board.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.single()

            progressTracker.currentStep = GENERATING_TRANSACTION

            val linearStateQueryCriteria = LinearStateQueryCriteria(
                null, listOf(linearId), null, StateStatus.UNCONSUMED, null
            )
            val gameBoardList =
                serviceHub.vaultService.queryBy(TicTacToeGame::class.java, linearStateQueryCriteria).states
            if (gameBoardList.isEmpty()) throw FlowException("Game doesn't exist!")
            val gameBoard = gameBoardList[0].state.data
            log.info("Existing game board is $gameBoard")

            val movingPlayer = serviceHub.myInfo.legalIdentities.first()
            log.info("Moving player is $movingPlayer")
            val otherPlayer = if (movingPlayer == gameBoard.playerX) {
                gameBoard.playerO
            } else {
                gameBoard.playerX
            }
            log.info("Other player is $otherPlayer")

            val newGameBoard = gameBoard.play(symbol, row, col)
            log.info("New game board is $newGameBoard")

            val txCommand =
                Command(TicTacToeContract.Commands.Play(row, col, symbol), gameBoard.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary).addInputState(gameBoardList[0])
                .addOutputState(newGameBoard, TicTacToeContract.ID).addCommand(txCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = GATHERING_SIGS
            val playerOSession = initiateFlow(otherPlayer)

            val fullySignedTx = subFlow(
                CollectSignaturesFlow(
                    partSignedTx, setOf(playerOSession), GATHERING_SIGS.childProgressTracker()
                )
            )

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(
                FinalityFlow(
                    fullySignedTx, setOf(playerOSession), FINALISING_TRANSACTION.childProgressTracker()
                )
            )
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(
        val otherPartySession: FlowSession
    ) : FlowLogic<SignedTransaction>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a TicTacToe transaction." using (output is TicTacToeGame)
                }
            }
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

}