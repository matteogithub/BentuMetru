# BentuMetru ⛵️

**BentuMetru NON è la solita app meteo.** Il suo scopo esclusivo è fornire un'indicazione sintetica e rapida sulla **qualità della propulsione a vela** incrociando i dati del vento e del moto ondoso. 

## ⚠️ Disclaimer e Sicurezza (LEGGERE ATTENTAMENTE)
Questa applicazione è fornita "così com'è" e il suo utilizzo è a totale rischio dell'utente. Si precisa che:
* **Nessuna funzione di salvaguardia:** L'app **NON** fornisce in alcun modo informazioni relative alla sicurezza in mare o alla navigazione sicura.
* **Obbligo di fonti ufficiali:** Prima di intraprendere qualsiasi uscita in mare, rimane **fondamentale e imprescindibile** leggere, confrontare e valutare con attenzione i bollettini meteorologici ufficiali diramati dalle autorità marittime competenti.
* **Responsabilità esclusiva:** La decisione finale di prendere il mare, così come la responsabilità per l'incolumità dell'imbarcazione e dell'equipaggio, ricade **sempre e comunque sul Comandante**.
* **Limiti dell'algoritmo:** L'attuale indice di valutazione non tiene in considerazione parametri marittimi essenziali quali:
  * Il tipo, la stazza e le caratteristiche dell'imbarcazione.
  * Lo scopo e la natura dell'uscita.
  * L'esperienza nautica, lo stato di salute e la composizione dell'equipaggio.

## Come funziona

