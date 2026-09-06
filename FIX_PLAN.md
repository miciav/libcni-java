# Piano di correzione dei finding

Data: 6 settembre 2026.

Questo documento pianifica gli interventi emersi dalla revisione del repository.
Non introduce modifiche al codice. La baseline è `./build.sh`: 82 test superati
su 82. Le prove aggiuntive della revisione hanno riprodotto i sei finding
funzionali sotto elencati; il problema del download delle dipendenze è stato
identificato per ispezione del codice.

## Obiettivi e perimetro

- Impedire che attachment distinti condividano accidentalmente la cache.
- Rifiutare risposte dei plugin non valide e mantenere errori pubblici coerenti.
- Preservare i dati previsti dalle versioni CNI dichiarate come supportate.
- Consentire di limitare e interrompere l'esecuzione dei plugin.
- Rendere affidabile il download delle dipendenze e semplificare i percorsi toccati.

Restano fuori perimetro le funzionalità già escluse dal README: GC/STATUS,
risultati 0.1.0/0.2.0, negoziazione `cniVersions` e implementazione completa del
formato cache upstream. Le modifiche devono mantenere le API esistenti quando
possibile; eventuali nuove opzioni devono essere additive.

## Ordine di esecuzione

| Fase | Interventi | Motivazione |
| --- | --- | --- |
| 1 | F2 parsing delle risposte, F6 errori pubblici | Definire un confine di decodifica affidabile prima di modificare i risultati. |
| 2 | F3 dati delle interfacce, F4 conversioni | Rendere coerenti modello, serializzazione, chaining e cache. |
| 3 | F1 identità della cache | Correggere la collisione con una strategia esplicita per i file esistenti. |
| 4 | F5 timeout e cancellazione | Correggere il ciclo di vita dei processi e verificarne la terminazione. |
| 5 | F7 download e semplificazioni residue | Completare l'affidabilità della build e rimuovere duplicazioni. |

Ogni fase deve poter essere revisionata separatamente. Le priorità esprimono
l'impatto, mentre l'ordine tiene conto delle dipendenze tecniche.

## F1 — Collisioni nella cache

**Priorità: alta.** Codice: [CNIConfig.java](src/main/java/io/libcni/CNIConfig.java),
`getCacheFilePath`, `cacheAdd`, `getCachedResult`, `cacheDel`.

La concatenazione `rete-container-interfaccia` non è univoca: gli attachment
`(a-b, c, eth0)` e `(a, b-c, eth0)` producono lo stesso file. Un ADD può
sovrascrivere il risultato altrui e un DEL può cancellarlo.

### Intervento

1. Introdurre una chiave derivata da una rappresentazione non ambigua della
   tripla, ad esempio SHA-256 di un array JSON di tre stringhe UTF-8.
2. Separare il nuovo formato in una directory dedicata, per esempio `results-v2`.
   Conservare nel documento cache anche rete, container e interfaccia e
   verificarli in lettura: non basta affidarsi al nome del file.
3. Centralizzare costruzione della chiave e verifica dell'identità, condividendole
   fra ADD, CHECK, DEL e getter pubblici.
4. Scrivere tramite file temporaneo nella stessa directory e sostituzione atomica
   dove supportata. Definire esplicitamente il comportamento se il filesystem
   non supporta lo spostamento atomico.

### Compatibilità

I file legacy contengono soltanto il risultato: non è possibile ricostruire con
certezza l'identità dell'attachment. Un fallback automatico a quei file
reintrodurrebbe la collisione.

La strategia proposta è leggere e scrivere per default soltanto il formato v2,
senza cancellare automaticamente i file legacy. Documentare nel README che
l'aggiornamento richiede di drenare e ricreare gli attachment con la versione
appropriata del runtime, così che CHECK e DEL non perdano il risultato originale.
Se occorre una migrazione senza drenaggio, realizzare prima uno strumento che
riceva un inventario autorevole degli attachment e rifiuti le associazioni ambigue.
Non tentare una migrazione basata soltanto sui nomi dei file.

### Verifica e accettazione

