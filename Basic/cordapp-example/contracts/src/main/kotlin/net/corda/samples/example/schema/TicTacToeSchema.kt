package net.corda.samples.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object TicTacToeSchema

object TicTacToeSchemaV1 : MappedSchema(
    schemaFamily = TicTacToeSchema.javaClass,
    version = 1,
    mappedTypes = listOf(PersistentTicTacToe::class.java)
) {

    override val migrationResource: String?
        get() = "tic-tac-toe.changelog-master";

    @Entity
    @Table(name = "tictactoe_states")
    class PersistentTicTacToe(
        @Column(name = "player_x")
        var playerXName: String,

        @Column(name = "player_o")
        var playerOName: String,

        @Column(name = "board")
        var board: String,

        @Column(name = "moves")
        var moves: String,

        @Column(name = "linear_id")
        @Type(type = "uuid-char")
        var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor() : this("", "", ",,|,,|,,", "", UUID.randomUUID())
    }
}