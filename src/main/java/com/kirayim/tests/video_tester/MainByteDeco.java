package com.kirayim.tests.video_tester;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_videoio;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;


public class MainByteDeco extends JDialog {
	private static final long serialVersionUID = -1894023028188131666L;

	VideoCapture capture = null;
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

	public MainByteDeco(String url) {
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

	public void videoCapture() {

		try (
				OpenCVFrameConverter.ToOrgOpenCvCoreMat converterToOpenCv = new OpenCVFrameConverter.ToOrgOpenCvCoreMat();
				OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
				) {

			Mat incomingFame = new Mat();
			Mat resizedFrame = new Mat();

			while (keepWorking) {
				if (capture == null || capture.isOpened() == false) {
					System.out.println("Open capture");
					capture = new VideoCapture(url, opencv_videoio.CAP_FFMPEG);

					if (capture.isOpened() == false) {
						System.out.println("Capture not opened");
						Thread.sleep(1000);
						continue;
					}
				}

				if (capture.read(incomingFame) && incomingFame.empty() == false) {

//				System.out.printf("Incoming frame %dx%d\n", incomingFame.width(), incomingFame.height());

					Dimension contentSize = canvas.getSize();
					opencv_imgproc.resize(incomingFame, resizedFrame,
							new Size(contentSize.width, contentSize.height),
							0, 0, Imgproc.INTER_LINEAR);

					opencv_imgproc.cvtColor(resizedFrame, resizedFrame, Imgproc.COLOR_RGB2BGR, 0);



					BufferedImage out;
					byte[] data = new byte[contentSize.width * contentSize.height * (int)resizedFrame.elemSize()];
					int type;
					org.opencv.core.Mat coreMat =  converterToOpenCv.convert(converter.convert(resizedFrame));

					coreMat.get(0, 0, data);

					if (coreMat.channels() == 1) {
						type = BufferedImage.TYPE_BYTE_GRAY;
					} else {
						type = BufferedImage.TYPE_3BYTE_BGR;
					}

					out = new BufferedImage(contentSize.width, contentSize.height, type);
					out.getRaster().setDataElements(0, 0, contentSize.width, contentSize.height, data);
					currentFrame = out;
					canvas.repaint();

				} else {
					System.out.println("No frame");
				}

			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// =====================================================================================

	public static void main(String[] args) throws Exception {

		SwingUtilities.invokeAndWait(() -> {
			new MainByteDeco(args[0]);
		});
	}

}
