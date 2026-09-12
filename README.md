# ChorusCore

A modular core for Paper servers: homes, warps, spawn, teleport requests, an economy of its
own, mail, private messages, sign shops, staff tools, item tools, kits, world controls and the
usual utilities. A hundred and thirty-one commands across fourteen modules, every one of them
switchable, priced and themed from its own config file, plus as many commands of your own as
you care to write.

**Coming from EssentialsX?** `/chorus import essentials` brings your homes, balances, warps,
mail, nicknames and sell prices across without touching a file in the old plugin.


Compiled against the Paper 1.18.2 API and emitting Java 17 bytecode, so a single jar runs on
anything from **1.18.2 to 26.2**, on Paper and on Folia.

Addons can build on it: it registers a public API as a Bukkit service and, when
PlaceholderAPI is installed, exposes its numbers as placeholders.

## Installing

1. Drop the jar into the server's `plugins/` folder.
2. Start the server. The config files below are created.
3. The first start needs internet access: the server fetches HikariCP, the SQLite driver and
   the MariaDB driver from Maven Central into its own `libraries/` folder.

ChorusCore keeps its own balances, so /pay and /balance work on a bare server. Vault is
optional: with it installed the same money is offered to every other plugin, and another
economy plugin can take over instead. PlaceholderAPI is optional too, and the expansion
registers itself when it is there.

The console says what it found:

```
  ░█████╗░██╗░░██╗░█████╗░██████╗░██╗░░░██╗░██████╗
  ██╔══██╗██║░░██║██╔══██╗██╔══██╗██║░░░██║██╔════╝
  ██║░░╚═╝███████║██║░░██║██████╔╝██║░░░██║╚█████╗░
  ██║░░██╗██╔══██║██║░░██║██╔══██╗██║░░░██║░╚═══██╗
  ╚█████╔╝██║░░██║╚█████╔╝██║░░██║╚██████╔╝██████╔╝
  ░╚════╝░╚═╝░░╚═╝░╚════╝░╚═╝░░╚═╝░╚═════╝░╚═════╝░
  ░█████╗░░█████╗░██████╗░███████╗
  ...

  ChorusCore 0.1.0  ·  Paper 1.21.4
  ✔ Storage        sqlite
  ✔ Economy        built in, 412 accounts
  ▪ Placeholders   PlaceholderAPI not installed
  ✔ Scheduling     one server thread
  ✔ Modules        14 of 14
  ✔ Commands       131 registered

  Ready in 214ms

  Thank you for using this plugin!
  * zTaivann
```

A tick is something it connected to. `startup-banner: false` in `config.yml` replaces the
whole thing with one line.

On Folia the Scheduling line reads `Folia regions` instead. Nothing is scheduled on a thread
that is not allowed to touch what the job is about to touch: work on a player goes to that
player, work on blocks goes to the region they are in, and everything else to the server as a
whole.

## Configuration

```
plugins/ChorusCore/
├── config.yml            storage, economy, confirmations, metrics, staff log
├── aliases.yml           extra names for every command
├── messages.yml          every line the plugin sends to chat
├── menus.yml             every word that appears on a screen
└── modules/
    ├── homes.yml             /home /sethome /delhome /homes /renamehome
    │                         /homeicon
    ├── warps.yml             /warp /warps /setwarp /delwarp /warpinfo /warpset
    ├── spawn.yml             /spawn /setspawn
    ├── teleport.yml          /tpa /tpahere /tpaccept /tpdeny /tpcancel
    │                         /tptoggle /tpauto /tpoffline /tpaall /back
    ├── economy.yml           /pay /paytoggle /balance /baltop /eco /paylog
    ├── chat.yml              /msg /reply /rtoggle /socialspy /msgtoggle /ignore
    │                         /mail
    ├── players.yml           /afk /seen /playtime /whois /list /ptime /pweather
    │                         /nick /realname
    ├── items.yml             /hat /condense /clearinventory /restore /itemname /lore
    │                         /more /skull /unbreakable /glow /enchant /stack
    │                         /give /exp /itemdb /recipe /book /firework /potion
    │                         /editsign
    ├── kits.yml              /kit /kits /kitedit /kitreset
    ├── world.yml             /world /time /weather /spawnmob /sweep /tree
    │                         /spawner /unlimited
    ├── shops.yml             chest shops, [Buy] and [Sell] signs, /shop /sell
    │                         /worth /setworth
    ├── staff.yml             /tp /tphere /tppos /tpall /vanish /gamemode /gmc
    │                         /gms /gma /gmsp /freeze /sudo /lockdown /tempfly
    │                         /note /stafflog
    ├── utility.yml           /heal /feed /fly /god /ping /fix /trash /speed
    │                         /tps /getpos /suicide /burn /top /near /invsee
    │                         /ecsee /craft /anvil /smithingtable /grindstone
    │                         /stonecutter /loom /cartography /enchanting
    │                         /enderchest /jump /bottom /break /depth /compass
    │                         /rest, and the service signs
    └── custom-commands.yml   the /discord and /rules of your server
```

`/chorus reload` re-reads all of them, and `/chorus reload <module>` re-reads one. The
`storage` section, `aliases.yml` and adding or removing a custom command are the three things
that need a full restart.

### Per-command rules

Every command has its own block in its module's file:

```yaml
commands:
  home:
    enabled: true
    warmup-seconds: 3
    cooldown-seconds: 30
    price: 50.0
    sound:
      key: entity.enderman.teleport
      volume: 0.7
      pitch: 1.2
    particle:
      name: PORTAL
      count: 45
      spread: 0.4
      height: 2.0
      speed: 0.04
```

| Option | What it does |
| --- | --- |
| `enabled` | `false` makes the command answer that it is switched off. Each module file also has an `enabled` at the top that covers all of its commands at once. |
| `warmup-seconds` | Stand still this long before the teleport happens. Moving or taking damage cancels it and nothing is charged. Only applies to commands that teleport. |
| `cooldown-seconds` | Wait this long before the same player may repeat the command. Starts only once it actually succeeded. |
| `price` | What it costs through Vault. Ignored when there is no economy. Charged only on success. |
| `sound` | A Minecraft sound name with volume (0-10) and pitch (0.5-2). Empty for silence. A name the client does not know just plays nothing. |
| `particle` | A Bukkit particle name, how many, and how far they spread. Empty for none. |

Each file opens its `commands` section with a `defaults` block, so a command only writes down
what it does differently. Warmup, cooldown and price default to `0`, which is off, and
cooldowns survive a disconnect.

Mojang renamed a fair number of particles over the years, so both the old and the new
spelling are accepted and the right one is picked for whichever version you run.

### Menus

