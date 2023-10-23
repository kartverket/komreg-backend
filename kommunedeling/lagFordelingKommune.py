import json

gammel_kommuneløpenummer = '51'
fylke = '11'
nyKommuneLøpenummerA = '61'
nyKommuneLøpenummerB = '62'

with open('fordelingsparametere_utsira.json', 'r', encoding='utf8') as f:
    fordelingsparametre = json.load(f)




def lagFordeling():
    fordeling = {}
    fordeling['fylkesnummer'] = fylke
    fordeling['kommuneløpenummer1'] = nyKommuneLøpenummerA
    fordeling['kommuneløpenummer2'] = nyKommuneLøpenummerB
    fordeling['ekisterende_kommuneløpenummer'] = gammel_kommuneløpenummer

    fordeling['matrikkelenhet'] = {}
    fordeling['veg'] = {}
    fordeling['krets'] = {}
    fordeling['teig'] = {}

    
    print("Fant " + str(len(fordelingsparametre['gårdsnumre'])) + " gårdsnummer")    
    for gårdsnummer in fordelingsparametre['gårdsnumre']:
        
        kommune = input('Hvilken kommune skal gårdsnummer ' + gårdsnummer + ' tilhøre? ')
        fordeling['matrikkelenhet'][gårdsnummer] = kommune
    print("Fant " + str(len(fordelingsparametre['adresseparseller'])) + " adresseparseller")
    for adresse in fordelingsparametre['adresseparseller']:
        
        fordeling['veg'] = {
            adresse['adressekode']: input('Hvilken kommune skal adressekode ' + adresse['adressekode'] + ' tilhøre? ')
        }
    print("Fant " + str(len(fordelingsparametre['kretser'])) + " kretser")
    for krets in fordelingsparametre['kretser']:
        
        fordeling['krets'] = {
            krets['kretsnummer']: input('Hvilken kommune skal kretsnummer ' + krets['kretsnummer'] + ' tilhøre? ')
        }
    print("Fant " + str(len(fordelingsparametre['teiger'])) + " teiger")
    for teig in fordelingsparametre['teiger']:
        
        fordeling['teig'] = {
            teig['teigId']: input('Hvilken kommune skal teigId ' + teig['teigId'] + ' tilhøre? ')
        }
    return fordeling

def save_json(data, filename):
    with open(filename, 'w', encoding='utf8') as f:
        json.dump(data, f, ensure_ascii=False, indent=4)


save_json({'transformasjoner': lagFordeling()}, 'output.json')

