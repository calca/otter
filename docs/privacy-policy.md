# Calm Otter — Privacy Policy

*Last updated: 2 October 2026*

**Short version: Calm Otter collects nothing. Everything stays on your phone.**

## What the app stores, and where

Everything Calm Otter stores lives **only on your device**, inside the app's
private storage:

- **Your pause history**: when each pause started, how long it lasted, how
  it ended, the first names of the people you paused with, the activity
  suggested for a shared pause, and your optional answer and short note
  after a pause.
- **Your settings**: theme, durations, the lists of apps that stay, scheduled
  pauses, and the other switches in Settings.
- **The password**, never in plain text: only a salted PBKDF2-HMAC-SHA256
  hash, encrypted with a key held by Android Keystore.

None of this is ever sent anywhere. Calm Otter does not have the
`INTERNET` permission: it **cannot** connect to the internet. There is no
account, no server, no analytics, no advertising, no crash reporting and no
third-party SDK that collects data (the QR scanner, ZXing, works entirely on
the device).

**Android backup.** If you have Android's own backup turned on, Android may
include the app's history and settings (never the password) in *your*
backup, in your Google account, like it does for other apps. That's
Android, under your control, not Calm Otter sending data: you can turn it
off in the phone's settings.

Uninstalling the app, or clearing its storage from Android settings,
deletes all of it. You can also clear History from inside the app. History
can be exported as a CSV file through Android's share menu: it goes only
where you choose to send it, by your own tap. The same goes for sharing a
finished pause or inviting a friend: the app prepares a short text with
the Play Store link, and you choose whether and where to send it.

## Permissions, and why

- **Accessibility service**: during a pause, to know which app is being
  opened so it can be covered with the pause screen. It does not read what
  is on the screen or what you type (`canRetrieveWindowContent` is off),
  and the information is used on the spot, never stored or sent. The app
  explains this and asks for your consent before you enable it.
- **Do Not Disturb access**: to silence notifications during a pause, and
  to restore your own settings afterwards.
- **Notifications**: the ongoing notification of a pause, the heads-up
  before a scheduled pause, and the optional Sunday note.
- **Camera** (only if you scan a QR to join a shared pause): the image is
  read on the device to decode the code and is not saved.
- **Bluetooth and NFC** (only for shared pauses): to let two phones in the
  same room find each other and agree on the start time. What they
  exchange is a device name, the duration, a start time and the suggested
  activity; the connection ends when the pause begins.
- **Run at startup**: to restore a pause in progress and re-arm scheduled
  pauses after the phone restarts.

## Links that leave the app

Settings contains links to the source code, the licence, the developer's
page and a feedback form on GitHub (the form is pre-filled with the app
version and phone model, which you can see and edit before sending). Tapping one opens your browser; from then on
you are on GitHub, under GitHub's own privacy policy.

## Children

Calm Otter is not directed at children under 13.

## Changes and contact

If this policy changes, the new version will be published at this same
address with a new date. Questions: open an issue at
<https://github.com/calca/otter/issues>.

---

# Calm Otter — Informativa sulla privacy

*Ultimo aggiornamento: 2 ottobre 2026*

**In breve: Calm Otter non raccoglie nulla. Tutto resta sul tuo telefono.**

## Cosa salva l'app, e dove

Tutto ciò che Calm Otter salva resta **solo sul tuo dispositivo**, nella
memoria privata dell'app:

- **La cronologia delle pause**: quando sono iniziate, quanto sono durate,
  come sono finite, i nomi delle persone con cui le hai fatte, l'attività
  proposta in una pausa di gruppo, e la risposta e la breve nota facoltative
  a fine pausa.
- **Le impostazioni**: tema, durate, liste delle app che restano, pause
  programmate e gli altri interruttori.
- **La password**, mai in chiaro: solo un hash PBKDF2-HMAC-SHA256 con sale,
  cifrato con una chiave custodita da Android Keystore.

Niente di tutto questo viene mai inviato. Calm Otter non ha il permesso
`INTERNET`: **non può** collegarsi a internet. Nessun account, nessun
server, nessuna analisi, pubblicità, segnalazione di errori o libreria di
terze parti che raccolga dati (il lettore QR, ZXing, lavora tutto sul
telefono).

**Backup di Android.** Se hai attivo il backup di Android, Android può
includere la cronologia e le impostazioni dell'app (mai la password) nel
*tuo* backup, nel tuo account Google, come fa per le altre app. È Android,
sotto il tuo controllo, non Calm Otter che invia dati: puoi disattivarlo
dalle impostazioni del telefono.

Disinstallare l'app, o cancellarne i dati dalle impostazioni di Android, li
elimina tutti. Puoi anche cancellare la Cronologia dall'app. La Cronologia
si può esportare in un file CSV dal menu di condivisione di Android: va solo
dove scegli tu di mandarla, con un tuo tocco. Lo stesso vale per condividere
una pausa finita o invitare un amico: l'app prepara un breve testo con il
link al Play Store, e sei tu a decidere se e dove mandarlo.

## Permessi, e perché

- **Servizio di accessibilità**: durante una pausa, per sapere quale app si
  sta aprendo e coprirla con la schermata della pausa. Non legge il
  contenuto dello schermo né quello che scrivi, e l'informazione si usa sul
  momento, senza salvarla né inviarla. L'app lo spiega e chiede il consenso
  prima dell'attivazione.
- **Accesso a Non disturbare**: per silenziare le notifiche durante una
  pausa e ripristinare le tue impostazioni alla fine.
- **Notifiche**: la notifica della pausa in corso, l'avviso prima di una
  pausa programmata e la nota facoltativa della domenica.
- **Fotocamera** (solo se scansioni un QR per unirti a una pausa di
  gruppo): l'immagine si legge sul telefono per decifrare il codice e non
  viene salvata.
- **Bluetooth e NFC** (solo per le pause di gruppo): per far trovare due
  telefoni nella stessa stanza e accordarsi sull'inizio. Si scambiano il
  nome del dispositivo, la durata, l'orario di inizio e l'attività
  proposta; la connessione si chiude quando la pausa comincia.
- **Avvio all'accensione**: per ripristinare una pausa in corso e le pause
  programmate dopo un riavvio.

## Link che escono dall'app

Nelle Impostazioni ci sono link al codice sorgente, alla licenza, alla
pagina dello sviluppatore e a un modulo di commento su GitHub (il modulo
arriva già compilato con versione dell'app e modello del telefono, che
vedi e puoi modificare prima di inviare). Toccarli apre il browser: da lì sei su
GitHub, con la sua informativa.

## Minori

Calm Otter non è rivolta a minori di 13 anni.

## Modifiche e contatti

Se questa informativa cambia, la nuova versione sarà pubblicata a questo
stesso indirizzo con una nuova data. Domande: apri una segnalazione su
<https://github.com/calca/otter/issues>.
