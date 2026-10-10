// Pont moteur -> interface Android (texte du jeu, etat)
#include <jni.h>
#include <SDL.h>
#include <SDL_system.h>
#include <android/log.h>
#include <string.h>

// Page de texte complete, envoyee quand le moteur attend un clic (octets UTF-8 bruts)
extern "C" void android_send_page(const char* utf8, int newPage, const char* window)
{
    JNIEnv* env = (JNIEnv*) SDL_AndroidGetJNIEnv();
    jobject act = (jobject) SDL_AndroidGetActivity();
    if (!env || !act) return;
    int n = utf8 ? (int) strlen(utf8) : 0;
    jbyteArray arr = env->NewByteArray(n);
    if (n) env->SetByteArrayRegion(arr, 0, n, (const jbyte*) utf8);
    jclass cls = env->GetObjectClass(act);
    jstring win = env->NewStringUTF(window ? window : "");
    jmethodID m = env->GetStaticMethodID(cls, "onGamePage", "([BILjava/lang/String;)V");
    if (m) env->CallStaticVoidMethod(cls, m, arr, (jint) newPage, win);
    env->DeleteLocalRef(win);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(act);
}

// L'interface doit afficher le texte de l'histoire (1) ou laisser le moteur dessiner (0)
extern "C" void android_send_mode(int story)
{
    JNIEnv* env = (JNIEnv*) SDL_AndroidGetJNIEnv();
    jobject act = (jobject) SDL_AndroidGetActivity();
    if (!env || !act) return;
    jclass cls = env->GetObjectClass(act);
    jmethodID m = env->GetStaticMethodID(cls, "onGameMode", "(I)V");
    if (m) env->CallStaticVoidMethod(cls, m, (jint) story);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(act);
}

// Portrait : le texte de l'histoire est affiche par l'interface Android
extern "C" volatile int g_native_text_wanted = 0;

extern "C" JNIEXPORT void JNICALL
Java_dev_umineko_android_MainActivity_nativeSetPortraitText(JNIEnv*, jclass, jint on)
{
    g_native_text_wanted = on;
}

// Liste des boutons du menu (id TAB image TAB fichier, un par ligne) ; chaine vide = pas de menu natif
extern "C" void android_send_buttons(const char* data)
{
    JNIEnv* env = (JNIEnv*) SDL_AndroidGetJNIEnv();
    jobject act = (jobject) SDL_AndroidGetActivity();
    if (!env || !act) return;
    int n = data ? (int) strlen(data) : 0;
    jbyteArray arr = env->NewByteArray(n);
    if (n) env->SetByteArrayRegion(arr, 0, n, (const jbyte*) data);
    jclass cls = env->GetObjectClass(act);
    jmethodID m = env->GetStaticMethodID(cls, "onGameButtons", "([B)V");
    if (m) env->CallStaticVoidMethod(cls, m, arr);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(act);
}

// Appui sur un bouton du menu natif : evenement SDL traite par la boucle du moteur
extern "C" volatile unsigned int g_android_press_event = 0;

extern "C" JNIEXPORT void JNICALL
Java_dev_umineko_android_MainActivity_nativePressButton(JNIEnv*, jclass, jint id)
{
    if (!g_android_press_event) g_android_press_event = SDL_RegisterEvents(1);
    if (g_android_press_event == (unsigned int) -1) { g_android_press_event = 0; return; }
    SDL_Event e;
    SDL_zero(e);
    e.type = g_android_press_event;
    e.user.code = id;
    SDL_PushEvent(&e);
}

// Page d'intro a texte incruste dans l'image (0 = aucune, 2 ou 3 = pages TIPS)
extern "C" void android_send_note(int id)
{
    JNIEnv* env = (JNIEnv*) SDL_AndroidGetJNIEnv();
    jobject act = (jobject) SDL_AndroidGetActivity();
    if (!env || !act) return;
    jclass cls = env->GetObjectClass(act);
    jmethodID m = env->GetStaticMethodID(cls, "onGameNote", "(I)V");
    if (m) env->CallStaticVoidMethod(cls, m, (jint) id);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(act);
}

// Ecran a choix (select) : en-tete + choix "id TAB texte", chaine vide = aucun
extern "C" void android_send_choices(const char* data)
{
    JNIEnv* env = (JNIEnv*) SDL_AndroidGetJNIEnv();
    jobject act = (jobject) SDL_AndroidGetActivity();
    if (!env || !act) return;
    int n = data ? (int) strlen(data) : 0;
    jbyteArray arr = env->NewByteArray(n);
    if (n) env->SetByteArrayRegion(arr, 0, n, (const jbyte*) data);
    jclass cls = env->GetObjectClass(act);
    jmethodID m = env->GetStaticMethodID(cls, "onGameChoices", "([B)V");
    if (m) env->CallStaticVoidMethod(cls, m, arr);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(act);
}

// Image de description (TIPS, personnage) : pixels ARGB, w = 0 pour l'effacer
extern "C" void android_send_tips_image(int w, int h, const unsigned int* argb)
{
    JNIEnv* env = (JNIEnv*) SDL_AndroidGetJNIEnv();
    jobject act = (jobject) SDL_AndroidGetActivity();
    if (!env || !act) return;
    jclass cls = env->GetObjectClass(act);
    jmethodID m = env->GetStaticMethodID(cls, "onGameTipsImage", "(II[I)V");
    if (m) {
        jintArray arr = NULL;
        if (w > 0 && h > 0 && argb) {
            arr = env->NewIntArray(w * h);
            env->SetIntArrayRegion(arr, 0, w * h, (const jint*) argb);
        }
        env->CallStaticVoidMethod(cls, m, (jint) w, (jint) h, arr);
        if (arr) env->DeleteLocalRef(arr);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(act);
}

// Vignettes des boutons de personnages : [id, largeur, hauteur, pixels ARGB...] repete
extern "C" void android_send_thumbs(const int* data, int count)
{
    JNIEnv* env = (JNIEnv*) SDL_AndroidGetJNIEnv();
    jobject act = (jobject) SDL_AndroidGetActivity();
    if (!env || !act) return;
    jclass cls = env->GetObjectClass(act);
    jmethodID m = env->GetStaticMethodID(cls, "onGameThumbs", "([I)V");
    if (m) {
        jintArray arr = env->NewIntArray(count > 0 ? count : 0);
        if (count > 0) env->SetIntArrayRegion(arr, 0, count, (const jint*) data);
        env->CallStaticVoidMethod(cls, m, arr);
        env->DeleteLocalRef(arr);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(act);
}
