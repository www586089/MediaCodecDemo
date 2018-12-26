package com.zfang.mediacodecdemo.util;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.provider.MediaStore;
import android.support.constraint.solver.LinearSystem;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class MediaParseUtil {

    private static String TAG = "MediaParseUtil";
    /**
     * 视频合并
     * @param videoList
     * @param outVideoPath
     */
    public static void mergeVideo(List<String> videoList, String outVideoPath) throws IOException {
        if (null == videoList || 0 == videoList.size()) {
            return;
        }
        if (1 == videoList.size()) {
            File srcFile = new File(videoList.get(0));
            File dstFile = new File(outVideoPath);
            srcFile.renameTo(dstFile);
            return;
        }

        MediaMuxer mediaMuxer = null;
        boolean result = false;
        int fileCount = videoList.size();
        int outVideoTrackIndex = -1;
        int outAudioTrackIndex = -1;
        long audioLastTime = 0;
        long videoLastTime = 0;

        Log.e(TAG, "mergeVideo, fileCount = " + fileCount);
        for (int i = 0; i < fileCount; i++) {
            String filePath = videoList.get(i);
            MediaExtractor videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(filePath);
            MediaExtractor audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(filePath);


            int audioTrackIndex = getTrackIndex(audioExtractor, "audio/");
            if (-1 == audioTrackIndex) {
                Log.e(TAG, "file = " + videoList.get(i) + " can't find audioTrack");
                continue;
            }
            MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrackIndex);
            int maxAudioSampleSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);

            int videoTrackIndex = getTrackIndex(videoExtractor, "video/");
            if (-1 == videoTrackIndex) {
                Log.e(TAG, "file = " + videoList.get(i) + " can't find videoTrack");
                continue;
            }
            MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
            int maxVideoSampleSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);

            if (!result) {
                result = true;
                mediaMuxer = new MediaMuxer(outVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                outVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
                outAudioTrackIndex = mediaMuxer.addTrack(audioFormat);
                mediaMuxer.start();
            }

            //copy video data
            int readSampleSize = -1;
            ByteBuffer videoByteBuffer = ByteBuffer.allocate(maxVideoSampleSize);
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            videoExtractor.selectTrack(videoTrackIndex);
            while (true) {
                readSampleSize = videoExtractor.readSampleData(videoByteBuffer, 0);
                if (readSampleSize < 0) {
                    Log.e(TAG, "No Video data, for file = " + filePath);
                    videoExtractor.unselectTrack(videoTrackIndex);
                    break;
                }

                videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime() + videoLastTime;
                videoBufferInfo.size = readSampleSize;
                videoBufferInfo.offset = 0;
                videoBufferInfo.flags = videoExtractor.getSampleFlags();
                mediaMuxer.writeSampleData(outVideoTrackIndex, videoByteBuffer, videoBufferInfo);
                Log.e(TAG, "Write video data, outVideoTrackIndex = " + outVideoTrackIndex + ", videoLastTime = " + videoLastTime);

                videoExtractor.advance();
            }


            //copy audio data
            ByteBuffer audioByteBuffer = ByteBuffer.allocate(maxAudioSampleSize);
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            audioExtractor.selectTrack(audioTrackIndex);
            Log.e(TAG, "start copy audio data for file = " + filePath);
            int frameCount = 0;
            while (true) {
                Log.e(TAG, "start copy audio data for file = " + filePath + ", frameCount = " + ++frameCount);
                readSampleSize = audioExtractor.readSampleData(audioByteBuffer, 0);
                Log.e(TAG, "read frameCount = " + frameCount + ", readSampleSize = " + readSampleSize);
                if (readSampleSize < 0) {
                    Log.e(TAG, "No audio data for file = " + filePath);
                    audioExtractor.unselectTrack(audioTrackIndex);
                    break;
                }

                audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime() + audioLastTime;
                audioBufferInfo.size = readSampleSize;
                audioBufferInfo.offset = 0;
                audioBufferInfo.flags = audioExtractor.getSampleFlags();
                Log.e(TAG, "begin wrte audio data for file = " + filePath);
                mediaMuxer.writeSampleData(outAudioTrackIndex, audioByteBuffer, audioBufferInfo);
                Log.e(TAG, "Write audio data, outAudioTrackIndex = " + outAudioTrackIndex + ", audioLastTime = " + audioLastTime);

                audioExtractor.advance();
            }
            videoLastTime = videoBufferInfo.presentationTimeUs;
            audioLastTime = audioBufferInfo.presentationTimeUs;
            if (videoLastTime < audioLastTime) {
                videoLastTime = audioLastTime;
            } else {
                audioLastTime = videoLastTime;
            }
            videoExtractor.release();
            videoExtractor = null;
            audioExtractor.release();
            audioExtractor = null;
        }


        if (null != mediaMuxer) {
            mediaMuxer.release();
            mediaMuxer = null;
        }
    }

    private static int getTrackIndex(MediaExtractor extractor, String type) {
        int trackCount = extractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(type)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 提取视频流
     * @param videoPath
     * @param outPath
     */
    public static void extractVideo(String videoPath, String outPath) throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(videoPath);

        int videoTrackIndex = -1;
        int width = -1;
        int height = -1;
        int frameMaxSize = -1;
        MediaFormat videoTrackFormat = null;
        int trackCount = mediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                videoTrackIndex = i;
                videoTrackFormat = format;
                width = format.getInteger(MediaFormat.KEY_WIDTH);
                height = format.getInteger(MediaFormat.KEY_HEIGHT);
                frameMaxSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                break;
            }
        }

        if (null == videoTrackFormat) {
            return;
        }

        MediaMuxer mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int outVideoTrackIndex = mediaMuxer.addTrack(videoTrackFormat);
        mediaMuxer.start();


        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer byteBuffer = ByteBuffer.allocate(frameMaxSize);
        int read = -1;
        mediaExtractor.selectTrack(videoTrackIndex);
        while (true) {
            read = mediaExtractor.readSampleData(byteBuffer, 0);
            if (read < 0) {
                mediaExtractor.unselectTrack(videoTrackIndex);
                break;
            }
            long sampleTime = mediaExtractor.getSampleTime();
            bufferInfo.presentationTimeUs = sampleTime;
            bufferInfo.size = read;
            bufferInfo.offset = 0;
            bufferInfo.flags = mediaExtractor.getSampleFlags();
            mediaMuxer.writeSampleData(outVideoTrackIndex, byteBuffer, bufferInfo);
            mediaExtractor.advance();
        }

        mediaMuxer.release();
        mediaMuxer = null;

        mediaExtractor.release();
        mediaExtractor = null;
    }

    /**
     * 提取音频流
     * @param videoPath
     * @param outPath
     */
    public static void extractMusic(String videoPath, String outPath) throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(videoPath);

        int trackCount = mediaExtractor.getTrackCount();
        MediaFormat audioFormat = null;
        int audioTrackIndex = -1;
        int maxFrameSize = 0;
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("audio/")) {
                audioFormat = format;
                audioTrackIndex = i;
                maxFrameSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                break;
            }
        }

        if (null == audioFormat) {
            return;
        }

        MediaMuxer mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int outAudioTrack = mediaMuxer.addTrack(audioFormat);
        mediaExtractor.selectTrack(audioTrackIndex);
        mediaMuxer.start();


        ByteBuffer byteBuffer = ByteBuffer.allocate(maxFrameSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int readSampleSize = -1;
        while (true) {
            readSampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
            if (readSampleSize < 0) {
                mediaExtractor.unselectTrack(audioTrackIndex);
                break;
            }

            bufferInfo.flags = mediaExtractor.getSampleFlags();
            bufferInfo.offset = 0;
            bufferInfo.size = readSampleSize;
            bufferInfo.presentationTimeUs = mediaExtractor.getSampleTime();
            mediaMuxer.writeSampleData(outAudioTrack, byteBuffer, bufferInfo);

            mediaExtractor.advance();
        }

        mediaMuxer.release();
        mediaMuxer = null;
        mediaExtractor.release();
        mediaExtractor = null;
    }

    /**
     * 音视频合并
     * @param videoTrackFile
     * @param audioTrackFile
     * @param outPath
     */
    public static void union2Video(String videoTrackFile, String audioTrackFile, String outPath) throws IOException {
        MediaExtractor videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(videoTrackFile);

        MediaExtractor audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(audioTrackFile);

        //find video track
        int maxVideoSampleSize = -1;
        int videoTrackIndex = -1;
        MediaFormat videoFormat = null;
        int trackCount = videoExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat mediaFormat = videoExtractor.getTrackFormat(i);
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                videoTrackIndex = i;
                videoFormat = mediaFormat;
                maxVideoSampleSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                break;
            }
        }

        if (null == videoFormat) {
            return;
        }

        trackCount = audioExtractor.getTrackCount();
        int audioTrackIndex = -1;
        int maxAudioSampleSize = -1;
        MediaFormat audioFormat = null;
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = audioExtractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("audio/")) {
                audioTrackIndex = i;
                audioFormat = format;
                maxAudioSampleSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                break;
            }
        }
        if (null == audioFormat) {
            return;
        }

        MediaMuxer mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int outVideoIndex = mediaMuxer.addTrack(videoFormat);
        int outAudioIndex = mediaMuxer.addTrack(audioFormat);
        mediaMuxer.start();
        int readSampleSize = -1;

        //write video data
        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer videoByteBuffer = ByteBuffer.allocate(maxVideoSampleSize);
        videoExtractor.selectTrack(videoTrackIndex);

        while (true) {
            readSampleSize = videoExtractor.readSampleData(videoByteBuffer, 0);
            if (readSampleSize < 0) {
                videoExtractor.unselectTrack(videoTrackIndex);
                readSampleSize = -1;
                break;
            }

            videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
            videoBufferInfo.size = readSampleSize;
            videoBufferInfo.offset = 0;
            videoBufferInfo.flags = videoExtractor.getSampleFlags();
            mediaMuxer.writeSampleData(outVideoIndex, videoByteBuffer, videoBufferInfo);

            videoExtractor.advance();
        }

        //write audio data
        ByteBuffer audioByteBuffer = ByteBuffer.allocate(maxAudioSampleSize);
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
        audioExtractor.selectTrack(audioTrackIndex);

        while (true) {
            readSampleSize = audioExtractor.readSampleData(audioByteBuffer, 0);
            if (readSampleSize < 0) {
                audioExtractor.unselectTrack(audioTrackIndex);
                break;
            }

            audioBufferInfo.flags = audioExtractor.getSampleFlags();
            audioBufferInfo.offset = 0;
            audioBufferInfo.size = readSampleSize;
            audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
            mediaMuxer.writeSampleData(outAudioIndex, audioByteBuffer, audioBufferInfo);

            audioExtractor.advance();
        }

        mediaMuxer.release();
        mediaMuxer = null;
        videoExtractor.release();
        videoExtractor = null;
        audioExtractor.release();
        audioExtractor = null;
    }


    public static void playXSpeed(String videoPath, int speed, String outPath) throws IOException {
        MediaExtractor audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(videoPath);

        MediaExtractor videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(videoPath);
        int speedTmp = 0;


        int audioTrackIndex = getTrackIndex(audioExtractor, "audio/");
        MediaFormat audioMediaFormat = audioExtractor.getTrackFormat(audioTrackIndex);
        int maxAudioSampleSize = audioMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        int audioSampleRate = audioMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        int videoTrackIndex = getTrackIndex(videoExtractor, "video/");
        MediaFormat videoMediaFormat = videoExtractor.getTrackFormat(videoTrackIndex);
        int maxVideoSampleSize = videoMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        int videoFrameRate = videoMediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);

        MediaMuxer mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int outVideoTrackIndex = mediaMuxer.addTrack(videoMediaFormat);
        int outAudioTrackIndex = mediaMuxer.addTrack(audioMediaFormat);
        mediaMuxer.start();
        Log.e(TAG, "audioSampleRate = " + audioSampleRate + ", videoFrameRate = " + videoFrameRate);


        long timeUnit = 1_000_000 / videoFrameRate;
        long audioTimeUnit = 1_000_000 / audioSampleRate;
        int frameIndex = 0;

        ByteBuffer videoByteBuffer = ByteBuffer.allocate(maxVideoSampleSize);
        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        int sampleSize = -1;
        videoExtractor.selectTrack(videoTrackIndex);
        while (true) {
            sampleSize = videoExtractor.readSampleData(videoByteBuffer, 0);
            if (sampleSize < 0) {
                Log.e(TAG, "video frameCount = " + frameIndex);
                videoExtractor.unselectTrack(videoTrackIndex);
                break;
            }

            if (frameIndex++ / 5 == 0) {
                videoBufferInfo.flags = MediaExtractor.SAMPLE_FLAG_SYNC;
            } else {
                videoBufferInfo.flags = 0;
            }

            videoBufferInfo.offset = 0;
            videoBufferInfo.size = sampleSize;
            videoBufferInfo.presentationTimeUs = frameIndex * timeUnit;
            mediaMuxer.writeSampleData(outVideoTrackIndex, videoByteBuffer, videoBufferInfo);
            while (speedTmp++ <= speed) {
                videoExtractor.advance();
            }
            speedTmp = 0;
        }
        speedTmp = 0;
        frameIndex = 0;

        ByteBuffer audioByteBuffer = ByteBuffer.allocate(maxAudioSampleSize);
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
        audioExtractor.selectTrack(audioTrackIndex);

        while (true) {
            sampleSize = audioExtractor.readSampleData(audioByteBuffer, 0);
            if (sampleSize < 0) {
                Log.e(TAG, "audio frameCount = " + frameIndex);
                audioExtractor.unselectTrack(audioTrackIndex);
                break;
            }

            audioBufferInfo.flags = audioExtractor.getSampleFlags();
            audioBufferInfo.offset = 0;
            audioBufferInfo.size = sampleSize;
            audioBufferInfo.presentationTimeUs = frameIndex++ * audioTimeUnit;
            mediaMuxer.writeSampleData(outAudioTrackIndex, audioByteBuffer, audioBufferInfo);
            while (speedTmp++ <= speed) {
                audioExtractor.advance();
            }
            speedTmp = 0;
        }
        audioExtractor.release();
        videoExtractor.release();
        mediaMuxer.release();
    }

    public static void addSubtitles(String videoPath, String outPath, String subtitleString) throws IOException {
        MediaMuxer mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        ByteBuffer subtitleTrackBuffer = ByteBuffer.allocate(4196);
        MediaCodec.BufferInfo subtitleBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat subtitleFormat = new MediaFormat();
        subtitleFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_TEXT_VTT);
        int subtitleTrackIndex = mediaMuxer.addTrack(subtitleFormat);
    }


    public static void videoClipHeader(String videoPath, String outPath, long duration) throws IOException {
        MediaExtractor videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(videoPath);
        MediaExtractor audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(videoPath);

        int videoMaxSampleSize = -1;
        int videoTrackIndex = -1;
        MediaFormat videoFormat = null;
        int trackCount = videoExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                videoTrackIndex = i;
                videoFormat = format;
                videoMaxSampleSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                break;
            }
        }

        int audioMaxSampleSize = -1;
        int audioTrackIndex = -1;
        MediaFormat audioFormat = null;
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("audio/")) {
                audioTrackIndex = i;
                audioFormat = format;
                audioMaxSampleSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                break;
            }
        }

        if (null == videoFormat || null == audioFormat) {
            return;
        }
        MediaMuxer mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int outVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
        int outAudioTrackIndex = mediaMuxer.addTrack(audioFormat);
        mediaMuxer.start();

        ByteBuffer videoByteBuffer = ByteBuffer.allocate(videoMaxSampleSize);
        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        int readSampleSize = -1;

        videoExtractor.selectTrack(videoTrackIndex);
        videoExtractor.seekTo(duration, MediaExtractor.SEEK_TO_NEXT_SYNC);
        while (true) {
            readSampleSize = videoExtractor.readSampleData(videoByteBuffer, 0);
            if (readSampleSize < 0) {
                videoExtractor.unselectTrack(videoTrackIndex);
                break;
            }
            long pts = videoExtractor.getSampleTime();
            videoBufferInfo.presentationTimeUs = pts - duration;
//            Log.e(TAG, "clipVideo, startTime = " + startTime + ", pts = " + pts + ", ptsReal = " + videoBufferInfo.presentationTimeUs);
            videoBufferInfo.size = readSampleSize;
            videoBufferInfo.offset = 0;
            videoBufferInfo.flags = videoExtractor.getSampleFlags();

            mediaMuxer.writeSampleData(outVideoTrackIndex, videoByteBuffer, videoBufferInfo);
            videoExtractor.advance();
        }

        ByteBuffer audioByteBuffer = ByteBuffer.allocate(audioMaxSampleSize);
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

        audioExtractor.selectTrack(audioTrackIndex);
        audioExtractor.seekTo(duration, MediaExtractor.SEEK_TO_NEXT_SYNC);
        while (true) {
            readSampleSize = audioExtractor.readSampleData(audioByteBuffer, 0);
            if (readSampleSize < 0) {
                audioExtractor.unselectTrack(audioTrackIndex);
                break;
            }

            long pts = audioExtractor.getSampleTime();

            audioBufferInfo.presentationTimeUs = pts - duration;
            Log.e(TAG, "clipVideo, startTime = " + duration + ", pts = " + pts + ", ptsReal = " + audioBufferInfo.presentationTimeUs);
            audioBufferInfo.size = readSampleSize;
            audioBufferInfo.offset = 0;
            audioBufferInfo.flags = audioExtractor.getSampleFlags();

            mediaMuxer.writeSampleData(outAudioTrackIndex, audioByteBuffer, audioBufferInfo);
            audioExtractor.advance();
        }

        audioExtractor.release();
        videoExtractor.release();
        mediaMuxer.release();
    }

    /**
     * 末尾 duration的时间去掉
     * @param videoPath
     * @param outPath
     * @param duration
     * @throws IOException
     */
    public static void videoClipTail(String videoPath, String outPath, long duration) throws IOException {
        MediaExtractor videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(videoPath);
        MediaExtractor audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(videoPath);

        int videoMaxSampleSize = -1;
        int videoTrackIndex = -1;
        long videoDuration = -1;
        MediaFormat videoFormat = null;
        int trackCount = videoExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                videoTrackIndex = i;
                videoFormat = format;
                videoMaxSampleSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                videoDuration = format.getLong(MediaFormat.KEY_DURATION);
                break;
            }
        }

        int audioMaxSampleSize = -1;
        int audioTrackIndex = -1;
        long audioDuration = -1;
        MediaFormat audioFormat = null;
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("audio/")) {
                audioTrackIndex = i;
                audioFormat = format;
                audioMaxSampleSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                audioDuration = format.getLong(MediaFormat.KEY_DURATION);
                break;
            }
        }

        if (null == videoFormat || null == audioFormat) {
            return;
        }
        MediaMuxer mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int outVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
        int outAudioTrackIndex = mediaMuxer.addTrack(audioFormat);
        mediaMuxer.start();

        ByteBuffer videoByteBuffer = ByteBuffer.allocate(videoMaxSampleSize);
        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        int readSampleSize = -1;

        videoExtractor.selectTrack(videoTrackIndex);
        while (true) {
            readSampleSize = videoExtractor.readSampleData(videoByteBuffer, 0);
            long pts = videoExtractor.getSampleTime();
            if (readSampleSize < 0 || pts + duration > videoDuration) {
                videoExtractor.unselectTrack(videoTrackIndex);
                break;
            }

            videoBufferInfo.presentationTimeUs = pts;
            videoBufferInfo.size = readSampleSize;
            videoBufferInfo.offset = 0;
            videoBufferInfo.flags = videoExtractor.getSampleFlags();

            mediaMuxer.writeSampleData(outVideoTrackIndex, videoByteBuffer, videoBufferInfo);
            videoExtractor.advance();
        }

        ByteBuffer audioByteBuffer = ByteBuffer.allocate(audioMaxSampleSize);
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

        audioExtractor.selectTrack(audioTrackIndex);
        while (true) {
            readSampleSize = audioExtractor.readSampleData(audioByteBuffer, 0);
            long pts = audioExtractor.getSampleTime();
            if (readSampleSize < 0 || pts + duration > audioDuration) {
                audioExtractor.unselectTrack(audioTrackIndex);
                break;
            }


            audioBufferInfo.presentationTimeUs = pts;
            audioBufferInfo.size = readSampleSize;
            audioBufferInfo.offset = 0;
            audioBufferInfo.flags = audioExtractor.getSampleFlags();

            mediaMuxer.writeSampleData(outAudioTrackIndex, audioByteBuffer, audioBufferInfo);
            audioExtractor.advance();
        }

        audioExtractor.release();
        videoExtractor.release();
        mediaMuxer.release();
    }


    /**
     * 提取中间
     * @param videoPath
     * @param outPath
     * @param startTime
     * @param endTime
     * @throws IOException
     */
    public static void videoClip(String videoPath, String outPath, long startTime, long endTime) throws IOException {
        MediaExtractor videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(videoPath);
        MediaExtractor audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(videoPath);

        int videoMaxSampleSize = -1;
        int videoTrackIndex = -1;
        MediaFormat videoFormat = null;
        int trackCount = videoExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                videoTrackIndex = i;
                videoFormat = format;
                videoMaxSampleSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                break;
            }
        }

        int audioMaxSampleSize = -1;
        int audioTrackIndex = -1;
        MediaFormat audioFormat = null;
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("audio/")) {
                audioTrackIndex = i;
                audioFormat = format;
                audioMaxSampleSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                break;
            }
        }

        if (null == videoFormat || null == audioFormat) {
            return;
        }
        MediaMuxer mediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int outVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
        int outAudioTrackIndex = mediaMuxer.addTrack(audioFormat);
        mediaMuxer.start();

        ByteBuffer videoByteBuffer = ByteBuffer.allocate(videoMaxSampleSize);
        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        int readSampleSize = -1;

        videoExtractor.selectTrack(videoTrackIndex);
        videoExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_NEXT_SYNC);
        while (true) {
            readSampleSize = videoExtractor.readSampleData(videoByteBuffer, 0);
            long pts = videoExtractor.getSampleTime();
            if (readSampleSize < 0 || pts > endTime) {
                videoExtractor.unselectTrack(videoTrackIndex);
                break;
            }

            videoBufferInfo.presentationTimeUs = pts - startTime;
//            Log.e(TAG, "clipVideo, startTime = " + startTime + ", pts = " + pts + ", ptsReal = " + videoBufferInfo.presentationTimeUs);
            videoBufferInfo.size = readSampleSize;
            videoBufferInfo.offset = 0;
            videoBufferInfo.flags = videoExtractor.getSampleFlags();

            mediaMuxer.writeSampleData(outVideoTrackIndex, videoByteBuffer, videoBufferInfo);
            videoExtractor.advance();
        }

        ByteBuffer audioByteBuffer = ByteBuffer.allocate(audioMaxSampleSize);
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

        audioExtractor.selectTrack(audioTrackIndex);
        audioExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_NEXT_SYNC);
        while (true) {
            readSampleSize = audioExtractor.readSampleData(audioByteBuffer, 0);
            long pts = audioExtractor.getSampleTime();
            if (readSampleSize < 0 || pts > endTime) {
                audioExtractor.unselectTrack(audioTrackIndex);
                break;
            }

            audioBufferInfo.presentationTimeUs = pts - startTime;
            Log.e(TAG, "clipVideo, startTime = " + startTime + ", pts = " + pts + ", ptsReal = " + audioBufferInfo.presentationTimeUs);
            audioBufferInfo.size = readSampleSize;
            audioBufferInfo.offset = 0;
            audioBufferInfo.flags = audioExtractor.getSampleFlags();

            mediaMuxer.writeSampleData(outAudioTrackIndex, audioByteBuffer, audioBufferInfo);
            audioExtractor.advance();
        }

        audioExtractor.release();
        videoExtractor.release();
        mediaMuxer.release();
    }


    /**
     * 提取中间
     * @param videoPath
     * @param outPath
     * @throws IOException
     */
    public static void videoCrop(String videoPath, String outPath) throws IOException {
        VideoCrop videoCrop = new VideoCrop();
        videoCrop.startCrop(videoPath, outPath);
    }

}