`/homes`, `/warps` and `/kits` open a clickable grid rather than printing a line of names.
Rows, the icon each entry uses, the filler and the navigation buttons are all set per module:

```yaml
menu:
  enabled: true
  rows: 3
  icon: LIME_BED
  filler: ''
  navigation-filler: GRAY_STAINED_GLASS_PANE
  previous-page: ARROW
  next-page: ARROW
  close: BARRIER
```

The bottom row is always navigation, so three rows shows eighteen entries a page and nothing
ever moves under the cursor between pages. Set `enabled: false` to go back to the written
list.

Every word on a screen lives in `menus.yml`, with a block per screen, so the homes grid and
the warps grid can say entirely different things:

```yaml
menu:
  buttons:                # the navigation strip, shared by every paged screen
    previous: '<color:#c9a227>« Previous'
  homes:
    title: '» YOUR HOMES «'
    entry: '%home%'
    lore-action: '➜ Click to teleport'
  warps:
    lore-action: '➜ Travel'
  kits: ...
  editor: ...             # the whole of /kitedit, prompts included
```

The rule is the key: anything starting with `menu.` is in `menus.yml`, everything else in
`messages.yml`.

`/warps` opens a screen of sections when any warp has one, and the warps inside it one click
further in. Give a warp its section with `/warpset shop section Towns`.

### Colours

Every line the plugin writes — `messages.yml`, kit names, item lore, warp descriptions —
takes MiniMessage tags and the old `&`-codes, in the same line if you like:

```yaml
'&c&lRed Bold'
'<red><bold>Red Bold'
'&6Gold and <gradient:#1f8f8c:#3fb8b4>a gradient'
```

Both hex spellings work as well: `&#a1b2c3`, and `&x&a&1&b&2&c&3` the way the old proxies
wrote it. A colour code clears the formatting before it, exactly as it does in vanilla, so
`&l&cText` comes out red and not bold. A doubled `&&` writes the character itself, for a line
that needs to talk about a code without becoming one.

This is the same everywhere, including `/itemname` and `/lore`, which take either format from
anyone holding `chorus.items.format`.

Values are put in as text, never as formatting. A player whose home is called `<red>` sees
those five characters, not a colour, and the same goes for `&`-codes — nobody can write their
way into looking like somebody else.

Item names and lore are not quietly italicised the way the game does it by default, and a line
of lore that names no colour is drawn grey rather than the purple the game falls back to.
Neither is written into your file: what you type is stored exactly as you typed it, the styling
happens when the line is drawn, and an editor screen shows you the plain text back.

### Aliases

`aliases.yml` holds every extra name. `plugin.yml` deliberately declares none, so this file
is the only place to look:

```yaml
delhome: [ removehome, remhome ]
tpa: [ call, tpask ]
```

An alias another plugin already owns is skipped. A name the **server itself** answers to is
taken over: `/clear` reaches `/clearinventory`, `/tps` reaches this plugin's. The line is
rewritten before it is dispatched, so `/clear Notch` is `/clearinventory Notch` all the way
down — its arguments, its permission, its messages. The original stays reachable as
`/minecraft:clear`, and switching a module off gives its names back.

## Commands

### Homes

| Command | Permission | Default |
| --- | --- | --- |
| `/sethome [name]` | `chorus.home.set` | everyone |
| `/home [name]` | `chorus.home.use` | everyone |
| `/delhome <name>` | `chorus.home.delete` | everyone |
| `/homes` | `chorus.home.list` | everyone |
| `/renamehome <old> <new>` | `chorus.home.rename` | everyone |
| `/homeicon <home> [item]` | `chorus.home.icon` | everyone |

With no name, `/home` and `/sethome` fall back to `homes.default-name`. A player with exactly
one home gets sent to it by a bare `/home` whatever it is called, unless you turn
`fallback-to-only-home` off.

`homes.price-per-home` adds to the price of `/sethome` for every home the player already has,
so the first is whatever the command block says and each one after costs more. Moving a home
you already have is never surcharged. `homes.world-limits` caps one world on top of the
overall limit, for the worlds where homes should be rare.

`/homeicon` sets what one home looks like in the grid. Renaming or moving a home keeps its
icon and the day it was made.

`/home <player>:<home>` takes staff to another player's home and needs `chorus.home.others`.
It only reaches a player who is online, since homes are held in memory for whoever is here
and nowhere else while they are not. `/sethome` over a home you already have asks to be run a
second time before it moves it.

### Warps

| Command | Permission | Default |
| --- | --- | --- |
| `/warp <name> [player]` | `chorus.warp.use` | everyone |
| `/warps [section]` | `chorus.warp.list` | everyone |
| `/warpinfo <name>` | `chorus.warp.info` | everyone |
| `/setwarp <name>` | `chorus.warp.set` | op |
| `/warpset <name> <setting> [value]` | `chorus.warp.set` | op |
| `/delwarp <name>` | `chorus.warp.delete` | op |

Everything a single warp overrides lives in the database rather than in a file, because warps
are made in game and a server owner should not have to edit a file and restart to say that
the one they just made costs money:

```
/warpset shop icon EMERALD          how it looks in /warps
/warpset shop price 100             what that one warp costs
/warpset shop cooldown 60           the wait after using that one
/warpset shop permission group.vip  who may use it
/warpset shop description The shop  a line under its name
/warpset shop section Towns         groups it in /warps
```

Leaving the value off puts a setting back to what `warps.yml` says. A permission set on the
warp itself wins outright, so one warp can be locked without turning on per-warp permissions
for every other one. Warps count how often they are used, and `/warpinfo` shows the number.

`/warps` opens a screen of sections once there are two or more; `/warps Towns` goes straight
into one. `warps.sort` says what order they come in inside a section: `name`, `uses` for the
most used first, or `created`.

`/warp <name> <player>` sends somebody else, and needs `chorus.warp.use.others`. Their trip
is free and starts no cooldown: paying for it out of your own pocket is nobody's idea of how
that should work.

A sign reading `[Warp]` on the first line and a warp name on the second sends whoever
right-clicks it, honouring that warp's price, wait and permission exactly as `/warp` would.
Making one needs `chorus.warp.sign.create`; `warps.signs` turns the whole thing off.

### Spawn

| Command | Permission | Default |
| --- | --- | --- |
| `/spawn [world]` | `chorus.spawn.use` | everyone |
| `/setspawn` | `chorus.spawn.set` | op |

Set `per-world` in `spawn.yml` and `/setspawn` sets the spawn of the world you are standing
in, while `/spawn` sends players to the spawn of the world they are in. Worlds with none of
their own fall back to `fallback-world`, and then to the single spawn.

### Teleport requests

