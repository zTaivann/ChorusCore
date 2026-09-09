# ChorusCore

A modular core for Paper servers: homes, warps, spawn, teleport requests, economy, private
messages, staff tools, item tools, kits and the usual utilities. Sixty-three commands across
twelve modules, every one of them switchable, priced and themed from its own config file, plus
as many commands of your own as you care to write.

Compiled against the Paper 1.18.2 API and emitting Java 17 bytecode, so a single jar runs on
anything from **1.18.2 to 26.2**.

Addons can build on it: it registers a public API as a Bukkit service and, when
PlaceholderAPI is installed, exposes its numbers as placeholders.

## Installing

1. Drop the jar into the server's `plugins/` folder.
2. Start the server. The config files below are created.
3. The first start needs internet access: the server fetches HikariCP, the SQLite driver and
   the MariaDB driver from Maven Central into its own `libraries/` folder.

Vault is optional: install it plus an economy plugin if you want prices to do anything.
PlaceholderAPI is optional too, and the expansion registers itself when it is there.

## Configuration

```
plugins/ChorusCore/
├── config.yml            storage, economy switch
├── aliases.yml           extra names for every command
├── messages.yml          every line the plugin sends
└── modules/
    ├── homes.yml             /home /sethome /delhome /homes
    ├── warps.yml             /warp /warps /setwarp /delwarp
    ├── spawn.yml             /spawn /setspawn
    ├── teleport.yml          /tpa /tpahere /tpaccept /tpdeny /tpcancel /back
    ├── economy.yml           /pay /balance
    ├── chat.yml              /msg /reply /socialspy
    ├── players.yml           /afk /seen /playtime
    ├── items.yml             /hat /condense /clearinventory /itemname /lore
    ├── kits.yml              /kit /kits
    ├── staff.yml             /tp /tphere /tppos /tpall /vanish
    │                         /gamemode /gmc /gms /gma /gmsp
    ├── utility.yml           /heal /feed /fly /god /ping /fix /trash /speed
    │                         /top /near /invsee /ecsee /craft /anvil
    │                         /smithingtable /grindstone /stonecutter /loom
    │                         /cartography /enchanting /enderchest
    └── custom-commands.yml   the /discord and /rules of your server
```

`/chorus reload` re-reads all of them. The `storage` section, `aliases.yml` and adding or
removing a custom command are the three things that need a full restart.

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

The titles, the entry names and the lore under them live in `messages.yml`, so the wording
and the colours are yours as well as the layout.

### Aliases

`aliases.yml` holds every extra name. `plugin.yml` deliberately declares none, so this file
is the only place to look:

```yaml
delhome: [ removehome, remhome ]
tpa: [ call, tpask ]
```

An alias another plugin already owns is skipped rather than fought over.

## Commands

### Homes

| Command | Permission | Default |
| --- | --- | --- |
| `/sethome [name]` | `chorus.home.set` | everyone |
| `/home [name]` | `chorus.home.use` | everyone |
| `/delhome <name>` | `chorus.home.delete` | everyone |
| `/homes` | `chorus.home.list` | everyone |

With no name, `/home` and `/sethome` fall back to `homes.default-name`. A player with exactly
one home gets sent to it by a bare `/home` whatever it is called, unless you turn
`fallback-to-only-home` off.

### Warps

| Command | Permission | Default |
| --- | --- | --- |
| `/warp <name>` | `chorus.warp.use` | everyone |
| `/warps` | `chorus.warp.list` | everyone |
| `/setwarp <name>` | `chorus.warp.set` | op |
| `/delwarp <name>` | `chorus.warp.delete` | op |

### Spawn

| Command | Permission | Default |
| --- | --- | --- |
| `/spawn` | `chorus.spawn.use` | everyone |
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
| `/back` | `chorus.back.use` | everyone |

`/tpaccept` and `/tpdeny` answer the most recent request when no name is given. `/tpcancel`
with no name withdraws every request you sent.

### Utility

| Command | Permission | Default |
| --- | --- | --- |
| `/heal [player]` | `chorus.utility.heal` | op |
| `/feed [player]` | `chorus.utility.feed` | op |
| `/fly [player]` | `chorus.utility.fly` | op |
| `/god [player]` | `chorus.utility.god` | op |
| `/speed <1-10> [player]` | `chorus.utility.speed` | op |
| `/top` | `chorus.utility.top` | op |
| `/near [radius]` | `chorus.utility.near` | op |
| `/invsee <player>` | `chorus.utility.invsee` | op |
| `/ecsee <player>` | `chorus.utility.ecsee` | op |
| `/ping [player]` | `chorus.utility.ping` | everyone |
| `/fix [all]` | `chorus.utility.fix` | op |
| `/trash` | `chorus.utility.trash` | op |

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

### Economy

| Command | Permission | Default |
| --- | --- | --- |
| `/pay <player> <amount>` | `chorus.economy.pay` | everyone |
| `/balance [player]` | `chorus.economy.balance` | everyone |