- Riprodurre la coppia in collisione e verificare risultati separati dopo due ADD.
- Verificare che DEL di uno non modifichi il risultato dell'altro.
- Rifiutare una voce il cui contenuto dichiari un'identità diversa da quella richiesta.
- Verificare che le protezioni dai separatori di percorso restino efficaci.
- Verificare la politica legacy e che i vecchi file non vengano cancellati.
- Simulare una scrittura fallita: una voce valida precedente deve restare leggibile.

## F2 — Risposte non valide accettate come successo

**Priorità: alta.** Codice: [Invoke.java](src/main/java/io/libcni/invoke/Invoke.java),
`fixupResultVersion` ed `execPluginWithResult`.

Stdout vuoto, `[]`, `42` e `null` diventano un risultato contenente soltanto
`cniVersion`. La catena può proseguire dopo un ADD che non ha restituito un
risultato valido.

### Intervento

1. Richiedere un oggetto JSON come risposta e rifiutare input vuoto, soli spazi,
   valori primitivi, array, `null` e JSON malformato.
2. Applicare il recupero della versione dalla configurazione solo dopo aver
   verificato la struttura della risposta. Definire e testare separatamente i
   casi versione assente, vuota, nulla e di tipo errato, confrontandoli con il
   comportamento upstream prima di alterare la compatibilità.
3. Propagare un `CniError` di decodifica con contesto del plugin e causa originale.
4. Conservare il supporto ai risultati che omettono sezioni non pertinenti;
   non rendere obbligatori indiscriminatamente IP, route o interfacce.

### Verifica e accettazione

- Test parametrizzati per le risposte non valide indicate sopra.
- Test per oggetti validi con versione esplicita e per il recupero della versione.
- In una catena, se il primo plugin restituisce una risposta non valida, il
   successivo non deve essere invocato e non deve essere scritta una nuova cache.

## F3 — Perdita dei campi delle interfacce

**Priorità: alta.** Codice: [Interface.java](src/main/java/io/libcni/types/Interface.java),
[CurrentResult.java](src/main/java/io/libcni/types/CurrentResult.java) e serializzazione
di `prevResult` in [CNIConfig.java](src/main/java/io/libcni/CNIConfig.java).

`mtu`, `socketPath` e `pciID` vengono eliminati durante il parsing, perché assenti
dal modello Java.

### Intervento

1. Aggiungere i campi con nomi JSON e tipi coerenti con lo schema CNI supportato.
   Per MTU usare un tipo numerico opzionale con un intervallo documentato e validato.
2. Aggiornare `copy()` e la serializzazione del risultato.
3. Stabilire insieme a F4 quali campi preservare o omettere quando si converte
   verso una versione precedente; confrontare schema e convertitori upstream.
4. Verificare il JSON effettivamente inviato ai plugin: `injectConf()` usa
   `Gson.toJsonTree`, quindi una correzione limitata a `toJsonString()` non basta.

### Verifica e accettazione

- Un risultato 1.1.0 mantiene i tre campi dopo parsing e riserializzazione.
- I campi arrivano al secondo plugin tramite `prevResult`.
- Restano presenti dopo scrittura e lettura della cache e nei successivi CHECK/DEL.
- L'assenza dei campi opzionali non introduce valori artificiali nel JSON.

## F4 — Conversioni dei risultati incomplete

**Priorità: media.** Codice: [CurrentResult.java](src/main/java/io/libcni/types/CurrentResult.java),
[IPConfig.java](src/main/java/io/libcni/types/IPConfig.java),
[ResultFactory.java](src/main/java/io/libcni/types/ResultFactory.java).

Cambiare soltanto `cniVersion` non converte lo schema: passando da 1.x a 0.4.0
manca `ips[].version`; nella direzione opposta il campo viene conservato.

### Intervento

1. Distinguere esplicitamente le famiglie 0.3.x/0.4.0 e 1.0.0/1.1.0 nei
   convertitori e nella serializzazione, mantenendo se possibile l'API `Result`.
2. Per il downgrade ricavare `"4"` o `"6"` dall'indirizzo IP; rifiutare indirizzi
   non validi senza effettuare risoluzioni DNS.
3. Per l'upgrade omettere il campo `version` delle configurazioni IP nel JSON.
4. Centralizzare la rappresentazione JSON del risultato e usarla sia nella cache
   sia nell'iniezione di `prevResult`, evitando percorsi di serializzazione divergenti.
