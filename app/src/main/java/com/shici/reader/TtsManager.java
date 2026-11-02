package com.shici.reader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;

/**
 * A single TextToSpeech connection shared by the whole app. The first tap pays
 * the engine start-up cost; later taps speak immediately.
 */
@SuppressLint("StaticFieldLeak") // Holds an application context only.
public final class TtsManager {

    private static volatile TtsManager instance;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean initializing;
    private boolean failed;
    private String pendingWord;

    public static TtsManager get(Context context) {
        if (instance == null) {
            synchronized (TtsManager.class) {
                if (instance == null) {
                    instance = new TtsManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private TtsManager(Context context) {
        this.context = context;
    }

    public void speak(String word) {
        if (word == null || word.trim().isEmpty()) {
            return;
        }
        pendingWord = word.trim();
        if (failed) {
            Toast.makeText(context, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (initializing) {
            return;
        }
        if (tts != null) {
            speakNow(pendingWord);
            return;
        }
        initializing = true;
        tts = new TextToSpeech(context, status -> mainHandler.post(() -> onInitialized(status)));
    }

    private void onInitialized(int status) {
        initializing = false;
        if (status != TextToSpeech.SUCCESS || tts == null) {
            failed = true;
            Toast.makeText(context, R.string.tts_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        int languageResult = tts.setLanguage(Locale.US);
        if (languageResult < TextToSpeech.LANG_AVAILABLE) {
            languageResult = tts.setLanguage(Locale.UK);
        }
        if (languageResult < TextToSpeech.LANG_AVAILABLE) {
            languageResult = tts.setLanguage(Locale.ENGLISH);
        }
        if (languageResult < TextToSpeech.LANG_AVAILABLE) {
            failed = true;
            Toast.makeText(context, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        tts.setSpeechRate(0.88f);
        tts.setPitch(1.0f);
        if (pendingWord != null) {
            speakNow(pendingWord);
        }
    }

    private void speakNow(String word) {
        int result = tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "shici-word");
        if (result == TextToSpeech.ERROR) {
            Toast.makeText(context, R.string.tts_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