Per la località scelta (ricerca testuale o posizione GPS), l'app scarica una previsione oraria fino a 72 ore e la traduce in un **semaforo a 4 colori** (🟢🟡🟠🔴) che sintetizza quanto quelle condizioni siano adatte alla propulsione a vela. I dati orari — vento (velocità, raffiche, direzione), stato del mare (altezza e periodo dell'onda), temperatura, probabilità di pioggia e rischio temporale — arrivano in tempo reale da [Open-Meteo](https://open-meteo.com/) (API meteo, API marine e API di geocoding, tutte gratuite e senza necessità di API key).

### Il semaforo delle condizioni

Per ogni ora di previsione la valutazione avviene in due fasi.

**1. Veti** — condizioni che escludono a priori la vela, indipendentemente da tutto il resto. Vengono controllati in quest'ordine (il primo che scatta vince ed è mostrato in chiaro nella card, es. *"Vento forte (> 18 nodi)"*):

1. **Bonaccia** — vento troppo debole per navigare.
2. **Vento forte** — vento sostenuto sopra la soglia del profilo attivo.
3. **Raffica pericolosa** — controllo indipendente dal vento medio: cattura le raffiche improvvise anche quando il vento sostenuto è nella norma.
4. **Onda enorme** — altezza dell'onda oltre la soglia del profilo.
5. **Onda troppo ripida** — un mare corto e "ripido" (poco periodo rispetto all'altezza) è più pericoloso di un'onda alta ma lunga della stessa altezza, quindi viene valutato a parte dalla sola altezza.
6. **Temporale** — segnalato dal codice meteo di Open-Meteo, a prescindere da vento e mare.

**2. Punteggio di comfort (0–100)** — se nessun veto scatta, l'app calcola un punteggio combinando due curve:

- *Comfort del vento*: pieno (100) in un range "ideale" di vento per il profilo scelto; scende gradualmente verso lo zero sia sotto (aria calma) sia sopra (troppo vento/raffiche, fino al punto esatto in cui scatterebbe il veto sulla raffica) tale range. Viene usato il valore più alto tra vento sostenuto e raffica, così una raffica forte penalizza il punteggio anche a vento medio moderato.
- *Comfort del mare*: basato sulla ripidità dell'onda (altezza/periodo²), non sulla sola altezza — un'onda lunga e dolce (mare formato/swell) resta confortevole anche se alta, mentre un mare corto e frastagliato (tipico di maestrale/libeccio sotto costa) penalizza il punteggio molto più rapidamente.

I due punteggi vengono moltiplicati tra loro e poi scontati fino a un massimo del -30% in base alla probabilità di pioggia (che di per sé non è mai un motivo di veto). Il punteggio finale determina il colore:

| Punteggio | Flag | Significato |
|---|---|---|
| ≥ 80 | 🟢 Verde | Condizioni ottimali |
| 60 – 79 | 🟡 Giallo | Condizioni discrete |
| 40 – 59 | 🟠 Arancione | Condizioni mediocri |
| < 40 | 🔴 Rosso | Condizioni sconsigliate |

Una flag rossa può quindi derivare da un veto esplicito (con motivo mostrato in card) oppure semplicemente da un punteggio di comfort basso, senza che nessun singolo parametro sia di per sé "estremo".

### Profili di navigazione

Le soglie che governano tutto quanto sopra dipendono dal **profilo** scelto in-app, pensato per adattare la valutazione all'esperienza dell'equipaggio e al tipo di barca. Cambiare profilo ricalcola all'istante il semaforo sui dati già scaricati, senza bisogno di nuove chiamate di rete.

| Soglia | 🟦 Prudente | 🟩 Crociera (default) | 🟥 Sportivo |
|---|---|---|---|
| Vento minimo (bonaccia sotto, nodi) | 4.0 | 3.5 | 3.0 |
| Vento massimo sostenuto (veto sopra, nodi) | 14.0 | 18.0 | 22.0 |
| Raffica massima (veto sopra, nodi) | 16.0 | 20.0 | 28.0 |
| Onda massima (veto sopra, m) | 0.8 | 1.5 | 2.0 |
| Ripidità massima dell'onda (veto sopra) | 0.06 | 0.08 | 0.12 |
| Range di vento ideale (nodi) | 6.0 – 12.0 | 6.0 – 16.0 | 5.0 – 20.0 |

Ogni soglia è uguale o più permissiva passando da Prudente a Sportivo, mai il contrario: più esperienza/prestazioni dichiarate → maggiore tolleranza su tutti i parametri.

* 🟦 **Prudente** — per famiglie, neofiti o uscite rilassanti. Soglie molto cautelative.
* 🟩 **Crociera** — per velisti medi e imbarcazioni da diporto. Equilibrio tra sicurezza e divertimento.
* 🟥 **Sportivo** — per esperti e barche performanti. Soglie alte per vento forte e condizioni impegnative.

## Funzionalità

* **Previsione oraria fino a 72 ore**, raggruppata per giorno, con vento, raffiche, direzione, onda (altezza e periodo), temperatura e probabilità di pioggia per ogni fascia oraria.
* **Mappa di overview** con un indicatore colorato secondo il semaforo corrente e una freccia che mostra la direzione del vento.
* **Ricerca località** o rilevamento della posizione GPS attuale (elaborata solo localmente, mai salvata).
* **Preferiti**: salva le località che consulti più spesso con un nome a tua scelta, per richiamarle in un tocco.
* **Cambio profilo istantaneo** (Prudente / Crociera / Sportivo), senza nuove chiamate di rete.
* **Condivisione rapida** della previsione corrente come testo, verso qualsiasi app di messaggistica.

## Caratteristiche Tecniche
* **Provider Dati:** Le informazioni orarie meteo-marine sono fornite in tempo reale tramite [Open-Meteo API](https://open-meteo.com/).
* **Privacy First:** I dati GPS (se autorizzati) vengono elaborati solo localmente dal dispositivo. Nessun dato personale o di posizione viene tracciato, memorizzato o condiviso. Consulta la nostra [Privacy Policy](PRIVACY_POLICY.md) per tutti i dettagli.
* **Stack:** App nativa Android scritta in Kotlin con Jetpack Compose.

## Installa l'App
L'applicazione è scaricabile e installabile direttamente da questo repository:
1. Vai alla pagina ufficiale delle **[Releases](https://github.com/matteogithub/BentuMetru/releases)**.
2. Scarica l'ultimo file `.apk` disponibile (es. `BentuMetru.apk`) cliccandoci sopra.
3. Apri il file appena scaricato sul tuo smartphone Android e autorizza l'installazione quando richiesto.

## Autore e Sviluppo
Architettura logica, design e formule matematiche ideati da Matteo Fraschini. L'implementazione del codice (Kotlin/Jetpack Compose) è stata realizzata con il supporto di strumenti di Intelligenza Artificiale (Gemini), costantemente revisionati, testati e validati dall'autore.