5. Verificare la versione dichiarata nel risultato rispetto al decoder selezionato
   e impedire che risultati con versioni non supportate passino senza validazione.
6. Correggere i commenti che dichiarano identica la struttura di tutte le versioni.

### Verifica e accettazione

- Matrice parametrizzata fra tutte le versioni dichiarate supportate.
- Fixture IPv4 e IPv6: downgrade con famiglia corretta, upgrade senza `version`.
- Verificare il JSON, non soltanto il campo `cniVersion` dell'oggetto Java.
- Verificare che una conversione non modifichi il risultato di partenza.
- Verificare conversione e chaining anche in presenza dei campi introdotti da F3.

## F5 — Esecuzione bloccata e cancellazione inefficace

**Priorità: alta.** Codice: [RawExec.java](src/main/java/io/libcni/invoke/RawExec.java)
e configurazione tramite [DefaultExec.java](src/main/java/io/libcni/invoke/DefaultExec.java).

La lettura sincrona di stdout può bloccare prima di `waitFor()`: interrompere il
thread chiamante non garantisce il raggiungimento del `finally` che termina il processo.

### Intervento

1. Spostare anche la lettura di stdout su un task dedicato, mantenendo separati
   stdout e stderr ed evitando deadlock con la scrittura di stdin.
2. Far attendere il processo al thread chiamante tramite un'operazione interrompibile.
3. Aggiungere un timeout configurabile con API additive. Conservare inizialmente
   l'assenza di scadenza nei costruttori esistenti per non imporre un limite arbitrario;
   l'interruzione deve comunque funzionare. Documentare come impostare un limite.
4. Applicare un'unica scadenza all'invocazione, inclusi i retry e il completamento
   dei task I/O, evitando attese senza limite dopo l'uscita del processo.
5. Su timeout o interruzione, terminare il processo, gestire i discendenti ancora
   individuabili e chiudere gli stream; attendere la pulizia entro un limite definito.
   Documentare il limite della gestione dei processi che si scollegano dal genitore.
6. Conservare lo stato di interruzione del chiamante e usare errori coerenti con F6.
   Raccogliere gli errori dei task I/O; distinguere il broken pipe atteso quando
   il plugin esce anticipatamente dagli altri errori di trasporto.

### Verifica e accettazione

- Un plugin che non termina viene fermato entro il timeout più un margine di cleanup.
- L'interruzione libera il chiamante anche durante una lettura di stdout bloccata.
- Nessun task I/O o processo di prova resta attivo dopo il cleanup.
- Output abbondante su entrambi gli stream e stdin voluminoso non causano deadlock.
- Un plugin che esce senza leggere stdin conserva il proprio risultato o errore.
- Test sincronizzati con segnali di avvio e attese limitate, evitando di affidarsi
   esclusivamente a pause temporali per determinare lo stato del processo.

## F6 — Eccezioni di parsing incoerenti

**Priorità: media.** Codice: [ConfigLoader.java](src/main/java/io/libcni/ConfigLoader.java),
[PluginDecoder.java](src/main/java/io/libcni/version/PluginDecoder.java),
[ResultFactory.java](src/main/java/io/libcni/types/ResultFactory.java),
[CniError.java](src/main/java/io/libcni/types/CniError.java).

`networkPluginConfFromBytes("{")` propaga `JsonSyntaxException` nonostante il
contratto documentato preveda `CniError`. I wrapper che intercettano soltanto
`CniError` non aggiungono il contesto previsto.

### Intervento

1. Tradurre gli errori JSON ai confini pubblici: configurazione non valida per
   i config loader, errore di decodifica per risposte e versioni dei plugin.
2. Aggiungere un costruttore di `CniError` con causa, mantenendo quello attuale.
3. Conservare indice del plugin, operazione e percorso del file dove disponibili.
4. Intercettare le eccezioni di parsing pertinenti; evitare catch generici che
   trasformino anche errori di programmazione in errori dell'input.
5. Condividere solo le piccole primitive di parsing realmente comuni, mantenendo
   distinti messaggi e codici richiesti dai diversi ingressi.

