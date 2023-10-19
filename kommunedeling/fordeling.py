import json


def load_input_file(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_output_file(data, filename):
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=4, ensure_ascii=False)


def create_transformasjon(type_name, id_field, obj, fylkesnummer, eksisterende_kommunenummer, kommune_til, is_list=False):
    id_value = obj if isinstance(obj, str) else obj.get(id_field, '')
    is_veg = type_name == "veg"
    kommune_fra = eksisterende_kommunenummer

    return {
        "type": type_name,
        "fylkesnummer": {
            "fra": fylkesnummer,
            "til": fylkesnummer
        },
        "kommuneløpenummer": {
            "fra": kommune_fra,
            "til": kommune_til if is_veg and is_list else kommune_til
        },
        id_field: {
            "fra": str(id_value),
            "til": str(id_value)
        }
    }


def main():
    input_data = load_input_file('fordelingsparametere.json')
    fordeling_data = load_input_file('regulering_fordeling.json')

    fylkesnummer = fordeling_data['fylkesnummer']
    kommune1 = fordeling_data['kommuneløpenummer1']
    eksisterende_kommunenummer = fordeling_data['eksisterende_kommuneløpenummer']

    transformasjoner = []

    input_keys = ["gårdsnumre", "adresseparseller", "kretser", "teiger"]
    type_id_fields = [("matrikkelenhet", "gårdsnummer"), ("veg",
                                                          "adressekode"), ("krets", "kretsnummer"), ("teig", "teigId")]

    for input_key, (type_name, id_field) in zip(input_keys, type_id_fields):
        items = input_data.get(input_key, [])

        for obj in items:
            id_value = obj if isinstance(obj, str) else obj.get(id_field, '')
            kommune = fordeling_data.get(type_name, {}).get(
                str(id_value), fylkesnummer + kommune1)
            
            is_list = False
            if isinstance(kommune, list) and type_name == 'veg':
                kommune_til = kommune
                is_list = True
            elif isinstance(kommune, list):
                kommune_til = kommune[0]
            else:
                kommune_til = kommune

            transformasjon = create_transformasjon(
                type_name, id_field, obj, fylkesnummer, eksisterende_kommunenummer, kommune_til, is_list=is_list)
            transformasjoner.append(transformasjon)

    output_data = {"transformasjoner": transformasjoner}
    save_output_file(output_data, 'output.json')
    print("Resultat skrevet til output.json.")


if __name__ == '__main__':
    main()
