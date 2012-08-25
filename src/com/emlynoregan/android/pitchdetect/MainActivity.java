package com.emlynoregan.android.pitchdetect;

import com.emlynoregan.external.Yin;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class MainActivity extends Activity 
{
	public static String _tag = "PD_MAIN";
	public static int ReadBufferSize = 1024;
	TextView _txtFrequency;
	TextView _txtNote;
	AudioRecord _audioRecord;
	boolean _wantSnapshot = false;
	float _pitch;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mainlayout);

        _txtFrequency = (TextView)findViewById(R.id.txtFrequency);
        _txtNote = (TextView)findViewById(R.id.txtNote);

        int lbufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT) * 8;
        
        _audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, lbufferSize);

        if (_audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
        {
	        _audioRecord.setPositionNotificationPeriod(44100 / 2); // should make sure the buffer is a multiple of this
	        
	        _audioRecord.setRecordPositionUpdateListener(
	        		new OnRecordPositionUpdateListener() 
	        		{
						public void onPeriodicNotification(AudioRecord recorder) 
						{
							runOnUiThread(new Runnable() 
							{
								public void run() 
								{
									String lout = String.format("%f Hz", _pitch);
									_txtFrequency.setText(lout);

									double lhalfSteps = getHalfStepsFromA4((float)_pitch);
									lout = halfStepsToString(lhalfSteps);
									_txtNote.setText(lout);
								}
							});
						}
						public void onMarkerReached(AudioRecord recorder) 
						{
							Log.d(_tag, "onMarkerReached");
						}
					}
	        );
        	
        	
        }
        else
        {
        	_txtFrequency.setText(
        			"Failed to initialize AudioRecord" +
        			", source = " + ((Integer)_audioRecord.getAudioSource()).toString() + 
        			", format = " + ((Integer)_audioRecord.getAudioFormat()).toString() + 
        			", rate = " + ((Integer)_audioRecord.getSampleRate()).toString() +
        			", config = " + ((Integer)_audioRecord.getChannelConfiguration()).toString()
        			);
        }

        if (savedInstanceState != null)
        	_pitch = savedInstanceState.getFloat("pitch");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    protected void onPause() 
    {
    	super.onPause();
    	
    	if (_audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
    	{
    		_audioRecord.stop();
    	}
    }
    
    @Override
    protected void onResume() 
    {
    	super.onResume();

    	if (_audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
    	{
    		_audioRecord.startRecording();
    		
        	Thread lrecorder = new Thread(new Runnable() 
        	{
				public void run() 
				{
					int lcounter = 0;
					while (_audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED)
					{
						final short[] lshortArray = new short[ReadBufferSize/2];
						
						_audioRecord.read(lshortArray, 0, ReadBufferSize/2);

						if (lcounter == 0)
						{
							Yin lyin = new Yin(_audioRecord.getSampleRate());
							double linstantaneousPitch = lyin.getPitch(lshortArray);
							if (linstantaneousPitch >= 0)
							{
								_pitch = (float)((2.0 * _pitch + linstantaneousPitch) / 3.0);
							}
						}
						
						lcounter = (lcounter + 1) % 5;
					}
				}
			});
        	
        	lrecorder.start();
    	}
    }
    
    public double getHalfStepsFromA4(float aFrequency)
    {
    	return 12 * (log(aFrequency / 440.0, 2) );
    }
    
    public String halfStepsToString(double aHalfStepsFromA4)
    {
    	String retval = "";
    	
    	
    	int lintHalfStepsFromC0 = (int)aHalfStepsFromA4 + 57;
    	
    	int loctave = (lintHalfStepsFromC0 / 12);

    	int lnoteInOctave = lintHalfStepsFromC0 % 12;
    	
    	switch (lnoteInOctave)
    	{
    		case 0: retval += "C"; break;
    		case 1: retval += "C#"; break;
    		case 2: retval += "D"; break;
    		case 3: retval += "D#"; break;
    		case 4: retval += "E"; break;
    		case 5: retval += "F"; break;
    		case 6: retval += "F#"; break;
    		case 7: retval += "G"; break;
    		case 8: retval += "G#"; break;
    		case 9: retval += "A"; break;
    		case 10: retval += "A#"; break;
    		case 11: retval += "B"; break;
    	}
    	
    	retval = String.format("%s%d", retval, loctave);
    	
    	return retval;
    }
    
    static double log(double x, double base)
    {
        return (Math.log(x) / Math.log(base));
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) 
    {
    	super.onSaveInstanceState(outState);
    	
    	outState.putFloat("pitch", _pitch);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) 
    {
    	super.onRestoreInstanceState(savedInstanceState);
    	
    	_pitch = savedInstanceState.getFloat("pitch");
    }
}
