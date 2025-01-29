CREATE INDEX  IF NOT EXISTS  transformasjon_kjoring_id_type_index ON transformasjon
    USING btree (kjoring, ((((transformasjon -> 'id'::text) -> 'type'::text) ->> 'value'::text)));
