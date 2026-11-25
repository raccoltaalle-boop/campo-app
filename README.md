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
I dati condivisi stanno su Firebase (Firestore). La configurazione va incollata in `index.html`,
nella costante `FIREBASE_CONFIG` in cima al primo `<script>`. Finché è `null` l'app funziona in
modalità prova, con i dati salvati solo sul dispositivo.

## Installarla sul telefono
Apri il link, poi dal menu del browser scegli "Aggiungi a schermata Home".