| Command | Permission | Default |
| --- | --- | --- |
| `/tpa <player>` | `chorus.tpa.use` | everyone |
| `/tpahere <player>` | `chorus.tpa.here` | everyone |
| `/tpaccept [player]` | `chorus.tpa.accept` | everyone |
| `/tpdeny [player]` | `chorus.tpa.deny` | everyone |
| `/tpcancel [player]` | `chorus.tpa.use` | everyone |
| `/tptoggle` | `chorus.tpa.toggle` | everyone |
| `/tpauto` | `chorus.tpa.auto` | everyone |
| `/tpaall` | `chorus.tpa.all` | op |
| `/tpoffline <player>` | `chorus.tpa.offline` | op |
| `/back [steps]` | `chorus.back.use` | everyone |

`/tpaccept` and `/tpdeny` answer the most recent request when no name is given. `/tpcancel`
with no name withdraws every request you sent. `/tptoggle` turns incoming requests down
without the sender having to be told twice, and is remembered between sessions.

`/back` walks a trail rather than a single step: `teleport.history-size` says how deep it
goes, and `/back 3` goes three places down it. During a warmup the seconds left show in the
action bar, and `teleport.safe-landing` stops a teleport dropping a player inside a wall,
over a void or into lava, looking for somewhere to stand nearby instead.

`/tpauto` accepts every request the moment it arrives instead of asking, and turns
`/tptoggle` off if it was on. `teleport.invulnerable-seconds` gives a few seconds of not
being hittable on arrival anywhere, which is what keeps a warp into a PvP world from being a
coin toss; attacking somebody gives it up at once.

`/tpoffline <player>` goes to where somebody logged out, reading the position written down
when they left. Somebody still online is simply where they are, so it answers for them too.

### Utility

| Command | Permission | Default |
| --- | --- | --- |
| `/heal [player]` | `chorus.utility.heal` | op |
| `/feed [player]` | `chorus.utility.feed` | op |
| `/fly [player]` | `chorus.utility.fly` | op |
| `/god [player]` | `chorus.utility.god` | op |
| `/speed <1-10> [player]` | `chorus.utility.speed` | op |
| `/top` | `chorus.utility.top` | op |
| `/jump` | `chorus.utility.jump` | op |
| `/bottom` | `chorus.utility.bottom` | op |
| `/break` | `chorus.utility.break` | op |
| `/depth` | `chorus.utility.depth` | everyone |
| `/compass` | `chorus.utility.compass` | everyone |
| `/rest [player]` | `chorus.utility.rest` | op |
| `/near [radius]` | `chorus.utility.near` | op |
| `/invsee <player>` | `chorus.utility.invsee` | op |
| `/ecsee <player>` | `chorus.utility.ecsee` | op |
| `/ping [player]` | `chorus.utility.ping` | everyone |
| `/fix [all]` | `chorus.utility.fix` | op |
| `/trash` | `chorus.utility.trash` | op |
| `/tps` | `chorus.utility.tps` | op |
| `/getpos [player]` | `chorus.utility.getpos` | op |
| `/suicide` | `chorus.utility.suicide` | everyone |
| `/burn <player> <seconds>` | `chorus.utility.burn` | op |

Portable screens, all `chorus.utility.<command>`, op by default:

`/craft` · `/anvil` · `/smithingtable` · `/grindstone` · `/stonecutter` · `/loom`
· `/cartography` · `/enchanting` · `/enderchest`

Aiming a command at someone else needs the same permission with `.others` on the end, for
example `chorus.utility.heal.others`. `/fix all` needs `chorus.utility.fix.all` on top of
`chorus.utility.fix`.

`/invsee` and `/ecsee` are live: what you drag into the window lands in the other player's
inventory straight away, and what they pick up shows in yours. They are read-only until the
viewer also holds `chorus.utility.invsee.edit` or `chorus.utility.ecsee.edit`, and anyone
with `chorus.utility.invsee.exempt` cannot be looked at.

Whatever is left in the `/trash` window when it closes is gone for good.

#### Service signs

Signs that do what a command does. Write one of these on the first line, put the arguments on
the middle lines and a price on the last one:

```
[Kit]            [Gamemode]       [Free]           [Repair]
starter          creative         64               all
                                  bread
100              0                                 50
```

`[Heal]` `[Feed]` `[Repair]` `[Disposal]` `[Workbench]` `[Enchant]` `[Gamemode]` `[Kit]`
`[Balance]` `[Spawn]` `[Free]`

Everything but `[Free]` runs the command as the player who clicked it, so **that command's
permission, cooldown and price all still apply** — a `[Kit]` sign cannot hand out a kit
somebody is not allowed to take, and it cannot skip the cooldown either. The price on the
sign is on top of whatever the command itself charges, and is only taken when the command
actually goes through.

Making one needs `chorus.signs.create.<kind>`; using one needs `chorus.signs.use.<kind>`.
`utility.signs: false` turns the whole thing off.

### Economy

| Command | Permission | Default |
| --- | --- | --- |
| `/pay <player> <amount>` | `chorus.economy.pay` | everyone |
| `/paytoggle` | `chorus.economy.toggle` | everyone |
| `/balance [player]` | `chorus.economy.balance` | everyone |
| `/baltop` | `chorus.economy.baltop` | everyone |
| `/eco <give|take|set|reset> <player> [amount]` | `chorus.economy.admin` | op |
| `/paylog [player]` | `chorus.economy.paylog` | everyone |

ChorusCore is an economy in its own right, so none of this needs another plugin. Balances live
in the same database as everything else and are held in memory while the server runs, which
is how a priced command can read one without a round trip.

`economy.provider` in `config.yml` says where the money comes from:

| | |
| --- | --- |
| `auto` | the built-in ledger, unless another economy plugin has registered with Vault |
| `self` | the built-in ledger, whatever else is installed |
| `vault` | another plugin only; with no Vault every price is ignored |
| `off` | no economy at all |

With Vault installed the built-in ledger is offered to every other plugin on the server, at a
low priority, so a shop or jobs plugin spends the same money these commands do and a
dedicated economy plugin still wins if you install one.

The symbol, decimals, opening balance and the ceiling are all in `config.yml`. Payments go to
online players only and honour the minimum and maximum in `economy.yml`. If the deposit fails
after the money left the sender it is put straight back.

`/balance <player>` needs `chorus.economy.balance.others`.

Every payment is written down, and `/paylog` reads it back. `/paylog <player>` and
`/paylog *` need `chorus.economy.paylog.others`. How long entries are kept is
`economy.log.keep-days`.

`/eco` creates and destroys money rather than moving it, and every use of it goes in the
staff log. Taking more than somebody has empties the account rather than going negative, and
`/eco reset` puts a balance back to the opening one. **It works on a player the server has
never had online**: a name the server does not recognise is looked up in the plugin's own
record of who has played here.

