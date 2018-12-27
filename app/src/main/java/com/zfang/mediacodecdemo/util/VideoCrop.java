package com.zfang.mediacodecdemo.util;

import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.fail;

public class VideoCrop {

    private String TAG = "VideoCrop";
    private boolean VERBOSE = true;
    // encoder / muxer state
    private MediaCodec mEncoder;
    private MediaCodec mDecoder;
    private CodecInputSurface mInputSurface;
    private MediaMuxer mMuxer;
    private int mTrackIndex;
    private boolean mMuxerStarted;

    // allocate one of these up front so we don't need to do it every time
    private MediaCodec.BufferInfo mBufferInfo;


    public void startCrop(String inputVideoPath, String outVideoPath) throws IOException {
        MediaExtractor videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(inputVideoPath);

        int videoMaxSampleSize = -1;
        int videoTrackIndex = -1;
        int bitRate = 1_500_000;
        int frameRate = -1;
        int iFrameInterval = 5;
        int colorFormat = -1;
        MediaFormat videoFormat = null;
        String mimeType = null;
        int trackCount = videoExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                videoTrackIndex = i;
                videoFormat = format;
                videoFormat.setInteger("crop-left", 360);
                videoFormat.setInteger("crop-top", 720);
                videoFormat.setInteger("crop-right", 720);
                videoFormat.setInteger("crop-bottom", 1440);
                videoMaxSampleSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);

//                bitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
//                iFrameInterval = format.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL);
//                colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                break;
            }
        }


        prepareEncoder(videoFormat, outVideoPath, colorFormat, mimeType, 360, 720, bitRate, frameRate, iFrameInterval);

        videoExtractor.selectTrack(videoTrackIndex);
        ByteBuffer byteBuffer = ByteBuffer.allocate(videoMaxSampleSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int readSampleSize = -1;
        int videoTrackIndexOutput = -1;
        boolean inputDone = false;
        boolean outputDone = false;
        while (true) {
            if (!inputDone) {
                int inputIndex = mEncoder.dequeueInputBuffer(0);
                if (inputIndex < 0) {
                    continue;
                }

                ByteBuffer bufferInput = mEncoder.getInputBuffer(inputIndex);
                if (null == bufferInput) {
                    continue;
                }
                readSampleSize = videoExtractor.readSampleData(bufferInput, 0);
                if (readSampleSize < 0) {
                    mEncoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    videoExtractor.unselectTrack(videoTrackIndex);
                    inputDone = true;
                } else {
                    long pts = videoExtractor.getSampleTime();
                    int flags = videoExtractor.getSampleFlags();
                    mEncoder.queueInputBuffer(inputIndex, 0, readSampleSize, pts, flags);
                }

                videoExtractor.advance();
            }


            if (!outputDone) {
                int outIndex = mEncoder.dequeueOutputBuffer(bufferInfo, 0);
                if (MediaCodec.INFO_TRY_AGAIN_LATER == outIndex) {
                    continue;
                } else if (MediaCodec.INFO_OUTPUT_FORMAT_CHANGED == outIndex) {
                    if (!mMuxerStarted) {
                        mMuxerStarted = true;
                        videoTrackIndexOutput = mMuxer.addTrack(mEncoder.getOutputFormat());
                        mMuxer.start();
                    }
                } else if (outIndex < 0) {
                    continue;//Unexpected state
                } else {
                    ByteBuffer buffer = mEncoder.getOutputBuffer(outIndex);
                    if (null != buffer) {
                        buffer.position(bufferInfo.offset);
                        buffer.limit(bufferInfo.offset + bufferInfo.size);
                        mMuxer.writeSampleData(videoTrackIndexOutput, buffer, bufferInfo);
                    }
                    mEncoder.releaseOutputBuffer(outIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
//                        if (!endOfStream) {
//                            Log.w(TAG, "reached end of stream unexpectedly");
//                        } else {
//                            if (VERBOSE) Log.d(TAG, "end of stream reached");
//                        }
                        break;      // out of while
                    }
                }
            }
        }
    }

    /**
     * Configures encoder and muxer state, and prepares the input Surface.  Initializes
     * mEncoder, mMuxer, mInputSurface, mBufferInfo, mTrackIndex, and mMuxerStarted.
     */
    private void prepareEncoder(MediaFormat videoFormat, String outPath, int colorFormat, String mimeType,
                                int width, int height, int bitRate, int frameRate, int iFrameInterval) throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(mimeType, width, height);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);

//        videoFormat.setInteger("crop-left", 360);
//        videoFormat.setInteger("crop-top", 720);
//        videoFormat.setInteger("crop-right", 720);
//        videoFormat.setInteger("crop-bottom", 1440);

