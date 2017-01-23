package org.usfirst.frc.team3042.steamworksvision;


import android.opengl.GLES20;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.usfirst.frc.team3042.steamworksvision.communication.TargetInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL10.GL_RGBA;
import static javax.microedition.khronos.opengles.GL10.GL_UNSIGNED_BYTE;
import static org.opencv.core.CvType.CV_8UC4;

public class OpenCVUtils {

    private static final double MIN_AREA = 300;
    private static final double MIN_STENCIL_SIMILARITY = 0.2;

    private static Mat stencil;
    private static Mat contoursFrame;

    private static Scalar lowerHSVBound;
    private static Scalar upperHSVBound;

    private static Mat filteredFrame;
    private static Mat erodedFrame;
    private static Mat dilatedFrame;
    private static List<MatOfPoint> contours;
    private static Point[] targetConvexHullLeft, targetConvexHullRight;
    private static MatOfPoint[] target;

    private static double x, y, distance;

    public static ArrayList<TargetInfo> processImage(int texIn, int texOut, int width, int height, int lowerH, int upperH,
                                              int lowerS, int upperS, int lowerV, int upperV, boolean outputHSVFrame) {
        ArrayList<TargetInfo> targets = new ArrayList<>();
        stencil = new Mat(8, 1, CvType.CV_32SC2);
        stencil.put(0, 0, new int[]{/*p1*/0, 0, /*p2*/ 0, 50, /*p3*/ 20, 50, /*p4*/ 20, 0});

        // Creating HSV bounds from individual components
        lowerHSVBound = new Scalar(lowerH, lowerS, lowerV, 0);
        upperHSVBound = new Scalar(upperH, upperS, upperV, 0);

        // Getting image from glFrame into OpenCV
        Mat input = new Mat(height, width, CV_8UC4);

        ByteBuffer buffer = ByteBuffer.allocate(input.rows() * input.cols() * input.channels());
        GLES20.glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        input.put(0, 0, buffer.array());

        // Processing image
        filteredFrame = filterImageHSV(input, lowerHSVBound, upperHSVBound);

        erodedFrame = erodeImage(filteredFrame);

        dilatedFrame = dilateImage(erodedFrame);

        contours = getContours(dilatedFrame);

        contoursFrame = dilatedFrame;
        Imgproc.cvtColor(contoursFrame, contoursFrame, Imgproc.COLOR_GRAY2BGR);

        Imgproc.drawContours(contoursFrame, contours, -1, new Scalar(255, 255, 0), 1);

        target = processContours(contours);

        targetConvexHullLeft = calculateConvexHull(target[0]);
        targetConvexHullRight = calculateConvexHull(target[1]);

        outputOverlayImage(contoursFrame, targetConvexHullLeft, targetConvexHullRight);

        // Outputting Mat to the screen
        ByteBuffer outBuffer;

        if(outputHSVFrame) {
            Imgproc.cvtColor(filteredFrame, filteredFrame, Imgproc.COLOR_BGR2BGRA);

            byte[] output = new byte[filteredFrame.rows() * filteredFrame.cols() * filteredFrame.channels()];
            filteredFrame.get(0, 0, output);
            outBuffer = ByteBuffer.wrap(output);
        } else {
            Imgproc.cvtColor(contoursFrame, contoursFrame, Imgproc.COLOR_BGR2BGRA);

            Core.bitwise_or(contoursFrame, input, input);
            byte[] output = new byte[input.rows() * input.cols() * input.channels()];
            input.get(0, 0, output);
            outBuffer = ByteBuffer.wrap(output);
        }

        GLES20.glActiveTexture(GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_2D, texOut);
        GLES20.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, outBuffer);

        targets.add(new TargetInfo(x, y ,distance));

