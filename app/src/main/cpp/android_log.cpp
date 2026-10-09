// Redirige stdout/stderr du moteur vers logcat (tag "Ponscr")
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <stdio.h>

static int pfd[2];

static void* pump(void*)
{
    char buf[1024];
    ssize_t n;
    while ((n = read(pfd[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[n - 1] == '\n') --n;
        buf[n] = 0;
        __android_log_write(ANDROID_LOG_INFO, "Ponscr", buf);
    }
    return nullptr;
}

extern "C" void android_redirect_stdio()
{
    static bool done = false;
    if (done) return;
    done = true;
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);
    pthread_t t;
    pthread_create(&t, nullptr, pump, nullptr);
    pthread_detach(t);
}
