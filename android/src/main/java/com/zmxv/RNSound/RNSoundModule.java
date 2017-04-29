package com.zmxv.RNSound;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RNSoundModule extends ReactContextBaseJavaModule {
  Map<Integer, MediaPlayer> playerPool = new HashMap<>();
  ReactApplicationContext context;
  final static Object NULL = null;
  Visualizer audioOutput = null;
  double intensity = 0;
  boolean isWaveformEnabled;
  boolean isProgressEnabled;
  Handler handler = new Handler(Looper.getMainLooper());
  int waveformDelay = 150;
  int progressDelay = 1000;
  Runnable waveformRunnable;
  Runnable progressRunnable;

  public RNSoundModule(ReactApplicationContext context) {
    super(context);
    this.context = context;
  }

  @Override
  public String getName() {
    return "RNSound";
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         WritableMap params) {
    reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
  }

  @ReactMethod
  public void prepare(final String fileName, final Integer key, final Callback callback) {
    MediaPlayer player = createMediaPlayer(fileName);
    if (player == null) {
      WritableMap e = Arguments.createMap();
      e.putInt("code", -1);
      e.putString("message", "resource not found");
      callback.invoke(e);
      return;
    } else {
      player.stop();
    }
    try {
      player.prepare();
    } catch (Exception exception) {
              Log.e("RNSoundModule", "Exception", exception);

       WritableMap e = Arguments.createMap();
        e.putInt("code", -1);
        e.putString("message", exception.getMessage());
        callback.invoke(e);
        return;
    }
    this.playerPool.put(key, player);
    WritableMap props = Arguments.createMap();
    props.putDouble("duration", player.getDuration() * .001);
    callback.invoke(NULL, props);
  }

  protected MediaPlayer createMediaPlayer(final String fileName) {
    int res = this.context.getResources().getIdentifier(fileName, "raw", this.context.getPackageName());
    if (res != 0) {
      return MediaPlayer.create(this.context, res);
    }
    if(fileName.startsWith("http://") || fileName.startsWith("https://")) {
      MediaPlayer mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      Log.i("RNSoundModule", fileName);
      try {
        mediaPlayer.setDataSource(fileName);
      } catch(IOException e) {
        Log.e("RNSoundModule", "Exception", e);
        return null;
      }
      return mediaPlayer;
    }

    File file = new File(fileName);
    if (file.exists()) {
      Uri uri = Uri.fromFile(file);
      return MediaPlayer.create(this.context, uri);
    }
    return null;
  }

  @ReactMethod
  public void play(final Integer key, final Callback callback) {
    final MediaPlayer player = this.playerPool.get(key);
    if (player == null) {
      callback.invoke(false);
      return;
    }
    if (player.isPlaying()) {
      return;
    }
    player.setOnCompletionListener(new OnCompletionListener() {
      @Override
      public synchronized void onCompletion(MediaPlayer mp) {
        if (!mp.isLooping()) {
          callback.invoke(true);
        }
      }
    });
    player.setOnErrorListener(new OnErrorListener() {
      @Override
      public synchronized boolean onError(MediaPlayer mp, int what, int extra) {
        callback.invoke(false);
        return true;
      }
    });
    player.start();

    if (isWaveformEnabled) {
      createVisualizer();
      handler.postDelayed(new Runnable() {
        public void run() {
          waveformRunnable = this;
          updateWaveform();
          handler.postDelayed(waveformRunnable, waveformDelay);
        }
      }, waveformDelay);
    }

    if (isProgressEnabled) {
      handler.postDelayed(new Runnable() {
        public void run() {
          progressRunnable = this;
          updateProgress(player);
          handler.postDelayed(progressRunnable, progressDelay);
        }
      }, progressDelay);
    }
  }

  @ReactMethod
  public void pause(final Integer key) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null && player.isPlaying()) {
      player.pause();
      releaseEventListeners();
    }
  }

  @ReactMethod
  public void stop(final Integer key) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null && player.isPlaying()) {
      player.pause();
      player.seekTo(0);
      releaseEventListeners();
    }
  }

  @ReactMethod
  public void release(final Integer key) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.release();
      this.playerPool.remove(key);
    }
  }

  @ReactMethod
  public void setVolume(final Integer key, final Float left, final Float right) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.setVolume(left, right);
    }
  }

  @ReactMethod
  public void setLooping(final Integer key, final Boolean looping) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.setLooping(looping);
    }
  }

  @ReactMethod
  public void setCurrentTime(final Integer key, final Float sec) {
    MediaPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.seekTo((int) Math.round(sec * 1000));
    }
  }

  @ReactMethod
  public void getCurrentTime(final Integer key, final Callback callback) {
    MediaPlayer player = this.playerPool.get(key);
    if (player == null) {
      callback.invoke(-1, false);
      return;
    }
    callback.invoke(player.getCurrentPosition() * .001, player.isPlaying());
  }

  @ReactMethod
  public void enable(final Boolean enabled) {
    // no op
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("IsAndroid", true);
    return constants;
  }

  @ReactMethod
  public void enableWaveform(final Boolean enabled) {
    isWaveformEnabled = enabled;
  }

  @ReactMethod
  public void enableProgress(final Boolean enabled) {
    isProgressEnabled = enabled;
  }

  private void createVisualizer(){
    int rate = Visualizer.getMaxCaptureRate();
    audioOutput = new Visualizer(0);
    audioOutput.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
      @Override
      public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
        float level = ((float) waveform[0] + 128f) / 256;
        intensity = level * 100;
      }

      @Override
      public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {

      }
    }, rate, true, false);
    audioOutput.setEnabled(true);
  }

  private void updateWaveform() {
    WritableMap params = Arguments.createMap();
    params.putInt("intensity", (int) intensity);
    sendEvent(getReactApplicationContext(), "OnWaveform", params);
  }

  private void updateProgress(MediaPlayer player) {
    WritableMap params = Arguments.createMap();
    params.putDouble("progress", player.getCurrentPosition() * .001);
    sendEvent(getReactApplicationContext(), "OnProgress", params);
  }

  private void releaseEventListeners() {
    handler.removeCallbacks(progressRunnable);
    handler.removeCallbacks(waveformRunnable);
    audioOutput.release();
  }

}
