package com.dvr.mel.dronevoicerecognition;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedList;
import java.util.Queue;

/**************************************************************************************************
 *  MicWavRecorderHandler in a nutshell:                                                          *
 *      _ Initialize a "Microphone Input Stream" using AudioRecord                                *
 *      _ Handle audiAnalysis / relevance and File IO operations to WavStreamHandler
 *      _ notify WavStreamHandler's thread every time the buffer is filled
 *      // TODO actualize this, kinda based on MVC architecture
 *
 *                                                                                                *
 *                                                                                                *
 * Limitations : don't try to use multiple MicWavRecorders at the same time... Just don't, ok ... *
 *               this class is implementing singleton design pattern anyway, so go crazy...try it *
 *               That wouldn't make sense anyway to grab (and modify) mic input buffer            *
 *               from multiple MicWavRecorder threads anyways,                                    *
 *               and concurrent mic input access is also prohibited                               *
 *************************************************************************************************/

/*****************************************
 * TODO List, what to tackle first:
 *          _ detectAudioSpike simili function
 *          _ creation and suppression of file
 *          _ recording of audio stream in those file
 *          _ kill WavStreamHandler thread in close()
 *          _ implement more safe barrier , make a List of streamBuffer and list of WavStreamHandler giving them ticket and stuff like that so they
 *          work in the correct order, shouldn't be mandatory considering the size of the buffer and the operation we compute on it
 *          _ chill out
 */

// custom Exception
class MicWavRecorderHandlerException extends Exception
{
    public MicWavRecorderHandlerException(String message)
    {
        super(message);
    }
}




public class MicWavRecorderHandler extends Thread
{
    /***************************************************
     *                                                 *
     *                INNER VARIABLES                  *
     *                                                 *
     ***************************************************/

    /**** Singleton insurance****/
    //MicWavRecorderHandler singletonInstance; //TODO when got time

    /**** AudioRecord's settings (AUDIO FORMAT SETTINGS) ****/
    private long SAMPLE_RATE; // in our usecase<=>16000, 16KHz // stored in a long cause it's stored as such in a wav header
    private int CHANNEL_MODE; // in our usecase<=>AudioFormat.CHANNEL_IN_MONO<=>mono signal
    private int ENCODING_FORMAT; // in our usecase<=>AudioFormat.ENCODING_PCM_16BIT<=>16 bits

    /**** Associated threads ****/
    public MicTestActivity activity; // Activity "linked to"/"which started" this MicWavRecorder
    private WavStreamHandler audioAnalyser = new WavStreamHandler(this);
                                  // used to analyse mic's input buffer without blocking
                                  // this thread from filling it. ("Producer, Consumer" problem)
                                  // all Files IO and Audio Analysing are delegated over there

    /**** AudioRecorder and its streamBuffer, streamBufferQueue ****/
    private AudioRecord mic;
    public int bufferSize; // size of following buffers
    private short[] streamBuffer; // buffer used to constantly listen to the mic
    public Queue<short[]> streamBufferQueue; // streamBuffer filled are pushed onto this Queue, waiting for their treatment

    /**** MicWavRecorder's lifespan variable ****/
    private volatile boolean runningState = true; // describe MicWavRecorder's lifespan
                                                  // by stopping its run() loop
                                                  // TODO : this is really basic thread management, to replace if enough time



    /***************************************************
     *                                                 *
     *           CONSTRUCTOR & "DESTRUCTOR"            *
     *                                                 *
     ***************************************************/


    MicWavRecorderHandler( long SAMPLE_RATE_, int CHANNEL_MODE_, int ENCODING_FORMAT_,
                    MicTestActivity activity_) throws MicWavRecorderHandlerException
    {
//        if (singletonInstance == null)
//            singletonInstance = this; // TODO later and better

        // Initializing "USER DETERMINED VARIABLES"
        SAMPLE_RATE = SAMPLE_RATE_;
        CHANNEL_MODE = CHANNEL_MODE_;
        ENCODING_FORMAT = ENCODING_FORMAT_;

        //Microphone Initialization
        bufferSize = 10*AudioRecord.getMinBufferSize((int)SAMPLE_RATE, CHANNEL_MODE, ENCODING_FORMAT);
                    // value expressed in bytes
                    // using 10 times the getMinBufferSize to avoid IO operations and reduce a bad "producer / consumer" case's probabilities
        mic = new AudioRecord( MediaRecorder.AudioSource.MIC,
                (int)SAMPLE_RATE, CHANNEL_MODE,
                ENCODING_FORMAT, bufferSize );
                // mic always on, completing a non-circular buffer
                // use audioAnalyser (WavStreamHandler) to detect if buffer is relevant or not
                //     <=> if phone is recording silence or not.
        Log.i("MicWavRecorder", "State"+mic.getState()); // check that AudioRecord has been correctly instantiated
        if ( mic.getState() != AudioRecord.STATE_INITIALIZED ) throw new MicWavRecorderHandlerException("Couldn't instantiate AudioRecord properly");

        // Initializing streamBufferQueue
        streamBufferQueue = new LinkedList<>();

        // Initializing buffers
        streamBuffer = new short[bufferSize];

        // Link current MivWavRecorder's thread to its MicTestActivity's thread
        activity = activity_;

        // Start the WavStreamHandler's thread that will detect audio's spikes
        audioAnalyser.start();

        // Start recording with the mic
        mic.startRecording();
    }



    public void close()
    {
        // closing microphone
        mic.stop();
        mic.release();

        //closing AudioAnalyser
        audioAnalyser.close();

        // stop the run loop / thread
        runningState = false;
    }



    /***************************************************
     *                                                 *
     *                   RUN LOOP                      *
     *                                                 *
     ***************************************************/



    @Override
    public void run()
    {
        while(runningState)
        {
            // update streamBuffer
            mic.read(streamBuffer, 0, bufferSize); // read() IS A BLOCKING METHOD !!!   
                                                   // it will wait for the buffer to be filled before returning it

            // add filled streamBuffer to the Queue of buffer to be analysed
            streamBufferQueue.add(streamBuffer);

            // notify WavStreamHandler that a streamBuffer is ready to be pulled and computed
            // delegate the analysis of streamBuffer and IO operations on another thread
            // so buffer can be filled without waiting for its analysis / missing any audioFrames
            audioAnalyser.notify();
        }

        // wait for WavStreamHandler to finish computing the streamBuffer of the queue before closing
        // will be waken up by a WavStreamHandler notification
        try { this.wait(); } catch (InterruptedException ie) { ie.printStackTrace();}
    }




}
