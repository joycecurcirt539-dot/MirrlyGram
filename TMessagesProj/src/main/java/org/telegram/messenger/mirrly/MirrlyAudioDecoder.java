package org.telegram.messenger.mirrly;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Decodes Telegram voice notes (.ogg Opus) and round videos (.mp4 AAC) into
 * normalized 16 kHz mono signed 16-bit PCM or WAV files for on-device and cloud STT.
 */
public final class MirrlyAudioDecoder {

    public static final int TARGET_SAMPLE_RATE = 16_000;
    private static final int MAX_PCM_BYTES = 16_000 * 2 * 360; // cap at 6 minutes mono 16kHz (~11.5 MB)

    private MirrlyAudioDecoder() {
    }

    public static boolean decodeToMonoPcm16(@NonNull File sourceFile, @NonNull File dstPcmFile, int sampleRate) {
        if (!sourceFile.exists() || sourceFile.length() == 0) {
            return false;
        }
        String srcPath = sourceFile.getAbsolutePath();
        String lowerName = sourceFile.getName().toLowerCase(Locale.ROOT);
        boolean isOpus = false;
        try {
            isOpus = lowerName.endsWith(".ogg") || lowerName.endsWith(".opus") || MediaController.isOpusFile(srcPath) == 1;
        } catch (Throwable ignore) {
        }

        if (isOpus) {
            try {
                if (MediaController.decodeOpusToPcm16(srcPath, dstPcmFile.getAbsolutePath(), sampleRate)
                        && dstPcmFile.exists() && dstPcmFile.length() > 0) {
                    return true;
                }
            } catch (Throwable t) {
                FileLog.e(t, false);
            }
        }

        return decodeWithMediaCodec(srcPath, dstPcmFile, sampleRate);
    }