Both need Vault plus an economy plugin; without one they say so and nothing else changes.
Payments go to online players only, are rounded to two decimals, and honour the minimum and
maximum in `economy.yml`. If the deposit fails after the money left the sender it is put
straight back.

`/balance <player>` needs `chorus.economy.balance.others`.

### Chat

| Command | Permission | Default |
| --- | --- | --- |
| `/msg <player> <message>` | `chorus.chat.msg` | everyone |
| `/reply <message>` | `chorus.chat.reply` | everyone |
| `/socialspy` | `chorus.chat.spy` | op |

`/reply` answers whoever you last spoke to, in either direction. `/socialspy` shows staff a
copy of everyone else's private messages; `spy-enabled` in `chat.yml` turns the whole thing
off server-wide.

Message text reaches chat as plain text, so a player cannot smuggle colour codes or
formatting into someone else's screen.

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

Each mode also needs its own permission, so a rank can have creative without spectator:
`chorus.staff.gamemode.creative`, `.survival`, `.adventure`, `.spectator`. Changing somebody
else needs `chorus.staff.gamemode.others`.

`chorus.staff.vanish.see` keeps a player able to see anyone vanished, and vanish is reapplied
when either side reconnects.

### Players

| Command | Permission | Default |
| --- | --- | --- |
| `/afk` | `chorus.players.afk` | everyone |
| `/seen <player>` | `chorus.players.seen` | everyone |
| `/playtime [player]` | `chorus.players.playtime` | everyone |

Players are marked away on their own after `auto-afk-minutes` of standing still, and come
back the moment they move, interact or type a command. `/playtime` reads the figure the
server already keeps, the same one the vanilla statistics screen shows.

### Items

| Command | Permission | Default |
| --- | --- | --- |
| `/hat` | `chorus.items.hat` | op |
| `/condense` | `chorus.items.condense` | op |
| `/clearinventory [player]` | `chorus.items.clearinventory` | op |
| `/itemname <text>` | `chorus.items.itemname` | op |
| `/lore <add\|set\|remove\|clear>` | `chorus.items.lore` | op |

`/condense` only takes untouched stacks: anything named, enchanted or damaged is left alone,
so a mistyped command can never eat a special item. What packs into what is listed in
`items.yml`.

`chorus.items.format` lets a player use MiniMessage tags in `/itemname` and `/lore`. Without
it their text is used exactly as typed.

### Kits

| Command | Permission | Default |
| --- | --- | --- |
| `/kit [name]` | `chorus.kits.use` | everyone |
| `/kits` | `chorus.kits.list` | everyone |

Kits live in `modules/kits.yml`, one block each: the items, what taking one costs, how long before
the same player may take it again, and whether it is one-time. `/kit` on its own opens the
same grid `/kits` does; `/kit <name>` takes one straight away.

Each kit also carries its own permission. Left out, it defaults to `chorus.kits.use.<name>`;
set it to `''` and anyone may take that kit. `kits.first-join` names the kit handed out the
first time a player ever joins.

Enchantments are written the way the game writes them today — `protection`, `sharpness`,
`unbreaking` — not the old `PROTECTION_ENVIRONMENTAL` spellings, so the same file works on
every supported version.

Anything that will not fit in the player's bags lands at their feet rather than vanishing.

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

`/chorus reload` — `chorus.admin`, op by default.

## Permissions worth knowing

| Permission | Effect |
| --- | --- |
| `chorus.home.limit.<n>` | Raises the cap to `n` homes. The highest number a player holds wins. |
| `chorus.home.unlimited` | No cap at all. |
| `chorus.warp.use.<name>` | Required per warp when `warps.per-warp-permission` is on. |
| `chorus.teleport.instant` | Skips every teleport warmup. |
| `chorus.bypass.cooldown` | Ignores every cooldown. |
| `chorus.bypass.price` | Never pays. |
| `chorus.back.ondeath` | Lets `/back` return to where the player died. |
| `chorus.economy.balance.others` | Reads someone else's balance. |
| `chorus.utility.invsee.edit` | Allows editing through `/invsee`, `/ecsee` needs its own. |
| `chorus.chat.spy` | Uses /socialspy and receives the copies. |
| `chorus.kits.use.<name>` | Required per kit, unless that kit sets its own permission. |

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
| `%chorus_balance%` | Their balance, formatted by the economy plugin. |
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

Three tables are created: `chorus_homes`, `chorus_locations` (warps and spawn points, under
different categories) and `chorus_kit_uses`. Every query runs off the main thread, so the
server never waits on the database.

## Building

You need a JDK 21 or newer. Nothing else: Gradle downloads itself.

```bash
./gradlew build
```

The jar lands in `build/libs/`. The same command runs the checks in `src/test/java`, which
hold the config files and the code to a single command list and exercise the SQL against a
throwaway SQLite file.

## License

MIT. See [LICENSE](LICENSE).
