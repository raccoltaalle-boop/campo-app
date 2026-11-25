# Campo – innaffiatura

App web per gestire l'innaffiatura di campi, orti, giardini e terrazzi condivisa tra più persone.

**Apri l'app:** https://raccoltaalle-boop.github.io/campo-app/

- Mappa del terreno disegnata a mano, con le piante posizionate sopra.
- Stato di ogni pianta: quando è stata innaffiata, da chi, quando toccherà di nuovo.
- Consigli di frequenza e quantità per stagione, tipo di pianta ed età.
- Meteo (Open-Meteo): la pioggia caduta conta come innaffiatura, la pioggia prevista fa rimandare.
- Attività ricorrenti (concimare, potare…) e storico.

Ogni modifica pubblicata su `main` è subito online: chi apre l'app trova sempre l'ultima versione.

## Dati condivisi
I dati condivisi stanno su Firebase (progetto `campo-app-10f83`, Firestore in Europa, accesso anonimo).
nella costante `FIREBASE_CONFIG` in cima al primo `<script>`. Finché è `null` l'app funziona in
modalità prova, con i dati salvati solo sul dispositivo.

## Installarla sul telefono
Apri il link, poi dal menu del browser scegli "Aggiungi a schermata Home".

## App Android
**Scarica l'APK:** https://github.com/raccoltaalle-boop/campo-app/raw/dist/campo.apk

L'APK è un guscio che apre la versione pubblicata su GitHub Pages: le modifiche all'app
arrivano a tutti senza reinstallare. GitHub Actions ricompila l'APK solo quando cambia
la cartella `android/` (firma con i secrets `KEYSTORE_B64` e `KEYSTORE_PASS`).
