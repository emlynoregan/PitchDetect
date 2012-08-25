package com.emlynoregan.external;

//import java.io.File;
//import java.io.IOException;
//
//import javax.sound.sampled.AudioFormat;
//import javax.sound.sampled.AudioInputStream;
//import javax.sound.sampled.AudioSystem;
//import javax.sound.sampled.UnsupportedAudioFileException;
//

/**
 * @author Joren Six
 *An implementation of the YIN pitch tracking algorithm.
 *See <a href="http://recherche.ircam.fr/equipes/pcm/cheveign/ps/2002_JASA_YIN_proof.pdf">the YIN paper.</a>
 *
 *Implementation originally based on <a href="http://aubio.org">aubio</a>
 *
 *
 * Updated by Emlyn O'Regan to work in the PitchDetect sample project for Android.
 * I removed all the realtime features (which are tied in with javax libraries, not good for Dalvik), and
 * modified Yin to be called with a byte buffer to be analyzed using the getPitch() method. So
 * just create yourself a Yin, then call getPitch(bytes) when you're ready.
 * 
 * Also converted it to use an array of Shorts instead of Floats.
 * 
 * Original implementation is here: http://tarsos.0110.be/artikels/lees/YIN_Pitch_Tracker_in_JAVA
 */
public class Yin {


	/**
	 * Used to start and stop real time annotations.
	 */
//	private static Yin yinInstance;

	/**
	 * The YIN threshold value (see paper)
	 */
	private final double threshold = 0.15;

//	private int bufferSize;
//	private int overlapSize;
	private float sampleRate;
	/**
	 * A boolean to start and stop the algorithm.
	 * Practical for real time processing of data.
	 */
//	private volatile boolean running;

	/**
	 * The original input buffer
	 */
	private short[] inputBuffer;

	/**
	 * The buffer that stores the calculated values.
	 * It is exactly half the size of the input buffer.
	 */
	private float[] yinBuffer;

	public Yin(float sampleRate){
		this.sampleRate = sampleRate;
//		bufferSize = 1024;
//		overlapSize = bufferSize/2;//half of the buffer overlaps
//		running = true;
//		inputBuffer = new short[bufferSize];
//		yinBuffer = new float[bufferSize/2];
	}

	/**
	 * Implements the difference function as described
	 * in step 2 of the YIN paper
	 */
	private void difference(){
		int j,tau;
		float delta;
		for(tau=0;tau < yinBuffer.length;tau++){
			yinBuffer[tau] = 0;
		}
		for(tau = 1 ; tau < yinBuffer.length ; tau++){
			for(j = 0 ; j < yinBuffer.length ; j++){
				delta = (float)inputBuffer[j] - (float)inputBuffer[j+tau];
				yinBuffer[tau] += delta * delta;
			}
		}
	}

	/**
	 * The cumulative mean normalized difference function
	 * as described in step 3 of the YIN paper
	 * <br><code>
	 * yinBuffer[0] == yinBuffer[1] = 1
	 * </code>
	 *
	 */
	private void cumulativeMeanNormalizedDifference(){
		int tau;
		yinBuffer[0] = 1;
		//Very small optimization in comparison with AUBIO
		//start the running sum with the correct value:
		//the first value of the yinBuffer
		float runningSum = yinBuffer[1];
		//yinBuffer[1] is always 1
		yinBuffer[1] = 1;
		//now start at tau = 2
		for(tau = 2 ; tau < yinBuffer.length ; tau++){
			runningSum += yinBuffer[tau];
			yinBuffer[tau] *= tau / runningSum;
		}
	}

	/**
	 * Implements step 4 of the YIN paper
	 */
	private int absoluteThreshold(){
		//Uses another loop construct
		//than the AUBIO implementation
		for(int tau = 1;tau<yinBuffer.length;tau++){
			if(yinBuffer[tau] < threshold){
				while(tau+1 < yinBuffer.length &&
						yinBuffer[tau+1] < yinBuffer[tau])
					tau++;
				return tau;
			}
		}
		//no pitch found
		return -1;
	}

	/**
	 * Implements step 5 of the YIN paper. It refines the estimated tau value
	 * using parabolic interpolation. This is needed to detect higher
	 * frequencies more precisely.
	 *
	 * @param tauEstimate
	 *            the estimated tau value.
	 * @return a better, more precise tau value.
	 */
	private float parabolicInterpolation(int tauEstimate) {
		float s0, s1, s2;
		int x0 = (tauEstimate < 1) ? tauEstimate : tauEstimate - 1;
		int x2 = (tauEstimate + 1 < yinBuffer.length) ? tauEstimate + 1 : tauEstimate;
		if (x0 == tauEstimate)
			return (yinBuffer[tauEstimate] <= yinBuffer[x2]) ? tauEstimate : x2;
		if (x2 == tauEstimate)
			return (yinBuffer[tauEstimate] <= yinBuffer[x0]) ? tauEstimate : x0;
		s0 = yinBuffer[x0];
		s1 = yinBuffer[tauEstimate];
		s2 = yinBuffer[x2];
		//fixed AUBIO implementation, thanks to Karl Helgason:
		//(2.0f * s1 - s2 - s0) was incorrectly multiplied with -1
		return tauEstimate + 0.5f * (s2 - s0 ) / (2.0f * s1 - s2 - s0);
	}

