# Kommunedelingsscript


Blander sammen fordelingsparametre og en fordelingsfil, og produserer en liste med transformasjoner som kan
limes rett inn i en regulering.

## Bruk

Scriptet trenger en fordelingsparametre.json-fil og en fordeling_input.json-fil. Fyll inn alle felter i fordeling_json
og kjør scriptet. Lim inn transformasjoner-listen i en eksisterende regulering i databasen, så kan reguleringen kjøres.

Man kan spesifisere hvor alle objektene fra fordelingsparametrene skal fordeles hvis man vil, men man trenger egentlig 
kun å spesifisere de som skal til kommune 2. Alle som ikke spesifiseres vil bli fordelt til kommune 1.

I Ålesund-Haram-tilfellet bør derfor Ålesund være kommune 1 og Haram kommune 2.

Fordelingsfil for Ålesund-Haram (som bare inneholder alt som skal til Haram) finnes i mappen "alesund_haram" (riktige 
fylkes- og kommunenumre må fylles inn).

De aller fleste objekter skal kun endre kommuneløpenummer, men kirkesogn skal også endre kretsnummer. Det blir håndtert
av scriptet hvis fordelingsparameteren for den kretsen har "nytt_kretsnummer" (se 
fordelingsparametre_eksempel.json). Scriptet håndterer også at veger kan fordeles til én eller flere kommuner, men det 
håndterer ikke splitting av veg. Splitting av veg må (foreløpig?) gjøres manuelt i reguleringen helt til slutt.
