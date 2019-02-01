package com.fabio.mediacodectestone;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioTrack.MODE_STREAM;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "fdl";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tryThis();
//        play(this,R.raw.audio);



    }


    private void tryThis() {


        new Thread(new Runnable() {
            public void run() {


                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                AssetFileDescriptor sampleFD = getResources().openRawResourceFd(R.raw.audio);

                MediaExtractor extractor = new MediaExtractor();

                try {
                    extractor.setDataSource(sampleFD.getFileDescriptor(), sampleFD.getStartOffset(), sampleFD.getLength());

                    Log.d(TAG, String.format("TRACKS #: %d", extractor.getTrackCount()));
                    MediaFormat trackFormat = extractor.getTrackFormat(0);
                    String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                    int bitrate = trackFormat.getInteger(MediaFormat.KEY_BIT_RATE);
                    Log.d(TAG, String.format("MIME TYPE: %s", mime));
                    Log.d(TAG, String.format("bitrate: %s", bitrate));
                    extractor.selectTrack(0);



                    extractor.seekTo(100*1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);



                    //setting AudioTrack for playback
                    AudioAttributes aAttributes = new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build();

                    AudioTrack mAudioTrack = new AudioTrack(
                            aAttributes,
                            new AudioFormat.Builder().build(),
                            1600,//bitrate,
                            MODE_STREAM,
                            0
                    );
                    mAudioTrack.setPlaybackRate(trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                    mAudioTrack.setVolume(1.0f);
                    mAudioTrack.play();


                    // Create Decoder
                    MediaCodec decoder = MediaCodec.createDecoderByType(mime);
                    decoder.configure(trackFormat, null /* surface */, null /* crypto */, 0 /* flags */);
                    decoder.start();


                    boolean sawInputEOS = false;
                    boolean sawOutputEOS = false;


                    while (!sawInputEOS && !sawOutputEOS) {
                        Log.d(TAG, "for loop");


                        // Read from mp3
                        int inputBufferId = decoder.dequeueInputBuffer(-1);
                        if (inputBufferId >= 0) {
                            ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                            // fill inputBuffer with valid data

                            long presentationTimeUs = 0;
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            extractor.advance();
                            Log.d(TAG, "read sampleSize:" + sampleSize);
                            if (sampleSize < 0) {
                                sawInputEOS = true;
                                sampleSize = 0;
                                Log.d(TAG, "saw EOF in input");
                            } else {
                                presentationTimeUs = extractor.getSampleTime();
                            }
                            decoder.queueInputBuffer(inputBufferId,
                                    0, //offset
                                    sampleSize,
                                    presentationTimeUs,
                                    sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        } else if(inputBufferId==MediaCodec.INFO_TRY_AGAIN_LATER) {
                            Log.w(TAG, "INFO_TRY_AGAIN_LATER");

                        } else if(inputBufferId==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            Log.w(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                            trackFormat = decoder.getOutputFormat();
                        } else
                            Log.w(TAG, "unknown error dequeueInputBuffer");






                        // decode
                        Log.d(TAG, "decoding....");
                        int outputBufferId = decoder.dequeueOutputBuffer(info, -1);

                        if (outputBufferId >= 0) {
                            ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferId);
                            trackFormat = decoder.getOutputFormat(outputBufferId); // option A
                            // bufferFormat is identical to outputFormat
                            // outputBuffer is ready to be processed or rendered.


                            //Audio Rendering
                            final byte[] chunk = new byte[info.size];
                            outputBuffer.get(chunk); // Read the buffer all at once
                            outputBuffer.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN

                            if (chunk.length > 0) {
                                Log.d(TAG, "writing chunk:" + chunk.length);
                                mAudioTrack.write(chunk, 0, chunk.length);
                            }
                            decoder.releaseOutputBuffer(outputBufferId, false /* render */);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEOS = true;
                                Log.d(TAG, "saw output EOS");
                            }


                        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // Subsequent data will conform to new format.
                            // Can ignore if using getOutputFormat(outputBufferId)
                            trackFormat = decoder.getOutputFormat(); // option B
                        }
                    }
                    Log.d(TAG, "done foe loop");


                    decoder.stop();
                    decoder.release();
                } catch (IOException ex) {
                    Log.e("exception:", ex.toString());
                }

            }

        }).start();

    }













    public void play(Context aContext, final int resourceId){

        final Context context = aContext;

        new Thread()
        {
            @Override
            public void run() {

                try {
                    AssetFileDescriptor fd = context.getResources().openRawResourceFd(resourceId);

                    MediaExtractor extractor = new MediaExtractor();
                    extractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                    extractor.selectTrack(0);

                    MediaFormat trackFormat = extractor.getTrackFormat(0);

                    MediaCodec decoder = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));
                    decoder.configure(trackFormat, null, null, 0);

                    decoder.start();
                    ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
                    ByteBuffer[] decoderOutputBuffers = decoder.getOutputBuffers();

                    int inputIndex = decoder.dequeueInputBuffer(-1);
                    ByteBuffer inputBuffer = decoderInputBuffers[inputIndex];
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    byte[] audioBuffer = null;
                    AudioTrack audioTrack = null;

                    int read = extractor.readSampleData(inputBuffer, 0);
                    while (read > 0) {
                        decoder.queueInputBuffer(inputIndex, 0, read, extractor.getSampleTime(), 0);

                        extractor.advance();

                        int outputIndex = decoder.dequeueOutputBuffer(bufferInfo, -1);
                        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                            trackFormat = decoder.getOutputFormat();

                        } else if (outputIndex >= 0) {

                            if (bufferInfo.size > 0) {

                                ByteBuffer outputBuffer = decoderOutputBuffers[outputIndex];
                                if (audioBuffer == null || audioBuffer.length < bufferInfo.size) {
                                    audioBuffer = new byte[bufferInfo.size];
                                }

                                outputBuffer.rewind();
                                outputBuffer.get(audioBuffer, 0, bufferInfo.size);
                                decoder.releaseOutputBuffer(outputIndex, false);

                                if (audioTrack == null) {
                                    int sampleRateInHz = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                                    int channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                                    int channelConfig = channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;


                                    audioTrack = new AudioTrack(
                                            AudioManager.STREAM_MUSIC,
                                            sampleRateInHz,
                                            channelConfig,
                                            AudioFormat.ENCODING_PCM_16BIT,
                                            AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, AudioFormat.ENCODING_PCM_16BIT) * 2,
                                            AudioTrack.MODE_STREAM);

                                    audioTrack.play();
                                }

                                audioTrack.write(audioBuffer, 0, bufferInfo.size);
                            }
                        }

                        inputIndex = decoder.dequeueInputBuffer(-1);
                        inputBuffer = decoderInputBuffers[inputIndex];

                        read = extractor.readSampleData(inputBuffer, 0);
                    }
                    decoder.stop();
                    decoder.release();
                    audioTrack.stop();
                    audioTrack.release();
                } catch (IOException ex) {
                    Log.e("exception:", ex.toString());
                }
            }
        }.start();
    }





}
