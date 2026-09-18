package com.calmotter.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Contenitore delle due schermate che disegnano l'otter — la Home
 * ([MainScreen]) e la schermata di blocco ([BlockScreen]) — con l'unico
 * scopo di **mettere l'otter esattamente nello stesso punto in entrambe**,
 * così che la transizione a elemento condiviso fra le due non abbia nulla da
 * animare e l'otter resti immobile mentre il resto sfuma (vedi MainActivity).
 *
 * ## Perché un contenitore condiviso e non due layout che "coincidono"
 *
 * Prima ogni schermata calcolava quella posizione per conto suo: la Home come
 * padding + intestazione, la schermata di blocco come uno `Spacer` di pari
 * altezza messo lì a mano. Due formule diverse che dovevano dare lo stesso
 * numero — e che infatti hanno smesso di darlo due volte, sempre per una
 * modifica fatta altrove e in perfetta buona fede (un refactor dello scroll,
 * un `Arrangement` cambiato). Ogni volta il sintomo era lo stesso: l'otter
 * che scivola all'avvio della pausa.
 *
 * Qui la posizione è calcolata **una volta sola**, da questo file, per
 * entrambe le schermate: non è più un'invariante da ricordarsi di mantenere,
 * è una cosa che le due schermate non hanno proprio modo di far divergere.
 *
 * ## Dove finisce l'otter
 *
 * Non esattamente al centro geometrico del viewport — un po' più in alto,
 * di [OtterBelowReserveHeight] / 2 — perché "centrare lo slot" e "far
 * *sembrare* centrata la composizione" non sono la stessa cosa: sopra
 * l'otter c'è solo [headerHeight] (spesso 0) più spazio vuoto, sotto c'è
 * sempre del contenuto vero (chip e riepilogo in Home, frase e avatar in
 * blocco). Con lo slot esattamente a metà, quel contenuto reale sotto
 * l'otter e il vuoto sopra non si bilanciano: il centro *percepito* — dove
 * l'occhio vede effettivamente qualcosa, non dove il layout mette il
 * centro dello slot — cade più in basso del centro dello schermo, segnalato
 * come "il contenuto è troppo in basso". [OtterBelowReserveHeight] è una
 * stima fissa, uguale per entrambe le schermate nonostante i loro
 * contenuti sotto l'otter abbiano altezze reali diverse, di quanto sotto
 * l'otter pesa in media — non è misurata dal `below` reale (che differisce
 * fra le due schermate e *quindi* non può entrare in questo calcolo, vedi
 * sotto), quindi resta identica sulle due schermate e non introduce alcuna
 * divergenza fra loro.
 *
 * `gapAboveOtter` è quindi quello che serve perché lo slot alto
 * [OtterSlotHeight] cada a metà **meno la metà di [OtterBelowReserveHeight]**,
 * e [headerHeight] viene sottratto proprio perché l'intestazione della Home
 * (che la schermata di blocco non ha) non sposti di un pixel ciò che viene
 * dopo. La posizione dipende quindi **solo dall'altezza dello schermo**, che
 * è identica nelle due schermate per definizione — non dal contenuto sopra
 * né da quello sotto, che sono diversi (Home: riga sessioni e "Tempo
 * insieme"; blocco: conto alla rovescia, frase, azioni) e la cui altezza
 * reale non compare in nessun calcolo qui, per lo stesso motivo per cui non
 * ci compare quella dell'intestazione: farla entrare (invece di una
 * costante fissa uguale per entrambe) è esattamente il tipo di modifica che
 * ha già fatto scivolare l'otter due volte, vedi sotto.
 *
 * [headerHeight] è un'altezza *fissa*: l'intestazione ci sta dentro, non se
 * la contratta. È deliberato — se potesse crescere (carattere di sistema
 * ingrandito) tornerebbe a spingere giù l'otter nella sola Home, cioè
 * esattamente il bug che questo contenitore esiste per rendere impossibile.
 * [OtterBelowReserveHeight] è fissa per lo stesso motivo.
 *
 * ## I due casi limite, dichiarati
 *
 * - **Contenuto più alto del viewport** (carattere grande, schermo corto):
 *   la colonna cresce e scorre invece di tagliare via il contenuto in
 *   silenzio. Appena si scorre, però, l'otter si stacca dal centro: ancorare
 *   e scorrere non possono valere insieme, e fra un otter fermo e del
 *   contenuto irraggiungibile vince il contenuto.
 * - **Viewport così basso che `gapAboveOtter` va a zero** (schermo molto
 *   corto, o orizzontale): si degrada a una normale colonna impilata
 *   dall'alto. Le due schermate si disallineano di [headerHeight], perché
 *   solo la Home ce l'ha. Su un telefono in verticale non succede — il gap
 *   sta sulle ~160dp anche dopo aver tolto metà di
 *   [OtterBelowReserveHeight], l'intestazione ne occupa 96 — ma è bene
 *   sapere da dove arriverebbe, se dovesse ricapitare.
 */
