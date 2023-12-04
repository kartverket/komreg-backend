CREATE TABLE id_cache (
    cache_hint text NOT NULL,
    id_type JSONB NOT NULL,
    id JSONB NOT NULL,
    PRIMARY KEY (cache_hint, id_type)
);
