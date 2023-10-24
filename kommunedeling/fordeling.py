import json


def save_json(data, filename):
    with open(filename, 'w', encoding='utf8') as f:
        json.dump(data, f, ensure_ascii=False, indent=4)


with open('fordelingsparametre.json', 'r', encoding='utf8') as f:
    fordelingsparametre = json.load(f)

with open('fordeling_input.json', 'r', encoding='utf8') as f:
    fordeling_input = json.load(f)

transformasjoner = []

eksisterende_kommuneløpenummer = fordeling_input['eksisterende_kommuneløpenummer']
fylkesnummer = fordeling_input['fylkesnummer']

for gårdsnummer in fordelingsparametre['gårdsnumre']:
    til_value = fordeling_input['matrikkelenhet'].get(
        gårdsnummer, eksisterende_kommuneløpenummer)
    transformasjoner.append({
        'type': 'matrikkelenhet',
        'fylkesnummer': {'fra': fylkesnummer, 'til': fylkesnummer},
        'kommuneløpenummer': {'fra': eksisterende_kommuneløpenummer, 'til': til_value},
        'gårdsnummer': {'fra': gårdsnummer, 'til': gårdsnummer},
    })

for adresse in fordelingsparametre['adresseparseller']:
    til_value = fordeling_input['veg'].get(
        adresse['adressekode'], [eksisterende_kommuneløpenummer])
    transformasjoner.append({
        'type': 'veg',
        'fylkesnummer': {'fra': fylkesnummer, 'til': fylkesnummer},
        'kommuneløpenummer': {'fra': eksisterende_kommuneløpenummer, 'til': til_value},
        'adressekode': {'fra': adresse['adressekode'], 'til': adresse['adressekode']},
    })

for krets in fordelingsparametre['kretser']:
    til_value = fordeling_input['krets'].get(
        krets['kretsnummer'], eksisterende_kommuneløpenummer)
    nytt_kretsnummer = krets.get('nytt_kretsnummer', krets['kretsnummer'])
    transformasjoner.append({
        'type': 'krets',
        'fylkesnummer': {'fra': fylkesnummer, 'til': fylkesnummer},
        'kommuneløpenummer': {'fra': eksisterende_kommuneløpenummer, 'til': til_value},
        'kretsnummer': {'fra': krets['kretsnummer'], 'til': nytt_kretsnummer},
        'kretstype': {'fra': krets['kretstype'], 'til': krets['kretstype']},
    })

for teig in fordelingsparametre['teiger']:
    til_value = fordeling_input['teig'].get(
        teig['teigId'], eksisterende_kommuneløpenummer)
    transformasjoner.append({
        'type': 'teig',
        'fylkesnummer': {'fra': fylkesnummer, 'til': fylkesnummer},
        'kommuneløpenummer': {'fra': eksisterende_kommuneløpenummer, 'til': til_value},
        'teigId': {'fra': teig['teigId'], 'til': teig['teigId']},
    })

save_json({'transformasjoner': transformasjoner}, 'output.json')
