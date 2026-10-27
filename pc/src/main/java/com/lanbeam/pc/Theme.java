package com.lanbeam.pc;

import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Colors and fonts shared by the whole PC UI (same palette as the Android app). */
final class Theme {
    static boolean dark;

    static Color BG, CARD, STROKE, PRIMARY, ON_PRIMARY, TONAL, ON_TONAL, TEXT, DIM, SUCCESS, DANGER, HEADER1, HEADER2, TRACK;

    private Theme() {}

    /** mode: "light", "dark" or anything else for "follow Windows". */
    static void init(String mode) {
        dark = "dark".equals(mode) || (!"light".equals(mode) && systemDark());
        if (dark) {
            BG = new Color(0x0F131A);
            CARD = new Color(0x1A2030);
            STROKE = new Color(0x2B3347);
            PRIMARY = new Color(0x90CAF9);
            ON_PRIMARY = new Color(0x0B1B2E);
            TONAL = new Color(0x243350);
            ON_TONAL = new Color(0xBBDEFB);
            TEXT = new Color(0xE8EBF2);
            DIM = new Color(0x9AA3B5);
            SUCCESS = new Color(0x81C784);
            DANGER = new Color(0xEF9A9A);
            HEADER1 = new Color(0x0D47A1);
            HEADER2 = new Color(0x00695C);
            TRACK = new Color(0x2B3347);
        } else {
            BG = new Color(0xF4F6FB);
            CARD = Color.WHITE;
            STROKE = new Color(0xE1E6F0);
            PRIMARY = new Color(0x1565C0);
            ON_PRIMARY = Color.WHITE;
            TONAL = new Color(0xE3EDFB);
            ON_TONAL = new Color(0x0D47A1);
            TEXT = new Color(0x1B1F2A);
            DIM = new Color(0x5D6678);
            SUCCESS = new Color(0x2E7D32);
            DANGER = new Color(0xC62828);
            HEADER1 = new Color(0x1565C0);
            HEADER2 = new Color(0x00897B);
            TRACK = new Color(0xDCE3F0);
        }
    }

    /** Applies the colors to dialogs (JOptionPane, file chooser, text fields) too. */
    static void applyToDialogs() {
        if (!dark) return;
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
        String[] bg = {"Panel.background", "OptionPane.background", "TextField.background", "ComboBox.background",
                "List.background", "Viewport.background", "ScrollPane.background", "FileChooser.background",
                "ToolTip.background", "Table.background", "TextArea.background", "FormattedTextField.background",
                "Button.background", "ComboBox.buttonBackground", "ScrollBar.background", "ScrollBar.track"};
        String[] fg = {"Label.foreground", "OptionPane.messageForeground", "TextField.foreground", "ComboBox.foreground",
                "List.foreground", "Button.foreground", "TextField.caretForeground", "ToolTip.foreground",
                "Table.foreground", "TextArea.foreground", "FormattedTextField.foreground", "OptionPane.foreground"};
        for (String k : bg) UIManager.put(k, new ColorUIResource(k.startsWith("TextField") || k.startsWith("Combo") || k.startsWith("List") || k.startsWith("Button") ? TONAL : CARD));
        for (String k : fg) UIManager.put(k, new ColorUIResource(TEXT));
        UIManager.put("List.selectionBackground", new ColorUIResource(PRIMARY));
        UIManager.put("List.selectionForeground", new ColorUIResource(ON_PRIMARY));
    }

    static Font font(float size, int style) {
        return new Font("Segoe UI", style, 1).deriveFont(style, size);
    }

    private static boolean systemDark() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("AppsUseLightTheme")) return line.trim().endsWith("0x0");
            }
        } catch (Exception ignored) {}
        return false;
    }
}
