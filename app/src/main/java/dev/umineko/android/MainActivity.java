package dev.umineko.android;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
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

    // Taille des libelles de la barre du bas (>= 16 sp)
    private static final int BAR_SP = 16;

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
        final CharSequence text = formatPage(raw);
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

    private static volatile CharSequence pendingText = "";
    private static volatile String pendingWho = "";
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

    // Couleur de texte par defaut du script : remplacee par celle du panneau
    private static final int SCRIPT_TEXT_COLOR = 0xffffff;
    // Coupure de ligne automatique du moteur, en attendant de savoir s'il faut une espace
    private static final char SOFT_BREAK = '\u0001';

    // Page de l'histoire avec sa mise en forme (couleur, italique, gras, taille).
    // Le moteur insere 0x0e + RRGGBB + style (1 italique, 2 gras) + taille en % (3 chiffres)
    // a chaque changement, et 0x0f a la place du retour a la ligne d'une coupure automatique.
    private static CharSequence formatPage(byte[] raw) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        java.io.ByteArrayOutputStream run = new java.io.ByteArrayOutputStream(raw.length);
        int color = SCRIPT_TEXT_COLOR, style = 0, pct = 100;
        for (int i = 0; i < raw.length; i++) {
            int b = raw[i] & 0xff;
            if (b == 0x0e && i + 10 < raw.length) {
                try {
                    int c = Integer.parseInt(new String(raw, i + 1, 6, StandardCharsets.US_ASCII), 16);
                    int st = Integer.parseInt(new String(raw, i + 7, 1, StandardCharsets.US_ASCII), 16);
                    int pc = Integer.parseInt(new String(raw, i + 8, 3, StandardCharsets.US_ASCII));
                    appendRun(sb, run, color, style, pct);
                    color = c; style = st; pct = pc;
                    i += 10;
                } catch (NumberFormatException e) { /* marque illisible : octet ignore */ }
                continue;
            }
            if (b == 0x0e || b == '\r') continue;
            if (b == 0x0f) { run.write(SOFT_BREAK); continue; }
            // codes de mise en forme du moteur, deja traduits par les marques (voir stripCodes)
            if (b >= 0x10 && b < 0x17) continue;
            if (b >= 0x17 && b <= 0x1e) { i += 2; continue; }
            if (b == 0x1f) { i += 1; continue; }
            run.write(b);
        }
        appendRun(sb, run, color, style, pct);

        // Le moteur coupe les lignes pour sa boite 960 px : on les recolle et on laisse Android
        // reflowrer. Les retours a la ligne du script, eux, sont gardes.
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (sb.charAt(i) != SOFT_BREAK) continue;
            if (i > 0 && sb.charAt(i - 1) >= 0x2e80) sb.delete(i, i + 1); // pas d'espace en japonais
            else sb.replace(i, i + 1, " ");
        }
        for (int i = sb.length() - 1; i > 0; i--) {
            char c = sb.charAt(i), prev = sb.charAt(i - 1);
            if (c == ' ' && (prev == ' ' || prev == '\n')) sb.delete(i, i + 1);
            else if (c == '\n' && prev == ' ') sb.delete(i - 1, i);
        }
        while (sb.length() > 0 && sb.charAt(0) <= ' ') sb.delete(0, 1);
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) <= ' ') sb.delete(sb.length() - 1, sb.length());
        return sb;
    }

    private static void appendRun(SpannableStringBuilder sb, java.io.ByteArrayOutputStream run,
                                  int color, int style, int pct) {
        if (run.size() == 0) return;
        int start = sb.length();
        sb.append(new String(run.toByteArray(), StandardCharsets.UTF_8));
        run.reset();
        int end = sb.length();
        // une couleur trop sombre serait illisible sur le fond du panneau
        int luma = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
        if (color != SCRIPT_TEXT_COLOR && luma >= 48)
            sb.setSpan(new ForegroundColorSpan(0xff000000 | color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int face = ((style & 1) != 0 ? Typeface.ITALIC : 0) | ((style & 2) != 0 ? Typeface.BOLD : 0);
        if (face != 0)
            sb.setSpan(new StyleSpan(face), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (pct > 0 && pct != 100)
            sb.setSpan(new RelativeSizeSpan(Math.max(0.6f, Math.min(2.5f, pct / 100f))), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        BUTTON_NAMES.put("r_btn_system", "System");
        BUTTON_NAMES.put("r_btn_oimages", "Original sprites");
        BUTTON_NAMES.put("r_btn_nimages", "New sprites");
        BUTTON_NAMES.put("volume", "Options");
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
        closeLogs(); // les logs sont au-dessus des ecrans du jeu : ils les cacheraient
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
        closeLogs();
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

    private static boolean isSlotName(String s) {
        String l = s.toLowerCase();
        return l.contains("savearea") || l.contains("loadarea");
    }

    private static final int C_BASE = Color.rgb(52, 46, 70);
    private static final int C_PRIMARY = Color.rgb(132, 60, 88);
    private static final int C_DANGER = Color.rgb(104, 42, 50);
    private static final int C_MUTED = Color.rgb(36, 34, 48);

    private static final class MenuItem {
        int id, x, y;
        String label;
        String info = "";
        MenuItem unlock;
    }

    private static boolean isPage(String l) {
        return l.toLowerCase().startsWith("page ");
    }

    private static boolean isStepper(String l) {
        return l.length() > 1 && (l.endsWith("+") || l.endsWith("-"));
    }

    // Libelle affiche : sans la parenthese finale explicative
    private static String shortLabel(String l) {
        int p = l.indexOf(" (");
        return p > 0 && l.endsWith(")") ? l.substring(0, p) : l;
    }

    private Button styled(String text, int sp, int color, int minHdp, final int id) {
        Button b = makeButton(text, sp, color);
        if (minHdp > 0) b.setMinHeight(dp(minHdp));
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            nativePressButton(id);
        });
        return b;
    }

    private LinearLayout hrow(int heightDp, int topDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                heightDp > 0 ? dp(heightDp) : ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), dp(topDp), dp(12), 0);
        menuList.addView(row, lp);
        return row;
    }

    private void cell(LinearLayout row, View v, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(v, lp);
    }

    private void fixedCell(LinearLayout row, View v, int widthDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(v, lp);
    }

    // Grille de boutons de meme poids ; un dernier element seul prend toute la largeur
    private void addGrid(java.util.List<MenuItem> list, int cols, int color, int sp, int heightDp) {
        for (int i = 0; i < list.size(); i += cols) {
            LinearLayout row = hrow(heightDp, 8);
            for (int c = 0; c < cols && i + c < list.size(); c++) {
                MenuItem it = list.get(i + c);
                cell(row, styled(shortLabel(it.label), sp, color, 0, it.id), 1f);
            }
        }
    }

    private Button unlockChip(MenuItem u) {
        Button b = styled("Unlock", 12, C_MUTED, 0, u.id);
        b.setTextColor(Color.rgb(176, 168, 196));
        return b;
    }

    private int curPage = 1;

    private View slotCard(MenuItem it) {
        int slot = (curPage - 1) * 8 + (it.id > 50 ? ((it.id - 51) % 8) + 1 : it.id);
        boolean empty = it.info.isEmpty();
        String date = it.info;
        String title = "";
        int cut = it.info.indexOf(" - ");
        if (cut > 0) {
            date = it.info.substring(0, cut);
            title = it.info.substring(cut + 3);
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(empty ? C_MUTED : C_BASE);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        card.setPadding(dp(8), dp(8), dp(12), dp(8));

        TextView badge = new TextView(this);
        badge.setText(String.valueOf(slot));
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        badge.setTypeface(Typeface.SERIF, Typeface.BOLD);
        badge.setTextColor(empty ? Color.rgb(110, 104, 128) : Color.rgb(232, 190, 120));
        badge.setGravity(Gravity.CENTER);
        card.addView(badge, new LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l1 = new TextView(this);
        l1.setText(empty ? "Empty" : date);
        l1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        l1.setTextColor(empty ? Color.rgb(120, 114, 138) : Color.WHITE);
        if (!empty) l1.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(l1);
        if (!title.isEmpty()) {
            TextView l2 = new TextView(this);
            l2.setText(title);
            l2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            l2.setTextColor(Color.rgb(190, 184, 206));
            col.addView(l2);
        }
        card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.setMinimumHeight(dp(64));
        final int id = it.id;
        card.setClickable(true);
        card.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            nativePressButton(id);
        });
        return card;
    }

    private boolean choicesShown = false;

    // Image de description (TIPS, personnage) : texte incruste, affiche agrandi en tete du menu
    private android.graphics.Bitmap tipsBitmap;

    // Vignettes des boutons de personnages : [id, largeur, hauteur, pixels...] repete
    private static final class Thumb {
        int x, y;
        android.graphics.Bitmap bmp;
    }
    private String lastMenuData = "";
    private final java.util.HashMap<Integer, Thumb> thumbInfo = new java.util.HashMap<>();

    public static void onGameThumbs(int[] d) {
        final MainActivity a = instance;
        if (a == null) return;
        final java.util.HashMap<Integer, Thumb> m = new java.util.HashMap<>();
        int i = 0;
        while (d != null && i + 5 <= d.length) {
            int id = d[i], x = d[i + 1], y = d[i + 2], w = d[i + 3], h = d[i + 4];
            i += 5;
            if (w <= 0 || h <= 0 || i + w * h > d.length) break;
            Thumb t = new Thumb();
            t.x = x; t.y = y;
            t.bmp = android.graphics.Bitmap.createBitmap(d, i, w, w, h, android.graphics.Bitmap.Config.ARGB_8888);
            m.put(id, t);
            i += w * h;
        }
        a.runOnUiThread(() -> {
            a.thumbInfo.clear();
            a.thumbInfo.putAll(m);
            a.showMenu(a.lastMenuData);
        });
    }

    public static void onGameTipsImage(int w, int h, int[] argb) {
        final MainActivity a = instance;
        if (a == null) return;
        android.graphics.Bitmap full = (w > 0 && h > 0 && argb != null)
                ? android.graphics.Bitmap.createBitmap(argb, w, h, android.graphics.Bitmap.Config.ARGB_8888)
                : null;
        if (full != null) {
            // rogne les marges transparentes autour du texte
            int x0 = w, y0 = h, x1 = -1, y1 = -1;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if ((argb[y * w + x] >>> 24) > 16) {
                        if (x < x0) x0 = x;
                        if (x > x1) x1 = x;
                        if (y < y0) y0 = y;
                        if (y > y1) y1 = y;
                    }
            if (x1 >= x0 && y1 >= y0) {
                x0 = Math.max(0, x0 - 6); y0 = Math.max(0, y0 - 6);
                x1 = Math.min(w - 1, x1 + 6); y1 = Math.min(h - 1, y1 + 6);
                full = android.graphics.Bitmap.createBitmap(full, x0, y0, x1 - x0 + 1, y1 - y0 + 1);
            }
        }
        final android.graphics.Bitmap bmp = full;
        a.runOnUiThread(() -> {
            a.tipsBitmap = bmp;
            a.showMenu(a.lastMenuData);
        });
    }

    private void showMenu(String data) {
        if (choicesShown && data.isEmpty()) return; // les choix (select) occupent deja cette zone
        lastMenuData = data;
        if (!data.isEmpty()) closeLogs();
        menuList.removeAllViews();
        if (tipsBitmap != null) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageBitmap(tipsBitmap);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            menuList.addView(iv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
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
            if (f.length >= 6 && isSlotName(f[2])) {
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
        // ordre de lecture ; les emplacements forment une ligne virtuelle classee par numero
        int slotY = Integer.MAX_VALUE;
        for (MenuItem it : items)
            if (isSlotName(it.label)) slotY = Math.min(slotY, it.y);
        for (MenuItem it : items)
            if (isSlotName(it.label)) { it.y = slotY; it.x = it.id; }
        java.util.Collections.sort(items, (p, q) ->
                p.y != q.y ? Integer.compare(p.y, q.y) : Integer.compare(p.x, q.x));

        // un bouton "Unlock" accompagne l'element place sur la meme ligne
        java.util.ArrayList<MenuItem> unlocks = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItem> rest = new java.util.ArrayList<>();
        for (MenuItem it : items) {
            if (it.label.equals("Unlock")) unlocks.add(it); else rest.add(it);
        }
        for (MenuItem u : unlocks) {
            MenuItem best = null;
            for (MenuItem it : rest)
                if (it.unlock == null && Math.abs(it.y - u.y) < 12 && (best == null || Math.abs(it.x - u.x) < Math.abs(best.x - u.x)))
                    best = it;
            if (best != null) best.unlock = u; else rest.add(u);
        }

        boolean titleScreen = false;
        for (MenuItem it : rest)
            if (it.label.equals("Start") || it.label.equals("Controls")) titleScreen = true;

        java.util.ArrayList<MenuItem> portraits = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItem> tabs = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItem> primary = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItem> slots = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItem> episodes = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItem> plain = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItem> withUnlock = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItem> links = new java.util.ArrayList<>();
        java.util.ArrayList<MenuItem> footer = new java.util.ArrayList<>();
        java.util.LinkedHashMap<String, MenuItem[]> steppers = new java.util.LinkedHashMap<>();
        for (MenuItem it : rest) {
            String l = it.label;
            String low = l.toLowerCase();
            if (thumbInfo.containsKey(it.id) && it.id >= 0) portraits.add(it);
            else if (isSlotName(l)) slots.add(it);
            else if (isPage(l)) tabs.add(it);
            else if (l.equals("Start") || l.equals("Episode 1")) primary.add(it);
            else if (l.startsWith("Episode ")) episodes.add(it);
            else if (low.contains("website")) links.add(it);
            else if (low.equals("exit") || low.contains("title")) footer.add(it);
            else if (isStepper(l)) {
                String base = l.substring(0, l.length() - 1);
                MenuItem[] pair = steppers.get(base);
                if (pair == null) { pair = new MenuItem[2]; steppers.put(base, pair); }
                pair[l.endsWith("+") ? 1 : 0] = it;
            }
            else if (it.unlock != null) withUnlock.add(it);
            else plain.add(it);
        }

        curPage = 1;
        for (MenuItem t : tabs) {
            if (t.label.contains("current")) {
                try { curPage = Integer.parseInt(t.label.substring(5, 6)); } catch (Exception e) { curPage = 1; }
            }
        }
        // 1. pages : onglets
        if (!tabs.isEmpty()) {
            LinearLayout row = hrow(44, 10);
            for (MenuItem t : tabs) {
                boolean cur = t.label.contains("current");
                Button b = styled(t.label.replace(" (current)", ""), 12, cur ? C_PRIMARY : C_MUTED, 0, t.id);
                if (!cur) b.setTextColor(Color.rgb(190, 184, 206));
                cell(row, b, 1f);
            }
        }
        // 2. action principale
        for (MenuItem it : primary) {
            LinearLayout row = hrow(titleScreen ? 76 : 70, 12);
            cell(row, styled(it.label, 20, C_PRIMARY, 0, it.id), 1f);
        }
        // 3. reglages a +/-
        for (java.util.Map.Entry<String, MenuItem[]> e : steppers.entrySet()) {
            MenuItem[] pair = e.getValue();
            LinearLayout row = hrow(54, 10);
            TextView name = new TextView(this);
            name.setText(e.getKey().replace("Music/SoundEffect", "Music & sound effects"));
            name.setTextColor(Color.rgb(236, 232, 224));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setPadding(dp(8), 0, 0, 0);
            cell(row, name, 1f);
            if (pair[0] != null) fixedCell(row, styled("−", 22, C_BASE, 0, pair[0].id), 64);
            if (pair[1] != null) fixedCell(row, styled("+", 22, C_BASE, 0, pair[1].id), 64);
        }
        // 3b. taille du texte de lecture (le menu en histoire, plus la barre)
        if (storyMode && !titleScreen) {
            LinearLayout row = hrow(54, 10);
            TextView name = new TextView(this);
            name.setText("Text size");
            name.setTextColor(Color.rgb(236, 232, 224));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setPadding(dp(8), 0, 0, 0);
            cell(row, name, 1f);
            Button smaller = makeButton("A−", 20, C_BASE);
            smaller.setOnClickListener(v -> changeTextSize(-1.5f));
            Button bigger = makeButton("A+", 20, C_BASE);
            bigger.setOnClickListener(v -> changeTextSize(1.5f));
            fixedCell(row, smaller, 64);
            fixedCell(row, bigger, 64);
        }
        // 4. emplacements de sauvegarde
        for (MenuItem s : slots) {
            View card = slotCard(s);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(16), dp(8), dp(16), 0);
            menuList.addView(card, lp);
        }
        // 5. episodes : cartes, avec leur pastille Unlock
        int epCols = episodes.size() <= 3 ? Math.max(1, episodes.size()) : 2;
        for (int i = 0; i < episodes.size(); i += epCols) {
            LinearLayout row = hrow(104, 10);
            for (int c = 0; c < epCols && i + c < episodes.size(); c++) {
                MenuItem it = episodes.get(i + c);
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
                col.addView(styled(it.label.replace("Episode ", "Ep. "), 17, C_BASE, 0, it.id), a);
                if (it.unlock != null) {
                    LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
                    b.topMargin = dp(6);
                    col.addView(unlockChip(it.unlock), b);
                }
                cell(row, col, 1f);
            }
        }
        // portraits des personnages : l'arbre de la scene, agrandi, avec ses liens et ses emplacements
        if (!thumbInfo.isEmpty()) {
            int wpx = getResources().getDisplayMetrics().widthPixels;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
            java.util.ArrayList<Integer> order = new java.util.ArrayList<>(thumbInfo.keySet());
            // fonds d'abord (ids <= -1000 et > -100000), puis cases vides, puis portraits cliquables
            java.util.Collections.sort(order, (p, q) -> {
                int rp = p >= 0 ? 2 : (p <= -100000 ? 1 : 0), rq = q >= 0 ? 2 : (q <= -100000 ? 1 : 0);
                return rp != rq ? Integer.compare(rp, rq) : Integer.compare(p, q);
            });
            for (int id : order) {
                Thumb t = thumbInfo.get(id);
                minX = Math.min(minX, t.x); minY = Math.min(minY, t.y);
                maxX = Math.max(maxX, t.x + t.bmp.getWidth());
                maxY = Math.max(maxY, t.y + t.bmp.getHeight());
            }
            float sc = (wpx * 0.66f) / Math.max(1, maxX - minX);
            FrameLayout tree = new FrameLayout(this);
            for (int id : order) {
                final int pid = id;
                Thumb t = thumbInfo.get(id);
                android.widget.ImageView iv;
                if (id >= 0) {
                    iv = new android.widget.ImageButton(this);
                    iv.setBackgroundColor(Color.TRANSPARENT);
                    iv.setOnClickListener(v -> {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        nativePressButton(pid);
                    });
                } else {
                    iv = new android.widget.ImageView(this);
                }
                iv.setImageBitmap(t.bmp);
                iv.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
                iv.setPadding(0, 0, 0, 0);
                FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(
                        Math.round(t.bmp.getWidth() * sc), Math.round(t.bmp.getHeight() * sc));
                pl.leftMargin = Math.round((t.x - minX) * sc);
                pl.topMargin = Math.round((t.y - minY) * sc);
                tree.addView(iv, pl);
            }
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                    Math.round((maxX - minX) * sc), Math.round((maxY - minY) * sc));
            tl.gravity = Gravity.CENTER_HORIZONTAL;
            tl.topMargin = dp(12);
            menuList.addView(tree, tl);
        }
        // 6. actions courtes : grille ; textes longs : une colonne
        boolean allShort = true;
        for (MenuItem it : plain) if (shortLabel(it.label).length() > 24) allShort = false;
        addGrid(plain, allShort && plain.size() > 1 ? 2 : 1, C_BASE, 16, 62);
        for (MenuItem it : withUnlock) {
            LinearLayout row = hrow(62, 8);
            cell(row, styled(shortLabel(it.label), 16, C_BASE, 0, it.id), 1f);
            fixedCell(row, unlockChip(it.unlock), 92);
        }
        // 7. liens discrets
        for (MenuItem it : links) {
            LinearLayout row = hrow(42, 14);
            Button b = styled(shortLabel(it.label), 13, C_MUTED, 0, it.id);
            b.setTextColor(Color.rgb(160, 154, 180));
            cell(row, b, 1f);
        }
        // 8. pied de page : retour / actions destructives
        if (!footer.isEmpty()) {
            LinearLayout row = hrow(56, 18);
            for (MenuItem it : footer) {
                boolean back = it.label.equalsIgnoreCase("exit") && !titleScreen;
                String ft = it.label.equalsIgnoreCase("title") ? "Return to title" : shortLabel(it.label);
                Button b = styled(back ? "Back" : ft, 15, back ? C_MUTED : C_DANGER, 0, it.id);
                if (back) b.setTextColor(Color.rgb(190, 184, 206));
                cell(row, b, 1f);
            }
        }
        int count = items.size();
        boolean hasBack = false;
        for (MenuItem it : footer) if (it.label.equalsIgnoreCase("exit") && !titleScreen) hasBack = true;
        if (count > 0 && storyMode && !hasBack) {
            LinearLayout row = hrow(54, footer.isEmpty() ? 18 : 10);
            Button close = styled("Close menu", 15, C_MUTED, 0, -1);
            close.setTextColor(Color.rgb(190, 184, 206));
            cell(row, close, 1f);
        }
        View pad = new View(this);
        menuList.addView(pad, new LinearLayout.LayoutParams(1, dp(16)));
        menuView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        menuView.scrollTo(0, 0);
    }

    // 1 = l'histoire est affichee par l'interface, 0 = le moteur dessine tout (titre, menus...)
    public static void onGameMode(int story) {
        final MainActivity a = instance;
        if (a != null) a.runOnUiThread(() -> a.showMode(story != 0));
    }

    private String lastPage = "";

    // Historique natif (le moteur ne dessine plus sa propre boite de texte)
    private final java.util.ArrayList<CharSequence[]> history = new java.util.ArrayList<>();
    private String curWho = "", curText = "";
    private CharSequence curRich = ""; // curText avec sa mise en forme
    private ScrollView historyView;
    private LinearLayout historyList;

    private void rememberPage(String text, CharSequence rich, String who) {
        boolean sameFlow = !curText.isEmpty() && text.startsWith(curText) && who.equals(curWho);
        if (!sameFlow && !curText.isEmpty()) {
            history.add(new CharSequence[] { curWho, curRich });
            if (history.size() > 400) history.remove(0);
        }
        curWho = who;
        curText = text;
        curRich = rich;
    }

    private void closeLogs() {
        if (historyView != null && historyView.getVisibility() == View.VISIBLE)
            historyView.setVisibility(View.GONE);
    }

    private void toggleHistory() {
        if (historyView.getVisibility() == View.VISIBLE) {
            historyView.setVisibility(View.GONE);
            return;
        }
        historyList.removeAllViews();
        java.util.ArrayList<CharSequence[]> all = new java.util.ArrayList<>(history);
        if (!curText.isEmpty()) all.add(new CharSequence[] { curWho, curRich });
        for (CharSequence[] e : all) {
            if (e[0].length() != 0) {
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
            t.setText("Logs are empty");
            t.setTextColor(Color.GRAY);
            t.setPadding(dp(20), dp(20), dp(20), 0);
            historyList.addView(t);
        }
        historyView.setVisibility(View.VISIBLE);
        historyView.post(() -> historyView.fullScroll(View.FOCUS_DOWN));
    }

    private void showPage(CharSequence rich, String who) {
        speakerText.setText(who);
        speakerText.setVisibility(who.isEmpty() ? View.GONE : View.VISIBLE);
        String t = rich.toString();
        if (t.equals(lastPage)) return;
        rememberPage(t, rich, who);
        lastPage = t;
        pageText.setText(rich);
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

        // Zone de lecture : toucher = avancer, balayer vers le bas = logs
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
        // balayage vers le haut alors qu'on etait deja au bout des logs = retour au jeu
        final float[] hDown = new float[2];
        final boolean[] hAtEnd = new boolean[1];
        historyView.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                hDown[0] = ev.getX();
                hDown[1] = ev.getY();
                hAtEnd[0] = !v.canScrollVertically(1);
            } else if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                float dy = ev.getY() - hDown[1];
                if (hAtEnd[0] && !v.canScrollVertically(1) && dy < -dp(60)
                        && Math.abs(dy) > Math.abs(ev.getX() - hDown[0])) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    toggleHistory();
                    return true;
                }
            }
            return false; // defilement et tap restent geres par le ScrollView
        });
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
        Button menu = tapButton("Menu", KeyEvent.KEYCODE_ESCAPE);
        menu.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            closeLogs();
            sendKey(KeyEvent.KEYCODE_ESCAPE);
        });
        addBarButton(bar, menu);
        addBarButton(bar, tapButton("Auto", KeyEvent.KEYCODE_A));
        addBarButton(bar, holdButton("Skip", KeyEvent.KEYCODE_CTRL_LEFT));
        Button logs = makeButton("Logs", BAR_SP, Color.rgb(44, 48, 64));
        logs.setOnClickListener(v -> toggleHistory());
        addBarButton(bar, logs);
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
                    if (dy > dp(60) && Math.abs(dy) > Math.abs(dx)) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        if (historyView.getVisibility() != View.VISIBLE) toggleHistory();
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
        Button b = makeButton(label, BAR_SP, Color.rgb(44, 48, 64));
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            sendKey(keyCode);
        });
        return b;
    }

    private Button holdButton(String label, int keyCode) {
        Button b = makeButton(label, BAR_SP, Color.rgb(120, 52, 52));
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
        if (historyView != null && historyView.getVisibility() == View.VISIBLE) {
            toggleHistory();
            return;
        }
        sendKey(KeyEvent.KEYCODE_ESCAPE);
    }
}
