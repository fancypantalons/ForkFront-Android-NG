package com.tbd.forkfront;

import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SoundPlayer {

	private SoundPool mSoundPool;
	private final Map<String, Integer> mSoundIds = new HashMap<>();
	private final Set<Integer> mLoadedSounds = new HashSet<>();

	public SoundPlayer() {
	}

	public void load(String filename)
	{
		if(!mSoundIds.containsKey(filename))
		{
			if(mSoundPool == null) {
				mSoundPool = new SoundPool.Builder()
						.setMaxStreams(10)
						.setAudioAttributes(new AudioAttributes.Builder()
								.setUsage(AudioAttributes.USAGE_GAME)
								.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
								.build())
						.build();
				mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
					@Override
					public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
						if(status == 0) {
							mLoadedSounds.add(sampleId);
						}
					}
				});
			}
			int soundId = mSoundPool.load(filename, 1);
			mSoundIds.put(filename, soundId);
		}
	}

	public void play(String filename, int volume)
	{
		Integer soundId = mSoundIds.get(filename);
		if(soundId == null || soundId == 0 || !mLoadedSounds.contains(soundId))
			return;
		float fVolume = Math.max(0.f, Math.min(1.f, volume / 100.f));
		mSoundPool.play(soundId, fVolume, fVolume, 1, 0, 1);
	}

	public void release()
	{
		if(mSoundPool != null) {
			mSoundPool.release();
			mSoundPool = null;
		}
		mSoundIds.clear();
		mLoadedSounds.clear();
	}
}
