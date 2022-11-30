package no.kartverket.komreg.matrikkelen

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

    override fun download(context: EntitySourceDownloadContext): Flow<SourceEntity<EntityData>> = flow {
        emitAll(
            dataSource
                .createConnectionBuilder()
                .buildConnectionPublisherOracle()
                .asFlow()
                .flatMapMerge { connection -> entitySources.asFlow().map { it(connection) } }
                .flatMapMerge { entitySource -> entitySource.download(context) }
        )
    }
}

class MatrikkelenEntitySourceFactory : EntitySourceFactory<MatrikkelConfig> {
    override val name: String
        get() = "Matrikkelen"

    override fun create(context: EntitySourceContext<MatrikkelConfig>): MatrikkelenEntitySource {
        val config = context.getEntitySourceConfig()
        val dataSource = OracleDataSource().apply {
            setConnectionProperty("autoCommit","false")
            setConnectionProperty("defaultRowPrefetch","65536")
            setConnectionProperty("oracle.jdbc.maxCachedBufferSize","12")
            url = config.jdbcUrl
            user = config.jdbcUser
            setPassword(config.jdbcPassword)
        }
        return MatrikkelenEntitySource(dataSource)
    }
}
