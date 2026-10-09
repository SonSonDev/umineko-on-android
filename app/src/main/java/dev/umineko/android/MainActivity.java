package dev.umineko.android;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class MainActivity extends SDLActivity {
    // Le jeu (script 07th-Mod) est rendu en 960x540
    private static final float GAME_RATIO = 540f / 960f;
    // En histoire (portrait), la scene est rognee sur les cotes : zone 4:3
    private static final float STORY_RATIO = 3f / 4f;

    private static MainActivity instance;

    private LinearLayout root;
    private LinearLayout panel;
    private TextView pageText;
    private TextView speakerText;
    private TextView hintText;
    private float textSp = 23f;
    private SharedPreferences prefs;

    private static native void nativeSetPortraitText(int on);
    private static native void nativePressButton(int id);

    // Le systeme doit creer le dossier de l'appli (droits corrects) avant tout push adb
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new File(getExternalFilesDir(null), "game").mkdirs();
        super.onCreate(savedInstanceState);
        instance = this;
        prefs = getSharedPreferences("ui", MODE_PRIVATE);
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        float defaultSp = Math.max(17f, Math.min(26f, widthDp * 0.056f));
        textSp = prefs.getFloat("textSp", defaultSp);
        buildLayout();
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "c++_shared", "SDL2", "SDL2_image", "SDL2_mixer", "main" };
    }

    // Le jeu est lu dans /sdcard/Android/data/<package>/files/game
    @Override
    protected String[] getArguments() {
        File base = getExternalFilesDir(null);
        File game = new File(base, "game");
        File save = new File(base, "save");
        game.mkdirs();
        save.mkdirs();
        return new String[] { "-r", game.getAbsolutePath() + "/", "-s", save.getAbsolutePath() + "/" };
    }

    // ---------------------------------------------------------------- texte du jeu

    // Appele par le moteur (thread SDL) quand une page de texte attend un clic
    public static void onGamePage(byte[] raw, int newPage, String window) {
        final String text = new String(stripCodes(raw), StandardCharsets.UTF_8);
        final String who = speakerName(window);
        final MainActivity a = instance;
        if (a == null) return;
        // la frappe envoie une page par lettre : on ne garde que la derniere par frame
        pendingText = text;
        pendingWho = who;
        if (pendingPosted.compareAndSet(false, true)) {
            a.runOnUiThread(() -> {
                pendingPosted.set(false);
                a.showPage(pendingText, pendingWho);
            });
        }
    }

    private static volatile String pendingText = "", pendingWho = "";
    private static final java.util.concurrent.atomic.AtomicBoolean pendingPosted =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // Codes de mise en forme du moteur (0x10-0x16 sans argument, 0x17-0x1e + 2 octets, 0x1f + 1 octet)
    private static byte[] stripCodes(byte[] raw) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(raw.length);
        for (int i = 0; i < raw.length; i++) {
            int b = raw[i] & 0xff;
            if (b >= 0x10 && b < 0x17) continue;
            if (b >= 0x17 && b <= 0x1e) { i += 2; continue; }
            if (b == 0x1f) { i += 1; continue; }
            out.write(b);
        }
        return out.toByteArray();
    }

    // Le nom du personnage est incruste dans l'image de la boite : textbox\normal\NN.png
    private static final java.util.HashMap<String, String> SPEAKERS = new java.util.HashMap<>();
    static {
        String[] names = {
            "01", "Ushiromiya Kinzo", "02", "Ushiromiya Krauss", "03", "Ushiromiya Natsuhi",
            "04", "Ushiromiya Jessica", "05", "Ushiromiya Eva", "06", "Ushiromiya Hideyoshi",
            "07", "Ushiromiya George", "08", "Ushiromiya Rudolf", "09", "Ushiromiya Kyrie",
            "10", "Ushiromiya Battler", "11", "Ushiromiya Ange", "12", "Ushiromiya Rosa",
            "13", "Ushiromiya Maria", "14", "Ronoue Genji", "15", "Shannon", "16", "Kanon",
            "17", "Gohda Toshiro", "18", "Kumasawa Chiyo", "19", "Nanjo Terumasa",
            "20", "Amakusa Juuza", "21", "Okonogi Tetsuro", "22", "Sumadera Kasumi",
            "23", "Professor Ootsuki", "24", "Captain Kawabata", "25", "Masayuki Nanjo",
            "26", "Sabakichi Kumasawa", "27", "Beatrice", "28", "Bernkastel", "29", "Lambdadelta",
            "30", "Virgilia", "31", "Ronove", "32", "Gaap", "33", "Sakutarou", "34", "EVA-Beatrice",
            "35", "Chiester 45", "36", "Chiester 410", "37", "Chiester 00", "38", "Lucifer",
            "39", "Leviathan", "40", "Satan", "41", "Belphegor", "42", "Mammon", "43", "Beelzebub",
            "44", "Asmodeus", "45", "Goat Butlers", "46", "Furudo Erika", "47", "Dlanor A. Knox",
            "48", "Gertrude", "49", "Cornelia", "50", "Featherine Augustus Aurora", "51", "Zepar",
            "52", "Furfur", "53", "Ushiromiya Lion", "54", "Willard H. Wright",
            "55", "Clair Vaux Bernardus", "56", "Hachijo Ikuko", "57", "Hachijo Tohya",
            "70", "ANGE-Beatrice", "71", "Beatrice the Elder", "72", "Kotobuki Yukari" };
        for (int i = 0; i + 1 < names.length; i += 2) SPEAKERS.put(names[i], names[i + 1]);
    }

    private static String speakerName(String window) {
        if (window == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]{2})[.]png$").matcher(window);
        if (!m.find()) return "";
        String n = SPEAKERS.get(m.group(1));
        return n == null ? "" : n;
    }

    // Boutons du menu du jeu (clic droit...) : affiches par l'interface, pas sur la scene
    public static void onGameButtons(byte[] raw) {
        final String data = new String(raw, StandardCharsets.UTF_8);
        final MainActivity a = instance;
        if (a != null) a.runOnUiThread(() -> a.showMenu(data));
    }

    private static final java.util.HashMap<String, String> BUTTON_NAMES = new java.util.HashMap<>();
    static {
        BUTTON_NAMES.put("r_btn_clear", "Hide text");
        BUTTON_NAMES.put("r_btn_language", "Switch language");
        BUTTON_NAMES.put("r_btn_system", "System (save, load, title...)");
        BUTTON_NAMES.put("r_btn_oimages", "Original sprites");
        BUTTON_NAMES.put("r_btn_nimages", "New sprites");
        BUTTON_NAMES.put("volume", "Options (volume, ADV mode...)");
        BUTTON_NAMES.put("start", "Start");
        BUTTON_NAMES.put("web", "Controls");
        BUTTON_NAMES.put("exit1", "Exit");
        BUTTON_NAMES.put("website", "07th Expansion website (Japanese only)");
        BUTTON_NAMES.put("ep1", "Episode 1");
        BUTTON_NAMES.put("ep2_1", "Episode 2");
        BUTTON_NAMES.put("ep3_1", "Episode 3");
        BUTTON_NAMES.put("ep4_1", "Episode 4");
        BUTTON_NAMES.put("load", "Load game");
        BUTTON_NAMES.put("unlock", "Unlock");
        for (int pg = 1; pg <= 5; pg++) {
            BUTTON_NAMES.put("page" + pg, "Page " + pg);
            BUTTON_NAMES.put("page" + pg + "_b", "Page " + pg + " (current)");
        }
        BUTTON_NAMES.put("english_textbutton", "English");
        BUTTON_NAMES.put("japanese_textbutton", "Japanese");
    }

    // Libelle d'un bouton : texte du sprite (":s#FFFFFF^~c4~Texte") ou nom du fichier image
    private static String buttonLabel(String imageName, String fileName) {
        String src = (imageName != null && imageName.contains("^")) ? imageName : "";
        if (!src.isEmpty()) {
            String t = src.substring(src.indexOf('^') + 1).replaceAll("~[^~]*~", "").replace("^", "").trim();
            if (!t.isEmpty()) return t;
        }
        String f = (fileName != null && !fileName.isEmpty()) ? fileName : imageName;
        if (f == null) return "";
        int cut = Math.max(Math.max(f.lastIndexOf((char) 92), f.lastIndexOf('/')), f.lastIndexOf(';'));
        String base = f.substring(cut + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        String nice = BUTTON_NAMES.get(base.toLowerCase());
        if (nice != null) return nice;
        // nom inconnu : "some_name" -> "Some name"
        String pretty = base.replace('_', ' ').trim();
        if (!pretty.isEmpty()) pretty = Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
        return pretty;
    }

    // Ecrans a choix (select) : invite + boutons natifs
    public static void onGameChoices(byte[] raw) {
        final String data = new String(stripCodes(raw), StandardCharsets.UTF_8);
        final MainActivity a = instance;
        if (a != null) a.runOnUiThread(() -> a.showChoices(data));
    }

    // Retire les marques de mise en forme du script : ^, ~c4~, #RRGGBB, > initial
    private static String cleanChoice(String t) {
        String s = t.replaceAll("~[^~]*~", "").replace("^", "").replaceAll("#[0-9A-Fa-f]{6}", "").trim();
        while (s.startsWith(">")) s = s.substring(1).trim();
        return s;
    }

    private LinearLayout choiceBox;
    private TextView choiceHeader;
    private LinearLayout choiceButtons;

    private void showChoices(String data) {
        choiceButtons.removeAllViews();
        choicesShown = !data.isEmpty();
        if (data.isEmpty()) {
            choiceBox.setVisibility(View.GONE);
            return;
        }
        String[] lines = data.split(String.valueOf((char) 10));
        String head = lines.length > 0 ? lines[0].replace((char) 1, (char) 10) : "";
        head = head.replaceAll("[ ]{2,}", " ").trim();
        choiceHeader.setText(head);
        choiceHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(15f, textSp * 0.82f));
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split(String.valueOf((char) 9), 2);
            if (f.length < 2) continue;
            final int id;
            try { id = Integer.parseInt(f[0]); } catch (NumberFormatException e) { continue; }
            String label = cleanChoice(f[1]);
            if (label.isEmpty()) continue;
            Button b = makeButton(label, 18, Color.rgb(52, 46, 70));
            b.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                nativePressButton(id);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
            lp.setMargins(dp(16), dp(5), dp(16), dp(5));
            choiceButtons.addView(b, lp);
        }
        hintText.setVisibility(View.GONE);
        choiceBox.setVisibility(View.VISIBLE);
    }

    // Pages d'intro dont le texte est incruste dans l'image : texte natif a la place
    public static void onGameNote(int id) {
        final MainActivity a = instance;
        if (a != null) a.runOnUiThread(() -> a.showNote(id));
    }

    private TextView noteView;
    private ScrollView noteScroll;

    private void showNote(int id) {
        if (id == 0) {
            noteScroll.setVisibility(View.GONE);
            return;
        }
        String nl2 = String.valueOf((char) 10) + (char) 10;
        String body = "New function: TIPS MODE" + nl2
                + "In the middle of the game, at any time, tap Menu to open the TIPS MODE." + nl2
                + "Besides the save and load functions, you can view character profiles and more. "
                + "Additional information will be added as you progress through the story. "
                + "It is a good idea to take a peek here every once in a while.";
        if (id == 3) body += nl2 + "Tap to start the game.";
        noteView.setText(body);
        noteView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(15f, textSp * 0.82f));
        noteScroll.setVisibility(View.VISIBLE);
        noteScroll.scrollTo(0, 0);
        hintText.setVisibility(View.GONE);
    }

    private LinearLayout menuList;
    private ScrollView menuView;

    private static final class MenuItem {
        int id, x, y;
        String label;
        String info = "";
    }

    private Button menuButton(MenuItem it, int sp) {
        String text = it.label;
        if (it.label.toLowerCase().startsWith("savearea")) {
            int slot = it.id > 50 ? ((it.id - 51) % 8) + 1 : it.id;
            text = "Slot " + slot + (it.info.isEmpty() ? "  -  empty" : "  -  " + it.info);
        }
        Button b = makeButton(text, sp, Color.rgb(52, 46, 70));
        b.setMinHeight(dp(58));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        final int id = it.id;
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            nativePressButton(id);
        });
        return b;
    }

    private boolean choicesShown = false;

    private void showMenu(String data) {
        if (choicesShown && data.isEmpty()) return; // les choix (select) occupent deja cette zone
        menuList.removeAllViews();
        java.util.ArrayList<MenuItem> items = new java.util.ArrayList<>();
        for (String line : data.split(String.valueOf((char) 10))) {
            if (line.isEmpty()) continue;
            String[] f = line.split(String.valueOf((char) 9), -1);
            if (f.length < 3) continue;
            MenuItem it = new MenuItem();
            try {
                it.id = Integer.parseInt(f[0]);
                if (f.length >= 5) { it.x = Integer.parseInt(f[3]); it.y = Integer.parseInt(f[4]); }
            } catch (NumberFormatException e) { continue; }
            it.label = buttonLabel(f[1], f[2]);
            if (it.label.isEmpty()) continue;
            if (f.length >= 6 && f[2].toLowerCase().contains("savearea")) {
                StringBuilder sb = new StringBuilder();
                for (String part : f[5].split(String.valueOf((char) 2))) {
                    String tagless = part.startsWith(":") && part.indexOf(';') > 0 ? part.substring(part.indexOf(';') + 1) : part;
                    String c = cleanChoice(tagless);
                    if (!c.isEmpty()) {
                        if (sb.length() > 0) sb.append(" - ");
                        sb.append(c);
                    }
                }
                it.info = sb.toString();
            }
            items.add(it);
        }
        // ordre de lecture : de haut en bas, puis de gauche a droite
        // emplacements de sauvegarde : une seule ligne virtuelle, classee par numero (ordre total coherent)
        int slotY = Integer.MAX_VALUE;
        for (MenuItem it : items)
            if (it.label.toLowerCase().startsWith("savearea")) slotY = Math.min(slotY, it.y);
        for (MenuItem it : items)
            if (it.label.toLowerCase().startsWith("savearea")) { it.y = slotY; it.x = it.id; }
        java.util.Collections.sort(items, (p, q) ->
                p.y != q.y ? Integer.compare(p.y, q.y) : Integer.compare(p.x, q.x));
        boolean[] used = new boolean[items.size()];
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            if (used[i]) continue;
            MenuItem it = items.get(i);
            used[i] = true;
            boolean unlock = it.label.equals("Unlock");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(16), dp(6), dp(16), dp(6));
            // un bouton Unlock est affiche sur la meme ligne que l'element qui l'accompagne,
            // quel que soit l'ordre dans lequel le tri les place
            MenuItem mate = null;
            for (int k = 0; k < items.size(); k++) {
                MenuItem o = items.get(k);
                if (!used[k] && Math.abs(o.y - it.y) < 12 && o.label.equals("Unlock") != unlock) {
                    mate = o;
                    used[k] = true;
                    break;
                }
            }
            if (mate != null) {
                MenuItem unlockItem = unlock ? it : mate;
                MenuItem mainItem = unlock ? mate : it;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                Button ub = menuButton(unlockItem, 14);
                ub.setText("Unlock " + mainItem.label);
                row.addView(ub, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.4f);
                ml.leftMargin = dp(8);
                row.addView(menuButton(mainItem, 18), ml);
                menuList.addView(row, lp);
            } else {
                menuList.addView(menuButton(it, 18), lp);
            }
            count++;
        }
        if (count > 0 && storyMode) {
            Button close = makeButton("Close menu", 16, Color.rgb(120, 52, 52));
            close.setOnClickListener(v -> nativePressButton(-1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            lp.setMargins(dp(16), dp(14), dp(16), dp(10));
            menuList.addView(close, lp);
        }
        menuView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    // 1 = l'histoire est affichee par l'interface, 0 = le moteur dessine tout (titre, menus...)
    public static void onGameMode(int story) {
        final MainActivity a = instance;
        if (a != null) a.runOnUiThread(() -> a.showMode(story != 0));
    }

    private String lastPage = "";

    // Historique natif (le moteur ne dessine plus sa propre boite de texte)
    private final java.util.ArrayList<String[]> history = new java.util.ArrayList<>();
    private String curWho = "", curText = "";
    private ScrollView historyView;
    private LinearLayout historyList;

    private void rememberPage(String text, String who) {
        boolean sameFlow = !curText.isEmpty() && text.startsWith(curText) && who.equals(curWho);
        if (!sameFlow && !curText.isEmpty()) {
            history.add(new String[] { curWho, curText });
            if (history.size() > 400) history.remove(0);
        }
        curWho = who;
        curText = text;
    }

    private void toggleHistory() {
        if (historyView.getVisibility() == View.VISIBLE) {
            historyView.setVisibility(View.GONE);
            return;
        }
        historyList.removeAllViews();
        java.util.ArrayList<String[]> all = new java.util.ArrayList<>(history);
        if (!curText.isEmpty()) all.add(new String[] { curWho, curText });
        for (String[] e : all) {
            if (!e[0].isEmpty()) {
                TextView w = new TextView(this);
                w.setText(e[0]);
                w.setTextColor(Color.rgb(232, 190, 120));
                w.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                w.setTypeface(Typeface.SERIF, Typeface.BOLD);
                w.setPadding(dp(20), dp(14), dp(20), 0);
                historyList.addView(w);
            }
            TextView t = new TextView(this);
            t.setText(e[1]);
            t.setTextColor(Color.rgb(214, 210, 202));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(15f, textSp - 5f));
            t.setTypeface(Typeface.SERIF);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(dp(20), dp(4), dp(20), dp(6));
            historyList.addView(t);
        }
        if (all.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("History is empty");
            t.setTextColor(Color.GRAY);
            t.setPadding(dp(20), dp(20), dp(20), 0);
            historyList.addView(t);
        }
        historyView.setVisibility(View.VISIBLE);
        historyView.post(() -> historyView.fullScroll(View.FOCUS_DOWN));
    }

    private void showPage(String raw, String who) {
        speakerText.setText(who);
        speakerText.setVisibility(who.isEmpty() ? View.GONE : View.VISIBLE);
        // Le moteur coupe les lignes pour sa boite 960 px : on les recolle et on laisse Android reflowrer
        String t = raw.replace("\r", "").replace("\n", " ").replaceAll(" {2,}", " ").trim();
        if (t.equals(lastPage)) return;
        rememberPage(t, who);
        lastPage = t;
        pageText.setText(t);
        hintText.setVisibility(View.GONE);
    }

    private boolean storyMode = false;

    private void showMode(boolean story) {
        storyMode = story;
        if (root != null) applyOrientation();
        if (!story) {
            lastPage = "";
            pageText.setText("");
        }
        hintText.setVisibility(story ? View.GONE : View.VISIBLE);
    }

    // ---------------------------------------------------------------- interface

    private boolean isPortrait() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private void buildLayout() {
        ViewGroup parent = (ViewGroup) mLayout.getParent();
        if (parent != null) parent.removeView(mLayout);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.addView(mLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = buildPanel();
        root.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Marge haute pour l'encoche / la barre d'etat
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        setContentView(root);
        applyOrientation();
    }

    // Portrait : scene 16:9 pleine largeur + texte natif. Paysage : jeu plein ecran, texte du moteur.
    private void applyOrientation() {
        boolean portrait = isPortrait();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mLayout.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        float ratio = storyMode ? STORY_RATIO : GAME_RATIO;
        lp.height = portrait ? Math.round(dm.widthPixels * ratio) : ViewGroup.LayoutParams.MATCH_PARENT;
        mLayout.setLayoutParams(lp);
        panel.setVisibility(portrait ? View.VISIBLE : View.GONE);
        try { nativeSetPortraitText(portrait ? 1 : 0); } catch (UnsatisfiedLinkError e) { /* lib pas encore chargee */ }
        root.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) applyOrientation();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (root != null) applyOrientation();
    }

    private LinearLayout buildPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(Color.rgb(16, 14, 20));

        // Zone de lecture : toucher = avancer, balayer vers le haut = historique
        FrameLayout reader = new FrameLayout(this);
        speakerText = new TextView(this);
        speakerText.setTextColor(Color.rgb(232, 190, 120));
        speakerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        speakerText.setTypeface(Typeface.SERIF, Typeface.BOLD);
        speakerText.setPadding(dp(20), dp(16), dp(20), 0);
        speakerText.setVisibility(View.GONE);
        pageText = new TextView(this);
        pageText.setTextColor(Color.rgb(240, 236, 228));
        pageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        pageText.setLineSpacing(0, 1.25f);
        pageText.setTypeface(Typeface.SERIF);
        pageText.setPadding(dp(20), dp(8), dp(20), dp(12));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(speakerText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(pageText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        reader.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        hintText = new TextView(this);
        hintText.setText("Tap here to continue");
        hintText.setTextColor(Color.argb(140, 255, 255, 255));
        hintText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hintText.setGravity(Gravity.CENTER);
        reader.addView(hintText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        historyView = new ScrollView(this);
        historyView.setBackgroundColor(Color.rgb(12, 10, 16));
        historyView.setVisibility(View.GONE);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyView.addView(historyList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        historyView.setOnClickListener(v -> toggleHistory());
        attachReaderGestures(reader);
        noteView = new TextView(this);
        noteView.setTextColor(Color.rgb(240, 236, 228));
        noteView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        noteView.setTypeface(Typeface.SERIF);
        noteView.setLineSpacing(0, 1.25f);
        noteView.setPadding(dp(20), dp(18), dp(20), dp(12));
        noteScroll = new ScrollView(this);
        noteScroll.addView(noteView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        noteScroll.setVisibility(View.GONE);
        final float noteSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        final float[] noteDown = new float[2];
        noteScroll.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                noteDown[0] = ev.getX();
                noteDown[1] = ev.getY();
            } else if (ev.getActionMasked() == MotionEvent.ACTION_UP
                    && Math.abs(ev.getX() - noteDown[0]) < noteSlop * 2
                    && Math.abs(ev.getY() - noteDown[1]) < noteSlop * 2) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                sendKey(KeyEvent.KEYCODE_ENTER);
            }
            return false; // le defilement reste gere par le ScrollView
        });
        reader.addView(noteScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        choiceBox = new LinearLayout(this);
        choiceBox.setOrientation(LinearLayout.VERTICAL);
        choiceBox.setBackgroundColor(Color.rgb(16, 14, 20));
        choiceBox.setVisibility(View.GONE);
        ScrollView headScroll = new ScrollView(this);
        choiceHeader = new TextView(this);
        choiceHeader.setTextColor(Color.rgb(236, 232, 224));
        choiceHeader.setTypeface(Typeface.SERIF);
        choiceHeader.setLineSpacing(0, 1.2f);
        choiceHeader.setPadding(dp(20), dp(14), dp(20), dp(8));
        headScroll.addView(choiceHeader, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        choiceBox.addView(headScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        choiceButtons = new LinearLayout(this);
        choiceButtons.setOrientation(LinearLayout.VERTICAL);
        choiceButtons.setPadding(0, dp(4), 0, dp(8));
        choiceBox.addView(choiceButtons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        reader.addView(choiceBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        menuView = new ScrollView(this);
        menuView.setBackgroundColor(Color.rgb(16, 14, 20));
        menuView.setVisibility(View.GONE);
        menuList = new LinearLayout(this);
        menuList.setOrientation(LinearLayout.VERTICAL);
        menuList.setPadding(0, dp(10), 0, dp(10));
        menuView.addView(menuList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        reader.addView(menuView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        reader.addView(historyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        p.addView(reader, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Barre d'actions fine
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.rgb(26, 24, 34));
        bar.setPadding(dp(8), dp(6), dp(8), dp(8));
        Button histo = makeButton("History", 12, Color.rgb(44, 48, 64));
        histo.setOnClickListener(v -> toggleHistory());
        addBarButton(bar, histo);
        addBarButton(bar, tapButton("Auto", KeyEvent.KEYCODE_A));
        addBarButton(bar, holdButton("Skip", KeyEvent.KEYCODE_CTRL_LEFT));
        addBarButton(bar, tapButton("Menu", KeyEvent.KEYCODE_ESCAPE));
        Button smaller = makeButton("A−", 15, Color.rgb(44, 48, 64));
        smaller.setOnClickListener(v -> changeTextSize(-1.5f));
        Button bigger = makeButton("A+", 15, Color.rgb(44, 48, 64));
        bigger.setOnClickListener(v -> changeTextSize(1.5f));
        addBarButton(bar, smaller);
        addBarButton(bar, bigger);
        p.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        return p;
    }

    private void changeTextSize(float delta) {
        textSp = Math.max(14f, Math.min(34f, textSp + delta));
        pageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        prefs.edit().putFloat("textSp", textSp).apply();
    }

    private void attachReaderGestures(View v) {
        final float slop = ViewConfiguration.get(this).getScaledTouchSlop();
        final float[] down = new float[2];
        v.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = ev.getX();
                    down[1] = ev.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dy = ev.getY() - down[1];
                    float dx = ev.getX() - down[0];
                    if (dy < -dp(60) && Math.abs(dy) > Math.abs(dx)) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        toggleHistory();
                    } else if (Math.abs(dx) < slop * 2 && Math.abs(dy) < slop * 2) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        sendKey(KeyEvent.KEYCODE_ENTER); // avancer
                    }
                    return true;
            }
            return true;
        });
    }

    private void addBarButton(LinearLayout bar, Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        bar.addView(b, lp);
    }

    private Button makeButton(String label, int sp, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        return b;
    }

    private Button tapButton(String label, int keyCode) {
        Button b = makeButton(label, 12, Color.rgb(44, 48, 64));
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            sendKey(keyCode);
        });
        return b;
    }

    private Button holdButton(String label, int keyCode) {
        Button b = makeButton(label, 12, Color.rgb(120, 52, 52));
        b.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    SDLActivity.onNativeKeyDown(keyCode);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    SDLActivity.onNativeKeyUp(keyCode);
                    return true;
            }
            return true;
        });
        return b;
    }

    private static void sendKey(int keyCode) {
        SDLActivity.onNativeKeyDown(keyCode);
        SDLActivity.onNativeKeyUp(keyCode);
    }

    // Retour Android = menu du jeu (clic droit) au lieu de fermer l'appli
    @Override
    public void onBackPressed() {
        sendKey(KeyEvent.KEYCODE_ESCAPE);
    }
}
