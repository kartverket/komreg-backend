# Kommunedelingsscript

## TODO:

* Fiks kommuneløpenummer i veg-objekter i output. "fra" skal ikke ha en liste, men "til" skal ha det.
* Scriptet skal kunne endre kretsnummer på kretser av type kirkesogn. (Hvis de har feltet "nytt_kretsnummer" i
fordelingsparametre, skal kretsnummer endres.

## Bruk

Scriptet trenger en input.json som inneholder fordelingsparametrene til den gamle kommunen.
Det trenger også en fordeling_input.json som sier til hvilken kommune hvert objekt skal til (default er kommune 1,
så man trenger egentlig ikke spesifisere mer enn det som skal til kommune 2)