`/baltop` ranks every account on the server, paged, and adds up what is in circulation. On a
server whose money belongs to another plugin there is no such list to read, so it falls back
to ranking whoever is online.

`/paytoggle` stops other players sending money, and is remembered between sessions.
`economy.confirm-above` makes a payment of a given size ask the sender to run the command
again before it goes through.

### Chat

| Command | Permission | Default |
| --- | --- | --- |
| `/msg <player> <message>` | `chorus.chat.msg` | everyone |
| `/reply <message>` | `chorus.chat.reply` | everyone |
| `/msgtoggle` | `chorus.chat.toggle` | everyone |
| `/ignore <player>` | `chorus.chat.ignore` | everyone |
| `/mail [read|send|sendall|| `/mail [read|send|sendall|clear]` | `chorus.mail.use` | everyone |
| `/rtoggle` | `chorus.chat.reply` | everyone |
| `/socialspy` | `chorus.chat.spy` | op |

`/reply` answers whoever you last spoke to, in either direction. `/socialspy` shows staff a
copy of everyone else's private messages; `spy-enabled` in `chat.yml` turns the whole thing
off server-wide.

Message text reaches chat as plain text, so a player cannot smuggle colour codes or
formatting into someone else's screen.

`/msgtoggle` turns private messages off entirely; `/ignore <player>` turns off one person and
remembers it between sessions. Either way the sender is told the same thing — that the player
is not taking messages — so being ignored is not announced. `chorus.chat.ignore.bypass`, op by
default, reaches anybody regardless.

`/mail` leaves a message for somebody who is not here. They are told how much is waiting the
next time they log in, and reading it marks the whole inbox as seen. `/mail read <player>`
needs `chorus.mail.read.others` and leaves the inbox unread, because staff looking into a
complaint should not change what the player is about to see.

`/rtoggle` chooses which end of a conversation `/reply` answers. It only matters when one is
interrupted: you write to Anna, Ben writes to you, and `/r` has to pick. `/mail send <player>
<message>` needs `chorus.mail.send`; `/mail sendall <message>` reaches everybody online and
needs `chorus.mail.all`. How long letters are kept and how many one player may have waiting
are in the `mail` section of `chat.yml`.

### Staff

| Command | Permission | Default |
| --- | --- | --- |
| `/tp <target>` or `/tp <who> <target>` | `chorus.staff.tp` | op |
| `/tphere <player>` | `chorus.staff.tphere` | op |
| `/tppos <x> <y> <z> [world] [player]` | `chorus.staff.tppos` | op |
| `/tpall` | `chorus.staff.tpall` | op |
| `/vanish` | `chorus.staff.vanish` | op |
| `/gamemode <mode> [player]` | `chorus.staff.gamemode` | op |
| `/gmc` `/gms` `/gma` `/gmsp` | `chorus.staff.gamemode` | op |
| `/freeze <player>` | `chorus.staff.freeze` | op |
| `/sudo <player> <command>` | `chorus.staff.sudo` | op |
| `/lockdown <on\|off> [reason]` | `chorus.staff.lockdown` | op |
| `/tempfly <player> <minutes>` | `chorus.staff.tempfly` | op |
| `/note <add\|list\|clear> <player> [text]` | `chorus.staff.note` | op |
| `/stafflog [player]` | `chorus.staff.log` | op |

Each mode also needs its own permission, so a rank can have creative without spectator:
`chorus.staff.gamemode.creative`, `.survival`, `.adventure`, `.spectator`. Changing somebody
else needs `chorus.staff.gamemode.others`.

`chorus.staff.vanish.see` keeps a player able to see anyone vanished, and vanish is reapplied
when either side reconnects.

Frozen players cannot move, teleport or run commands; `/msg` and `/reply` stay open so they
can answer whoever froze them, and the freeze survives a reconnect because logging out is the
first thing anybody tries. `chorus.staff.freeze.exempt` protects the holder.

`/lockdown` closes the door to everyone without `chorus.staff.lockdown.bypass`. Nobody already
on the server is thrown out, and it is not remembered across a restart: coming back up already
closed, with nobody having said so, is how a server stays empty all evening by accident.

`/sudo` runs a command as somebody else, with their permissions rather than yours.
`chorus.staff.sudo.exempt` protects the holder, and every use goes in the staff log.

`/note` keeps a line about a player for whoever deals with them next, and works on somebody
who has logged off. `/stafflog` reads back what staff have been doing: freezes, `/sudo`,
`/lockdown`, `/tempfly`, notes and every `/eco`. Both are set up in the `staff-log` section
of `config.yml`.

### Players

| Command | Permission | Default |
| --- | --- | --- |
| `/afk [reason]` | `chorus.players.afk` | everyone |
| `/seen <player>` | `chorus.players.seen` | everyone |
| `/playtime [player]` | `chorus.players.playtime` | everyone |
| `/whois <player>` | `chorus.players.whois` | op |
| `/list` | `chorus.players.list` | everyone |
| `/ptime <time> [player]` | `chorus.players.ptime` | op |
| `/pweather <clear|rain|reset> [player]` | `chorus.players.pweather` | op |
| `/nick [player] <name|off>` | `chorus.players.nick` | op |
| `/realname <nickname>` | `chorus.players.realname` | everyone |

Players are marked away on their own after `auto-afk-minutes` of standing still, and come
back the moment they move, interact or type a command. `/playtime` reads the figure the
server already keeps, the same one the vanilla statistics screen shows.

`/whois` gathers everything the server already knows about a player into one card, and works
on somebody who has logged off. The address is behind `chorus.players.whois.ip`. `/list`
respects vanish: a player hidden from you is missing from both the names and the count.

`/ptime` and `/pweather` change only what one client is shown; the world keeps its own time
and weather, and so does everybody standing next to them.

`/afk` takes a reason: `/afk eating` tells everybody what you are doing.

`/seen` says when somebody was last here and where they logged out.
`chorus.players.seen.address` adds the address they came from and every other account that
has connected from it, which is what turns `/seen` into a way of catching somebody back on a
second account.

`/whois` can add the country, but only if you ask it to. There is no country in a player's
connection: the only way to know is to send their address to a service outside your server.
That is a real decision, so `players.geoip` starts switched off and says why. Turn it on and
the answer appears for whoever already holds `chorus.players.whois.ip`.

`/nick` changes the name a player is shown by, in chat and in the tab list.
`chorus.players.nick.colour` is needed for colour codes and the limit in `players.yml` counts
letters rather than codes, so a coloured nickname is not half as long as a plain one. A name
that is already the username of somebody online is refused outright, and `/realname` reads a
nickname back to the account behind it.

### Items

| Command | Permission | Default |
| --- | --- | --- |
| `/hat` | `chorus.items.hat` | op |
| `/condense` | `chorus.items.condense` | op |
| `/clearinventory [player]` (also `/clear`) | `chorus.items.clearinventory` | op |
| `/restore <player> [list|number]` | `chorus.items.restore` | op |
| `/restore` without restoring | `chorus.items.restore.view` | op |
| `/itemname <text>` | `chorus.items.itemname` | op |
| `/lore <add\|set\|remove\|clear>` | `chorus.items.lore` | op |
| `/more [amount]` | `chorus.items.more` | op |
| `/skull [player]` | `chorus.items.skull` | op |
| `/unbreakable` | `chorus.items.unbreakable` | op |
| `/glow` | `chorus.items.glow` | op |
| `/enchant <enchantment> [level]` | `chorus.items.enchant` | op |
| `/stack` | `chorus.items.stack` | op |
| `/give [player] <item> [amount]` | `chorus.items.give` | op |
| `/exp [show|give|take|set|reset] [player] [amount]` | `chorus.items.exp` | op |
| `/itemdb [item]` | `chorus.items.itemdb` | everyone |
| `/recipe [item|hand] [number]` | `chorus.items.recipe` | everyone |
| `/book [author|title] [text]` | `chorus.items.book` | op |
| `/firework ...` | `chorus.items.firework` | op |
| `/potion <effect> [level] [seconds]` | `chorus.items.potion` | op |
| `/editsign <set|clear|copy|paste>` | `chorus.items.editsign` | op |

`/condense` only takes untouched stacks: anything named, enchanted or damaged is left alone,
so a mistyped command can never eat a special item. What packs into what is listed in
`items.yml`.

`/give` hands out items by name, to yourself when no player is given; anything that does not
fit drops at their feet. It takes the item apart in the same breath:

```
/give Notch diamond_sword 1 name:Excalibur lore:Forged_in_the_End enchant:sharpness:5 unbreakable
```

| Word | What it does |
| --- | --- |
| `name:` | The display name. Underscores become spaces. |
| `lore:` | The lore. `|` starts a new line. |
| `enchant:<name>:<level>` | An enchantment. Level `0` takes one off. |
| `unbreakable` | Never wears out. |
| `glow` | The enchanted shimmer without an enchantment behind it. |
| `hide` | Hides every tooltip line the game adds. |
| `durability:<n>` | How much use is left. |
| `colour:<r,g,b>` | Leather armour. `#ff0000` works too. |
| `owner:<player>` | Whose head it is. |
| `amount:<n>` | How many. |

