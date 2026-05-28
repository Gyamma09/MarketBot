THOME Minearth - Build & Installazione
=======================================

REQUISITI
- Java 21
- Maven 3.8+
- Paper/Spigot 1.21.x

COMPILAZIONE
  mvn clean package
Il jar finale (con HikariCP shaded) sara' in:
  target/THOME-Minearth-1.0.0.jar

INSTALLAZIONE
1. Copia il jar nella cartella /plugins del server.
2. Avvia il server una volta per generare config.yml, messages.yml, gui.yml.
3. Stoppa, configura le credenziali MySQL in config.yml.
4. Riavvia.

DIPENDENZE OPZIONALI (soft)
- LuckPerms  -> limiti home dinamici via permesso thome.limit.<n>
- Vault      -> costi TP e acquisto slot (se assente, tutto gratis)
- CombatLogX / DeluxeCombat -> blocco TP in combat
- ViaVersion / ViaBackwards / Geyser -> supportati nativamente

PERMESSI
- thome.use            uso base (default: true)
- thome.admin          /thome reload + bypass cooldown/warmup
- thome.limit.<n>      limite home (es. thome.limit.10 = 10 home)
- thome.limit.*        home illimitate

COMANDI
- /home [nome]   GUI home oppure tp diretto
- /homes         apre la GUI
- /sethome <n>   imposta home
- /delhome <n>   elimina home
- /tpa <player>  apre GUI conferma TPA
- /tpaccept      accetta
- /tpdeny        rifiuta
- /tpatoggle     attiva/disattiva ricezione TPA
- /thome reload  ricarica le config
