package no.kartverket.komreg.matrikkelen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.jdk9.asFlow
import no.kartverket.komreg.domain.EntityData
import no.kartverket.komreg.experimental.*
import oracle.jdbc.OracleConnection
import oracle.jdbc.pool.OracleDataSource

class MatrikkelenEntitySource(private val dataSource: OracleDataSource) : EntitySource<EntityData> {
    private val entitySources: List<(OracleConnection) -> EntitySource<EntityData>> = listOf {
        MatrikkelenhetEntitySource(it)
    }

    override fun download(context: DownloadContext): Flow<Validation<out EntityData>> = flow {
        emitAll(
            dataSource
                .createConnectionBuilder()
                .buildConnectionPublisherOracle()
                .asFlow()
                .flowOn(Dispatchers.IO)
                .flatMapMerge { connection -> entitySources.asFlow().map { it(connection) } }
                .flatMapMerge { entitySource -> entitySource.download(context).flowOn(Dispatchers.IO) }
        )
    }
}

class MatrikkelenEntitySourceFactory : EntitySourceFactory<MatrikkelConfig> {
    override val name: String
        get() = "Matrikkelen"

    override fun create(context: EntitySourceContext<MatrikkelConfig>): MatrikkelenEntitySource {
        val config = context.getEntitySourceConfig()
        val dataSource = OracleDataSource()
        dataSource.url = config.jdbcUrl
        dataSource.user = config.jdbcUser
        dataSource.setPassword(config.jdbcPassword)
        return MatrikkelenEntitySource(dataSource)
    }
}