Colour codes in `name:` and `lore:` need `chorus.items.format`; without it the text is used
exactly as typed. A word that means nothing is reported and skipped rather than refusing the
whole item.

`/exp` works in points, or in levels when the amount ends in `L`: `/exp give Notch 5L`.
`/itemdb` says what the thing in your hand is called, which is the answer to "what do I type
in the config for this". `/recipe` shows how something is made, datapack recipes included.
`/clearinventory` asks to be run a second time before it empties anything.

`/editsign` writes straight to the block, so a protection plugin does not get a say — it is
an operator tool and the default permission says so. A sign that belongs to a shop is refused.

#### Inventory backups

**A player never loses everything without a copy of it being kept.** One is taken when they

- **die** — with what killed them, and who, if it was a player
- **join** and **leave**, so a rollback has both ends of a session
- **change world**, named after the world they left
- are emptied by **`/clear`** or by a **kit** with `clear-inventory`

Each copy holds the inventory, the ender chest, the experience, the health, the food and where
they were.

`/restore <player>` opens a screen of them, newest first. Clicking one lays that inventory out
exactly as it was carried, armour and offhand included, so the decision is made **looking at
the items rather than guessing**. From there:

- **Restore inventory** puts those items back
- **Hand the items over** gives them back **without taking away what they carry now**, for
  when somebody should get their diamonds back but has since earned a new set. Anything that
  does not fit drops at their feet
- **Ender chest** opens the ender chest from the same moment, with its own restore button
- **Restore everything** does the inventory, the ender chest, the experience, the health and
  the food

A filter on the bottom row cycles through the reasons that actually have something under
them, so forty deaths do not stand between you and the clear you are looking for.

Restoring takes a copy of what it replaces, so restoring the wrong one is itself undoable.

`chorus.items.restore.view` opens the screens without the restore buttons, for staff who
should be able to look into a dispute without being able to hand out diamonds.

The commands still work for anyone who would rather type:

```
/restore Notch list     what there is, with when, why and who
/restore Notch 3        the third one down, everything
```

**It works on a player who is not online.** Nothing can be handed to somebody who is not
there, so the restore waits in the database and is applied the moment they log in — they are
told who did it.

The copy keeps the whole item — enchantments, lore, custom model data, another plugin's tags.
The `backups` block in `items.yml` sets how long copies are kept, how many per player, which
moments take one, and what the screen looks like.

`chorus.items.format` lets a player use MiniMessage tags in `/itemname` and `/lore`. Without
it their text is used exactly as typed.

`/enchant` writes enchantments the way the game writes them today — `protection`, `sharpness`,
`unbreaking` — and tab completion only offers the ones this version actually has. Level `0`
takes one off, and levels beyond the vanilla maximum need `chorus.items.enchant.unsafe`.

`/glow` gives an item the enchanted shimmer without an enchantment behind it, and says so
rather than hiding the enchantments of an item that already has some. `/stack` merges the
loose piles in your inventory, matching on name, lore, enchantments and damage, so a renamed sword
never disappears into an ordinary one.

### Kits

| Command | Permission | Default |
| --- | --- | --- |
| `/kit [name]` | `chorus.kits.use` | everyone |
| `/kits` | `chorus.kits.list` | everyone |
| `/kitedit [kit] [setting] [value]` | `chorus.kits.edit` | op |
| `/kitreset <kit|*> [player]` | `chorus.kits.reset` | op |

Kits live in `modules/kits.yml`, one block each: the items, what taking one costs, how long before
the same player may take it again, and whether it is one-time. `/kit` on its own opens the
same grid `/kits` does; `/kit <name>` takes one straight away.

Each kit also carries its own permission. Left out, it defaults to `chorus.kits.use.<name>`;
set it to `''` and anyone may take that kit. `kits.first-join` names the kit handed out the
first time a player ever joins.

`/kitreset <kit> [player]` clears a cooldown, or lets a one-time kit be taken again;
`/kitreset * <player>` does every kit at once. The player has to be online, since the record
of what they have taken is only loaded while they are.