    public static boolean wrapPcmAsWav(@NonNull File srcPcmFile, @NonNull File dstWavFile, int sampleRate) {
        if (!srcPcmFile.exists() || srcPcmFile.length() == 0) {
            return false;
        }
        long dataLen = Math.min(srcPcmFile.length(), 0x7FFF_0000L);
        byte[] header = new byte[44];
        long byteRate = (long) sampleRate * 2L;
        long totalDataLen = dataLen + 36L;

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        writeIntLE(header, 4, (int) totalDataLen);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        writeIntLE(header, 16, 16);
        writeShortLE(header, 20, (short) 1); // PCM
        writeShortLE(header, 22, (short) 1); // Mono
        writeIntLE(header, 24, sampleRate);
        writeIntLE(header, 28, (int) byteRate);
        writeShortLE(header, 32, (short) 2);
        writeShortLE(header, 34, (short) 16);
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        writeIntLE(header, 40, (int) dataLen);

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(srcPcmFile));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dstWavFile))) {
            out.write(header);
            byte[] buf = new byte[8192];
            long remaining = dataLen;
            int read;
            while (remaining > 0 && (read = in.read(buf, 0, (int) Math.min(buf.length, remaining))) > 0) {
                out.write(buf, 0, read);
                remaining -= read;
            }
            out.flush();
            return dstWavFile.exists() && dstWavFile.length() > 44;
        } catch (IOException e) {
            FileLog.e(e, false);
            return false;
        }
    }

    private static boolean decodeWithMediaCodec(@NonNull String srcPath, @NonNull File dstPcmFile, int targetSampleRate) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        BufferedOutputStream out = null;
        try {
            extractor.setDataSource(srcPath);
            int audioTrackIndex = -1;
            MediaFormat audioFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (!TextUtils.isEmpty(mime) && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    audioFormat = fmt;
                    break;
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                return false;
            }
            extractor.selectTrack(audioTrackIndex);
            String mime = audioFormat.getString(MediaFormat.KEY_MIME);
            if (TextUtils.isEmpty(mime)) {
                return false;
            }

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(audioFormat, null, null, 0);
            codec.start();

            out = new BufferedOutputStream(new FileOutputStream(dstPcmFile), 16_384);

            int inSampleRate = audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44_100;
            int inChannels = audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? Math.max(1, audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) : 1;
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && audioFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                pcmEncoding = audioFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            int idleOutputLoopsAfterEos = 0;
            int totalBytesWritten = 0;

            double step = (double) Math.max(1, inSampleRate) / (double) Math.max(1, targetSampleRate);
            double accum = 0.0;
            long boxSum = 0L;
            int boxCount = 0;
            byte[] pair = new byte[2];

            while (!sawOutputEOS && totalBytesWritten < MAX_PCM_BYTES) {
                if (!sawInputEOS) {
                    int inIndex = codec.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                        if (inBuf != null) {
                            int sampleSize = extractor.readSampleData(inBuf, 0);
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                            } else {
                                long pts = extractor.getSampleTime();
                                codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex >= 0) {
                    idleOutputLoopsAfterEos = 0;
                    ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                    if (outBuf != null && info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        outBuf.position(info.offset);
                        outBuf.limit(info.offset + info.size);
                        outBuf.order(ByteOrder.LITTLE_ENDIAN);

                        int bytesPerSample = pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : (pcmEncoding == AudioFormat.ENCODING_PCM_8BIT ? 1 : 2);
                        int frameSize = bytesPerSample * inChannels;
                        int frameCount = frameSize > 0 ? info.size / frameSize : 0;

                        for (int f = 0; f < frameCount; f++) {
                            long chSum = 0L;
                            for (int c = 0; c < inChannels; c++) {
                                short sample16;
                                if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                    float v = outBuf.getFloat();
                                    sample16 = (short) Math.max(-32768, Math.min(32767, Math.round(v * 32767.0f)));
                                } else if (pcmEncoding == AudioFormat.ENCODING_PCM_8BIT) {
                                    int u = outBuf.get() & 0xFF;
                                    sample16 = (short) ((u - 128) << 8);
                                } else {
                                    sample16 = outBuf.getShort();
                                }
                                chSum += sample16;
                            }
                            short mono = (short) (chSum / inChannels);
                            if (inSampleRate <= targetSampleRate) {
                                accum += 1.0;
                                double invStep = (double) targetSampleRate / (double) Math.max(1, inSampleRate);
                                while (accum >= 1.0 / invStep && totalBytesWritten < MAX_PCM_BYTES) {
                                    pair[0] = (byte) (mono & 0xFF);
                                    pair[1] = (byte) ((mono >> 8) & 0xFF);
                                    out.write(pair, 0, 2);
                                    totalBytesWritten += 2;
                                    accum -= 1.0 / invStep;
                                }
                            } else {
                                boxSum += mono;
                                boxCount++;
                                accum += 1.0;
                                if (accum >= step && totalBytesWritten < MAX_PCM_BYTES) {
                                    accum -= step;
                                    int avg = boxCount > 0 ? (int) (boxSum / boxCount) : mono;
                                    if (avg > 32767) avg = 32767;
                                    if (avg < -32768) avg = -32768;
                                    pair[0] = (byte) (avg & 0xFF);
                                    pair[1] = (byte) ((avg >> 8) & 0xFF);
                                    out.write(pair, 0, 2);
                                    totalBytesWritten += 2;
                                    boxSum = 0;
                                    boxCount = 0;
                                }
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    idleOutputLoopsAfterEos = 0;
                    MediaFormat newFormat = codec.getOutputFormat();
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        inSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        step = (double) Math.max(1, inSampleRate) / (double) Math.max(1, targetSampleRate);
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        inChannels = Math.max(1, newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                    accum = 0.0;
                    boxSum = 0L;
                    boxCount = 0;
                } else if (sawInputEOS && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (++idleOutputLoopsAfterEos >= 200) {
                        break;
                    }
                }
            }
            if (out != null) {
                out.flush();
            }
            return totalBytesWritten > 0;
        } catch (Throwable t) {
            FileLog.e(t, false);
            return false;
        } finally {
            try {
                if (out != null) out.close();
            } catch (IOException ignore) {}
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Throwable ignore) {}
            try {
                extractor.release();
            } catch (Throwable ignore) {}
        }
    }

    private static void writeIntLE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeShortLE(byte[] buf, int offset, short value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