### Verifica e accettazione

- JSON malformato e campi di tipo errato producono il `CniError` atteso nei
   loader, nel decoder VERSION e nel parser dei risultati.
- Una configurazione plugin non valida dentro una lista conserva l'indice.
- La causa originale resta accessibile tramite `getCause()`.
- I codici degli errori già restituiti correttamente dai plugin non vengono alterati.

## F7 — Download delle dipendenze non affidabile

**Priorità: media.** Codice: [build.sh](build.sh), funzione `fetch`.

Il download non fallisce sugli errori HTTP e scrive direttamente sul file
definitivo. Una pagina di errore o un trasferimento incompleto possono essere
riutilizzati come JAR nelle build successive.

### Intervento

1. Usare `curl --fail --show-error --location` mantenendo un timeout esplicito.
2. Scaricare su un file temporaneo nella directory di destinazione e rinominarlo
   soltanto dopo aver verificato successo e integrità del JAR.
3. Rimuovere soltanto il temporaneo della singola operazione in caso di errore.
4. Verificare anche i file già presenti; non saltare un file corrotto soltanto
   perché esiste. Un checksum fissato per ogni dipendenza può fornire una
   verifica più forte del semplice controllo della struttura dell'archivio.

### Verifica e accettazione

Con una prova isolata della funzione di download, simulare HTTP 404/500,
trasferimento interrotto, file preesistente corrotto e download riuscito.
Gli errori devono produrre exit code non zero senza pubblicare un JAR incompleto;
una successiva esecuzione deve poter riuscire. La prova non deve dipendere da
malfunzionamenti reali di Maven Central né alterare i JAR usati dal repository.

## Semplificazioni da integrare

| Modifica | Collocazione | Vincolo |
| --- | --- | --- |
| Inizializzare `exec` nel costruttore e renderlo `final`; eliminare `ensureExec()` | `CNIConfig` | Preservare il comportamento del parametro `exec == null`. |
| Preparare configurazione e `runtimeConfig` in un unico passaggio JSON | `buildOneConfig`, `injectRuntimeConfig` | Conservare i campi sconosciuti e il filtro delle capability. |
| Aggregare le capability con `LinkedHashSet` | `validateNetworkList` | Preservare ordine, deduplicazione e tipo di ritorno pubblico. |
| Riutilizzare Gson con configurazione coerente | Codec di configurazioni, risultati ed errori | Non bypassare i convertitori di risultato introdotti da F4. |

Integrare queste modifiche nelle fasi che toccano già il relativo codice, oppure
in una revisione finale separata. Non introdurre nuove gerarchie o framework
per eliminare poche righe di duplicazione.

## Verifica finale e consegna

1. Eseguire i test mirati dopo ogni fase e `./build.sh` dopo l'integrazione.
2. Eseguire una compilazione in una directory di output nuova per escludere che
   classi residue mascherino problemi della build.
3. Aggiungere fixture di protocollo conformi alle versioni supportate. Dove
   disponibile, eseguire anche una prova con un plugin CNI reale senza modifiche
   alla rete host; le prove privilegiate vanno isolate in un ambiente dedicato.
4. Aggiornare README e Javadoc per cache v2, transizione dagli attachment legacy,
   timeout, semantica degli errori e conversioni effettivamente supportate.
5. Riportare test eseguiti, eventuali prove di interoperabilità non effettuate e
   limitazioni residue. Il superamento dei test con plugin shell simulati non
   deve essere presentato come verifica completa con plugin CNI reali.

Il lavoro è completo quando ogni finding ha una correzione verificata, i test
esistenti restano verdi e i cambiamenti di compatibilità sono documentati.

## Riferimenti di protocollo

- [Specifica CNI, risultati ADD](https://github.com/containernetworking/cni/blob/main/SPEC.md#add-success).
- [Modello e convertitori upstream 1.x](https://github.com/containernetworking/cni/blob/main/pkg/types/100/types.go).
- [Modello upstream 0.4.0](https://github.com/containernetworking/cni/blob/main/pkg/types/040/types.go).

I link upstream puntano a `main`: durante l'implementazione fissare il commit
di riferimento per rendere riproducibili le decisioni di compatibilità.
