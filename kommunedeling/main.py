import json
from typing import List

import requests as req

from fordeling import createRegulering


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


#Tilgjengelige miljøer
dev = {"url" :'https://komreg-backend.dev.skip.statkart.no/', "env": "dev"}
local = {"url": 'http://localhost:8080', "env": "local"}
test = {"url": 'https://komreg-backend.test.skip.statkart.no/', "env": "test"}



# Path til fordelingsinput
fordelingsInputPath = 'alesund_haram/fordeling_input_alesund_haram.json'

with open(fordelingsInputPath, 'r', encoding='utf8') as f:
    fordeling_input = json.load(f)

# Kommunenummeret til kommunen du skal hente fordelingsparametere for
kommunenr = fordeling_input["fylkesnummer"] + fordeling_input["eksisterende_kommuneløpenummer"]
nyKommune1 = fordeling_input["fylkesnummer"] + fordeling_input["kommuneløpenummer1"]
nyKommune2 = fordeling_input["fylkesnummer"] + fordeling_input["kommuneløpenummer2"]

# Setter hvilket miljø du vil hente grunndata fra
valgtEnv = test

createRegulering(fordeling_input=fordeling_input, grunndata=createGrunndataTilFordeling(valgtEnv["url"], kommunenr), reguleringNavn = kommunenr + "->" + nyKommune1 + "_" + nyKommune2 + "_" + valgtEnv["env"] + "_regulering")