	/**
	 * The main flow of the YIN algorithm. Returns a pitch value in Hz or -1 if
	 * no pitch is detected using the current values of the input buffer.
	 *
	 * @return a pitch value in Hz or -1 if no pitch is detected.
	 */
	public float getPitch(short[] aInputBuffer) 
	{
		inputBuffer = aInputBuffer;
		yinBuffer = new float[inputBuffer.length/2];
		
		int tauEstimate = -1;
		float pitchInHertz = -1;

		//step 2
		difference();

		//step 3
		cumulativeMeanNormalizedDifference();

		//step 4
		tauEstimate = absoluteThreshold();

		//step 5
		if(tauEstimate != -1){
			 float betterTau = parabolicInterpolation(tauEstimate);

			//step 6
			//TODO Implement optimization for the YIN algorithm.
			//0.77% => 0.5% error rate,
			//using the data of the YIN paper
			//bestLocalEstimate()

			//conversion to Hz
			pitchInHertz = sampleRate/betterTau;
		}

		return pitchInHertz;
	}

//
//	/**
//	 * The interface to use to react to detected pitches.
//	 * @author Joren Six
//	 *
//	 */
//	public interface DetectedPitchHandler{
//		/**
//		 * Use this method to react to detected pitches.
//		 * The handleDetectedPitch is called for every sample even when
//		 * there is no pitch detected: in that case -1 is the pitch value.
//		 * @param time in seconds
//		 * @param pitch in Hz
//		 */
//		void handleDetectedPitch(float time,float pitch);
//	}
//
//	/**
//	 * Annotate a file with pitch information.
//	 *
//	 * @param fileName
//	 *            the file to annotate.
//	 * @param detectedPitchHandler
//	 *            handles the pitch information.
//	 * @throws UnsupportedAudioFileException
//	 *             Currently only WAVE files with one channel (MONO) are
//	 *             supported.
//	 * @throws IOException
//	 *             If there is an error reading the file.
//	 */
//
//	
//	public static void processFile(String fileName,DetectedPitchHandler detectedPitchHandler)  throws UnsupportedAudioFileException, IOException{
//		AudioInputStream ais = AudioSystem.getAudioInputStream(new File(fileName));
//		AudioFloatInputStream afis = AudioFloatInputStream.getInputStream(ais);
//		Yin.processStream(afis,detectedPitchHandler);
//	}
//
//	/**
//	 * Annotate an audio stream: useful for real-time pitch tracking.
//	 *
//	 * @param afis
//	 *            The audio stream.
//	 * @param detectedPitchHandler
//	 *            Handles the pitch information. If null then the annotated
//	 *            pitch information is printed to <code>System.out</code>
//	 * @throws UnsupportedAudioFileException
//	 *             Currently only WAVE streams with one channel (MONO) are
//	 *             supported.
//	 * @throws IOException
//	 *             If there is an error reading the stream.
//	 */
//	public static void processStream(AudioFloatInputStream afis,DetectedPitchHandler detectedPitchHandler) throws UnsupportedAudioFileException, IOException{
//		AudioFormat format = afis.getFormat();
//		float sampleRate = format.getSampleRate();
//		double frameSize = format.getFrameSize();
//		double frameRate = format.getFrameRate();
//		float time = 0;
//
//		//by default use the print pitch handler
//		if(detectedPitchHandler==null)
//			detectedPitchHandler = Yin.PRINT_DETECTED_PITCH_HANDLER;
//
//		//number of bytes / frameSize * frameRate gives the number of seconds
//		//because we use float buffers there is a factor 2: 2 bytes per float?
//		//Seems to be correct but a float uses 4 bytes: confused programmer is confused.
//		float timeCalculationDivider = (float) (frameSize * frameRate / 2);
//		long floatsProcessed = 0;
//		yinInstance = new Yin(sampleRate);
//		int bufferStepSize = yinInstance.bufferSize - yinInstance.overlapSize;
//
//		//read full buffer
//		boolean hasMoreBytes = afis.read(yinInstance.inputBuffer,0, yinInstance.bufferSize) != -1;
//		floatsProcessed += yinInstance.inputBuffer.length;
//		while(hasMoreBytes && yinInstance.running) {
//			float pitch = yinInstance.getPitch();
//			time = floatsProcessed / timeCalculationDivider;
//			detectedPitchHandler.handleDetectedPitch(time,pitch);
//			//slide buffer with predefined overlap
//			for(int i = 0 ; i < bufferStepSize ; i++)
//				yinInstance.inputBuffer[i]=yinInstance.inputBuffer[i+yinInstance.overlapSize];
//			hasMoreBytes = afis.read(yinInstance.inputBuffer,yinInstance.overlapSize,bufferStepSize) != -1;
//			floatsProcessed += bufferStepSize;
//		}
//	}
//
//	/**
//	 * Stops real time annotation.
//	 */
//	public static void stop(){
//		if(yinInstance!=null)
//			yinInstance.running = false;
//	}
//
//
//	public static DetectedPitchHandler PRINT_DETECTED_PITCH_HANDLER = new DetectedPitchHandler() {
//		@Override
//		public void handleDetectedPitch(float time, float pitch) {
//			System.out.println(time + "\t" + pitch);
//		}
//	};
//
//	public static void main(String... args) throws UnsupportedAudioFileException, IOException{
//		Yin.processFile("../Tarsos/audio/pitch_check/brass-880.wav", PRINT_DETECTED_PITCH_HANDLER);
//	}
}
