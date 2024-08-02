package net.corda.samples.example.helpers

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardAndMovesTests {

    @Test
    fun `Create board and place some symbols`() {
        val boardAndMoves = BoardAndMoves.empty()

        assertTrue(boardAndMoves.isPristine())
        assertEquals(",,|,,|,,", boardAndMoves.serializeBoard())
        assertEquals("", boardAndMoves.serializeMoves())

        boardAndMoves.play(1, 1, "X")

        assertFalse(boardAndMoves.isPristine())
        assertEquals(",,|,X,|,,", boardAndMoves.serializeBoard())
        assertEquals("1,1,X", boardAndMoves.serializeMoves())

        boardAndMoves.play(0, 2, "O")

        assertFalse(boardAndMoves.isPristine())
        assertEquals(",,O|,X,|,,", boardAndMoves.serializeBoard())
        assertEquals("1,1,X|0,2,O", boardAndMoves.serializeMoves())

        boardAndMoves.play(2, 0, "X")

        assertFalse(boardAndMoves.isPristine())
        assertEquals(",,O|,X,|X,,", boardAndMoves.serializeBoard())
        assertEquals("1,1,X|0,2,O|2,0,X", boardAndMoves.serializeMoves())
    }

}