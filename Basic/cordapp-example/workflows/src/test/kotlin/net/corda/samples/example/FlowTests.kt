package net.corda.samples.example

import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowException
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.samples.example.flows.CreateTicTacToeGameFlow
import net.corda.samples.example.flows.ExampleFlow
import net.corda.samples.example.flows.PlayTicTacToeGameFlow
import net.corda.samples.example.states.IOUState
import net.corda.samples.example.states.TicTacToeGame
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IOUFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
            MockNetworkParameters(
                cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("net.corda.samples.example.contracts"),
                    TestCordapp.findCordapp("net.corda.samples.example.flows")
                )
            )
        )
        a = network.createPartyNode()
        b = network.createPartyNode()
        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(a, b).forEach { it.registerInitiatedFlow(ExampleFlow.Acceptor::class.java) }
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `flow rejects invalid IOUs`() {
        val flow = ExampleFlow.Initiator(-1, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()

        // The IOUContract specifies that IOUs cannot have negative values.
        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the initiator`() {
        val flow = ExampleFlow.Initiator(1, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(b.info.singleIdentity().owningKey)
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the acceptor`() {
        val flow = ExampleFlow.Initiator(1, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(a.info.singleIdentity().owningKey)
    }

    @Test
    fun `flow records a transaction in both parties' transaction storages`() {
        val flow = ExampleFlow.Initiator(1, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(a, b)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `recorded transaction has no inputs and a single output, the input IOU`() {
        val iouValue = 1
        val flow = ExampleFlow.Initiator(iouValue, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as IOUState
            assertEquals(recordedState.value, iouValue)
            assertEquals(recordedState.lender, a.info.singleIdentity())
            assertEquals(recordedState.borrower, b.info.singleIdentity())
        }
    }

    @Test
    fun `flow records the correct IOU in both parties' vaults`() {
        val iouValue = 1
        val flow = ExampleFlow.Initiator(1, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        // We check the recorded IOU in both vaults.
        for (node in listOf(a, b)) {
            node.transaction {
                val ious = node.services.vaultService.queryBy<IOUState>().states
                assertEquals(1, ious.size)
                val recordedState = ious.single().state.data
                assertEquals(recordedState.value, iouValue)
                assertEquals(recordedState.lender, a.info.singleIdentity())
                assertEquals(recordedState.borrower, b.info.singleIdentity())
            }
        }
    }
}


class TicTacToeFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
            MockNetworkParameters(
                cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("net.corda.samples.example.contracts"),
                    TestCordapp.findCordapp("net.corda.samples.example.flows")
                )
            )
        )
        a = network.createPartyNode()
        b = network.createPartyNode()
        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(a, b).forEach { it.registerInitiatedFlow(ExampleFlow.Acceptor::class.java) }
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `Can play a Tic-Tac-Toe game`() {
        a.startFlow(CreateTicTacToeGameFlow.Initiator(b.info.singleIdentity()))
        network.runNetwork()

        val board0 = a.services.vaultService.queryBy<TicTacToeGame>().states.single().state.data
        assertEquals(",,|,,|,,", board0.board)
        assertEquals("", board0.moves)

        a.startFlow(PlayTicTacToeGameFlow.Initiator(board0.linearId.id, "X", 1, 1))
        network.runNetwork()

        val board1 = a.services.vaultService.queryBy<TicTacToeGame>().states.single().state.data
        assertEquals(",,|,X,|,,", board1.board)
        assertEquals("1,1,X", board1.moves)

        val future = b.startFlow(PlayTicTacToeGameFlow.Initiator(board1.linearId.id, "O", 0, 0))
        network.runNetwork()
        val signedTx = future.getOrThrow()

        val board2 = a.services.vaultService.queryBy<TicTacToeGame>().states.single().state.data
        assertEquals("O,,|,X,|,,", board2.board)
        assertEquals("1,1,X|0,0,O", board2.moves)

        val recordedTx = a.services.validatedTransactions.getTransaction(signedTx.id)
        assertNotNull(recordedTx)
    }

    @Test
    fun `Cannot place outside the Tic-Tac-Toe game board`() {
        a.startFlow(CreateTicTacToeGameFlow.Initiator(b.info.singleIdentity()))
        network.runNetwork()

        val board0 = a.services.vaultService.queryBy<TicTacToeGame>().states.single().state.data

        val future = a.startFlow(PlayTicTacToeGameFlow.Initiator(board0.linearId.id, "X", 3, 1))
        network.runNetwork()
        val e = assertThrows<FlowException> {
            future.getOrThrow()
        }
        assertEquals("Cannot place a symbol outside the board.", e.message)
    }

    @Test
    fun `Cannot place unexpected symbol on the Tic-Tac-Toe game board`() {
        a.startFlow(CreateTicTacToeGameFlow.Initiator(b.info.singleIdentity()))
        network.runNetwork()

        val board0 = a.services.vaultService.queryBy<TicTacToeGame>().states.single().state.data

        val future = a.startFlow(PlayTicTacToeGameFlow.Initiator(board0.linearId.id, "A", 1, 1))
        network.runNetwork()
        val e = assertThrows<FlowException> {
            future.getOrThrow()
        }
        assertEquals("The only accepted symbols are X and O.", e.message)
    }

    @Test
    fun `Must place alternating symbols`() {
        a.startFlow(CreateTicTacToeGameFlow.Initiator(b.info.singleIdentity()))
        network.runNetwork()

        val board0 = a.services.vaultService.queryBy<TicTacToeGame>().states.single().state.data
        assertEquals(",,|,,|,,", board0.board)
        assertEquals("", board0.moves)

        a.startFlow(PlayTicTacToeGameFlow.Initiator(board0.linearId.id, "X", 1, 1))
        network.runNetwork()

        val board1 = a.services.vaultService.queryBy<TicTacToeGame>().states.single().state.data
        assertEquals(",,|,X,|,,", board1.board)
        assertEquals("1,1,X", board1.moves)

        // place again symbol X
        val future = b.startFlow(PlayTicTacToeGameFlow.Initiator(board1.linearId.id, "X", 0, 0))
        network.runNetwork()
        val e = assertThrows<FlowException> {
            future.getOrThrow()
        }
        assertTrue(e.message!!.startsWith("Contract verification failed: Output state has invalid moves."))
    }

}