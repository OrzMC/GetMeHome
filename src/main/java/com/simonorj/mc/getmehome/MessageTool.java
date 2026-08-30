package com.simonorj.mc.getmehome;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class MessageTool {
    private static ClassLoader loader = null;

    private static String base(I18n i18n, Locale locale, Object... args) {
        String msg = getBundleString(i18n, locale);

        return String.format(msg, args);
    }

    private static String getBundleString(I18n i18n, Locale locale) {
        if (loader != null) {
            try {
                return ResourceBundle.getBundle("GetMeHome", locale, loader).getString(i18n.toString());
            } catch (NullPointerException | MissingResourceException ignore) {
            }
        }

        return ResourceBundle.getBundle("i18n.GetMeHome", locale).getString(i18n.toString());
    }

    static void reloadI18n(File i18nFolder) {
        if (!i18nFolder.isDirectory()) {
            loader = null;
            return;
        }

        try {
            URL[] urls = {i18nFolder.toURI().toURL()};
            loader = new URLClassLoader(urls);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            loader = null;
        }
    }

    public static String raw(I18n i18n, CommandSender p, Object... args) {
        return base(i18n, getLocale(p), args);
    }

    public static String prefixed(I18n i18n, CommandSender p, Object... args) {
        String pre = GetMeHome.getInstance().getPrefix();
        if (!pre.isEmpty()) pre += ' ';

        String focus = GetMeHome.getInstance().getFocusColor();
        String content = GetMeHome.getInstance().getContentColor();

        for (int i = args.length - 1; i >= 0; i--) {
            args[i] = focus + args[i].toString() + content;
        }

        return pre + content + base(i18n, getLocale(p), args);
    }

    public static String error(I18n i18n, CommandSender p, Object... args) {
        String pre = GetMeHome.getInstance().getPrefix();
        if (!pre.isEmpty()) pre += ' ';

        for (int i = args.length - 1; i >= 0; i--) {
            args[i] = args[i].toString();
        }

        return pre + "§c" + base(i18n, getLocale(p), args);
    }

    private static Locale getLocale(CommandSender sender) {
        if (!(sender instanceof Player p))
            return Locale.getDefault();
        return p.locale();
    }
}
