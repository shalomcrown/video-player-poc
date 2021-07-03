package com.kirayim.tests.video_tester;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVInputFormat;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.PointerPointer;

public class MainByteDecoFFMPEG extends JDialog {
		private static final long serialVersionUID = -1894023028188131666L;

		Thread videoThread = null;
		String url;
		boolean keepWorking = true;
		JLabel canvas;
		BufferedImage currentFrame = null;

		static {
			System.setProperty("sun.awt.noerasebackground", "true");
			nu.pattern.OpenCV.loadShared();
			System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
		}

		// =====================================================================================

		public MainByteDecoFFMPEG(String url) {
			super();

			this.url = url;
			setSize(800, 600);

			canvas = new JLabel("") {
				private static final long serialVersionUID = 5879063277258760446L;

				@Override
				public void paint(Graphics g) {
					if (currentFrame != null) {
						g.drawImage(currentFrame, 0, 0, null);
					}
				}
			};

			getContentPane().add(canvas);

			videoThread = new Thread(this::videoCapture);
			videoThread.setDaemon(true);
			videoThread.start();

			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					keepWorking = false;
					videoThread.interrupt();
					System.exit(0);
				}
			});

			setVisible(true);
		}

		// =====================================================================================

		String getLibavError(int errnum) {
			byte[] errbuf = new byte[AV_ERROR_MAX_STRING_SIZE];
			av_strerror(errnum, errbuf, AV_ERROR_MAX_STRING_SIZE);

			return new String(errbuf);
		}

		// =====================================================================================

		public void videoCapture() {

			try (AVPacket pkt = new AVPacket()) {
			    AVFormatContext fmt_ctx = new AVFormatContext(null);
			    AVInputFormat inputFormat = null;
			    AVDictionary dict = new AVDictionary();

		        int ret = -1, i = 0, v_stream_idx = -1;

		        av_dict_set(dict, "frame_drop_threshold", "1", 0);
		        av_dict_set(dict, "tune", "zerolatency", 0);
		        av_dict_set(dict, "frames", "1", 0);

		        if (url.startsWith("/dev/video")) {
			        Dimension contentSize = canvas.getSize();
			        String resolutionString = String.format("%dx%d", contentSize.width, contentSize.height);
			        av_dict_set(dict, new BytePointer("framerate"), new BytePointer("15"), 0);
					av_dict_set(dict, "input_format", "mjpeg", 0);
					av_dict_set(dict, "video_size", resolutionString, 0);

					inputFormat = av_find_input_format("v4l2");

				    if (inputFormat == null) {
				    	System.out.printf("Cannot find input format - video4linux2");
			            throw new IllegalStateException();
				    }
		        }

		        ret = avformat_open_input(fmt_ctx, url, inputFormat, dict);

		        if (ret < 0) {
		            System.out.printf("Open video file %s failed: %s\n", url, getLibavError(ret));
		            throw new IllegalStateException();
		        }

		        if (avformat_find_stream_info(fmt_ctx, (PointerPointer<?>)null) < 0) {
		            System.exit(-1);
		        }

		        av_dump_format(fmt_ctx, 0, url, 0);

		        for (i = 0; i < fmt_ctx.nb_streams(); i++) {
		            if (fmt_ctx.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
		                v_stream_idx = i;
		                break;
		            }
		        }
		        if (v_stream_idx == -1) {
		            System.out.println("Cannot find video stream");
		            throw new IllegalStateException();
		        } else {
		            System.out.printf("Video stream %d with resolution %dx%d\n", v_stream_idx,
		                    fmt_ctx.streams(i).codecpar().width(),
		                    fmt_ctx.streams(i).codecpar().height());
		        }

		        AVCodecContext codec_ctx = avcodec_alloc_context3(null);
		        avcodec_parameters_to_context(codec_ctx, fmt_ctx.streams(v_stream_idx).codecpar());

		        AVCodec codec = avcodec_find_decoder(codec_ctx.codec_id());
		        if (codec == null) {
		            System.out.println("Unsupported codec for video file");
		            throw new IllegalStateException();
		        }

		        ret = avcodec_open2(codec_ctx, codec, (PointerPointer<?>)null);
		        if (ret < 0) {
		            System.out.println("Can not open codec");
		            throw new IllegalStateException();
		        }

		        AVFrame frm = av_frame_alloc();

		        while (av_read_frame(fmt_ctx, pkt) >= 0) {
		            if (pkt.stream_index() == v_stream_idx) {

		                if (avcodec_send_packet(codec_ctx, pkt) >= 0) {
		                	while (avcodec_receive_frame(codec_ctx, frm) >= 0) {

		        		        Dimension contentSize = canvas.getSize();

		        		        // Allocate an AVFrame structure
		        		        AVFrame pFrameRGB = av_frame_alloc();

		        		        if (pFrameRGB == null) {
		        		            System.out.println("Can't open frame");
		        		            System.exit(-1);
		        		        }

		        		        pFrameRGB.format(AV_PIX_FMT_RGB24);
		        		        pFrameRGB.width(contentSize.width);
		        		        pFrameRGB.height(contentSize.height);

		        		        if (av_image_alloc(pFrameRGB.data(), pFrameRGB.linesize(),
		        		        		contentSize.width, contentSize.height, AV_PIX_FMT_RGB24, 8) < 0) {
		        		            System.out.println("Can't allocate output data");
		        		            throw new IllegalStateException();
		        		        }

		        		        System.out.printf("incoming: %dx%d outgoing %dx%d, lineSize[0] %d\n",
		        		        		codec_ctx.width(), codec_ctx.height(),
		        		        		contentSize.width, contentSize.height,
		        		        		pFrameRGB.linesize(0)
		        		        		);

		        		        SwsContext sws_ctx = sws_getContext(
		        		                codec_ctx.width(),
		        		                codec_ctx.height(),
		        		                codec_ctx.pix_fmt(),
		        		                contentSize.width,
		        		                contentSize.height,
		        		                AV_PIX_FMT_RGB24,
		        		                SWS_BILINEAR,
		        		                null,
		        		                null,
		        		                (DoublePointer)null
		        		            );

		        		        if (sws_ctx == null) {
		        		            System.out.println("Can not use sws");
		        		            throw new IllegalStateException();
		        		        }

				                sws_scale(
				                        sws_ctx,
				                        frm.data(),
				                        frm.linesize(),
				                        0,
				                        codec_ctx.height(),
				                        pFrameRGB.data(),
				                        pFrameRGB.linesize()
				                    );

								byte[] data = new byte[contentSize.height * pFrameRGB.linesize(0)];

								BytePointer pointer = pFrameRGB.data(0);
								pointer.get(data);

								BufferedImage out = new BufferedImage(contentSize.width, contentSize.height, BufferedImage.TYPE_3BYTE_BGR);
								out.getRaster().setDataElements(0, 0, contentSize.width, contentSize.height, data);

								av_free(pFrameRGB);
								currentFrame = out;
								canvas.repaint();
		                	}
		                }
		            }

		            av_packet_unref(pkt);
		        }

		        av_frame_free(frm);

		        avcodec_close(codec_ctx);
		        avcodec_free_context(codec_ctx);

		        avformat_close_input(fmt_ctx);

			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}

		// =====================================================================================

		public static void main(String[] args) throws Exception {

			SwingUtilities.invokeAndWait(() -> {
				new MainByteDecoFFMPEG(args[0]);
			});
		}

}
