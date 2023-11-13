import json
from typing import List

import requests as req

from fordeling import createRegulering


def save_json(data, filename):
        with open(filename, 'w', encoding='utf8') as f:
            json.dump(data, f, ensure_ascii=False, indent=4)

def getGrunndata(envUrl: str, kommunenr: str):
    try:
        response = req.get(envUrl + "/kommuner/" + kommunenr + "/fordelingsparametre")
        if response.status_code == 200:
            return response.json()
        else:
            print(f"Request failed with status code {response.status_code}")
    except Exception as e:
        print("Something went wrong:", e)

# Henter vegadresser fra miljøet du har valgt. Kreves egt ikke, men det gir en komplett grunndata for kommunen
def getVegadresse(envUrl:str, kommunenr: str, vegadresse: str):
    try:
        response = req.get(envUrl + "/kommuner/" + kommunenr + "/fordelingsparametre" + "/veg/" + vegadresse)
        if response.status_code == 200:
            return response.json()
        else:
            print(f"Request failed with status code {response.status_code}")
    except Exception as e:
        print("Something went wrong:", e)


def createGrunndataTilFordeling(envUrl: str, kommunenr: str, vegadresser: List[str] = None):
    grunndata: dict = getGrunndata(envUrl, kommunenr)
    grunndata['vegadresse'] = []
    
    if vegadresser is not None:
        for vegadresse in vegadresser:
            vegadresseAPI = getVegadresse(envUrl, kommunenr, vegadresse)
            for vegadresse in vegadresseAPI["vegadresser"]:
                grunndata['vegadresse'].append(vegadresse)
            
    return grunndata


def getKommune(envUrl, fylkesnummer, lopenummer):
    try:
        response = req.get(envUrl + "/kommuner")
        if response.status_code == 200:
            res=  response.json()
            return next((item for item in res if item['fylkesnummer'] == fylkesnummer and item['kommunenummer'] == lopenummer), None)
        else:
            print(f"Request failed with status code {response.status_code}")
    except Exception as e:
        print("Something went wrong:", e) 

def createGodkjenteGardsnummere(grunndata):
    godkjenteGardsnummere = []
    gardsnummer_start = int(grunndata["gårdsnumre"][0])
    
    for i in range(1, len(grunndata["gårdsnumre"])):
        gardsnummer = int(grunndata["gårdsnumre"][i])
        if gardsnummer - int(grunndata["gårdsnumre"][i-1]) != 1:
            godkjenteGardsnummere.append({
                "fra": gardsnummer_start,
                "til": int(grunndata["gårdsnumre"][i-1])
            })
            gardsnummer_start = gardsnummer

    # Capture the last serie after the loop completes
    godkjenteGardsnummere.append({
        "fra": gardsnummer_start,
        "til": gardsnummer
    })

    return godkjenteGardsnummere




#Tilgjengelige miljøer
dev = {"url" :'https://komreg-backend.dev.skip.statkart.no/', "env": "dev"}
local = {"url": 'http://localhost:8080', "env": "local"}
test = {"url": 'https://komreg-backend.test.skip.statkart.no/', "env": "test"}



# Path til fordelingsinput
fordelingsInputPath = 'alesund_haram/fordeling_input_alesund_haram.json'

# nyeKommunerJson
nyeKommunerPath = 'alesund_haram/nyeKommuner.json' 

with open(fordelingsInputPath, 'r', encoding='utf8') as f:
    fordeling_input = json.load(f)


with open(nyeKommunerPath, 'r', encoding='utf8') as f:
    nyeKommuner = json.load(f)

# Kommunenummeret til kommunen du skal hente fordelingsparametere for
kommunenr = fordeling_input["fylkesnummer"] + fordeling_input["eksisterende_kommuneløpenummer"]
nyKommune1 = fordeling_input["fylkesnummer"] + fordeling_input["kommuneløpenummer1"]
nyKommune2 = fordeling_input["fylkesnummer"] + fordeling_input["kommuneløpenummer2"]


# Setter hvilket miljø du vil hente grunndata fra
valgtEnv = test


transformasjoner = createRegulering(fordeling_input=fordeling_input, grunndata=createGrunndataTilFordeling(valgtEnv["url"], kommunenr) )




save_json({"nyeKommuner": nyeKommuner, "transformasjoner": transformasjoner}, kommunenr + "_til_" + nyKommune1 + "_" + nyKommune2 + "_" + valgtEnv["env"] + "_regulering.json")