Enchantments are written the way the game writes them today — `protection`, `sharpness`,
`unbreaking` — not the old `PROTECTION_ENVIRONMENTAL` spellings, so the same file works on
every supported version.

Anything that will not fit in the player's inventory lands at their feet rather than vanishing.

**Beyond the items**, a kit can take:

```yaml
auto-armor: true          armour goes on rather than into the inventory
clear-inventory: false    empties the inventory before handing it over
max-claims: 5             how many times in total it may ever be taken
placeholders: true        fills %player%, %date% and %time% into item text

requirements:             what has to be true before they may take it
  - 'permission: chorus.kits.vip'
  - condition: 'placeholder: %player_level% >= 10'
    deny: '<red>Come back at level 10.'

claim-actions:            what happens when they take it
  - 'message: <green>Enjoy your %kit% kit.'
  - 'sound: entity.player.levelup 1 1.4'
  - 'console: lp user %player% parent add vip'

fail-actions:             what happens when they are refused
  - 'sound: block.note_block.bass 0.5 0.7'
```

Requirements come in five kinds — `permission`, `placeholder`, `money`, `playtime` and `kit`
— and `placeholder` is the one that makes the others optional: anything any plugin exposes
through PlaceholderAPI can gate a kit, compared with `>=`, `<=`, `==`, `!=`, `>`, `<` or
`contains`. Actions come in eight: `message`, `broadcast`, `actionbar`, `title`, `sound`,
`console`, `player` and `close`, run in the order they are listed.

`placeholders: true` is what turns `%player%` in a sword's lore into the name of whoever
claimed it. Only the items that actually hold a `%` are rebuilt per player, so a kit of sixty
plain items pays nothing for it.

#### The kit editor

`/kitedit` opens the whole of that as a screen. Every setting is a button:

- **Icon** — click an item in your inventory and it takes the slot, replacing whatever was on it.
  The whole item is kept, so a kit shown as a named, enchanted sword stays that sword.
- **Items** — lay the kit out by clicking. The screen copies rather than moves, so nothing
  ever leaves your inventory and a kit full of diamond cannot be turned into a diamond machine.
- **Cooldown, max claims, price** — left click raises, right lowers, shift makes the step a
  big one, and `Q` asks for the exact figure in chat.
- **Requirements, claim actions, fail actions** — each opens a screen of its own with one
  line to an item. Click a line to rewrite it, shift-click to remove it, and on a requirement
  right-click to give it the sentence the player sees when it turns them away. A line the
  plugin cannot make sense of is shown in red rather than hidden, so a typo can be found here
  instead of only being missed in game. The **Add** button on each of those screens lists
  every type it accepts, with an example of each and the comparisons a requirement can make,
  and the prompt that asks for the line repeats them — nothing has to be looked up elsewhere.
- **One-time, auto armour, clear first, placeholders** — click to switch.
- **Display name, lore, permission** — click and type the value in chat. What you type is
  stored exactly as you typed it; the editor shows it back as plain text.

Nothing in the screen sends you away to type a command, and nothing you type in a prompt
becomes a chat message. The commands still work for anyone who would rather type:
`/kitedit vip cooldown 3600`, `/kitedit vip items`, `/kitedit vip create`, and so on.

Whatever the screen writes goes back into `modules/kits.yml` in exactly the form above, so a
kit built in game can still be opened in a text editor afterwards.

### World

| Command | Permission | Default |
| --- | --- | --- |
| `/world [name]` | `chorus.world.go` | op |
| `/time [when] [world\|all]` | `chorus.world.time` | op |
| `/weather <clear\|rain\|storm> [minutes] [world]` | `chorus.world.weather` | op |
| `/spawnmob <mob> [amount] [player]` | `chorus.world.spawnmob` | op |
| `/sweep [what] [radius]` | `chorus.world.sweep` | op |
| `/tree <kind>` | `chorus.world.tree` | op |
| `/spawner <mob> [delay]` | `chorus.world.spawner` | op |
| `/unlimited [list|clear]` | `chorus.world.unlimited` | op |

`/world` with no name lists the worlds with their kind and how many players are in each.
Turning on `world.per-world-permission` makes each one need `chorus.world.go.<name>` as well,
which is how a creative or event world stays shut without a second plugin.

`/time` accepts `day`, `noon`, `dusk`, `night`, `midnight`, `dawn` or a number of ticks, and
always moves **forwards** to the next time it will be, so asking for morning in the afternoon
does not wind the day back under everything that counts from it. `/time day all` does every
world at once.

`/spawnmob` puts mobs where somebody is standing, capped by `world.spawnmob-limit`.

`/tree` grows one where you are looking; the kinds come from the server, so a version that
adds one offers it. `/spawner` changes what the spawner under your cursor makes.
`/unlimited` marks the held block so placing it never uses it up, per material, and the list
goes when you log out.

`/sweep` clears entities out. The word can be a group or the name of one kind of mob:

```
/sweep drops 50        loose items within fifty blocks
/sweep monsters        every monster in this world
/sweep zombie 30       just the zombies nearby
```

Groups: `drops`, `experience`, `arrows`, `boats`, `minecarts`, `vehicles`, `monsters`,
`animals`, `ambient`, `tamed`, `named`, `villagers`, `armourstands`, `frames`, `paintings`,
`mobs` and `all`. **A player is never swept**, and `all` leaves out the things a server builds
rather than spawns — armour stands, paintings, item frames and villagers each have to be named
before they go. Without a radius it takes the whole world, which is the version that asks to be
run twice.

**It can run on a timer.** `world.auto-sweep` clears the ground every so often with a warning
first, which is the one piece of housekeeping every busy server ends up asking somebody to do
by hand:

```yaml
auto-sweep:
  enabled: false
  minutes: 15
  warn-seconds: 30
  target: drops
  worlds: []
```

An empty `worlds` list means every world.

### Shops

| Command | Permission | Default |
| --- | --- | --- |
| `/sell <hand\|all\|item> [amount]` | `chorus.shops.sell` | everyone |
| `/worth [hand\|item] [amount]` | `chorus.shops.worth` | everyone |
| `/setworth [item] <price>` | `chorus.shops.admin` | op |
| `/shop [info|price|mode|remove|list]` | `chorus.shops.chest.use` | everyone |

Shops run by the server, on signs. Four lines: what the sign does, how many, what, and for
how much.

```
[Buy]            [Sell]
64               32
diamond          iron_ingot
250              40
```

Right-clicking buys or sells. Making one needs `chorus.shops.create`, using one needs
`chorus.shops.use`, and `shops.signs` turns the whole thing off. A `[Sell]` sign may leave the
price line empty, in which case the item is worth whatever the list in `shops.yml` says; a
`[Buy]` sign always has to name its price.

