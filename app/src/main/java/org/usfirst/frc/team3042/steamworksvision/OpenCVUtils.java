package org.usfirst.frc.team3042.steamworksvision;


import android.opengl.GLES20;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
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

    private static final double MIN_AREA = 200;
    private static final double MIN_STENCIL_SIMILARITY = 5;

    private static Mat stencil;
    private static Mat contoursFrame;

    private static Scalar lowerHSVBound;
    private static Scalar upperHSVBound;

    private static Mat filteredFrame;
    private static Mat erodedFrame;
    private static Mat dilatedFrame;
    private static Mat boilerDilatedFrame;
    private static List<MatOfPoint> contours;
    private static Point[] targetConvexHullLeft, targetConvexHullRight;
    private static Point[] boilerUpperHull;
    private static Point[] boilerLowerHull;
    private static MatOfPoint[] target;
    private static MatOfPoint boilerTargetUpper;
    private static MatOfPoint boilerTargetLower;

    private static boolean targetFound = false;

    private static double x, y, centerTopY, centerBottomY;

    private static double filterContoursMinArea = 300.0;
    private static double filterContoursMinPerimeter = 0;
    private static double filterContoursMinWidth = 0;
    private static double filterContoursMaxWidth = 1000;
    private static double filterContoursMinHeight = 0;
    private static double filterContoursMaxHeight = 1000;
    private static double[] filterContoursSolidity = {90.0, 100.0};
    private static double filterContoursMaxVertices = 1000.0;
    private static double filterContoursMinVertices = 4.0;
    private static double filterContoursMinRatio = 0.3;
    private static double filterContoursMaxRatio = 0.6;

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

        contours = filterContours(contours, filterContoursMinArea, filterContoursMinPerimeter,
                filterContoursMinWidth, filterContoursMaxWidth, filterContoursMinHeight,
                filterContoursMaxHeight, filterContoursSolidity, filterContoursMaxVertices,
                filterContoursMinVertices, filterContoursMinRatio, filterContoursMaxRatio);

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
            Imgproc.cvtColor(filteredFrame, filteredFrame, Imgproc.COLOR_GRAY2BGRA);

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

        releaseMats();

        GLES20.glActiveTexture(GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_2D, texOut);
        GLES20.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, outBuffer);

        if(targetFound) {
            targets.add(new TargetInfo(x, y, centerTopY, centerBottomY));
        }

        return targets;
    }

    //Getting boiler data
    public static ArrayList<TargetInfo> processBoilerImage(int texIn, int texOut, int width, int height, int lowerH, int upperH,
                                                     int lowerS, int upperS, int lowerV, int upperV, boolean outputHSVFrame) {
        ArrayList<TargetInfo> targets = new ArrayList<>();

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
        boilerDilatedFrame = dilatedFrame.clone();

        contours = getContours(dilatedFrame);

        contoursFrame = dilatedFrame;
        Imgproc.cvtColor(contoursFrame, contoursFrame, Imgproc.COLOR_GRAY2BGR);

        Imgproc.drawContours(contoursFrame, contours, -1, new Scalar(255, 255, 0), 1);

        boilerTargetUpper = processContoursBoiler(contours);

        boilerUpperHull = calculateConvexHull(boilerTargetUpper);

        Mat croppedImage = boilerCropImage(boilerUpperHull, boilerDilatedFrame);

        List<MatOfPoint> croppedContours = getContours(croppedImage);

        boilerTargetLower = processContoursBoiler(croppedContours);

        boilerLowerHull = calculateConvexHull(boilerTargetLower);

        for(int i = 0; i<boilerLowerHull.length; i++){
            boilerLowerHull[i].x+=cropX;
            boilerLowerHull[i].y+=cropY;
        }

        Point targetCenter = getConvexHullCenter(boilerUpperHull);
        Point targetCenter2 = getConvexHullCenter(boilerLowerHull);

        int pixelDistance = (int) (targetCenter2.y-targetCenter.y);

        outputOverlayImage(contoursFrame, boilerUpperHull, boilerLowerHull);

        // Outputting Mat to the screen
        ByteBuffer outBuffer;

        if(outputHSVFrame) {
            Imgproc.cvtColor(filteredFrame, filteredFrame, Imgproc.COLOR_GRAY2BGRA);

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

        releaseMats();

        GLES20.glActiveTexture(GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_2D, texOut);
        GLES20.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, outBuffer);

        if(targetFound) {
            targets.add(new TargetInfo(pixelDistance, -9999, centerTopY, centerBottomY));
            //Hello darkness my old friend... I've come to talk to you again...
            //Hello darkness my old friend I've come to talk with you again because a vision softly creeping left its seeds while I was sleeping, and the vision that was planted in my brain still remains, within the sound of silence. In restless dreams I walked alone, narrow streets of cobble stone. Neath the halo of a street lamp I turned my collar to the cold and damn, when my eyes were stabbed by the flash of a neon light, that split the night. And touched the sound of silence. And in the naked light I saw, ten thousand people maybe more, people talking without speaking, people hearing without listening, people writing songs that voices never share and no one dared. Disturb the sound of silence. Fools, said I, you do not know, silence like a cancer grows, hear my words that I might teach you, take my arms that I might reach you. But my words, like silent raindrops fell, and echoed in the wells of silence. And the people bowed and prayed to the neon god they made and the sign flashed out its warning, in the words that it was forming and the sign said the words of the prophets are written on the subway walls, and tenement halls. And whispered in the sounds of silence.
        }

        return targets;
    }

    // Releases all Mats to keep memory clear
    private static void releaseMats() {
        filteredFrame.release();
        erodedFrame.release();
        dilatedFrame.release();
        boilerDilatedFrame.release();

        target[0].release();
        target[1].release();

        boilerTargetUpper.release();
        boilerTargetLower.release();

        for(int i = 0; i < contours.size(); i++) {
            contours.get(i).release();
        }
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

        centerTopY = (targetConvexHullLeft[0].y + targetConvexHullLeft[1].y + targetConvexHullRight[0].y + targetConvexHullRight[1].y) / 4;
        centerBottomY = (targetConvexHullLeft[2].y + targetConvexHullLeft[3].y + targetConvexHullRight[2].y + targetConvexHullRight[3].y) / 4;

        x = centerX;
        y = centerY;
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

    /**
     * Filters out contours that do not meet certain criteria.
     * @param inputContours is the input list of contours
     * @param minArea is the minimum area of a contour that will be kept
     * @param minPerimeter is the minimum perimeter of a contour that will be kept
     * @param minWidth minimum width of a contour
     * @param maxWidth maximum width
     * @param minHeight minimum height
     * @param maxHeight maximimum height
     * @param solidity the minimum and maximum solidity of a contour
     * @param minVertexCount minimum vertex Count of the contours
     * @param maxVertexCount maximum vertex Count
     * @param minRatio minimum ratio of width to height
     * @param maxRatio maximum ratio of width to height
     */
    private static List<MatOfPoint> filterContours(List<MatOfPoint> inputContours, double minArea,
                                double minPerimeter, double minWidth, double maxWidth, double minHeight, double
                                        maxHeight, double[] solidity, double maxVertexCount, double minVertexCount, double
                                        minRatio, double maxRatio) {
        final MatOfInt hull = new MatOfInt();
        List<MatOfPoint> output = new ArrayList<>();
        output.clear();
        //operation
        for (int i = 0; i < inputContours.size(); i++) {
            final MatOfPoint contour = inputContours.get(i);
            final Rect bb = Imgproc.boundingRect(contour);
            if (bb.width < minWidth || bb.width > maxWidth) continue;
            if (bb.height < minHeight || bb.height > maxHeight) continue;
            final double area = Imgproc.contourArea(contour);
            if (area < minArea) continue;
            if (Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true) < minPerimeter) continue;
            Imgproc.convexHull(contour, hull);
            MatOfPoint mopHull = new MatOfPoint();
            mopHull.create((int) hull.size().height, 1, CvType.CV_32SC2);
            for (int j = 0; j < hull.size().height; j++) {
                int index = (int)hull.get(j, 0)[0];
                double[] point = new double[] { contour.get(index, 0)[0], contour.get(index, 0)[1]};
                mopHull.put(j, 0, point);
            }
            final double solid = 100 * area / Imgproc.contourArea(mopHull);
            if (solid < solidity[0] || solid > solidity[1]) continue;
            if (contour.rows() < minVertexCount || contour.rows() > maxVertexCount)	continue;
            final double ratio = bb.width / (double)bb.height;
            if (ratio < minRatio || ratio > maxRatio) continue;
            output.add(contour);
        }

        return output;
    }

    private static MatOfPoint[] processContours(List<MatOfPoint> contours) {
        double[] similarities = new double[contours.size()];
        for (int i = 0; i < contours.size(); i++) {
            MatOfPoint currentContour = contours.get(i);

            // Filtering out small contours
            if (Imgproc.contourArea(currentContour) > MIN_AREA) {
                // Calculating similarity to the u shape of the goal
                double similarity = Imgproc.matchShapes(currentContour, stencil, Imgproc.CV_CONTOURS_MATCH_I3, 0);
                System.out.println(similarity);
                if (similarity < MIN_STENCIL_SIMILARITY) {
                    similarities[i] = similarity;
                } else similarities[i] = 1000;
            } else {
                similarities[i] = 1000;
            }
        }

        // Finding 2 most similar of the contours, lower similarity is better
        // 2 targets found as there are 2 targets
        int mostSimilarGoals[] = {-1, -1};
        for (int i = 0; i < similarities.length; i++) {
            if (similarities[i] != 1000) {
                if (similarities[i] < ((mostSimilarGoals[1] == -1) ? 1000 : similarities[mostSimilarGoals[1]])) {
                    if (similarities[i] < ((mostSimilarGoals[0] == -1) ? 1000 : similarities[mostSimilarGoals[0]])) {
                        mostSimilarGoals[1] = mostSimilarGoals[0];
                        mostSimilarGoals[0] = i;
                    } else {
                        mostSimilarGoals[1] = i;
                    }
                }
            }
        }

        // Find leftmost of 2 goals
        int left = 0, right = 0;
        if (mostSimilarGoals[1] != -1) {
            Point[][] convexHulls = {calculateConvexHull(contours.get(mostSimilarGoals[0])),
                    calculateConvexHull(contours.get(mostSimilarGoals[1]))};

            left = (convexHulls[0][2].x < convexHulls[1][2].x) ? 0 : 1;
            right = (left == 0) ? 1 : 0;
        }

        MatOfPoint[] targetContour = {new MatOfPoint(), new MatOfPoint()};
        if (mostSimilarGoals[left] == -1 || mostSimilarGoals[right] == -1) {
            targetFound = false;

            System.out.println("No similar contours found");
        } else {
            targetFound = true;

            targetContour[0] = contours.get(mostSimilarGoals[left]);
            targetContour[1] = contours.get(mostSimilarGoals[right]);
        }

        return targetContour;
    }

    // Calculating the convex hull of a roughly rectangular contour
    private static Point[] calculateConvexHull(MatOfPoint contour) {
        Point[] targetPoints = contour.toArray();
        Point[] convexHull = new Point[4];
        convexHull[0] = new Point(10000, 10000);
        convexHull[1] = new Point(0, 10000);
        convexHull[2] = new Point(0, 0);
        convexHull[3] = new Point(10000, 0);

        // Iterating through all points in the contour to find farthest in each direction
        for(int i = 0; i < targetPoints.length; i++) {
            Point currentPoint = targetPoints[i];
            if (convexHull[0].x + convexHull[0].y > currentPoint.x + currentPoint.y) convexHull[0] = currentPoint;
            if (convexHull[1].y - convexHull[1].x > currentPoint.y - currentPoint.x) convexHull[1] = currentPoint;
            if (convexHull[2].x + convexHull[2].y < currentPoint.x + currentPoint.y) convexHull[2] = currentPoint;
            if (convexHull[3].x - convexHull[3].y > currentPoint.x - currentPoint.y) convexHull[3] = currentPoint;
        }

        return convexHull;
    }

    //Methods for boiler target acquisition
    private static Mat boilerStencil;
    private static MatOfPoint processContoursBoiler(List<MatOfPoint> contours) {
        //Set up boiler stencil
        boilerStencil = new Mat(8, 1, CvType.CV_32SC2);
        boilerStencil.put(0, 0, new int[]{/*p1*/0, 19, /*p2*/ 6, 5, /*p3*/ 20, 0, /*p4*/ 46, 0,
                /*p5*/ 58, 5, /*p6*/ 64, 19, /*p7*/ 46, 16, /*p8*/ 20, 16, /*p9 0, 20*/});

        double[] similarities = new double[contours.size()];
        for(int i = 0; i < contours.size(); i++) {
            MatOfPoint currentContour = contours.get(i);

            //Filtering out small contours
            if(Imgproc.contourArea(currentContour) > 60) {
                //Calculating similarity to the u shape of the goal
                double similarity = Imgproc.matchShapes(currentContour, boilerStencil, Imgproc.CV_CONTOURS_MATCH_I3, 0);
                //System.out.println(similarity);
                if(similarity < 20) {
                    similarities[i] = similarity;
                }
                else similarities[i] = 1000;
            }
            else {
                similarities[i] = 1000;
            }
        }

        //Finding 2 most similar of the contours, lower similarity is better
        //2 targets found as up to two goals could be in vision
        int mostSimilarGoal = -1;
        for(int i = 0; i < similarities.length; i++) {
            if(similarities[i] != 1000) {
                if(similarities[i] < ((mostSimilarGoal == -1)? 1000: similarities[mostSimilarGoal])) {
                    mostSimilarGoal = i;
                }
            }
        }

        MatOfPoint targetContour;
        if(mostSimilarGoal == -1) {
            System.out.println("No similar contour found");
            targetContour = new MatOfPoint();
        }
        else {
            targetContour = contours.get(mostSimilarGoal);
        }

        return targetContour;
    }

    private static int croppingHeight = 70;
    private static int croppingError = 8;
    private static int cropX = 0;
    private static int cropY = 0;
    private static Mat boilerCropImage(Point[] convexHull, Mat image){
        double lowestY = 0;
        double leftX = image.width(), rightX = 0;

        for(int i = 0; i < convexHull.length; i++){
            if(lowestY < convexHull[i].y){
                lowestY = convexHull[i].y;
            }
            if(leftX > convexHull[i].x){
                leftX = convexHull[i].x;
            }
            if(rightX < convexHull[i].x){
                rightX = convexHull[i].x;
            }
        }

        //Add in error for the cropping
        leftX -= croppingError;
        rightX += croppingError;

        //Calculate values for rectangle
        int x = (int)leftX;
        int y = (int)lowestY;
        int width = (int)rightX - x;
        int height = croppingHeight;

        Rect cropROI = new Rect( x, y, width, height);

        Mat cropped = new Mat(image, cropROI);

        cropX = x;
        cropY = y;

        return cropped;
    }

    private static Point getConvexHullCenter(Point[] convexHull){
        int avgX = 0;
        int avgY = 0;

        for(int i = 0; i< convexHull.length; i++){
            avgX += convexHull[i].x;
            avgY += convexHull[i].y;
        }

        avgX/=4;
        avgY/=4;

        Point center = new Point(avgX,avgY);

        return center;
    }
}