        return targets;
    }

    //Outputting an image with overlaid contours and convex hull on target
    private static void outputOverlayImage(Mat contoursFrame, Point[] targetConvexHullLeft, Point[] targetConvexHullRight) {
        //Overlaying target onto original image
        Imgproc.line(contoursFrame, targetConvexHullLeft[0], targetConvexHullLeft[1], new Scalar(255, 255, 255), 2);
        Imgproc.line(contoursFrame, targetConvexHullLeft[1], targetConvexHullLeft[2], new Scalar(255, 255, 255), 2);
        Imgproc.line(contoursFrame, targetConvexHullLeft[2], targetConvexHullLeft[3], new Scalar(255, 255, 255), 2);
        Imgproc.line(contoursFrame, targetConvexHullLeft[3], targetConvexHullLeft[0], new Scalar(255, 255, 255), 2);

        Imgproc.line(contoursFrame, targetConvexHullRight[0], targetConvexHullRight[1], new Scalar(255, 255, 255), 2);
        Imgproc.line(contoursFrame, targetConvexHullRight[1], targetConvexHullRight[2], new Scalar(255, 255, 255), 2);
        Imgproc.line(contoursFrame, targetConvexHullRight[2], targetConvexHullRight[3], new Scalar(255, 255, 255), 2);
        Imgproc.line(contoursFrame, targetConvexHullRight[3], targetConvexHullRight[0], new Scalar(255, 255, 255), 2);

        // Drawing crosshair on center of image
        double crosshairSize = 20;
        double centerX = (targetConvexHullLeft[0].x + targetConvexHullLeft[1].x + targetConvexHullLeft[2].x + targetConvexHullLeft[3].x +
                targetConvexHullRight[0].x + targetConvexHullRight[1].x + targetConvexHullRight[2].x + targetConvexHullRight[3].x) / 8;
        double centerY = (targetConvexHullLeft[0].y + targetConvexHullLeft[1].y + targetConvexHullLeft[2].y + targetConvexHullLeft[3].y +
                targetConvexHullRight[0].y + targetConvexHullRight[1].y + targetConvexHullRight[2].y + targetConvexHullRight[3].y) / 8;

        Imgproc.line(contoursFrame,  new Point(centerX + crosshairSize / 2, centerY), new Point(centerX - crosshairSize / 2, centerY), new Scalar(255, 255, 255), 2);
        Imgproc.line(contoursFrame,  new Point(centerX, centerY + crosshairSize / 2), new Point(centerX, centerY - crosshairSize / 2), new Scalar(255, 255, 255), 2);

        x = centerX;
        y = centerY;
        distance = 5;
    }

    private static Mat filterImageHSV(Mat image, Scalar lowerHSVBound, Scalar upperHSVBound) {
        Mat hsvFrame = new Mat();
        Mat filteredFrame = new Mat();

        //Converting to HSV and filtering to a binary image
        Imgproc.cvtColor(image, hsvFrame, Imgproc.COLOR_BGR2HSV);
        Core.inRange(hsvFrame, lowerHSVBound, upperHSVBound, filteredFrame);
        filteredFrame.convertTo(filteredFrame, CvType.CV_8UC1);

        hsvFrame.release();

        return filteredFrame;
    }

    private static Mat erodeImage(Mat image) {
        Mat output = new Mat();
        Imgproc.erode(image, output, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2,2)));

        return output;
    }

    private static Mat dilateImage(Mat image) {
        Mat output = new Mat();
        Imgproc.dilate(image, output, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2,2)));

        return output;
    }

    private static List<MatOfPoint> getContours(Mat image) {
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(image, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        return contours;
    }

    private static MatOfPoint[] processContours(List<MatOfPoint> contours) {
        double[] similarities = new double[contours.size()];
        for(int i = 0; i < contours.size(); i++) {
            MatOfPoint currentContour = contours.get(i);

            //Filtering out small contours
            if(Imgproc.contourArea(currentContour) > MIN_AREA) {
                //Calculating similarity to the u shape of the goal
                double similarity = Imgproc.matchShapes(currentContour, stencil, Imgproc.CV_CONTOURS_MATCH_I3, 0);
                System.out.println(similarity);
                if(similarity < MIN_STENCIL_SIMILARITY) {
                    similarities[i] = similarity;
                }
                else similarities[i] = 1000;
            }
            else {
                similarities[i] = 1000;
            }
        }

        //Finding 2 most similar of the contours, lower similarity is better
        //2 targets found as there are 2 targets
        int mostSimilarGoals[] = {-1, -1};
        for(int i = 0; i < similarities.length; i++) {
            if(similarities[i] != 1000) {
                if(similarities[i] < ((mostSimilarGoals[1] == -1)? 1000: similarities[mostSimilarGoals[1]])) {
                    if(similarities[i] < ((mostSimilarGoals[0] == -1)? 1000: similarities[mostSimilarGoals[0]])) {
                        mostSimilarGoals[1] = mostSimilarGoals[0];
                        mostSimilarGoals[0] = i;
                    }
                    else {
                        mostSimilarGoals[1] = i;
                    }
                }
            }
        }

        //Find leftmost of 2 goals
        int left = 0, right = 0;
        if(mostSimilarGoals[1] != -1) {
            Point[][] convexHulls = {calculateConvexHull(contours.get(mostSimilarGoals[0])),
                    calculateConvexHull(contours.get(mostSimilarGoals[1]))};

            left = (convexHulls[0][2].x < convexHulls[1][2].x)? 0 : 1;
            right = (left == 0)? 1 : 0;
        }

        MatOfPoint[] targetContour = {new MatOfPoint(), new MatOfPoint()};
        if(mostSimilarGoals[left] == -1 || mostSimilarGoals[right] == -1) {
            System.out.println("No similar contours found");
        }
        else {
            targetContour[0] = contours.get(mostSimilarGoals[left]);
            targetContour[1] = contours.get(mostSimilarGoals[right]);
        }

        return targetContour;
    }

    //Calculating the convex hull of a roughly rectangular contour
    private static Point[] calculateConvexHull(MatOfPoint contour) {
        Point[] targetPoints = contour.toArray();
        Point[] convexHull = new Point[4];
        convexHull[0] = new Point(10000, 10000);
        convexHull[1] = new Point(0, 10000);
        convexHull[2] = new Point(0, 0);
        convexHull[3] = new Point(10000, 0);

        //Iterating through all points in the contour to find farthest in each direction
        for(int i = 0; i < targetPoints.length; i++) {
            Point currentPoint = targetPoints[i];
            if (convexHull[0].x + convexHull[0].y > currentPoint.x + currentPoint.y) convexHull[0] = currentPoint;
            if (convexHull[1].y - convexHull[1].x > currentPoint.y - currentPoint.x) convexHull[1] = currentPoint;
            if (convexHull[2].x + convexHull[2].y < currentPoint.x + currentPoint.y) convexHull[2] = currentPoint;
            if (convexHull[3].x - convexHull[3].y > currentPoint.x - currentPoint.y) convexHull[3] = currentPoint;
        }

        return convexHull;
    }
}
