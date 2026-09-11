# WalksyLibControl

A server plugin that switches off specific [WalksyLib](https://modrinth.com/mod/walksylib) mods, or single
features within them, on players' clients. Only mods that opt in client-side can be switched off.
Runs on Spigot, Paper and its forks (with Brigadier commands) and Folia, on Minecraft 1.20.5+ with
Java 21+.

## Install

Download `WalksyLibControl-<version>.jar` from [Modrinth](https://modrinth.com/project/walksylibcontrol)
and put it in `plugins/`.

On a Velocity/BungeeCord network, install it on each backend server.

## Switching things off

The mod ID comes from the mod's `fabric.mod.json`; feature IDs are whatever the mod passes to
`allowOptOutFromServer`. `status <player>` shows what one player's client allows.

```
/wlcontrol disable testmod                                  # the whole mod
/wlcontrol disable testmod zoom                             # one feature
/wlcontrol enable testmod zoom                              # back on
/wlcontrol disablewithreason testmod zoom "Zoom is unfair"  # players see the reason
```

- Reasons go in quotes, so they can't be mistaken for a feature.
- `disablewithreason` on something already off just changes its reason.
- Changes apply instantly. Players with `wlcontrol.bypass` are never affected.

## Rule sets

A rule set is a named list of restrictions, e.g. one per gamemode/kit (UHC, NethPot, Vanilla etc.). Plain `disable`/`enable` edit the
global set, which applies to everyone. A player also gets every rule set that is:

- bound to their world: `ruleset uhc world add uhc_1`
- applied to them: `player <player|@sel> apply uhc` (until `remove` or they leave)
- bound to a permission they have: `ruleset uhc permission server.gamemode.uhc` (checked on join and world change)

```
/wlcontrol ruleset create uhc
/wlcontrol ruleset uhc disable shieldstatus
/wlcontrol ruleset uhc world add uhc_1
/wlcontrol player %player% apply uhc    # e.g. from a minigame plugin's arena join
```

## Commands

All permissions start with `wlcontrol.`; `wlcontrol.admin`
(op by default) grants them all.

| Command | Permission |
|---|---|
| `disable[withreason] <modId> [feature] ["<reason>"]` | `command.edit` |
| `enable[withreason] <modId> [feature\|*] ["<reason>"]` (`*` = the mod and all its features) | `command.edit` |
| `status [player]` | `command.status` |
| `player <p\|@sel> disable\|enable[withreason] ...` / `apply\|remove <ruleset>` / `clear` | `command.player` |
| `ruleset list \| create <id> \| delete <id>` | `command.ruleset` |
| `ruleset <id> [info] \| disable ... \| enable ... \| clear` | `command.ruleset` |
| `ruleset <id> world add\|remove <world>` / `permission <node\|none>` | `command.ruleset` |
| `reload` | `command.reload` |

## Files

- `config.yml`: `log-client-reports` logs which mods each joining client lets the server switch off.
- `messages.yml`: every message the command sends.
- `rules.yml`: global and rule set restrictions.

## Developers

```groovy
repositories {
    maven { url = 'https://api.modrinth.com/maven' }
}
dependencies {
    compileOnly 'maven.modrinth:walksylibcontrol:<version>'
}
```

Add `depend: [WalksyLibControl]` to your `plugin.yml`.

```java
import main.walksy.lib.control.api.*;

ModControl control = ModControl.get();

RuleSet uhc = control.ruleSet("uhc")
        .disable("consumableoptimizer", "Consumable optimizer is not allowed on this server")
        .disableFeature("shieldstatus", "other-player-shields", "Seeing other players' shield status is disallowed in UHC");

uhc.applyTo(player);
uhc.removeFrom(player);
uhc.bindWorld("uhc_1").bindPermission("server.gamemode.uhc");

control.global().disable("anchoroptimizer");
control.player(player).disable("clientprojectiles");
control.player(player).isModDisabled("clientprojectiles");
```

- Rule sets made through the API aren't saved unless you call `.persistent(true)`.
- Events: `ClientModsReportedEvent` (a client reported what it allows) and
  `PlayerRestrictionsChangeEvent` (a player's restrictions changed and were sent).
