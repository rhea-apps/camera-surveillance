package camera_surveillance;

import com.atul.JavaOpenCV.Imshow;
import cv_bridge.CvImage;
import org.apache.commons.lang3.tuple.MutablePair;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.reactive_ros.ReactiveTopic;
import org.reactive_ros.streams.Stream;
import org.reactive_ros.streams.messages.Topic;

import org.reactive_ros.util.functions.Func2;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import sensor_msgs.Image;

import java.util.concurrent.TimeUnit;

public class Surveillance extends AbstractNodeMain {
    private static final Topic CAMERA = new Topic("/camera/rgb/image_color", Image._TYPE);

    private final Imshow window = new Imshow("Live Feed");

    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    @Override
    public GraphName getDefaultNodeName() { return GraphName.of("surveillance"); }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        ReactiveTopic reactiveTopic = new ReactiveTopic(connectedNode);
        Stream.setReactiveTopic(reactiveTopic);

        // Camera Image topic
        Stream<Mat> image = Stream.<Image>from(CAMERA).flatMap(im -> {
            try {
                return Stream.just(CvImage.toCvCopy(im).image);
            } catch (Exception e) {
                return Stream.error(e);
            }
        });

        Stream<Mat> initial = image.take(1).cache().repeat();
        Stream.zip(image, initial, (Func2<Mat, Mat, MutablePair<Mat, Mat>>) MutablePair::new)
                .sample(100, TimeUnit.MILLISECONDS) // backpressure
                .timeout(1, TimeUnit.MINUTES) // stop monitoring when video stream stops (does not produce an image for 1 min)
                .filter(Surveillance::containsHuman) // Only stream images that contain some new object
                .map(MutablePair::getLeft)
                .subscribe(
                        window::showImage,
                        e -> window.Window.setVisible(false),
                        () -> window.Window.setVisible(false)
                );
    }

    private static boolean containsHuman(MutablePair<Mat,Mat> pair) {
        Mat m1 = pair.getLeft(), m2 = pair.getRight(), m = new Mat();
        Core.absdiff(m1, m2, m);
        Imgproc.threshold(m, m, 80, 255, Imgproc.THRESH_BINARY);
        Imgproc.erode(m, m, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
        for (int i = 0; i < m.rows(); i++)
            for (int j = 0; j < m.cols(); j++) {
                double[] pixel = m.get(i, j);
                double sum = pixel[0]  + pixel[1] + pixel[2];
                if (sum > 50) return true;
            }
        return false;
    }   
}
