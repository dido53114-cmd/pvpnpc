# PvpNpc — PNJ de teleportation reseau + arenes PvP avec regen

## Ce que fait ce plugin

- Un **PNJ** (villageois fige) que tu places ou tu veux : clic droit dessus = le joueur est envoye
  vers un **autre serveur** de ton reseau (via BungeeCord/Velocity).
- Sur le serveur PvP, les joueurs qui arrivent rejoignent automatiquement une **file d'attente**
  avec un son en boucle et un message dans la barre d'action.
- Des que 2 joueurs sont en attente et qu'une arene est libre : **teleportation** dans l'arene,
  **decompte de 5 secondes** (titre + son), pendant lequel les joueurs sont invulnerables et ne
  peuvent pas se blesser.
- Une fois le decompte fini : le PvP est autorise **seulement entre les 2 joueurs du combat**, ce
  qui permet a plusieurs combats de tourner **en meme temps dans le meme monde** sans se gener.
- A la mort d'un joueur, **son stuff tombe au sol** dans l'arene et il est renvoye immediatement.
  Le **gagnant reste sur place** pour looter, avec **5 minutes** (configurable) avant d'etre
  teleporte automatiquement — s'il part avant (ex: `/spawn`), il est libere immediatement et
  l'arene est **restauree bloc par bloc** a ce moment-la seulement (donc le loot n'est jamais
  detruit sous ses pieds).
- **Zone qui se retrecit** : si un combat traine (5 minutes par defaut), une zone commence a se
  refermer sur le centre de l'arene ; rester hors de cette zone inflige des degats progressifs,
  ce qui force la fin du combat au lieu qu'il dure indefiniment.
- **Anti-fuite** : impossible d'utiliser `/server`, `/hub`, `/tpa`, etc. pendant un combat actif, et
  si un joueur se deconnecte en plein combat, il perd automatiquement.

## Important : ce que ca necessite

Ce plugin **ne peut pas** a lui seul faire voyager un joueur entre deux serveurs independants.
Il faut que tes deux serveurs (le "hub" et le "pvp") soient relies par un **proxy BungeeCord ou
Velocity** (en mode "legacy forwarding" compatible BungeeCord). C'est la maniere standard dont
fonctionnent tous les reseaux du type Hypixel/DonutSMP.

Si tu n'as pas encore de proxy : installe BungeeCord ou Velocity, mets `bungeecord: true` (ou
l'equivalent Velocity) dans le `spigot.yml`/`paper-global.yml` de **chacun** de tes deux serveurs,
et declare-les dans la config du proxy avec un nom (ex: `hub` et `pvp`).

## Installation

### Option A — compiler via GitHub Actions (aucune installation necessaire)

Ce projet contient deja un fichier `.github/workflows/build.yml` qui compile automatiquement
le plugin des que le code est pousse sur GitHub. Marche a suivre :

1. Cree un compte GitHub gratuit (github.com) si tu n'en as pas.
2. Cree un nouveau depot (bouton vert "New").
3. Sur la page du depot vide, clique sur "uploading an existing file" (ou "Add file" > "Upload files")
   et glisse-depose tout le contenu de ce dossier `pvpnpc` (y compris `pom.xml`, `src/` et `.github/`).
4. Valide ("Commit changes"). Ca declenche automatiquement la compilation.
5. Va dans l'onglet "Actions" du depot, clique sur le run le plus recent, puis en bas de la page
   sur l'artefact "PvpNpc-jar" pour le telecharger (c'est un .zip contenant le .jar).
6. Deballe le zip, tu obtiens `PvpNpc.jar` : c'est le fichier a mettre dans `plugins/`.

### Option B — compiler toi-meme (si tu as Java 17+ et Maven installes)

1. Compile avec :
   ```
   mvn package
   ```
2. Le fichier `target/PvpNpc.jar` est genere.

### Une fois le .jar obtenu (les deux options)

1. Mets le fichier `PvpNpc.jar` dans le dossier `plugins/` **des deux serveurs** (hub et pvp),
   via le gestionnaire de fichiers de ton panel (bouton "Envoyer").
2. Redemarre les deux serveurs pour generer le `config.yml`.

## Configuration

Dans `plugins/PvpNpc/config.yml` :

- Sur le **serveur hub** : `server-role: HUB`, `npc-target-server: "pvp"` (le nom du serveur PvP
  tel que declare dans le proxy).
- Sur le **serveur pvp** : `server-role: PVP`, `return-server: "hub"` (nom du hub cote proxy, ou
  laisse vide pour rester sur le meme serveur).

## Mise en place en jeu

Sur le **serveur PvP** (`server-role: PVP`), pour chaque zone de combat :

1. Va au premier coin de la zone -> `/arenapvp pos1`
2. Va au coin oppose -> `/arenapvp pos2`
3. Va au point ou le joueur 1 doit apparaitre -> `/arenapvp spawn1`
4. Va au point ou le joueur 2 doit apparaitre -> `/arenapvp spawn2`
5. `/arenapvp create <nom>` — l'arene est creee et sauvegardee.

Repete l'operation pour creer plusieurs arenes (plusieurs combats simultanes possibles).

Sur le **serveur hub**, place-toi ou tu veux le PNJ puis :
```
/npcpvp create pvp
```
(remplace `pvp` par le nom exact de ton serveur PvP cote proxy si different).

## Commandes

- `/arenapvp pos1|pos2|spawn1|spawn2|create <nom>|remove <nom>|list`
- `/npcpvp create <serveur-cible>|remove`

Permission requise : `pvpnpc.admin` (OP par defaut).

## Reglages de la phase de loot et de la zone qui se retrecit

Dans `config.yml` :

```yaml
fight-timer:
  shrink-after-seconds: 300     # delai avant que la zone commence a se retrecir
  shrink-duration-seconds: 60   # temps pour atteindre le rayon minimum
  min-radius: 3                 # rayon minimum autour du centre de l'arene
  damage-per-tick: 2.0          # degats infliges hors zone a chaque intervalle
  tick-interval-seconds: 1

loot:
  grace-period-seconds: 300     # temps laisse au gagnant pour looter avant renvoi automatique
```

## Limites connues / pistes d'amelioration

- La zone qui se retrecit est un cercle simple centre sur l'arene (pas un vrai mur visuel type
  Battle Royale) : les joueurs sont juste avertis et prennent des degats hors zone. Je peux ajouter
  un effet visuel (particules) si tu veux que ce soit plus lisible en jeu.
- Le son de la file d'attente utilise les sons vanilla (pas de vraie musique .ogg). Pour une vraie
  musique custom, il faut un resource pack avec un `sounds.json` et jouer ce son personnalise.
- La restauration de blocs est faite en RAM et par lots (config `restore.blocks-per-tick`) : pour
  des arenes tres grandes (>50x50x50), prevoir un peu de delai de restauration.
- Le matchmaking est simple (1er arrive, 1er servi, 1v1). Facile a etendre pour des equipes ou un
  systeme de classement si besoin.
- Si tu utilises deja Citizens pour tes PNJ, je peux adapter le plugin pour s'appuyer dessus plutot
  que sur un Villageois brut.