Nothing runs out of stock — these are the server's own shops, which is what those two words on
the first line have meant since somebody first wrote them.

`/sell all` sells everything you are carrying that has a price, which is what a player coming
back from a mine actually wants. **Anything renamed, enchanted or damaged is left alone**, so
a named sword can never go for the price of the metal.

`shops.sell-multiplier` scales every price on the way out, so `0.8` pays eighty per cent
without editing the list. `/setworth diamond 75` writes a price straight into `shops.yml`, and
a price of `0` takes the item off the list.

#### Chest shops

Shops players run themselves, out of their own chests.

Hold what you want to sell, put a sign on a chest, barrel or trapped chest, and write `[Shop]`
on the first line. The price is asked for in chat, and the sign writes itself:

```
Ghost_chu
Selling 5
Diamond
$125.12 each
```

Write `buy` on the second line instead to make a shop that buys from players. `/shop` changes
a shop you are looking at: `price`, `mode`, `remove`, `info`, and `list` for all of yours.

Right-clicking somebody else's shop shows what it holds and asks how many you want. `all`
takes as many as the stock, your money and your inventory allow.

**Nothing duplicates.** That is the whole design of the trade, so it is worth saying how:

- Everything is measured again at the moment of the trade. How much stock, how much room, how
  much money — all read from the live inventories, never from what the screen said a few
  seconds earlier.
- Items only ever move from one place to another. Nothing is built from a template and handed
  over while the original stays where it was, which is the shape every duplication bug has.
- Every step after the first is reversible, and is reversed on failure. If the money will not
  move, the items go back where they came from before anybody is told anything.
- There is no waiting in the middle. No database call, no scheduled task, no callback: from
  the first measurement to the last item there is no point at which another player can act.

The one thing that can still go wrong is money disappearing, if a deposit is refused after a
withdrawal went through. That is the right way round, and it is reported so it can be put
right by hand.

**The shop is protected as one piece.** The container, its sign and the other half of a double
chest cannot be broken, blown up, pushed by a piston, merged into by a second chest placed
next to it, or drained by a hopper. Only the owner — or somebody with
`chorus.shops.chest.admin` — can take one down, and doing so closes the shop rather than
leaving a sign that points at nothing.

| Setting | What it does |
| --- | --- |
| `chest.default-limit` | Shops per player. Raise it per rank with `chorus.shops.chest.limit.<n>`. |
| `chest.creation-cost` | What making one costs. |
| `chest.protect-hoppers` | Stops a hopper emptying a shop. |
| `chest.max-price` | The most one item may cost, so a mistyped price cannot empty somebody. |
| `chest.reach` | How far from the shop a trade may still be finished. |

An admin with `chorus.shops.chest.unlimited` can write `admin` on the second line, or run
`/shop unlimited`, for a shop that never runs out and never pays out.

**The item turns slowly above the chest**, so you can see what a shop sells from across the
road. It is a dropped item with everything that makes a dropped item behave taken away: no
gravity, no ageing, no despawning, no picking up, and no merging with the one over the shop
next door. It is also never written to the world file, which is what makes it impossible for
a crash to leave one behind — they go when a chunk unloads and come back when it loads again.
`chest.display: false` turns them off.

### Custom commands

`modules/custom-commands.yml` is where `/discord`, `/rules` and the rest of your server's own
commands go. A command there can print lines, run other commands as the player, run them as
the console, carry its own aliases, permission and cooldown, and play a sound.

`run-as-console` is the useful one: it lets a player trigger something they have no
permission to do themselves. Treat it as you would treat op.

Adding, renaming or removing a custom command needs a server restart, because the command
has to be registered with Bukkit before anything can call it. Changing what an existing one
says or does is picked up by `/chorus reload`.

### Administration

| Command | Permission | Default |
| --- | --- | --- |
| `/commands [module|search] [page]` (also `/help`) | `chorus.help` | everyone |
| `/chorus reload [module]` | `chorus.admin` | op |
| `/chorus status` | `chorus.admin` | op |
| `/chorus debug` | `chorus.admin` | op |

`/commands` lists only what the player actually holds the permission for and that is switched
on, grouped by module, so it is never a catalogue of things that will refuse them.
`/commands homes` narrows it to one module, and any other word searches the names.

`/chorus reload` re-reads everything; `/chorus reload kits` re-reads one module without
touching the rest of the server. `/chorus debug` prints the server version, the Java version,
the scheduling model, memory, the storage and economy in use, which modules are on and off
and what else is installed — one block to paste into a bug report.

#### Coming from EssentialsX

```
/chorus import essentials          read everything, write nothing
/chorus import essentials run      do it
```

Brings across **homes, balances, warps, mail, nicknames, first and last seen, addresses and
the sell prices in `worth.yml`**.

Three rules make it safe to run on a live server:

1. **Nothing in the Essentials folder is touched.** Every file is opened for reading. If you
   change your mind, putting the old plugin back is all it takes.
2. **Nothing already in Chorus is overwritten.** A home, a warp or a balance that is already
   here is counted as left alone. Add `overwrite` after `run` if you want the other
   behaviour.
3. **It can be run without doing anything.** Without `run` it reads every file and reports
   exactly what a real run would write, down to the count.

The check pass prints something like this:

```
» CHORUSCORE « Found 1,284 players. Nothing was written.
▪ Homes 2,930 ▪ Balances 1,284 ▪ Warps 41
▪ Mail 88 ▪ Nicknames 173 ▪ Prices 96
▪ Left alone because Chorus already had them » 3
```

A file it cannot read is reported by name and skipped; the rest still come across.

**Kits are deliberately not imported.** Essentials writes them as its own item strings, and a
kit that came across half right is worse than one that never came across at all. Build those
in `/kitedit`, which takes a few minutes and gives you claim actions and requirements along
the way.

Restart the server once the real run finishes: the balances and the warps are read into memory
when the plugin starts, so that is when the imported ones appear.

#### Confirmations

The things that cannot be taken back ask to be run a second time first: `/clearinventory`,
`/delhome`, `/delwarp`, `/sethome` over a home you already have, `/sweep` of a whole world
and a payment over `economy.confirm-above`. The window is `confirmations.seconds` in
`config.yml`; `0` asks for nothing and `chorus.bypass.confirm` skips them.

#### Metrics

`metrics: true` in `config.yml` sends the server version, the storage type, the economy mode
and how many modules are on to bStats. No player data of any kind. Set it to `false` to send
nothing.

## Permissions worth knowing