@Composable
fun OtterAnchoredScreen(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    headerHeight: Dp = 0.dp,
    // Livello decorativo a piena pagina, disegnato per primo (quindi sotto
    // tutto il resto) e ricevuto già insieme al centro Y dell'otter — vedi
    // [AmbientRipples] in MainScreen.kt, l'unico chiamante: le onde partono
    // dallo stesso punto fisso in cui questo contenitore mette l'otter,
    // invece che dal centro del proprio, piccolo riquadro. Non è
    // sessionActive-gated qui: quella decisione resta a chi lo passa
    // (BlockScreen non passa nulla, quindi resta vuoto di default).
    background: @Composable BoxScope.(otterCenterY: Dp) -> Unit = {},
    header: @Composable BoxScope.() -> Unit = {},
    otter: @Composable BoxScope.() -> Unit,
    below: @Composable ColumnScope.() -> Unit,
) {
    // calmBackground sul Box esterno a schermo pieno, non sulla colonna: il
    // velo di colore deve passare sotto le barre di sistema (edge-to-edge
    // imposto da targetSdk 37).
    Box(modifier = modifier.fillMaxSize().calmBackground()) {
        // BoxWithConstraints *dentro* safeDrawingPadding: misurato fuori,
        // maxHeight conterebbe anche le barre di sistema, e sia il centro
        // calcolato qui sotto sia heightIn(min) risulterebbero sbagliati
        // esattamente di quegli inset — vedi [CalmScreenColumn].
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val gapAboveOtter = (
                (maxHeight - OtterSlotHeight - OtterBelowReserveHeight) / 2 - headerHeight
                ).coerceAtLeast(0.dp)
            val otterCenterY = headerHeight + gapAboveOtter + OtterSlotHeight / 2

            Box(modifier = Modifier.fillMaxSize()) { background(otterCenterY) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    // Solo orizzontale: un padding verticale qui falserebbe il
                    // centro calcolato sopra. Lo spazio in alto lo dà
                    // [headerHeight], quello in basso se lo gestisce [below].
                    .padding(horizontal = horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(headerHeight),
                    content = header,
                )
                Spacer(modifier = Modifier.height(gapAboveOtter))
                Box(
                    modifier = Modifier.fillMaxWidth().height(OtterSlotHeight),
                    contentAlignment = Alignment.Center,
                    content = otter,
                )
                below()
            }
        }
    }
}

/**
 * Stima fissa e condivisa di quanto pesa, visivamente, il contenuto sotto
 * l'otter — vedi il commento di classe di [OtterAnchoredScreen], sezione
 * "Dove finisce l'otter". Le due schermate vorrebbero valori diversi (Home,
 * che ha un'intestazione a mangiarsi parte del vuoto sopra, tornerebbe
 * bilanciata già a ~35dp; il blocco, senza intestazione, vorrebbe qualcosa
 * fra ~70dp e ~110dp a seconda che la frase pescata vada su una riga o due)
 * — misurato sul dispositivo scattando screenshot e confrontando quanto
 * spazio vuoto resta sopra l'otter contro quanto ne resta sotto l'ultimo
 * elemento del `below` di ciascuna schermata. Questo valore è una via di
 * mezzo pesata verso Home (schermata vista per prima, ad ogni apertura
 * dell'app) piuttosto che il punto di minimo sbilanciamento assoluto:
 * quest'ultimo (~65dp) lascia Home leggermente spinta in alto, un errore
 * nella direzione opposta a quella segnalata — meglio restare un po' corti
 * sul blocco (che comunque migliora, e non di poco, rispetto a 0) che
 * introdurre un nuovo "troppo in alto" proprio dove l'errore originale era
 * stato notato.
 */
private val OtterBelowReserveHeight = 295.dp
