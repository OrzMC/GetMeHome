package com.simonorj.mc.getmehome.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.simonorj.mc.getmehome.GetMeHome;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Registers every command through Paper 26.x {@code LifecycleEvents.COMMANDS} + Brigadier.
 *
 * <p>On Paper 26.2 YAML command declarations are no longer the authoritative registration
 * path, and other plugins (e.g. Essentials) can pre-empt the Bukkit bridge in the native
 * Brigadier tree. Registering natively here keeps GetMeHome's own suggestions/execution
 * in charge. The actual command logic is delegated to the existing executors
 * ({@link HomeCommands}, {@link ListHomesCommand}, {@link MetaCommand}) untouched.
 *
 * <p>Every node is gated with {@code .requires(...)}: senders lacking the permission see
 * "Unknown or incomplete command" instead of an explicit denial. This is an intentional
 * security practice (hiding the command's existence), consistent with native Paper 26.x
 * command behavior.
 */
public class CommandRegistrar {
    private static final String GLOBAL_FLAG = "-global";
    private static final String GLOBAL_SHORT_FLAG = "-g";

    private final GetMeHome plugin;
    private final HomeCommands homeCommands;
    private final ListHomesCommand listHomesCommand;
    private final MetaCommand metaCommand;

    public CommandRegistrar(GetMeHome plugin) {
        this.plugin = plugin;
        this.homeCommands = new HomeCommands(plugin);
        this.listHomesCommand = new ListHomesCommand(plugin);
        this.metaCommand = new MetaCommand();
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(
                    Commands.literal("home")
                            .requires(src -> src.getSender().hasPermission("getmehome.command.home"))
                            .executes(ctx -> run(ctx, "home", new String[0]))
                            .then(Commands.argument("target", StringArgumentType.string())
                                    .suggests((ctx, builder) -> suggestFirstArg(ctx, builder, "getmehome.command.home.other"))
                                    .executes(ctx -> run(ctx, "home", new String[]{arg(ctx, "target")}))
                                    .then(Commands.argument("home", StringArgumentType.string())
                                            .requires(src -> src.getSender().hasPermission("getmehome.command.home.other"))
                                            .suggests(this::suggestPlayerHome)
                                            .executes(ctx -> run(ctx, "home", new String[]{arg(ctx, "target"), arg(ctx, "home")}))
                                    )
                            )
                            .build(),
                    "Sends you home",
                    List.of("h")
            );

            commands.register(
                    Commands.literal("sethome")
                            .requires(src -> src.getSender().hasPermission("getmehome.command.sethome"))
                            .executes(ctx -> run(ctx, "sethome", new String[0]))
                            .then(Commands.argument("target", StringArgumentType.string())
                                    .suggests((ctx, builder) -> suggestFirstArg(ctx, builder, "getmehome.command.sethome.other"))
                                    .executes(ctx -> run(ctx, "sethome", new String[]{arg(ctx, "target")}))
                                    .then(Commands.argument("home", StringArgumentType.string())
                                            .requires(src -> src.getSender().hasPermission("getmehome.command.sethome.other"))
                                            .suggests(this::suggestPlayerHome)
                                            .executes(ctx -> run(ctx, "sethome", new String[]{arg(ctx, "target"), arg(ctx, "home")}))
                                    )
                            )
                            .build(),
                    "Sets home at your current position"
            );

            commands.register(
                    Commands.literal("setdefaulthome")
                            .requires(src -> src.getSender().hasPermission("getmehome.command.setdefaulthome"))
                            .executes(ctx -> run(ctx, "setdefaulthome", new String[0]))
                            .then(Commands.argument("home", StringArgumentType.string())
                                    .suggests(this::suggestOwnHomes)
                                    .executes(ctx -> run(ctx, "setdefaulthome", new String[]{arg(ctx, "home")}))
                            )
                            .build(),
                    "Sets a different home name as the default home."
            );

            commands.register(
                    Commands.literal("delhome")
                            .requires(src -> src.getSender().hasPermission("getmehome.command.delhome"))
                            .executes(ctx -> run(ctx, "delhome", new String[0]))
                            .then(Commands.argument("target", StringArgumentType.string())
                                    .suggests((ctx, builder) -> suggestFirstArg(ctx, builder, "getmehome.command.delhome.other"))
                                    .executes(ctx -> run(ctx, "delhome", new String[]{arg(ctx, "target")}))
                                    .then(Commands.argument("home", StringArgumentType.string())
                                            .requires(src -> src.getSender().hasPermission("getmehome.command.delhome.other"))
                                            .suggests(this::suggestPlayerHome)
                                            .executes(ctx -> run(ctx, "delhome", new String[]{arg(ctx, "target"), arg(ctx, "home")}))
                                    )
                            )
                            .build(),
                    "Deletes a set home"
            );

            commands.register(
                    Commands.literal("listhomes")
                            .requires(src -> src.getSender().hasPermission("getmehome.command.listhomes"))
                            .executes(ctx -> run(ctx, "listhomes", new String[0]))
                            .then(Commands.argument("arg1", StringArgumentType.string())
                                    .suggests(this::suggestListHomesFirstArg)
                                    .executes(ctx -> run(ctx, "listhomes", new String[]{arg(ctx, "arg1")}))
                                    .then(Commands.argument("arg2", StringArgumentType.string())
                                            .suggests(this::suggestListHomesSecondArg)
                                            .executes(ctx -> run(ctx, "listhomes", new String[]{arg(ctx, "arg1"), arg(ctx, "arg2")}))
                                    )
                            )
                            .build(),
                    "Lists all the homes",
                    List.of("homes")
            );

            commands.register(
                    Commands.literal("getmehome")
                            .executes(ctx -> run(ctx, "getmehome", new String[0]))
                            .then(Commands.argument("action", StringArgumentType.string())
                                    .suggests(this::suggestMetaAction)
                                    .executes(ctx -> run(ctx, "getmehome", new String[]{arg(ctx, "action")}))
                            )
                            .build(),
                    "GetMeHome's main (help) command"
            );
        });
    }

    /** Bridges a Brigadier invocation back to the shared executor logic. */
    private int run(CommandContext<CommandSourceStack> ctx, String command, String[] args) {
        CommandSender sender = ctx.getSource().getSender();
        switch (command) {
            case "home":
            case "sethome":
            case "setdefaulthome":
            case "delhome":
                return homeCommands.onCommand(sender, command, command, args) ? 1 : 0;
            case "listhomes":
                return listHomesCommand.execute(sender, command, args) ? 1 : 0;
            case "getmehome":
                return metaCommand.execute(sender, args) ? 1 : 0;
            default:
                return 0;
        }
    }

    private static String arg(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    /**
     * Suggests a home name, wrapping it in double quotes when it contains characters outside
     * Brigadier's {@code word()} set ({@code [0-9A-Za-z._-]}). Quoting keeps the completion a
     * single token so the client-side {@code string()} argument parses it back exactly, e.g.
     * {@code "foo:bar"}. Plain names are suggested unchanged.
     */
    private static void suggestHomeName(SuggestionsBuilder builder, String name) {
        if (name.matches(".*[^0-9A-Za-z._-].*")) {
            builder.suggest("\"" + name + "\"");
        } else {
            builder.suggest(name);
        }
    }

    /**
     * First argument of /home, /sethome, /delhome: the player's own home names, plus online
     * player names when the sender holds the {@code *.other} permission (mirrors the legacy
     * {@code onTabComplete} for {@code args.length == 1}).
     */
    private CompletableFuture<Suggestions> suggestFirstArg(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, String otherPerm) {
        suggestOwnHomes(ctx, builder);
        CommandSender sender = ctx.getSource().getSender();
        String start = builder.getRemainingLowerCase();

        if (otherPerm != null && sender.hasPermission(otherPerm)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(start)) {
                    builder.suggest(p.getName());
                }
            }
        }
        return builder.buildFuture();
    }

    /**
     * First argument of /setdefaulthome (and the own-home part of /home, /sethome,
     * /delhome): only the sender's own home names (mirrors the legacy {@code onTabComplete}
     * for {@code args.length == 1}).
     */
    private CompletableFuture<Suggestions> suggestOwnHomes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player) {
            String start = builder.getRemainingLowerCase();
            for (String n : plugin.getStorage().getAllHomes(((Player) sender).getUniqueId()).keySet()) {
                if (n.toLowerCase().startsWith(start)) {
                    suggestHomeName(builder, n);
                }
            }
        }
        return builder.buildFuture();
    }

    /**
     * Second argument of /home, /sethome, /delhome: home names of the target player
     * (mirrors the legacy {@code onTabComplete} for {@code args.length == 2}).
     */
    private CompletableFuture<Suggestions> suggestPlayerHome(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String playerName = ctx.getArgument("target", String.class);
        UUID uuid = plugin.getStorage().getUniqueID(playerName);
        if (uuid != null) {
            String start = builder.getRemainingLowerCase();
            for (String n : plugin.getStorage().getAllHomes(uuid).keySet()) {
                if (n.toLowerCase().startsWith(start)) {
                    suggestHomeName(builder, n);
                }
            }
        }
        return builder.buildFuture();
    }

    /** First argument of /listhomes: online players (with {@code *.other}) and the -global flag. */
    private CompletableFuture<Suggestions> suggestListHomesFirstArg(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSender sender = ctx.getSource().getSender();
        String lower = builder.getRemainingLowerCase();

        if (sender.hasPermission("getmehome.command.listhomes.other")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(lower)) {
                    builder.suggest(p.getName());
                }
            }
        }
        if (GLOBAL_FLAG.startsWith(lower)) {
            builder.suggest(GLOBAL_FLAG);
        }
        return builder.buildFuture();
    }

    /** Second argument of /listhomes: only valid after -global/-g with the {@code *.other} permission. */
    private CompletableFuture<Suggestions> suggestListHomesSecondArg(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String arg1 = ctx.getArgument("arg1", String.class);
        if (!(GLOBAL_FLAG.equalsIgnoreCase(arg1) || GLOBAL_SHORT_FLAG.equalsIgnoreCase(arg1))
                || !ctx.getSource().getSender().hasPermission("getmehome.command.listhomes.other")) {
            return builder.buildFuture();
        }
        String lower = builder.getRemainingLowerCase();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(lower)) {
                builder.suggest(p.getName());
            }
        }
        return builder.buildFuture();
    }

    /** First argument of /getmehome: reload/clearcache for senders with the reload permission. */
    private CompletableFuture<Suggestions> suggestMetaAction(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission("getmehome.reload")) {
            return builder.buildFuture();
        }
        String low = builder.getRemainingLowerCase();
        if ("reload".startsWith(low)) {
            builder.suggest("reload");
        }
        if ("clearcache".startsWith(low)) {
            builder.suggest("clearcache");
        }
        return builder.buildFuture();
    }
}
