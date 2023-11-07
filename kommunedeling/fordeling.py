import json
from typing import List


def createRegulering(grunndata, fordeling_input, reguleringNavn):

    def save_json(data, filename):
        with open(filename, 'w', encoding='utf8') as f:
            json.dump(data, f, ensure_ascii=False, indent=4)


    transformasjoner = []

    transformasjoner.append({
        "type": "kommune",
        "fylkesnummer": {
            "fra": fordeling_input["fylkesnummer"],
            "til": fordeling_input["fylkesnummer"]
        },
        "kommuneløpenummer": {
            "fra": fordeling_input["eksisterende_kommuneløpenummer"],
            "til": [fordeling_input["kommuneløpenummer1"], fordeling_input["kommuneløpenummer2"]]
        }
    })

    eksisterende_kommuneløpenummer = fordeling_input['eksisterende_kommuneløpenummer']
    kommune1 = fordeling_input['kommuneløpenummer1']
    fylkesnummer = fordeling_input['fylkesnummer']

    for gårdsnummer in grunndata['gårdsnumre']:
        til_value = fordeling_input['matrikkelenhet'].get(gårdsnummer, kommune1)
        transformasjoner.append({
            'type': 'matrikkelenhet',
            'fylkesnummer': {'fra': fylkesnummer, 'til': fylkesnummer},
            'kommuneløpenummer': {'fra': eksisterende_kommuneløpenummer, 'til': til_value},
            'gårdsnummer': {'fra': gårdsnummer, 'til': gårdsnummer},
        })

    for adresse in grunndata['adresseparseller']:
        til_value = fordeling_input['veg'].get(adresse['adressekode'], [kommune1])
        if not isinstance(til_value, list):
            til_value = [til_value]
        transformasjoner.append({
            'type': 'veg',
            'fylkesnummer': {'fra': fylkesnummer, 'til': fylkesnummer},
            'kommuneløpenummer': {'fra': eksisterende_kommuneløpenummer, 'til': til_value},
            'adressekode': {'fra': adresse['adressekode'], 'til': adresse['adressekode']},
        })

    
    alleredeFordelteKretser = [item['fra'] for sublist in fordeling_input['krets'].values() for item in sublist]

    for krets in grunndata['kretser']:
      
        if krets["kretsnummer"] not in alleredeFordelteKretser:
            transformasjoner.append({
                'type': 'krets',
                'fylkesnummer': {'fra': fordeling_input["fylkesnummer"], 'til': fordeling_input["fylkesnummer"]},
                'kommuneløpenummer': {'fra': fordeling_input["eksisterende_kommuneløpenummer"], 'til': kommune1},
                'kretsnummer': {'fra': krets['kretsnummer'], 'til': krets['kretsnummer']},
                'kretstype': {'fra': krets['kretstype'], 'til': krets['kretstype']},
                })



    for kommunelopenummer in fordeling_input['krets'].keys():
            if (len(fordeling_input['krets'][kommunelopenummer]) > 0 and isinstance(fordeling_input['krets'][kommunelopenummer], List)):
                for fordelingsKrets in fordeling_input['krets'][kommunelopenummer]:
                    
                    transformasjoner.append({
                        'type': 'krets',
                        'fylkesnummer': {'fra': fordeling_input["fylkesnummer"], 'til': fordeling_input["fylkesnummer"]},
                        'kommuneløpenummer': {'fra': fordeling_input["eksisterende_kommuneløpenummer"], 'til': kommunelopenummer},
                        'kretsnummer': {'fra': fordelingsKrets['fra'], 'til': fordelingsKrets['til']},
                        'kretstype': {'fra': fordelingsKrets['kretstype'], 'til': fordelingsKrets['kretstype']},
                        })
    

    for teig in grunndata['teiger']:
        til_value = fordeling_input['teig'].get(teig['teigId'], kommune1)
        transformasjoner.append({
            'type': 'teig',
            'fylkesnummer': {'fra': fylkesnummer, 'til': fylkesnummer},
            'kommuneløpenummer': {'fra': eksisterende_kommuneløpenummer, 'til': til_value},
            'teigId': {'fra': teig['teigId'], 'til': teig['teigId']},
        })

    if (fordeling_input['vegadresse']):

        
        
        for adressekode in fordeling_input["vegadresse"].keys():
            foo: dict = {}
            
            #print("adressekode " + adressekode)
            foo["type"] = "vegadresse"
            foo["fylkesnummer"] = {"fra": fylkesnummer, "til": fylkesnummer}
            
            for kommunelopenummer in fordeling_input["vegadresse"][adressekode]:
                foo["komuneløpenummer"] = {"fra": eksisterende_kommuneløpenummer, "til": kommunelopenummer}
                foo["adressekode"] = {"fra": adressekode, "til": adressekode}
                
                if (len(fordeling_input["vegadresse"][adressekode][kommunelopenummer]) > 1):
                    for adressenummer in fordeling_input["vegadresse"][adressekode][kommunelopenummer]:
                        foo["adressenummer"] = {"fra": adressenummer, "til": adressenummer}
                        transformasjoner.append(dict(foo))
    
    save_json({'transformasjoner': transformasjoner}, reguleringNavn + '.json')