//        videoFormat.setInteger(MediaFormat.KEY_WIDTH, width);
//        videoFormat.setInteger(MediaFormat.KEY_HEIGHT, height);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
//        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, findNonSurfaceColorFormat(selectCodec(mimeType), mimeType));
        if (VERBOSE) Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        //
        // If you want to have two EGL contexts -- one for display, one for recording --
        // you will likely want to defer instantiation of CodecInputSurface until after the
        // "display" EGL context is created, then modify the eglCreateContext call to
        // take eglGetCurrentContext() as the share_context argument.
        mEncoder = MediaCodec.createEncoderByType(mimeType);
        mEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        mInputSurface = new CodecInputSurface(mEncoder.createInputSurface());
        mEncoder.start();

        // Output filename.  Ideally this would use Context.getFilesDir() rather than a
        // hard-coded output directory.
//        String outputPath = new File(OUTPUT_DIR,
//                "test." + width + "x" + height + ".mp4").toString();
        Log.i(TAG, "Output file is " + outPath);


        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        try {
            mMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        // FIXME: select codecs based on the complete use-case, not just the mime
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (!info.isEncoder()) {
                continue;
            }

            String[] types = info.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * Returns a color format that is supported by the codec and isn't COLOR_FormatSurface.  Throws
     * an exception if none found.
     */
    private static int findNonSurfaceColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                return colorFormat;
            }
        }
        return 0;   // not reached
    }

    private void prepareDecoder(String outPath, String mimeType, int width, int height, int bitRate, int frameRate, int iFrameInterval) throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(mimeType, width, height);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);

        format.setInteger("crop-left", 360);
        format.setInteger("crop-top", 720);
        format.setInteger("crop-right", 720);
        format.setInteger("crop-bottom", 1440);
        if (VERBOSE) Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        //
        // If you want to have two EGL contexts -- one for display, one for recording --
        // you will likely want to defer instantiation of CodecInputSurface until after the
        // "display" EGL context is created, then modify the eglCreateContext call to
        // take eglGetCurrentContext() as the share_context argument.
        mEncoder = MediaCodec.createEncoderByType(mimeType);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = new CodecInputSurface(mEncoder.createInputSurface());
        mEncoder.start();
    }


    /**
     * Holds state associated with a Surface used for MediaCodec encoder input.
     * <p>
     * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses
     * that to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to
     * be sent to the video encoder.
     * <p>
     * This object owns the Surface -- releasing this will release the Surface too.
     */
    private static class CodecInputSurface {
        private static final int EGL_RECORDABLE_ANDROID = 0x3142;

        private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
        private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

        private Surface mSurface;

        /**
         * Creates a CodecInputSurface from a Surface.
         */
        public CodecInputSurface(Surface surface) {
            if (surface == null) {
                throw new NullPointerException();
            }
            mSurface = surface;

            eglSetup();
        }

        /**
         * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
         */
        private void eglSetup() {
            mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException("unable to get EGL14 display");
            }
            int[] version = new int[2];
            if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
                throw new RuntimeException("unable to initialize EGL14");
            }

            // Configure EGL for recording and OpenGL ES 2.0.
            int[] attribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL_RECORDABLE_ANDROID, 1,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                    numConfigs, 0);
            checkEglError("eglCreateContext RGB888+recordable ES2");

            // Configure context for OpenGL ES 2.0.
            int[] attrib_list = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                    attrib_list, 0);
            checkEglError("eglCreateContext");

            // Create a window surface, and attach it to the Surface we received.
            int[] surfaceAttribs = {
                    EGL14.EGL_NONE
            };
            mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurface,
                    surfaceAttribs, 0);
            checkEglError("eglCreateWindowSurface");
        }

        /**
         * Discards all resources held by this class, notably the EGL context.  Also releases the
         * Surface that was passed to our constructor.
         */
        public void release() {
            if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
                EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(mEGLDisplay);
            }
            mSurface.release();

            mEGLDisplay = EGL14.EGL_NO_DISPLAY;
            mEGLContext = EGL14.EGL_NO_CONTEXT;
            mEGLSurface = EGL14.EGL_NO_SURFACE;

            mSurface = null;
        }

        /**
         * Makes our EGL context and surface current.
         */
        public void makeCurrent() {
            EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
            checkEglError("eglMakeCurrent");
        }

        /**
         * Calls eglSwapBuffers.  Use this to "publish" the current frame.
         */
        public boolean swapBuffers() {
            boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
            checkEglError("eglSwapBuffers");
            return result;
        }

        /**
         * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
         */
        public void setPresentationTime(long nsecs) {
            EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
            checkEglError("eglPresentationTimeANDROID");
        }

        /**
         * Checks for EGL errors.  Throws an exception if one is found.
         */
        private void checkEglError(String msg) {
            int error;
            if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
                throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
            }
        }
    }

}
