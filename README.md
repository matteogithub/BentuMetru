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

## Caratteristiche Tecniche
* **Indice di Qualità:** Valutazione calcolata su parametri combinati di velocità del vento, raffiche, altezza e periodo dell'onda.
* **Provider Dati:** Le informazioni orarie meteo-marine sono fornite in tempo reale tramite [Open-Meteo API](https://open-meteo.com/).
* **Privacy First:** I dati GPS (se autorizzati) vengono elaborati solo localmente dal dispositivo. Nessun dato personale o di posizione viene tracciato, memorizzato o condiviso. Consulta la nostra [Privacy Policy](PRIVACY_POLICY.md) per tutti i dettagli.

## Installa l'App
L'applicazione è scaricabile e installabile direttamente da questo repository:
1. Vai alla pagina ufficiale delle **[Releases](https://github.com/matteogithub/BentuMetru/releases)**.
2. Scarica l'ultimo file `.apk` disponibile (es. `BentuMetru.apk`) cliccandoci sopra.
3. Apri il file appena scaricato sul tuo smartphone Android e autorizza l'installazione quando richiesto.

## Autore e Sviluppo
Architettura logica, design e formule matematiche ideati da Matteo Fraschini. L'implementazione del codice (Kotlin/Jetpack Compose) è stata realizzata con il supporto di strumenti di Intelligenza Artificiale (Gemini), costantemente revisionati, testati e validati dall'autore.
