package net.corda.samples.example.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.samples.example.contracts.TicTacToeContract
import net.corda.samples.example.schema.TicTacToeSchemaV1

@BelongsToContract(TicTacToeContract::class)
data class TicTacToeGame(
    val playerX: Party,
    val playerO: Party,
    val board: String,
    val moves: String,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState {

    override val participants: List<Party> get() = listOf(playerX, playerO)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is TicTacToeSchemaV1 -> TicTacToeSchemaV1.PersistentTicTacToe(
                this.playerX.name.toString(),
                this.playerO.name.toString(),
                this.board,
                this.moves,
                this.linearId.id
            )

            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TicTacToeSchemaV1)
}