| Permission | Effect |
| --- | --- |
| `chorus.home.limit.<n>` | Raises the cap to `n` homes. The highest number a player holds wins. |
| `chorus.home.unlimited` | No cap at all. |
| `chorus.warp.use.<name>` | Required per warp when `warps.per-warp-permission` is on. |
| `chorus.teleport.instant` | Skips every teleport warmup. |
| `chorus.bypass.cooldown` | Ignores every cooldown. |
| `chorus.bypass.price` | Never pays. |
| `chorus.bypass.confirm` | Skips every confirmation prompt. |
| `chorus.back.ondeath` | Lets `/back` return to where the player died. |
| `chorus.economy.balance.others` | Reads someone else's balance. |
| `chorus.utility.invsee.edit` | Allows editing through `/invsee`, `/ecsee` needs its own. |
| `chorus.warp.use.others` | Sends another player to a warp. |
| `chorus.warp.sign.create` | Makes a `[Warp]` sign. |
| `chorus.staff.lockdown.bypass` | Logs in during a lockdown. |
| `chorus.staff.freeze.exempt` | Cannot be frozen. |
| `chorus.staff.sudo.exempt` | Cannot be sudoed. |
| `chorus.tpa.toggle.bypass` | Reaches players who have `/tptoggle` on. |
| `chorus.chat.spy` | Uses /socialspy and receives the copies. |
| `chorus.kits.use.<name>` | Required per kit, unless that kit sets its own permission. |
| `chorus.home.others` | Allows `/home <player>:<home>`. |
| `chorus.tpa.offline` | Allows `/tpoffline`. |
| `chorus.players.seen.address` | Adds the address and shared accounts to `/seen`. |
| `chorus.players.nick.colour` | Allows colour codes in a nickname. |
| `chorus.world.go.<name>` | Required per world when `world.per-world-permission` is on. |
| `chorus.shops.create` | Makes a `[Buy]` or `[Sell]` sign. |
| `chorus.mail.all` | Sends mail to everybody at once. |
| `chorus.mail.read.others` | Reads another player's mail. |
| `chorus.shops.chest.limit.<n>` | Raises how many chest shops a player may own. |
| `chorus.shops.chest.admin` | Reaches every chest shop, whoever owns it. |
| `chorus.shops.chest.unlimited` | Allows a shop that never runs out or pays out. |
| `chorus.signs.use.<kind>` | Uses a service sign. `chorus.signs.create.<kind>` makes one. |
| `chorus.items.format` | Colour codes in /itemname, /lore and /give. |

LuckPerms examples:

```
lp group vip permission set chorus.home.limit.10
lp group vip permission set chorus.warp.use.vipzone
lp group staff permission set chorus.bypass.cooldown
```

`chorus.home.limit.<n>` must be an explicit number. A wildcard such as
`chorus.home.limit.*` carries no number, so it cannot raise the cap; use
`chorus.home.unlimited` for that.

Permissions are checked in code rather than declared in `plugin.yml`, so players see the
message from `messages.yml` instead of Bukkit's built-in one.

## For other plugins

### The API

ChorusCore registers itself as a Bukkit service, so an addon never has to touch its
internals:

```java
ChorusProvider.get().flatMap(ChorusApi::homes)
        .ifPresent(homes -> homes.list(player.getUniqueId()));
```

Add `softdepend: [ ChorusCore ]` to your plugin.yml and check the result, or `depend` and
assume it is there. Everything that belongs to a module comes back as an `Optional`, because
any module can be switched off in its config and an addon has to cope with that.

| Part | What it gives you |
| --- | --- |
| `homes()` | Read, save and delete homes of players who are online. |
| `warps()` | Every warp, and which of them a given sender may use. |
| `spawns()` | The spawn a player in a given world would be sent to. |
| `economy()` | Balances and transfers. Reports itself disabled when there is no Vault. |
| `teleports()` | The same delayed, cancel-on-move teleport the plugin's own commands use. |
| `messages()` | Send or render anything from messages.yml, prefix included. |

There are cancellable events too: `ChorusTeleportEvent`, `ChorusHomeSaveEvent` and
`ChorusPaymentEvent`.

Only the interfaces in `dev.chorus.core.api` are promised to stay stable. Everything else is
free to change between versions.

### PlaceholderAPI

If PlaceholderAPI is installed the expansion registers itself, which is how a tab or
scoreboard plugin shows any of this without depending on ChorusCore at all.

| Placeholder | Shows |
| --- | --- |
| `%chorus_homes_used%` | Homes the player has. |
| `%chorus_homes_limit%` | Their cap, or the infinity sign. |
| `%chorus_homes_free%` | How many more they may set. |
| `%chorus_warps_total%` | Warps on the server. |
| `%chorus_warps_available%` | Warps this player may use. |
| `%chorus_balance%` | Their balance, formatted the way the economy writes it. |
| `%chorus_balance_raw%` | The same as a plain number. |
| `%chorus_playtime%` | How long they have played. |
| `%chorus_afk%` | `true` or `false`. |
| `%chorus_vanished%` | `true` or `false`. |
| `%chorus_spawn_set%` | Whether their world has a spawn. |

Every one of them reads from memory. None touch the database, so a scoreboard refreshing
several times a second costs nothing.

## Storage

SQLite by default: one file in the plugin folder, nothing to configure. For several servers
sharing data, set `storage.type` to `mysql` and fill in the `mysql` section. The same driver
handles MySQL and MariaDB.

Every query runs off the main thread, so the server never waits on the database. Balances are
the one thing held in memory as well, because a priced command has to read one the instant it
runs; changes are written back behind the scenes.

The tables are `chorus_homes`, `chorus_locations` (warps and spawn points, under different
categories), `chorus_warp_details`, `chorus_kit_uses`, `chorus_player_flags`,
`chorus_players` (names, nicknames, addresses and where each player logged out),
`chorus_balances`, `chorus_payments`, `chorus_mail`, `chorus_inventory_backups`,
`chorus_pending_restores`, `chorus_chest_shops`, `chorus_notes` and `chorus_staff_log`.

`chorus_schema_version` records how far each of them has been brought up to date.
`CREATE TABLE IF NOT EXISTS` cannot add a column to a table that already exists, so each one
is written as an ordered list of steps and a server upgrading from an older release runs only
the steps it has not seen. A step that fails rolls back and is not recorded as done.

## Building

You need a JDK 21 or newer. Nothing else: Gradle downloads itself.

```bash
./gradlew build
```

The jar lands in `build/libs/ChorusCore-<version>.jar`, with bStats shaded into it under this
plugin's own package. The `-plain` jar next to it is the same classes without bStats and is
not the one to install. The same command runs the checks in `src/test/java`, which hold the
config files and the code to a single command list and exercise the SQL against a throwaway
SQLite file.

## License

MIT. See [LICENSE](LICENSE).
