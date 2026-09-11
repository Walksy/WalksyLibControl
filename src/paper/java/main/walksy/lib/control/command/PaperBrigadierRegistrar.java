package main.walksy.lib.control.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Predicate;

public final class PaperBrigadierRegistrar implements CommandRegistrar {

    @Override
    public boolean register(final JavaPlugin plugin, final ModControlCommand command) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(new Tree(command).root().build(), DESCRIPTION, ModControlCommand.ALIASES));
        return true;
    }

    private static final class Tree {
        private final ModControlCommand command;
        private final Command<CommandSourceStack> run;
        private final SuggestionProvider<CommandSourceStack> suggest;

        private Tree(final ModControlCommand command) {
            this.command = command;
            this.run = this::run;
            this.suggest = (context, builder) -> {
                final String[] tokens = tokens(builder.getInput(), true);
                for (final String option : this.command.complete(context.getSource().getSender(), label(builder.getInput()),
                        tokens)) {
                    builder.suggest(option);
                }
                return builder.buildFuture();
            };
        }

        private int run(final CommandContext<CommandSourceStack> context) {
            this.command.execute(context.getSource().getSender(), label(context.getInput()),
                    tokens(context.getInput(), false));
            return Command.SINGLE_SUCCESS;
        }

        private static String label(final String input) {
            final String trimmed = input.startsWith("/") ? input.substring(1) : input;
            final int space = trimmed.indexOf(' ');
            return space < 0 ? trimmed : trimmed.substring(0, space);
        }

        private static String[] tokens(final String input, final boolean keepTrailing) {
            final String trimmed = input.startsWith("/") ? input.substring(1) : input;
            final int space = trimmed.indexOf(' ');
            if (space < 0) {
                return new String[0];
            }
            final String rest = trimmed.substring(space + 1);
            return keepTrailing ? rest.split(" ", -1) : rest.isBlank() ? new String[0] : rest.split(" ");
        }

        private static Predicate<CommandSourceStack> permission(final String node) {
            return source -> ControlPermissions.has(source.getSender(), node);
        }

        private RequiredArgumentBuilder<CommandSourceStack, String> word(final String name) {
            return Commands.argument(name, StringArgumentType.word()).suggests(this.suggest);
        }

        private LiteralArgumentBuilder<CommandSourceStack> root() {
            final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ModControlCommand.NAME)
                    .requires(source -> ControlPermissions.any(source.getSender()))
                    .executes(this.run);
            root.then(Commands.literal("help").executes(this.run));
            root.then(Commands.literal("status").requires(permission(ControlPermissions.STATUS)).executes(this.run)
                    .then(this.word("player").executes(this.run)));
            for (final String verb : ModControlCommand.EDITS) {
                root.then(this.edit(verb).requires(permission(ControlPermissions.EDIT)));
            }
            root.then(this.player().requires(permission(ControlPermissions.PLAYER)));
            root.then(this.ruleSet().requires(permission(ControlPermissions.RULE_SET)));
            root.then(Commands.literal("reload").requires(permission(ControlPermissions.RELOAD)).executes(this.run));
            return root;
        }

        private LiteralArgumentBuilder<CommandSourceStack> edit(final String verb) {
            final boolean enable = verb.startsWith(ModControlCommand.ENABLE);
            final boolean withReason = verb.endsWith("withreason");
            final RequiredArgumentBuilder<CommandSourceStack, String> modId = this.word("modId");
            if (withReason) {
                modId.then(this.reason());
                modId.then(this.word("feature").then(this.reason()));
                if (enable) {
                    modId.then(Commands.literal("*").then(this.reason()));
                }
            } else {
                modId.executes(this.run);
                modId.then(this.word("feature").executes(this.run));
                if (enable) {
                    modId.then(Commands.literal("*").executes(this.run));
                }
            }
            return Commands.literal(verb).then(modId);
        }

        private RequiredArgumentBuilder<CommandSourceStack, String> reason() {
            return Commands.argument("reason", StringArgumentType.string()).executes(this.run);
        }

        private LiteralArgumentBuilder<CommandSourceStack> player() {
            final RequiredArgumentBuilder<CommandSourceStack, ?> target = Commands.argument("player", ArgumentTypes.players());
            for (final String verb : ModControlCommand.EDITS) {
                target.then(this.edit(verb));
            }
            target.then(Commands.literal("apply").then(this.word("ruleset").executes(this.run)));
            target.then(Commands.literal("remove").then(this.word("ruleset").executes(this.run)));
            target.then(Commands.literal("clear").executes(this.run));
            return Commands.literal("player").then(target);
        }

        private LiteralArgumentBuilder<CommandSourceStack> ruleSet() {
            final RequiredArgumentBuilder<CommandSourceStack, String> id = this.word("id").executes(this.run);
            id.then(Commands.literal("info").executes(this.run));
            for (final String verb : ModControlCommand.EDITS) {
                id.then(this.edit(verb));
            }
            id.then(Commands.literal("clear").executes(this.run));
            id.then(Commands.literal("world")
                    .then(Commands.literal("add").then(this.word("world").executes(this.run)))
                    .then(Commands.literal("remove").then(this.word("world").executes(this.run))));
            id.then(Commands.literal("permission").then(this.word("node").executes(this.run)));
            return Commands.literal("ruleset")
                    .then(Commands.literal("list").executes(this.run))
                    .then(Commands.literal("create").then(Commands.argument("id", StringArgumentType.word()).executes(this.run)))
                    .then(Commands.literal("delete").then(this.word("id").executes(this.run)))
                    .then(id);
        }
    }
}
