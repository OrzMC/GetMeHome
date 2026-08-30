package com.simonorj.mc.getmehome.command;

import com.google.common.collect.ImmutableList;
import com.simonorj.mc.getmehome.GetMeHome;
import com.simonorj.mc.getmehome.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.simonorj.mc.getmehome.MessageTool.prefixed;
import static com.simonorj.mc.getmehome.MessageTool.raw;

public class MetaCommand implements TabExecutor {
    private static final String RELOAD_PERM = "getmehome.reload";
    private final GetMeHome plugin = GetMeHome.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length != 0 && sender.hasPermission(RELOAD_PERM)) {
            if (args[0].equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                plugin.loadConfig();
                plugin.loadStorage();

                sender.sendMessage(Component.text("Configuration reloaded successfully.").color(NamedTextColor.GREEN));
                return true;
            }

            if (args[0].equalsIgnoreCase("clearcache")) {
                plugin.getStorage().clearCache();
                sender.sendMessage(Component.text("Cache cleared.").color(NamedTextColor.GREEN));
                return true;
            }
        }

        sender.sendMessage(prefixed(I18n.CMD_META_HEADING, sender, plugin.getPluginMeta().getVersion(), plugin.getPluginMeta().getAuthors().getFirst()));
        sender.sendMessage(prefixed(I18n.CMD_META_TRANSLATED, sender, raw(I18n.LANGUAGE_TRANSLATED_BY, sender)));
        return true;
    }

    /**
     * Entry point for the Paper 26.x Brigadier path (see {@link CommandRegistrar}).
     * The legacy {@link Command} arguments are unused by the meta logic.
     */
    public boolean execute(@NotNull CommandSender sender, String[] args) {
        return onCommand(sender, null, "getmehome", args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (sender.hasPermission(RELOAD_PERM) && args.length == 1) {
            ImmutableList.Builder<String> ret = ImmutableList.builder();

            String low = args[0].toLowerCase();
            if ("reload".startsWith(low))
                ret.add("reload");

            if ("clearcache".startsWith(low))
                ret.add("clearcache");

            return ret.build();
        }
        return ImmutableList.of();
    }
}
